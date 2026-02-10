@file:OptIn(ExperimentalLayoutApi::class)

package com.danmuapi.manager.ui.screens.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.danmuapi.manager.data.model.LogsResponse
import com.danmuapi.manager.data.model.ServerLogEntry
import com.danmuapi.manager.ui.components.ManagerCard
import com.danmuapi.manager.ui.screens.LogsScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun LogsTabContent(
    rootAvailable: Boolean?,
    serviceRunning: Boolean,
    adminToken: String,
    consoleLogLimit: Int,
    onSetConsoleLogLimit: (Int) -> Unit,
    serverLogs: List<ServerLogEntry>,
    serverLogsLoading: Boolean,
    serverLogsError: String?,
    onRefreshServerLogs: () -> Unit,
    onClearServerLogs: () -> Unit,
    moduleLogs: LogsResponse?,
    onRefreshModuleLogs: () -> Unit,
    onClearModuleLogs: () -> Unit,
    onReadModuleLogTail: (path: String, lines: Int, onResult: (String) -> Unit) -> Unit,
) {
    var selected by remember { mutableIntStateOf(0) }
    val segments = listOf("服务日志", "模块日志")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            segments.forEachIndexed { idx, title ->
                FilterChip(
                    selected = selected == idx,
                    onClick = { selected = idx },
                    label = { Text(title) },
                )
            }
        }

        HorizontalDivider()

        val presets = listOf(100, 300, 600, 1200)
        var limitText by remember(consoleLogLimit) { mutableStateOf(consoleLogLimit.toString()) }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "显示",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                presets.forEach { v ->
                    FilterChip(
                        selected = consoleLogLimit == v,
                        onClick = { onSetConsoleLogLimit(v) },
                        label = { Text("$v") },
                    )
                }
            }
            OutlinedTextField(
                value = limitText,
                onValueChange = { raw -> limitText = raw.filter { it.isDigit() }.take(5) },
                modifier = Modifier.width(92.dp),
                singleLine = true,
                label = { Text("条数") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedButton(
                onClick = {
                    val v = limitText.toIntOrNull()
                    if (v != null) onSetConsoleLogLimit(v)
                },
                enabled = limitText.toIntOrNull() != null,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text("应用")
            }
        }

        when (selected) {
            0 -> ServerLogsView(
                serviceRunning = serviceRunning,
                adminToken = adminToken,
                maxDisplayLines = consoleLogLimit,
                logs = serverLogs,
                loading = serverLogsLoading,
                error = serverLogsError,
                onRefresh = onRefreshServerLogs,
                onClear = onClearServerLogs,
            )
            else -> ModuleLogsView(
                rootAvailable = rootAvailable,
                tailLines = consoleLogLimit,
                logsResponse = moduleLogs,
                onRefresh = onRefreshModuleLogs,
                onClear = onClearModuleLogs,
                onReadTail = onReadModuleLogTail,
            )
        }
    }
}

@Composable
private fun ServerLogsView(
    serviceRunning: Boolean,
    adminToken: String,
    maxDisplayLines: Int,
    logs: List<ServerLogEntry>,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onClear: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var autoRefresh by remember { mutableStateOf(false) }
    var followTail by remember { mutableStateOf(true) }
    var filter by remember { mutableStateOf("all") }
    var keyword by remember { mutableStateOf("") }
    var confirmCopyAll by remember { mutableStateOf(false) }

    LaunchedEffect(autoRefresh, serviceRunning) {
        if (!serviceRunning) return@LaunchedEffect
        while (autoRefresh) {
            onRefresh()
            delay(2000)
        }
    }

    val filtered = remember(logs, filter, keyword) {
        val kw = keyword.trim().lowercase(Locale.getDefault())
        logs.filter { e ->
            val okLevel = when (filter) {
                "error" -> e.level.equals("error", true) || e.level.equals("fatal", true)
                "warn" -> e.level.equals("warn", true) || e.level.equals("warning", true)
                "info" -> e.level.equals("info", true)
                "debug" -> e.level.equals("debug", true) || e.level.equals("trace", true)
                else -> true
            }
            val okKw = kw.isBlank() ||
                e.message.lowercase(Locale.getDefault()).contains(kw) ||
                e.level.lowercase(Locale.getDefault()).contains(kw) ||
                e.timestamp.lowercase(Locale.getDefault()).contains(kw)
            okLevel && okKw
        }
    }

    val safeMax = maxDisplayLines.coerceIn(50, 5000)
    val displayLogs = remember(filtered, safeMax) {
        if (filtered.size > safeMax) filtered.takeLast(safeMax) else filtered
    }
    val truncated = filtered.size > displayLogs.size
    val displayText = remember(displayLogs) { displayLogs.joinToString("\n") { it.toLine() } }

    val warnColor = remember { Color(0xFFFFC107) }
    val errorColor = MaterialTheme.colorScheme.error
    val debugColor = MaterialTheme.colorScheme.onSurfaceVariant
    val normalColor = MaterialTheme.colorScheme.onSurface

    fun lineColor(levelRaw: String): Color {
        val lv = levelRaw.trim().uppercase(Locale.getDefault())
        return when {
            lv == "ERROR" || lv == "FATAL" -> errorColor
            lv == "WARN" || lv == "WARNING" -> warnColor
            lv == "DEBUG" || lv == "TRACE" -> debugColor
            else -> normalColor
        }
    }

    val listState = rememberLazyListState()
    val tailKey = remember(displayLogs) {
        displayLogs.lastOrNull()?.let { "${it.timestamp}|${it.level}|${it.message.hashCode()}" } ?: ""
    }
    LaunchedEffect(tailKey, followTail) {
        if (!followTail) return@LaunchedEffect
        if (displayLogs.isNotEmpty()) {
            try {
                listState.scrollToItem(displayLogs.lastIndex)
            } catch (_: Throwable) {
            }
        }
    }

    if (confirmCopyAll) {
        AlertDialog(
            onDismissRequest = { confirmCopyAll = false },
            title = { Text("复制全部日志？") },
            text = {
                Text("当前筛选结果共 ${filtered.size} 条。复制全部可能较大，部分机型会变慢。\n\n建议优先复制当前显示内容。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmCopyAll = false
                        clipboard.setText(AnnotatedString(filtered.joinToString("\n") { it.toLine() }))
                    }
                ) { Text("仍要复制") }
            },
            dismissButton = { TextButton(onClick = { confirmCopyAll = false }) { Text("取消") } },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ManagerCard(title = "服务日志") {
                Text("来自 /api/logs。", style = MaterialTheme.typography.bodyMedium)

                Spacer(Modifier.height(10.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = onRefresh, enabled = serviceRunning && !loading) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("刷新")
                    }

                    OutlinedButton(
                        onClick = { clipboard.setText(AnnotatedString(displayText)) },
                        enabled = displayText.isNotBlank(),
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (truncated) "复制当前显示" else "复制")
                    }

                    if (truncated && filtered.isNotEmpty()) {
                        OutlinedButton(onClick = { confirmCopyAll = true }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("复制全部")
                        }
                    }

                    if (adminToken.isNotBlank()) {
                        OutlinedButton(onClick = onClear, enabled = serviceRunning && !loading) {
                            Icon(Icons.Filled.Delete, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("清空")
                        }
                    }

                    if (!followTail && displayLogs.isNotEmpty()) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    try {
                                        listState.scrollToItem(displayLogs.lastIndex)
                                    } catch (_: Throwable) {
                                    }
                                }
                            },
                        ) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("到底部")
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(selected = filter == "all", onClick = { filter = "all" }, label = { Text("全部") })
                    FilterChip(selected = filter == "info", onClick = { filter = "info" }, label = { Text("Info") })
                    FilterChip(selected = filter == "warn", onClick = { filter = "warn" }, label = { Text("Warn") })
                    FilterChip(selected = filter == "error", onClick = { filter = "error" }, label = { Text("Error") })
                    FilterChip(selected = filter == "debug", onClick = { filter = "debug" }, label = { Text("Debug") })
                }

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("搜索") },
                    placeholder = { Text("关键词 / 时间 / 级别") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                )

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = autoRefresh,
                            onCheckedChange = { autoRefresh = it },
                            enabled = serviceRunning,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("自动刷新（2s）")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = followTail,
                            onCheckedChange = { followTail = it },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("跟随底部")
                    }
                }

                Spacer(Modifier.height(6.dp))

                Text(
                    "当前：${displayLogs.size}${if (truncated) "（已截断显示最后 $safeMax 条 / 共 ${filtered.size} 条）" else " 条"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (!serviceRunning) {
                    Spacer(Modifier.height(8.dp))
                    Text("服务未运行，无法读取服务日志。", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        item {
            when {
                loading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("加载中…")
                    }
                }

                error != null -> {
                    Text("加载失败：$error", color = MaterialTheme.colorScheme.error)
                }

                displayLogs.isEmpty() -> {
                    Text("暂无日志")
                }

                else -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    ) {
                        SelectionContainer {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 260.dp, max = 560.dp),
                                contentPadding = PaddingValues(12.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                items(displayLogs) { e ->
                                    Text(
                                        text = e.toLine(),
                                        color = lineColor(e.level),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun ServerLogEntry.toLine(): String {
    val ts = timestamp.ifBlank { "-" }
    val lv = level.ifBlank { "-" }
        .uppercase(Locale.getDefault())
        .padEnd(5)
        .take(5)
    return "$ts  $lv  $message"
}

@Composable
private fun ModuleLogsView(
    rootAvailable: Boolean?,
    tailLines: Int,
    logsResponse: LogsResponse?,
    onRefresh: () -> Unit,
    onClear: () -> Unit,
    onReadTail: (path: String, lines: Int, onResult: (String) -> Unit) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onRefresh) {
                Text("刷新")
            }
        }

        if (rootAvailable == false) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("需要 Root 权限", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "模块日志需要 Root 执行脚本读取文件。请确保已授予 Root 权限。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        LogsScreen(
            paddingValues = PaddingValues(0.dp),
            logs = logsResponse,
            tailLines = tailLines,
            onClearAll = onClear,
            onReadTail = onReadTail,
        )
    }
}

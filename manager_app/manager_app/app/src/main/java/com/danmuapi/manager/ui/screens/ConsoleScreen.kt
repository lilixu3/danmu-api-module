@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.danmuapi.manager.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.material3.Surface
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.danmuapi.manager.data.model.EnvVarItem
import com.danmuapi.manager.data.model.EnvVarMeta
import com.danmuapi.manager.data.model.LogsResponse
import com.danmuapi.manager.data.model.ServerConfigResponse
import com.danmuapi.manager.data.model.ServerLogEntry
import com.danmuapi.manager.network.HttpResult
import com.danmuapi.manager.ui.components.ManagerCard
import com.danmuapi.manager.util.rememberLanIpv4Addresses
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Locale

@Composable
fun ConsoleScreen(
    paddingValues: PaddingValues,
    rootAvailable: Boolean?,
    serviceRunning: Boolean,
    apiToken: String,
    apiPort: Int,
    apiHost: String,
    adminToken: String,
    sessionAdminToken: String,
    consoleLogLimit: Int,
    onSetConsoleLogLimit: (Int) -> Unit,
    onSetSessionAdminToken: (String) -> Unit,
    onClearSessionAdminToken: () -> Unit,
    serverConfig: ServerConfigResponse?,
    serverConfigLoading: Boolean,
    serverConfigError: String?,
    serverLogs: List<ServerLogEntry>,
    serverLogsLoading: Boolean,
    serverLogsError: String?,
    moduleLogs: LogsResponse?,
    onRefreshConfig: (useAdminToken: Boolean) -> Unit,
    onRefreshServerLogs: () -> Unit,
    onClearServerLogs: () -> Unit,
    onSetEnv: (key: String, value: String) -> Unit,
    onDeleteEnv: (key: String) -> Unit,
    onClearCache: () -> Unit,
    onDeploy: () -> Unit,
    onRefreshModuleLogs: () -> Unit,
    onClearModuleLogs: () -> Unit,
    onReadModuleLogTail: (path: String, lines: Int, onResult: (String) -> Unit) -> Unit,
    requestApi: suspend (
        method: String,
        path: String,
        query: Map<String, String?>,
        bodyJson: String?,
        useAdminToken: Boolean,
    ) -> HttpResult,
    validateAdminToken: suspend (token: String) -> Pair<Boolean, String?>,
) {
    val tabs = listOf("日志", "接口", "推送", "系统")
    var selectedTab by remember { mutableIntStateOf(0) }

    // Console admin features are unlocked only when user explicitly enters ADMIN_TOKEN in this run.
    // (Do not auto-use ADMIN_TOKEN from .env.)
    val effectiveAdminToken = sessionAdminToken.trim()

    // Initial refresh (best-effort)
    LaunchedEffect(serviceRunning) {
        if (serviceRunning && serverConfig == null && !serverConfigLoading) {
            onRefreshConfig(false)
        }
        if (serviceRunning && serverLogs.isEmpty() && !serverLogsLoading) {
            onRefreshServerLogs()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 16.dp,
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        when (selectedTab) {
            0 -> LogsTab(
                rootAvailable = rootAvailable,
                serviceRunning = serviceRunning,
                adminToken = effectiveAdminToken,
                consoleLogLimit = consoleLogLimit,
                onSetConsoleLogLimit = onSetConsoleLogLimit,
                serverLogs = serverLogs,
                serverLogsLoading = serverLogsLoading,
                serverLogsError = serverLogsError,
                onRefreshServerLogs = onRefreshServerLogs,
                onClearServerLogs = onClearServerLogs,
                moduleLogs = moduleLogs,
                onRefreshModuleLogs = onRefreshModuleLogs,
                onClearModuleLogs = onClearModuleLogs,
                onReadModuleLogTail = onReadModuleLogTail,
            )

            1 -> ApiTestTab(
                serviceRunning = serviceRunning,
                adminToken = effectiveAdminToken,
                requestApi = requestApi,
            )

            2 -> PushDanmuTab(
                serviceRunning = serviceRunning,
                apiToken = apiToken,
                apiPort = apiPort,
                requestApi = requestApi,
            )

            3 -> SystemSettingsTab(
                rootAvailable = rootAvailable,
                serviceRunning = serviceRunning,
                adminTokenFromEnv = adminToken,
                sessionAdminToken = sessionAdminToken,
                onSetSessionAdminToken = onSetSessionAdminToken,
                onClearSessionAdminToken = onClearSessionAdminToken,
                validateAdminToken = validateAdminToken,
                serverConfig = serverConfig,
                loading = serverConfigLoading,
                error = serverConfigError,
                onRefreshConfig = onRefreshConfig,
                onSetEnv = onSetEnv,
                onDeleteEnv = onDeleteEnv,
                onClearCache = onClearCache,
                onDeploy = onDeploy,
            )
        }
    }
}

private fun categoryLabel(category: String): String {
    return when (category.lowercase(Locale.getDefault())) {
        "api" -> "API"
        "source" -> "数据源"
        "match" -> "匹配"
        "danmu" -> "弹幕"
        "cache" -> "缓存"
        "system" -> "系统"
        else -> category
    }
}

@Composable
private fun categoryAccentColor(category: String): androidx.compose.ui.graphics.Color {
    val c = category.lowercase(Locale.getDefault())
    return when {
        c.contains("api") -> MaterialTheme.colorScheme.primary
        c.contains("source") -> MaterialTheme.colorScheme.secondary
        c.contains("match") -> MaterialTheme.colorScheme.tertiary
        c.contains("cache") -> MaterialTheme.colorScheme.secondary
        c.contains("system") -> MaterialTheme.colorScheme.tertiary
        c.contains("danmu") -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.primary
    }
}

// ===========================
// 日志
// ===========================

@Composable
private fun LogsTab(
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

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            segments.forEachIndexed { idx, title ->
                FilterChip(
                    selected = selected == idx,
                    onClick = { selected = idx },
                    label = { Text(title) },
                    colors = FilterChipDefaults.filterChipColors(),
                )
            }
        }

        HorizontalDivider()

        // Display limit (avoid a wall of text / jank when logs explode)
        val presets = listOf(100, 300, 600, 1200)
        var limitText by remember(consoleLogLimit) { mutableStateOf(consoleLogLimit.toString()) }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("显示", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                onValueChange = { raw ->
                    limitText = raw.filter { it.isDigit() }.take(5)
                },
                modifier = Modifier.width(96.dp),
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

    // Auto refresh (when service is running)
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

    // Keep UI smooth
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
                Text("当前筛选结果共 ${filtered.size} 条。复制全部可能较大，部分机型会变慢。\n\n建议优先复制“当前显示”。")
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

                // Actions
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

                // Filters
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
                        // Line-by-line rendering so we can color ERROR/WARN.
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
    // Keep it compact & readable in a monospace panel.
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

        // Reuse existing log file viewer (the old "日志"页面). No separate route.
        LogsScreen(
            paddingValues = PaddingValues(0.dp),
            logs = logsResponse,
            tailLines = tailLines,
            onClearAll = onClear,
            onReadTail = onReadTail,
        )
    }
}

// ===========================
// 接口调试
// ===========================

private data class ApiParam(
    val name: String,
    val label: String,
    val type: String = "text", // text/select
    val required: Boolean = false,
    val placeholder: String = "",
    val options: List<String> = emptyList(),
    val default: String? = null,
    val description: String = "",
)

private data class ApiEndpoint(
    val key: String,
    val name: String,
    val icon: String,
    val method: String,
    val path: String,
    val description: String,
    val params: List<ApiParam> = emptyList(),
    val hasBody: Boolean = false,
    val bodyTemplate: String? = null,
)

@Composable
private fun ApiTestTab(
    serviceRunning: Boolean,
    adminToken: String,
    requestApi: suspend (method: String, path: String, query: Map<String, String?>, bodyJson: String?, useAdminToken: Boolean) -> HttpResult,
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    // Clipboard on Android has a binder size limit; copying huge payloads can crash the app.
    val maxClipboardBytes = 500_000 // ~500KB (safe margin)

    var pendingExportText by remember { mutableStateOf<String?>(null) }
    var pendingExportName by remember { mutableStateOf("danmu-api.txt") }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
        onResult = { uri ->
            val content = pendingExportText ?: return@rememberLauncherForActivityResult
            if (uri == null) return@rememberLauncherForActivityResult
            val name = pendingExportName
            scope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(content.toByteArray(Charsets.UTF_8))
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "已导出：$name", Toast.LENGTH_SHORT).show()
                    }
                } catch (t: Throwable) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            "导出失败：${t.message ?: t.javaClass.simpleName}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    )

    fun copyToClipboardSafe(label: String, text: String) {
        val size = text.toByteArray(Charsets.UTF_8).size
        if (size > maxClipboardBytes) {
            Toast.makeText(context, "内容过大（约 ${humanBytes(size.toLong())}），建议导出为文件", Toast.LENGTH_LONG).show()
            return
        }
        try {
            clipboard.setText(AnnotatedString(text))
            Toast.makeText(context, "已复制：$label", Toast.LENGTH_SHORT).show()
        } catch (t: Throwable) {
            Toast.makeText(context, "复制失败：${t.message ?: t.javaClass.simpleName}", Toast.LENGTH_LONG).show()
        }
    }

    val endpoints = remember {
        listOf(
            ApiEndpoint(
                key = "searchAnime",
                name = "搜索动漫",
                icon = "🔍",
                method = "GET",
                path = "/api/v2/search/anime",
                description = "根据关键词搜索动漫（关键词也可为播放链接URL）",
                params = listOf(
                    ApiParam("keyword", "关键词/播放链接URL", "text", true, "例如：生万物 或 http://v.qq.com/…")
                )
            ),
            ApiEndpoint(
                key = "searchEpisodes",
                name = "搜索剧集",
                icon = "📺",
                method = "GET",
                path = "/api/v2/search/episodes",
                description = "搜索指定动漫的剧集列表",
                params = listOf(
                    ApiParam("anime", "动漫名称", "text", true, "例如：生万物"),
                    ApiParam("episode", "集数过滤", "text", false, "可选：纯数字 / movie"),
                )
            ),
            ApiEndpoint(
                key = "matchAnime",
                name = "匹配动漫",
                icon = "🎯",
                method = "POST",
                path = "/api/v2/match",
                description = "根据文件名智能匹配动漫",
                params = listOf(
                    ApiParam("fileName", "文件名", "text", true, "例如：生万物 S02E08")
                ),
                hasBody = true,
                bodyTemplate = "{\n  \"fileName\": \"\"\n}"
            ),
            ApiEndpoint(
                key = "getBangumi",
                name = "获取番剧详情",
                icon = "📋",
                method = "GET",
                path = "/api/v2/bangumi/:animeId",
                description = "获取指定番剧的详细信息",
                params = listOf(
                    ApiParam("animeId", "动漫ID", "text", true, "例如：236379")
                )
            ),
            ApiEndpoint(
                key = "getComment",
                name = "获取弹幕",
                icon = "💬",
                method = "GET",
                path = "/api/v2/comment/:commentId",
                description = "获取指定剧集的弹幕数据",
                params = listOf(
                    ApiParam("commentId", "弹幕ID", "text", true, "例如：10009"),
                    ApiParam("format", "格式", "select", false, options = listOf("json", "xml"), default = "json"),
                    ApiParam("segmentflag", "分片标志", "select", false, options = listOf("true", "false")),
                )
            ),
            ApiEndpoint(
                key = "getCommentByUrl",
                name = "通过URL获取弹幕",
                icon = "🔗",
                method = "GET",
                path = "/api/v2/comment",
                description = "通过视频URL直接获取弹幕",
                params = listOf(
                    ApiParam("url", "视频URL", "text", true, "例如：https://example.com/video.mp4"),
                    ApiParam("format", "格式", "select", false, options = listOf("json", "xml"), default = "json"),
                )
            ),
            ApiEndpoint(
                key = "getSegmentComment",
                name = "获取分片弹幕",
                icon = "🧩",
                method = "POST",
                path = "/api/v2/segmentcomment",
                description = "通过请求体获取分片弹幕",
                params = listOf(
                    ApiParam("format", "格式", "select", false, options = listOf("json", "xml"), default = "json"),
                ),
                hasBody = true,
                bodyTemplate = "{\n  \"url\": \"\",\n  \"platform\": \"qq\",\n  \"cid\": \"\",\n  \"start\": 0,\n  \"duration\": 600\n}"
            ),
        )
    }

    var selectedKey by remember { mutableStateOf(endpoints.first().key) }
    val selected = endpoints.first { it.key == selectedKey }

    val paramState = remember { mutableStateMapOf<String, String>() }
    LaunchedEffect(selectedKey) {
        paramState.clear()
        selected.params.forEach { p ->
            if (p.default != null) paramState[p.name] = p.default
        }
        if (selected.hasBody && selected.bodyTemplate != null) {
            // keep in body state
        }
    }

    var bodyText by remember { mutableStateOf(selected.bodyTemplate.orEmpty()) }
    LaunchedEffect(selectedKey) {
        bodyText = selected.bodyTemplate.orEmpty()
    }

    // Raw response may be large; keep UI rendering as a bounded preview.
    var responseRaw by remember { mutableStateOf("") }
    var responsePreview by remember { mutableStateOf("") }
    var responseHint by remember { mutableStateOf<String?>(null) }
    var responseMeta by remember { mutableStateOf("") }
    var responseContentType by remember { mutableStateOf<String?>(null) }
    var responseTruncatedByClient by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmCopyFull by remember { mutableStateOf(false) }

    fun send() {
        if (!serviceRunning) {
            error = "服务未运行"
            return
        }
        error = null
        loading = true
        responseRaw = ""
        responsePreview = ""
        responseHint = null
        responseTruncatedByClient = false
        responseMeta = ""
        responseContentType = null

        scope.launch {
            val useAdmin = false
            val method = selected.method

            // Build path
            var path = selected.path
            // Replace :param placeholders
            Regex(":([A-Za-z0-9_]+)").findAll(path).forEach { m ->
                val key = m.groupValues.getOrNull(1).orEmpty()
                val v = paramState[key].orEmpty().trim()
                path = path.replace(":$key", v)
            }

            // Build query
            val query = mutableMapOf<String, String?>()
            selected.params
                .filterNot { selected.path.contains(":${it.name}") }
                .forEach { p ->
                    val v = paramState[p.name]
                    if (!v.isNullOrBlank()) query[p.name] = v
                }

            val body = if (selected.hasBody) {
                if (selected.key == "matchAnime") {
                    // keep simple: if user only filled fileName, generate json
                    val fileName = paramState["fileName"].orEmpty().trim()
                    if (fileName.isNotBlank()) {
                        JSONObject().apply { put("fileName", fileName) }.toString()
                    } else {
                        bodyText
                    }
                } else {
                    bodyText
                }
            } else null

            val result = requestApi(method, path, query, body, useAdmin)
            loading = false

            if (result.isSuccessful) {
                responseRaw = result.body
                responseTruncatedByClient = result.truncated

                val sizeInfo = result.bodyBytesKept.takeIf { it > 0L }?.let { " • ${humanBytes(it)}" }.orEmpty()
                val ctInfo = result.contentType?.let { " • $it" }.orEmpty()
                val truncInfo = if (result.truncated) " • 已截断" else ""
                responseMeta = "HTTP ${result.code} • ${result.durationMs}ms$ctInfo$sizeInfo$truncInfo"

                responseContentType = result.contentType

                // Pretty print JSON only when it's small enough and not truncated.
                val pretty = if (!result.truncated) prettifyIfJson(result.body, maxChars = 160_000) else result.body

                // UI preview cap: large text layout can still ANR on some devices.
                val previewMaxChars = 60_000
                responsePreview = if (pretty.length > previewMaxChars) {
                    responseHint = "响应较大：仅预览前 ${previewMaxChars} 字符（建议使用“导出”保存完整内容）。"
                    pretty.take(previewMaxChars) + "\n\n…（预览已截断）"
                } else {
                    responseHint = if (result.truncated) {
                        "响应过大：已被客户端限制读取约 ${humanBytes(result.bodyBytesKept)}，用于避免卡死/闪退。"
                    } else null
                    pretty
                }
            } else {
                responseMeta = "HTTP ${result.code} • ${result.durationMs}ms"
                error = result.error ?: "请求失败"
                responseContentType = result.contentType
                responseRaw = result.body
                responsePreview = if (result.body.length > 60_000) result.body.take(60_000) + "\n\n…（预览已截断）" else result.body
                responseTruncatedByClient = result.truncated
                if (result.truncated) {
                    responseHint = "错误响应过大：已被客户端截断读取，避免卡死。"
                }
            }
        }
    }

    if (confirmCopyFull) {
        AlertDialog(
            onDismissRequest = { confirmCopyFull = false },
            title = { Text("响应较大") },
            text = {
                val size = responseRaw.toByteArray(Charsets.UTF_8).size.toLong()
                Text(
                    "当前已读取内容约 ${humanBytes(size)}。\n\n" +
                        "由于系统剪贴板有大小限制，复制完整内容可能导致闪退。\n" +
                        "建议：导出为文件，或仅复制预览。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmCopyFull = false
                        val name = suggestApiExportFileName(
                            endpoint = selected,
                            params = paramState.toMap(),
                            contentType = responseContentType,
                            truncated = responseTruncatedByClient
                        )
                        pendingExportName = name
                        pendingExportText = responseRaw
                        exportLauncher.launch(name)
                    }
                ) { Text("导出") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { confirmCopyFull = false }) { Text("取消") }
                    TextButton(
                        onClick = {
                            confirmCopyFull = false
                            copyToClipboardSafe("预览", responsePreview)
                        }
                    ) { Text("复制预览") }
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ManagerCard(title = "接口调试") {
                Text(
                    "在 App 内直接调用 danmu-api 接口，用于调试与排错。",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (!serviceRunning) {
                    Spacer(Modifier.height(8.dp))
                    Text("服务未运行，无法请求接口。", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        item {
            Text("选择接口", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            endpoints.forEach { ep ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedKey = ep.key },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedKey == ep.key) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(ep.icon, modifier = Modifier.width(28.dp))
                        Column(Modifier.weight(1f)) {
                            Text(ep.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${ep.method}  ${ep.path}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (ep.description.isNotBlank()) {
                                Text(
                                    ep.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        item {
            ManagerCard(title = "参数") {
                selected.params.forEach { p ->
                    Spacer(Modifier.height(8.dp))
                    when (p.type) {
                        "select" -> {
                            // Simple select: render as chips
                            Text(p.label, style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.height(4.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                p.options.forEach { opt ->
                                    FilterChip(
                                        selected = (paramState[p.name] ?: p.default) == opt,
                                        onClick = { paramState[p.name] = opt },
                                        label = { Text(opt) }
                                    )
                                }
                            }
                        }
                        else -> {
                            OutlinedTextField(
                                value = paramState[p.name].orEmpty(),
                                onValueChange = { paramState[p.name] = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text(p.label + if (p.required) " *" else "") },
                                placeholder = { if (p.placeholder.isNotBlank()) Text(p.placeholder) },
                            )
                        }
                    }
                }

                if (selected.hasBody) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = bodyText,
                        onValueChange = { bodyText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("请求体 (JSON)") },
                        minLines = 6,
                        maxLines = 12,
                    )
                }

                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = { send() }, enabled = !loading && serviceRunning) {
                        if (loading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("发送请求")
                    }
                    OutlinedButton(
                        onClick = {
                            copyToClipboardSafe("预览", responsePreview)
                        },
                        enabled = responsePreview.isNotBlank()
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("复制预览")
                    }
                    if (responseRaw.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                val name = suggestApiExportFileName(
                                    endpoint = selected,
                                    params = paramState.toMap(),
                                    contentType = responseContentType,
                                    truncated = responseTruncatedByClient
                                )
                                pendingExportName = name
                                pendingExportText = responseRaw
                                exportLauncher.launch(name)
                            },
                            enabled = responseRaw.isNotBlank()
                        ) {
                            Icon(Icons.Filled.FileDownload, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("导出")
                        }

                        OutlinedButton(
                            onClick = {
                                val bytes = responseRaw.toByteArray(Charsets.UTF_8).size
                                // For large payloads, don't try clipboard: it may crash.
                                if (bytes > maxClipboardBytes || responseTruncatedByClient) {
                                    confirmCopyFull = true
                                } else {
                                    copyToClipboardSafe("完整", responseRaw)
                                }
                            },
                            enabled = responseRaw.isNotBlank()
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("复制完整")
                        }
                    }
                }
            }
        }

        item {
            if (error != null) {
                Text("错误：$error", color = MaterialTheme.colorScheme.error)
            }
            if (responseMeta.isNotBlank()) {
                Text(responseMeta, style = MaterialTheme.typography.labelMedium)
            }
            if (responseHint != null) {
                Spacer(Modifier.height(6.dp))
                Text(responseHint!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (responsePreview.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    val scroll = rememberScrollState()
                    SelectionContainer {
                        Text(
                            responsePreview,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 140.dp, max = 520.dp)
                                .verticalScroll(scroll)
                                .padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}

private fun prettifyIfJson(raw: String, maxChars: Int = 120_000): String {
    if (raw.length > maxChars) return raw
    val t = raw.trim()
    if (!(t.startsWith("{") && t.endsWith("}")) && !(t.startsWith("[") && t.endsWith("]"))) return raw
    return try {
        if (t.startsWith("{")) JSONObject(t).toString(2) else JSONArray(t).toString(2)
    } catch (_: Throwable) {
        raw
    }
}

private fun humanBytes(bytes: Long): String {
    val b = bytes.coerceAtLeast(0L)
    val units = arrayOf("B", "KB", "MB", "GB")
    var v = b.toDouble()
    var i = 0
    while (v >= 1024.0 && i < units.lastIndex) {
        v /= 1024.0
        i++
    }
    return if (i == 0) "${b}${units[i]}" else String.format(Locale.getDefault(), "%.1f%s", v, units[i])
}


private fun sanitizeForFilenamePart(input: String?, maxLen: Int = 24): String {
    val raw = input.orEmpty().trim()
    if (raw.isBlank()) return ""
    // Remove characters not allowed in common file systems.
    val cleaned = raw
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .replace(Regex("\\s+"), "_")
        .replace(Regex("_+"), "_")
        .trim('_')
    return cleaned.take(maxLen)
}

private fun extractHostForFilename(url: String): String {
    return try {
        val host = java.net.URI(url.trim()).host.orEmpty()
        host.replace('.', '-').trim('-')
    } catch (_: Throwable) {
        ""
    }
}

private fun suggestApiExportFileName(
    endpoint: ApiEndpoint,
    params: Map<String, String>,
    contentType: String?,
    truncated: Boolean,
): String {
    val ts = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(java.util.Date())

    val ext = when {
        params["format"]?.equals("xml", true) == true -> "xml"
        params["format"]?.equals("json", true) == true -> "json"
        contentType?.contains("xml", true) == true -> "xml"
        contentType?.contains("json", true) == true -> "json"
        else -> "txt"
    }

    val base = when (endpoint.key) {
        "getComment" -> "comment-" + sanitizeForFilenamePart(params["commentId"], 16)
        "getBangumi" -> "bangumi-" + sanitizeForFilenamePart(params["animeId"], 16)
        "searchAnime" -> "search-anime-" + sanitizeForFilenamePart(params["keyword"], 18)
        "searchEpisodes" -> "search-episodes-" + sanitizeForFilenamePart(params["anime"], 18)
        "matchAnime" -> "match-" + sanitizeForFilenamePart(params["fileName"], 22)
        "getCommentByUrl" -> {
            val host = extractHostForFilename(params["url"].orEmpty())
            if (host.isNotBlank()) "comment-url-$host" else "comment-url"
        }
        "getSegmentComment" -> "segmentcomment"
        else -> sanitizeForFilenamePart(endpoint.key, 28).ifBlank { "danmu-api" }
    }.trim('-', '_')

    val suffix = if (truncated) "-partial" else ""
    val name = "$base-$ts$suffix.$ext"

    // Keep it readable but avoid ridiculously long file names.
    return if (name.length > 140) name.take(140) else name
}


// ===========================
// 推送弹幕
// ===========================

private data class AnimeItem(
    val animeId: Int,
    val title: String,
    val typeDesc: String = "",
)

private data class EpisodeItem(
    val episodeId: Int,
    val title: String,
    val episodeNumber: String = "",
)

@Composable
private fun PushDanmuTab(
    serviceRunning: Boolean,
    apiToken: String,
    apiPort: Int,
    requestApi: suspend (
        method: String,
        path: String,
        query: Map<String, String?>,
        bodyJson: String?,
        useAdminToken: Boolean
    ) -> HttpResult,
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    val lanIps = rememberLanIpv4Addresses()
    val lanIp = lanIps.firstOrNull()

    // Auto-detect candidate /24 subnets from current LAN IPs.
    // Since danmu-api runs locally, we don't expose subnet input to users.
    val detectedSubnets = remember(lanIps) {
        lanIps.mapNotNull { ip ->
            val parts = ip.split('.')
            if (parts.size == 4) parts.take(3).joinToString(".") else null
        }.distinct()
    }

    // Search state
    var keyword by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var animes by remember { mutableStateOf<List<AnimeItem>>(emptyList()) }
    var selectedAnime by remember { mutableStateOf<AnimeItem?>(null) }
    var episodes by remember { mutableStateOf<List<EpisodeItem>>(emptyList()) }
    var loadingEpisodes by remember { mutableStateOf(false) }

    // Push target (OK影视默认 9978)
    val okPushPath = remember { "/action?do=refresh&type=danmaku&path=" }
    var lanPort by remember { mutableStateOf("9978") }

    // One-shot push parameters (NOT persisted; only affects push URL query params)
    // OK影视 通常支持通过额外 query 参数控制弹幕大小/偏移（不同壳子实现可能略有差异）。
    var danmuSize by remember { mutableStateOf("") }
    var danmuOffset by remember { mutableStateOf("") }

    fun currentPort(): Int = lanPort.trim().toIntOrNull() ?: 9978

    fun buildPushTemplate(host: String, port: Int): String = "http://$host:$port$okPushPath"

    // Discovered devices
    var scanning by remember { mutableStateOf(false) }
    var scanProgress by remember { mutableIntStateOf(0) }
    var scanTotal by remember { mutableIntStateOf(0) }
    var foundDevices by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedDevice by remember { mutableStateOf<String?>(null) }
    var autoScan by remember { mutableStateOf(true) }
    var lastAutoScanKey by remember { mutableStateOf<String?>(null) }

    // Last push result
    var lastPushOk by remember { mutableStateOf<Boolean?>(null) }
    var lastPushMessage by remember { mutableStateOf<String?>(null) }

    val localHosts = remember(lanIps) { (setOf("127.0.0.1") + lanIps).toSet() }

    fun labelForHost(host: String): String {
        return when {
            host == "127.0.0.1" -> "本机 (127.0.0.1)"
            lanIps.contains(host) -> "本机 ($host)"
            else -> host
        }
    }

    fun currentPushTemplate(): String {
        val host = selectedDevice ?: "127.0.0.1"
        return buildPushTemplate(host, currentPort())
    }

    fun search() {
        if (!serviceRunning) {
            searchError = "服务未运行"
            return
        }
        val kw = keyword.trim()
        if (kw.isBlank()) {
            searchError = "请输入关键词"
            return
        }
        searching = true
        searchError = null
        selectedAnime = null
        episodes = emptyList()
        animes = emptyList()

        scope.launch {
            val res = requestApi(
                "GET",
                "/api/v2/search/anime",
                mapOf("keyword" to kw),
                null,
                false,
            )
            searching = false
            if (!res.isSuccessful) {
                searchError = res.error ?: "搜索失败"
                return@launch
            }
            try {
                val obj = JSONObject(res.body)
                val arr = obj.optJSONArray("animes") ?: JSONArray()
                val list = mutableListOf<AnimeItem>()
                for (i in 0 until arr.length()) {
                    val a = arr.optJSONObject(i) ?: continue
                    list.add(
                        AnimeItem(
                            animeId = a.optInt("animeId", a.optInt("bangumiId")),
                            title = a.optString("animeTitle", a.optString("title")),
                            typeDesc = a.optString("typeDescription"),
                        )
                    )
                }
                animes = list
                if (list.isEmpty()) searchError = "未找到结果"
            } catch (_: Throwable) {
                searchError = "解析响应失败"
            }
        }
    }

    fun loadEpisodes(anime: AnimeItem) {
        if (!serviceRunning) return
        selectedAnime = anime
        episodes = emptyList()
        loadingEpisodes = true
        scope.launch {
            val res = requestApi(
                "GET",
                "/api/v2/bangumi/${anime.animeId}",
                emptyMap<String, String?>(),
                null,
                false,
            )
            loadingEpisodes = false
            if (!res.isSuccessful) {
                searchError = res.error ?: "获取剧集失败"
                return@launch
            }
            try {
                val obj = JSONObject(res.body)
                val bangumi = obj.optJSONObject("bangumi") ?: JSONObject()
                val eps = bangumi.optJSONArray("episodes") ?: JSONArray()
                val list = mutableListOf<EpisodeItem>()
                for (i in 0 until eps.length()) {
                    val e = eps.optJSONObject(i) ?: continue
                    list.add(
                        EpisodeItem(
                            episodeId = e.optInt("episodeId"),
                            title = e.optString("episodeTitle"),
                            episodeNumber = e.optString("episodeNumber"),
                        )
                    )
                }
                episodes = list
            } catch (_: Throwable) {
                searchError = "解析剧集失败"
            }
        }
    }

    fun buildCommentUrl(episodeId: Int): String {
        // If pushing to local player (127.0.0.1 / 本机 IP), prefer loopback for maximum compatibility.
        val host = if (selectedDevice != null && localHosts.contains(selectedDevice)) {
            "127.0.0.1"
        } else {
            lanIp ?: "127.0.0.1"
        }
        return "http://$host:$apiPort/$apiToken/api/v2/comment/$episodeId?format=xml"
    }

    fun pushOne(episode: EpisodeItem) {
        val template = currentPushTemplate()
        val commentUrl = buildCommentUrl(episode.episodeId)

        // Extra per-push parameters
        val sizeText = danmuSize.trim()
        val offsetText = danmuOffset.trim()

        fun isFiniteNumber(s: String): Boolean {
            val d = s.toDoubleOrNull() ?: return false
            return d.isFinite()
        }

        if (sizeText.isNotBlank() && !isFiniteNumber(sizeText)) {
            lastPushOk = false
            lastPushMessage = "弹幕大小格式不正确：$sizeText"
            Toast.makeText(context, "弹幕大小必须是数字（可留空）", Toast.LENGTH_SHORT).show()
            return
        }
        if (offsetText.isNotBlank() && !isFiniteNumber(offsetText)) {
            lastPushOk = false
            lastPushMessage = "弹幕偏移量格式不正确：$offsetText"
            Toast.makeText(context, "偏移量必须是数字（可留空）", Toast.LENGTH_SHORT).show()
            return
        }

        val extras = buildString {
            if (sizeText.isNotBlank()) append("&size=").append(java.net.URLEncoder.encode(sizeText, "UTF-8"))
            if (offsetText.isNotBlank()) append("&offset=").append(java.net.URLEncoder.encode(offsetText, "UTF-8"))
        }

        val finalUrl = template + java.net.URLEncoder.encode(commentUrl, "UTF-8") + extras

        lastPushOk = null
        lastPushMessage = "推送中：${episode.episodeNumber.ifBlank { episode.episodeId.toString() }}"

        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val conn = java.net.URL(finalUrl).openConnection()
                    conn.connectTimeout = 1500
                    conn.readTimeout = 2500
                    conn.getInputStream().use { it.readBytes() }
                    true
                } catch (_: Throwable) {
                    false
                }
            }
            lastPushOk = ok
            lastPushMessage = if (ok) {
                "推送成功：${episode.episodeNumber} ${episode.title}".trim()
            } else {
                "推送失败：请确认目标设备可访问（${selectedDevice ?: "127.0.0.1"}:${currentPort()}）"
            }
            if (ok) {
                clipboard.setText(AnnotatedString(commentUrl))
                Toast.makeText(context, "已推送并复制弹幕链接", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "推送失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun scanLan() {
        if (scanning) return

        val port = currentPort()

        fun sortDevices(list: List<String>): List<String> {
            val lanSet = lanIps.toSet()
            return list.distinct().sortedWith(
                compareBy<String>({ it != "127.0.0.1" }, { !lanSet.contains(it) }, { it })
            )
        }

        scanning = true
        scanProgress = 0
        foundDevices = emptyList()

        scope.launch {
            val discovered = mutableListOf<String>()

            val candidates = mutableListOf<String>()
            // Always include localhost + this device LAN IPs
            candidates.addAll(localHosts)

            // Auto-scan detected /24 networks (no manual subnet input)
            detectedSubnets.forEach { subnet ->
                for (i in 1..254) {
                    candidates.add("$subnet.$i")
                }
            }

            val uniq = candidates.distinct()
            scanTotal = uniq.size

            val chunkSize = 64
            val timeoutMs = 250
            var done = 0

            for (chunk in uniq.chunked(chunkSize)) {
                val tasks = chunk.map { host ->
                    async(Dispatchers.IO) {
                        val ok = try {
                            Socket().use { s ->
                                s.connect(InetSocketAddress(host, port), timeoutMs)
                            }
                            true
                        } catch (_: Throwable) {
                            false
                        }
                        if (ok) host else null
                    }
                }

                tasks.forEach { d ->
                    val host = d.await()
                    if (host != null) discovered.add(host)
                }

                done += chunk.size
                scanProgress = done
                foundDevices = sortDevices(discovered)
            }

            scanning = false

            val sorted = sortDevices(discovered)
            foundDevices = sorted
            if (sorted.isNotEmpty() && (selectedDevice == null || !sorted.contains(selectedDevice))) {
                selectedDevice = sorted.first()
            }
        }
    }

    // Auto scan when selected anime / port / networks change.
    LaunchedEffect(selectedAnime?.animeId, lanPort, autoScan, serviceRunning, detectedSubnets) {
        val key = "${selectedAnime?.animeId}:${lanPort.trim()}:${detectedSubnets.joinToString(",")}"
        if (serviceRunning && selectedAnime != null && autoScan && key != lastAutoScanKey) {
            lastAutoScanKey = key
            scanLan()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ManagerCard(title = "弹幕推送") {
                Text(
                    "搜索番剧 → 选择剧集 → 推送到局域网播放器（自动检测网段/设备）。",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (!serviceRunning) {
                    Spacer(Modifier.height(8.dp))
                    Text("服务未运行，无法搜索/生成弹幕链接。", color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(8.dp))
                if (lanIp == null) {
                    Text("未检测到局域网 IPv4，跨设备推送可能不可用。", color = MaterialTheme.colorScheme.error)
                } else {
                    Text("本机 IP：$lanIp", style = MaterialTheme.typography.bodySmall)
                }
                if (detectedSubnets.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "已检测网段：${detectedSubnets.joinToString { "$it.*" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            ManagerCard(title = "搜索番剧") {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("关键词") },
                    placeholder = { Text("例如：鬼灭 / 进击 / 你的名字") },
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { search() }, enabled = !searching && serviceRunning) {
                        if (searching) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("搜索")
                    }
                    OutlinedButton(
                        onClick = {
                            keyword = ""
                            animes = emptyList()
                            episodes = emptyList()
                            selectedAnime = null
                            searchError = null
                        }
                    ) {
                        Icon(Icons.Filled.Clear, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("清空")
                    }
                }
                if (searchError != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(searchError!!, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        if (animes.isNotEmpty()) {
            item { Text("搜索结果", style = MaterialTheme.typography.titleMedium) }
            items(animes, key = { it.animeId }) { anime ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { loadEpisodes(anime) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedAnime?.animeId == anime.animeId)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(anime.title, style = MaterialTheme.typography.titleSmall)
                        if (anime.typeDesc.isNotBlank()) {
                            Text(
                                anime.typeDesc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text("ID: ${anime.animeId}", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        if (selectedAnime != null) {
            item {
                ManagerCard(title = "目标设备") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = lanPort,
                            onValueChange = { lanPort = it.filter { ch -> ch.isDigit() }.take(5) },
                            modifier = Modifier.width(120.dp),
                            singleLine = true,
                            label = { Text("端口") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(checked = autoScan, onCheckedChange = { autoScan = it })
                            Spacer(Modifier.width(8.dp))
                            Text("自动扫描", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Text("本次推送参数", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = danmuSize,
                            onValueChange = { danmuSize = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("弹幕大小") },
                            placeholder = { Text("留空=默认") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                        OutlinedTextField(
                            value = danmuOffset,
                            onValueChange = { danmuOffset = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("偏移量") },
                            placeholder = { Text("留空=默认") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "仅对本次推送请求生效，不会写入配置。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (danmuSize.trim().isNotBlank() || danmuOffset.trim().isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    danmuSize = ""
                                    danmuOffset = ""
                                }
                            ) {
                                Icon(Icons.Filled.Clear, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("重置参数")
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(onClick = { scanLan() }, enabled = !scanning) {
                            Text(if (scanning) "扫描中…" else "扫描设备")
                        }
                        if (scanTotal > 0) {
                            Text(
                                if (scanning) "$scanProgress/$scanTotal" else "已扫描 $scanTotal",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (foundDevices.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text("发现设备（点选）", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            foundDevices.forEach { host ->
                                FilterChip(
                                    selected = selectedDevice == host,
                                    onClick = { selectedDevice = host },
                                    label = { Text(labelForHost(host)) }
                                )
                            }
                        }
                    } else {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "未发现设备：请确认播放器已开启 ${currentPort()} 端口，或先手动在播放器中打开推送功能。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    val pushTemplate = currentPushTemplate()
                    Text("推送接口", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                pushTemplate,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(
                                onClick = { clipboard.setText(AnnotatedString(pushTemplate)) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            ) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("复制")
                            }
                        }
                    }

                    if (lastPushMessage != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            lastPushMessage!!,
                            color = when (lastPushOk) {
                                true -> MaterialTheme.colorScheme.primary
                                false -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        if (episodes.isNotEmpty()) {
            item {
                Text("剧集列表", style = MaterialTheme.typography.titleMedium)
                if (loadingEpisodes) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("加载中…")
                    }
                }
            }

            items(episodes, key = { it.episodeId }) { ep ->
                val commentUrl = remember(selectedDevice, lanPort, lanIp, ep.episodeId) {
                    buildCommentUrl(ep.episodeId)
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${ep.episodeNumber} ${ep.title}".trim(), style = MaterialTheme.typography.titleSmall)
                        Text("弹幕ID: ${ep.episodeId}", style = MaterialTheme.typography.labelSmall)

                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Text(
                                commentUrl,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(10.dp)
                            )
                        }

                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { pushOne(ep) },
                                enabled = serviceRunning
                            ) {
                                Text("推送")
                            }
                            OutlinedButton(onClick = { clipboard.setText(AnnotatedString(commentUrl)) }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("复制链接")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}



// ===========================
// 系统配置（环境变量可视化）
// ===========================

@Composable
private fun SystemSettingsTab(
    rootAvailable: Boolean?,
    serviceRunning: Boolean,
    adminTokenFromEnv: String,
    sessionAdminToken: String,
    onSetSessionAdminToken: (String) -> Unit,
    onClearSessionAdminToken: () -> Unit,
    validateAdminToken: suspend (token: String) -> Pair<Boolean, String?>,
    serverConfig: ServerConfigResponse?,
    loading: Boolean,
    error: String?,
    onRefreshConfig: (useAdminToken: Boolean) -> Unit,
    onSetEnv: (key: String, value: String) -> Unit,
    onDeleteEnv: (key: String) -> Unit,
    onClearCache: () -> Unit,
    onDeploy: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Whether the server has ADMIN_TOKEN configured.
    // Prefer the value returned by /api/config. Fallback to local .env parsing.
    val hasAdminTokenOnServer: Boolean? = serverConfig?.hasAdminToken
    val adminTokenConfigured: Boolean = hasAdminTokenOnServer ?: adminTokenFromEnv.isNotBlank()

    // 0 = 预览模式（只读/脱敏），1 = 管理员模式（需要输入 ADMIN_TOKEN 才能解锁编辑）
    var mode by remember { mutableIntStateOf(if (sessionAdminToken.isNotBlank()) 1 else 0) }

    val hasSessionAdmin = sessionAdminToken.isNotBlank()
    val isAdminModeSelected = mode == 1
    val canAdminOps = serviceRunning && isAdminModeSelected && hasSessionAdmin
    val canEdit = canAdminOps

    // Admin login state (this session only)
    var validatingAdmin by remember { mutableStateOf(false) }
    var adminAuthError by remember { mutableStateOf<String?>(null) }

    // Admin bootstrap (when ADMIN_TOKEN is not configured yet)
    var setupAdminToken by remember { mutableStateOf("") }
    var revealSetupToken by remember { mutableStateOf(false) }

    // Token input (never persisted; never auto-filled)
    var tokenInput by remember(isAdminModeSelected) { mutableStateOf("") }
    var revealToken by remember { mutableStateOf(false) }

    // Leaving admin mode should drop the in-memory token, so re-entering always requires re-typing.
    LaunchedEffect(mode) {
        if (mode == 0 && hasSessionAdmin) {
            onClearSessionAdminToken()
            adminAuthError = null
            validatingAdmin = false
        }
    }

    // If the server reports there is no ADMIN_TOKEN, make sure we never keep a session admin token.
    LaunchedEffect(hasAdminTokenOnServer) {
        if (hasAdminTokenOnServer == false && hasSessionAdmin) {
            onClearSessionAdminToken()
            mode = 0
            adminAuthError = null
            validatingAdmin = false
        }
    }

    fun toast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    fun enterAdminMode() {
        val v = tokenInput.trim()
        if (v.isBlank()) {
            toast("请填写 ADMIN_TOKEN")
            return
        }
        if (!serviceRunning) {
            toast("服务未运行")
            return
        }
        if (!adminTokenConfigured) {
            toast("未配置 ADMIN_TOKEN，请先配置并保存")
            return
        }
        if (validatingAdmin) return

        validatingAdmin = true
        adminAuthError = null
        scope.launch {
            val (ok, err) = try {
                validateAdminToken(v)
            } catch (t: Throwable) {
                false to (t.message ?: "验证失败")
            }
            validatingAdmin = false
            if (ok) {
                onSetSessionAdminToken(v)
                toast("已进入管理员模式（本次会话）")
                // Clear input ASAP to reduce shoulder-surfing risk.
                tokenInput = ""
                revealToken = false
                adminAuthError = null
            } else {
                adminAuthError = err ?: "ADMIN_TOKEN 输入错误"
                toast(adminAuthError!!)
            }
        }
    }

    var search by remember { mutableStateOf("") }
    var confirmDeleteKey by remember { mutableStateOf<String?>(null) }

    // Refresh config when service/admin mode changes.
    LaunchedEffect(serviceRunning, canAdminOps) {
        if (serviceRunning) onRefreshConfig(canAdminOps)
    }

    val meta = serverConfig?.envVarConfig.orEmpty()
    val original = serverConfig?.originalEnvVars.orEmpty()
    val categories = serverConfig?.categorizedEnvVars.orEmpty()

    // Effective value (includes defaults) comes from categorizedEnvVars.
    val effectiveByKey = remember(categories) {
        categories.values.flatten().associate { it.key to it.value }
    }

    // Per-key edits (admin mode only)
    val edits = remember { mutableStateMapOf<String, String>() }
    LaunchedEffect(canEdit) {
        if (!canEdit) edits.clear()
    }

    // Category collapse state
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    fun baseline(key: String): String {
        return original[key] ?: effectiveByKey[key].orEmpty()
    }

    fun getCurrent(key: String): String {
        return edits[key] ?: original[key] ?: effectiveByKey[key].orEmpty()
    }

    fun isChanged(key: String): Boolean {
        return edits.containsKey(key) && edits[key] != baseline(key)
    }

    if (confirmDeleteKey != null) {
        AlertDialog(
            onDismissRequest = { confirmDeleteKey = null },
            title = { Text("确认删除") },
            text = { Text("将从 .env 中移除：${confirmDeleteKey}\n\n这会让该项回到默认值（如有）。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val key = confirmDeleteKey!!
                        confirmDeleteKey = null
                        onDeleteEnv(key)
                    },
                    enabled = canEdit
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteKey = null }) { Text("取消") }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ManagerCard(title = "系统配置") {
                Text(
                    "以更清晰的方式管理 danmu-api 的环境变量配置。",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))

                if (!serviceRunning) {
                    Text("服务未运行：无法通过 API 读取/写入配置。", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                }
                if (rootAvailable == false) {
                    Text(
                        "未获取 Root：部分兜底操作不可用。",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(6.dp))
                }

                Text("模式", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = mode == 0,
                        onClick = { mode = 0 },
                        label = { Text("预览模式") }
                    )
                    FilterChip(
                        selected = mode == 1,
                        onClick = { mode = 1 },
                        label = { Text("管理员模式") }
                    )
                }

                Spacer(Modifier.height(8.dp))

                if (mode == 0) {
                    Text(
                        "预览模式：只读展示，敏感变量将被隐藏。要修改配置请切换到“管理员模式”。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    when {
                        // ADMIN_TOKEN is not configured: do not ask for input; guide user to set it first.
                        !adminTokenConfigured -> {
                            Text(
                                "管理员模式：当前服务端未配置 ADMIN_TOKEN，无法进入管理员模式。\n" +
                                    "请先设置 ADMIN_TOKEN 并保存，保存后再进入管理员模式。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = setupAdminToken,
                                onValueChange = { setupAdminToken = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("设置 ADMIN_TOKEN") },
                                placeholder = { Text("建议使用难猜的字符串") },
                                visualTransformation = if (revealSetupToken) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { revealSetupToken = !revealSetupToken }) {
                                        Icon(
                                            imageVector = if (revealSetupToken) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                            contentDescription = null
                                        )
                                    }
                                }
                            )

                            Spacer(Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Button(
                                    onClick = {
                                        val v = setupAdminToken.trim()
                                        if (v.isBlank()) {
                                            toast("请填写 ADMIN_TOKEN")
                                        } else {
                                            onSetEnv("ADMIN_TOKEN", v)
                                            toast("已提交保存 ADMIN_TOKEN，请稍后刷新/重新进入")
                                            // Do not auto-fill login input for security.
                                            setupAdminToken = ""
                                            revealSetupToken = false
                                        }
                                    },
                                    enabled = setupAdminToken.trim().isNotBlank() && serviceRunning && !loading
                                ) {
                                    Text("保存 ADMIN_TOKEN")
                                }
                                OutlinedButton(onClick = { mode = 0 }) {
                                    Text("返回预览")
                                }
                            }
                        }

                        // ADMIN_TOKEN exists but not yet provided in this session.
                        !hasSessionAdmin -> {
                            Text(
                                "管理员模式：需要手动输入 ADMIN_TOKEN 才能解锁编辑（不会自动导入/不会写入本机存储）。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (adminTokenFromEnv.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "提示：检测到系统已配置 ADMIN_TOKEN，但为避免误触/泄露，此处不会自动导入。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = tokenInput,
                                onValueChange = {
                                    tokenInput = it
                                    adminAuthError = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("ADMIN_TOKEN") },
                                placeholder = { Text("请输入管理员 Token") },
                                isError = adminAuthError != null,
                                visualTransformation = if (revealToken) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { revealToken = !revealToken }) {
                                        Icon(
                                            imageVector = if (revealToken) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                            contentDescription = null
                                        )
                                    }
                                }
                            )
                            if (adminAuthError != null) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    adminAuthError!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            Spacer(Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Button(
                                    onClick = { enterAdminMode() },
                                    enabled = tokenInput.trim().isNotBlank() && serviceRunning && !loading && !validatingAdmin
                                ) {
                                    if (validatingAdmin) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(8.dp))
                                        Text("验证中…")
                                    } else {
                                        Text("进入管理员模式")
                                    }
                                }
                                OutlinedButton(onClick = { mode = 0 }, enabled = !validatingAdmin) {
                                    Text("返回预览")
                                }
                            }
                        }

                        // Already unlocked.
                        else -> {
                            Text(
                                "管理员模式已开启（本次会话）。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    onClearSessionAdminToken()
                                    mode = 0
                                    toast("已退出管理员模式")
                                },
                                enabled = serviceRunning
                            ) {
                                Text("退出管理员模式")
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { onRefreshConfig(canAdminOps) },
                        enabled = serviceRunning && !loading
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("刷新")
                    }
                    OutlinedButton(
                        onClick = onClearCache,
                        enabled = canAdminOps
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("清理缓存")
                    }
                    OutlinedButton(
                        onClick = onDeploy,
                        enabled = canAdminOps
                    ) {
                        Icon(Icons.Filled.RestartAlt, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("重新部署")
                    }
                }

                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("搜索") },
                    placeholder = { Text("例如：TOKEN / PORT / CACHE") },
                )

                when {
                    loading -> {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("加载中…")
                        }
                    }
                    error != null -> {
                        Spacer(Modifier.height(8.dp))
                        Text("加载失败：$error", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        if (!canEdit && isAdminModeSelected && serviceRunning && adminTokenConfigured) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("管理员模式未解锁", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "请输入 ADMIN_TOKEN 后才能编辑/保存配置。当前仍以预览模式展示（敏感项已隐藏）。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        val q = search.trim().lowercase(Locale.getDefault())
        val searching = q.isNotBlank()

        categories.forEach { (category, items) ->
            val filtered = if (q.isBlank()) items else items.filter {
                it.key.lowercase(Locale.getDefault()).contains(q) ||
                    getCurrent(it.key).lowercase(Locale.getDefault()).contains(q) ||
                    it.description.lowercase(Locale.getDefault()).contains(q)
            }
            if (filtered.isEmpty()) return@forEach

            val isExpanded = searching || (expanded[category] ?: true)

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            expanded[category] = !(expanded[category] ?: true)
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        categoryLabel(category),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = null
                    )
                }
            }

            if (!isExpanded) return@forEach

            items(filtered, key = { it.key }) { env ->
                val metaItem = meta[env.key] ?: EnvVarMeta(category = category, type = env.type, description = env.description)
                val keyExistsInEnv = original.containsKey(env.key)

                val rawValue = getCurrent(env.key)
                val maskedByBackend = keyExistsInEnv && original[env.key].orEmpty().trim().all { it == '*' } && original[env.key].orEmpty().isNotBlank()
                val maskedForPreview = !canEdit && shouldMaskInPreview(
                    key = env.key,
                    type = metaItem.type.ifBlank { env.type },
                    description = metaItem.description.ifBlank { env.description },
                    value = rawValue,
                )
                val masked = maskedByBackend || maskedForPreview

                if (canEdit) {
                    EnvEditorRow(
                        category = metaItem.category.ifBlank { category },
                        keyName = env.key,
                        description = metaItem.description.ifBlank { env.description },
                        type = metaItem.type,
                        options = metaItem.options,
                        currentValue = rawValue,
                        isDefaultValue = !keyExistsInEnv,
                        min = metaItem.min,
                        max = metaItem.max,
                        masked = maskedByBackend,
                        onValueChange = { edits[env.key] = it },
                        onCopyKey = { clipboard.setText(AnnotatedString(env.key)) },
                        onCopyValue = { clipboard.setText(AnnotatedString(rawValue)) },
                        onSave = {
                            val v = getCurrent(env.key)
                            onSetEnv(env.key, v)
                            edits.remove(env.key)
                        },
                        onReset = {
                            if (keyExistsInEnv) {
                                confirmDeleteKey = env.key
                            } else {
                                edits.remove(env.key)
                            }
                        },
                        saveEnabled = isChanged(env.key),
                        resetEnabled = (keyExistsInEnv || edits.containsKey(env.key)),
                    )
                } else {
                    EnvPreviewRow(
                        category = metaItem.category.ifBlank { category },
                        keyName = env.key,
                        description = metaItem.description.ifBlank { env.description },
                        type = metaItem.type.ifBlank { env.type },
                        value = rawValue,
                        isDefaultValue = !keyExistsInEnv,
                        masked = masked,
                        onCopyKey = { clipboard.setText(AnnotatedString(env.key)) },
                        onCopyValue = {
                            if (!masked) clipboard.setText(AnnotatedString(rawValue))
                        },
                    )
                }
            }
        }
    }
}


@Composable
private fun EnvPreviewRow(
    category: String,
    keyName: String,
    description: String,
    type: String,
    value: String,
    isDefaultValue: Boolean,
    masked: Boolean,
    onCopyKey: () -> Unit,
    onCopyValue: () -> Unit,
) {
    val accent = categoryAccentColor(category)
    val shown = when {
        masked -> "（已隐藏）"
        value.isBlank() -> "(空)"
        else -> value
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.25f)),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(keyName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (isDefaultValue) {
                    Text(
                        "默认",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                }
                Text(type, style = MaterialTheme.typography.labelSmall, color = accent)
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onCopyKey, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "复制键")
                }
            }

            if (description.isNotBlank()) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    shown,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !masked && value.isNotBlank()) { onCopyValue() }
                        .padding(10.dp)
                )
            }
        }
    }
}

private fun shouldMaskInPreview(
    key: String,
    type: String,
    description: String,
    value: String,
): Boolean {
    // 1) Explicit type
    if (type.equals("password", true)) return true

    // 2) Already masked by backend
    val v = value.trim()
    if (v.isNotBlank() && v.all { it == '*' }) return true

    // 3) Key-based heuristics
    val k = key.trim().uppercase(Locale.getDefault())
    val hit = listOf(
        "TOKEN",
        "ADMIN",
        "PASSWORD",
        "PASS",
        "SECRET",
        "KEY",
        "COOKIE",
        "SESS",
        "AUTH",
        "BEARER",
        "JWT",
        "SIGN",
        "PRIVATE",
        "ACCESS",
    ).any { k.contains(it) }
    if (hit) return true

    // 4) Description-based heuristics (Chinese/English)
    val d = description.lowercase(Locale.getDefault())
    if (d.contains("token") || d.contains("password") || d.contains("secret") || d.contains("cookie")) return true
    if (d.contains("密码") || d.contains("令牌") || d.contains("密钥") || d.contains("cookie")) return true

    // 5) Value-based heuristics (JWT / URL with credentials)
    if (v.startsWith("eyJ") && v.count { it == '.' } >= 2) return true
    if (v.contains("://") && v.contains("@") && v.substringBefore("@").contains(":")) return true

    return false
}


@Composable
private fun EnvEditorRow(
    category: String,
    keyName: String,
    description: String,
    type: String,
    options: List<String>,
    currentValue: String,
    isDefaultValue: Boolean,
    min: Double?,
    max: Double?,
    masked: Boolean,
    onValueChange: (String) -> Unit,
    onCopyKey: () -> Unit,
    onCopyValue: () -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
    saveEnabled: Boolean,
    resetEnabled: Boolean,
) {
    var reveal by remember { mutableStateOf(false) }

    fun parseCommaList(v: String): List<String> {
        return v.split(',').map { it.trim() }.filter { it.isNotBlank() }
    }

    val accent = categoryAccentColor(category)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.25f)),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(keyName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (isDefaultValue) {
                    Text(
                        "默认",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                }
                Text(type, style = MaterialTheme.typography.labelSmall, color = accent)
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onCopyKey, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "复制键")
                }
            }

            if (description.isNotBlank()) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            when (type) {
                "boolean" -> {
                    val checked = currentValue.equals("true", true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = checked, onCheckedChange = { onValueChange(if (it) "true" else "false") })
                        Spacer(Modifier.width(8.dp))
                        Text(if (checked) "true" else "false")
                    }
                }
                "select" -> {
                    if (options.isEmpty()) {
                        OutlinedTextField(
                            value = currentValue,
                            onValueChange = onValueChange,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("当前：${currentValue.ifBlank { "(空)" }}", style = MaterialTheme.typography.bodySmall)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                options.forEach { opt ->
                                    FilterChip(
                                        selected = currentValue == opt,
                                        onClick = { onValueChange(opt) },
                                        label = { Text(opt) }
                                    )
                                }
                            }
                        }
                    }
                }
                "multi-select" -> {
                    val selected = parseCommaList(currentValue)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            if (selected.isEmpty()) "(空)" else selected.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (options.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                options.forEach { opt ->
                                    val isSel = selected.contains(opt)
                                    FilterChip(
                                        selected = isSel,
                                        onClick = {
                                            val next = if (isSel) selected.filterNot { it == opt } else (selected + opt)
                                            onValueChange(next.joinToString(","))
                                        },
                                        label = { Text(opt) }
                                    )
                                }
                            }
                        } else {
                            OutlinedTextField(
                                value = currentValue,
                                onValueChange = onValueChange,
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                placeholder = { Text("逗号分隔，例如：a,b,c") }
                            )
                        }
                    }
                }
                "source-order", "platform-order" -> {
                    val selected = parseCommaList(currentValue)

                    fun commit(list: List<String>) {
                        onValueChange(list.joinToString(","))
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("当前顺序", style = MaterialTheme.typography.labelMedium)
                        if (selected.isEmpty()) {
                            Text("(空)", style = MaterialTheme.typography.bodySmall)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                selected.forEachIndexed { idx, item ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(item, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)

                                        IconButton(
                                            onClick = {
                                                if (idx <= 0) return@IconButton
                                                val next = selected.toMutableList()
                                                val t = next[idx - 1]
                                                next[idx - 1] = next[idx]
                                                next[idx] = t
                                                commit(next)
                                            },
                                            enabled = idx > 0,
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "上移")
                                        }
                                        IconButton(
                                            onClick = {
                                                if (idx >= selected.lastIndex) return@IconButton
                                                val next = selected.toMutableList()
                                                val t = next[idx + 1]
                                                next[idx + 1] = next[idx]
                                                next[idx] = t
                                                commit(next)
                                            },
                                            enabled = idx < selected.lastIndex,
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "下移")
                                        }
                                        IconButton(
                                            onClick = {
                                                val next = selected.filterNot { it == item }
                                                commit(next)
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Filled.Clear, contentDescription = "移除")
                                        }
                                    }
                                }
                            }
                        }

                        if (options.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text("可选项", style = MaterialTheme.typography.labelMedium)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                options.forEach { opt ->
                                    val isSel = selected.contains(opt)
                                    FilterChip(
                                        selected = isSel,
                                        onClick = {
                                            val next = if (isSel) {
                                                selected.filterNot { it == opt }
                                            } else {
                                                selected + opt
                                            }
                                            commit(next)
                                        },
                                        label = { Text(opt) }
                                    )
                                }
                            }
                        } else {
                            OutlinedTextField(
                                value = currentValue,
                                onValueChange = onValueChange,
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                placeholder = { Text("逗号分隔，例如：a,b,c") }
                            )
                        }
                    }
                }
                "number" -> {
                    OutlinedTextField(
                        value = currentValue,
                        onValueChange = { v ->
                            val filtered = v.filter { it.isDigit() || it == '.' || it == '-' }
                            onValueChange(filtered)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        supportingText = {
                            val range = buildString {
                                if (min != null || max != null) {
                                    append("范围：")
                                    append(min?.toString() ?: "-")
                                    append(" ~ ")
                                    append(max?.toString() ?: "-")
                                }
                            }
                            if (range.isNotBlank()) Text(range)
                        }
                    )
                }
                "password" -> {
                    OutlinedTextField(
                        value = currentValue,
                        onValueChange = onValueChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (!reveal) PasswordVisualTransformation() else VisualTransformation.None,
                        trailingIcon = {
                            TextButton(onClick = { reveal = !reveal }) {
                                Text(if (reveal) "隐藏" else "显示")
                            }
                        }
                    )
                    if (masked) {
                        Text("当前值已脱敏，保存会覆盖原值。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                    }
                }
                else -> {
                    OutlinedTextField(
                        value = currentValue,
                        onValueChange = onValueChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = type != "json" && type != "color-list",
                        minLines = if (type == "json" || type == "color-list") 3 else 1,
                        maxLines = if (type == "json" || type == "color-list") 8 else 1,
                    )
                    if (masked) {
                        Text("当前值已脱敏，保存会覆盖原值。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onCopyValue, enabled = currentValue.isNotBlank()) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("复制值")
                }
                Button(onClick = onSave, enabled = saveEnabled) {
                    Text("保存")
                }
                OutlinedButton(onClick = onReset, enabled = resetEnabled) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isDefaultValue) "恢复默认" else "重置")
                }
            }
        }
    }
}

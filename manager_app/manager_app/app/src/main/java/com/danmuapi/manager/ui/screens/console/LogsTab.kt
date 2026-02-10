package com.danmuapi.manager.ui.screens.console

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.danmuapi.manager.data.model.LogsResponse
import com.danmuapi.manager.data.model.ServerLogEntry
import com.danmuapi.manager.ui.screens.console.components.*
import kotlinx.coroutines.launch

/**
 * 日志 Tab - 服务器日志和模块日志
 */
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
    var selectedLogType by remember { mutableIntStateOf(0) }
    val logTypes = listOf("服务器日志", "模块日志")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 日志类型切换
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                logTypes.forEachIndexed { index, type ->
                    FilterChip(
                        selected = selectedLogType == index,
                        onClick = { selectedLogType = index },
                        label = { Text(type) },
                        modifier = Modifier.weight(1f),
                        leadingIcon = {
                            Icon(
                                imageVector = if (index == 0) Icons.Default.Cloud else Icons.Default.Storage,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }
        }

        // 日志内容
        when (selectedLogType) {
            0 -> ServerLogsSection(
                serviceRunning = serviceRunning,
                adminToken = adminToken,
                logs = serverLogs,
                loading = serverLogsLoading,
                error = serverLogsError,
                logLimit = consoleLogLimit,
                onSetLogLimit = onSetConsoleLogLimit,
                onRefresh = onRefreshServerLogs,
                onClear = onClearServerLogs,
            )

            1 -> ModuleLogsSection(
                rootAvailable = rootAvailable,
                logs = moduleLogs,
                onRefresh = onRefreshModuleLogs,
                onClear = onClearModuleLogs,
                onReadLogTail = onReadModuleLogTail,
            )
        }
    }
}

/**
 * 服务器日志区域
 */
@Composable
private fun ServerLogsSection(
    serviceRunning: Boolean,
    adminToken: String,
    logs: List<ServerLogEntry>,
    loading: Boolean,
    error: String?,
    logLimit: Int,
    onSetLogLimit: (Int) -> Unit,
    onRefresh: () -> Unit,
    onClear: () -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var showLimitDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 工具栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "共 ${logs.size} 条",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 限制数量
                    OutlinedButton(
                        onClick = { showLimitDialog = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("限制: $logLimit")
                    }

                    // 刷新
                    IconButton(
                        onClick = onRefresh,
                        enabled = serviceRunning && !loading
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }

                    // 清空
                    IconButton(
                        onClick = onClear,
                        enabled = serviceRunning && adminToken.isNotEmpty() && logs.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "清空")
                    }

                    // 滚动到底部
                    IconButton(
                        onClick = {
                            scope.launch {
                                if (logs.isNotEmpty()) {
                                    listState.animateScrollToItem(logs.size - 1)
                                }
                            }
                        },
                        enabled = logs.isNotEmpty()
                    ) {
                        Icon(Icons.Default.VerticalAlignBottom, contentDescription = "滚动到底部")
                    }
                }
            }

            HorizontalDivider()

            // 日志列表
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    !serviceRunning -> EmptyState(
                        icon = Icons.Default.CloudOff,
                        message = "服务未运行"
                    )

                    loading && logs.isEmpty() -> LoadingState()

                    error != null -> ErrorState(
                        message = error,
                        onRetry = onRefresh
                    )

                    logs.isEmpty() -> EmptyState(
                        icon = Icons.Default.Description,
                        message = "暂无日志"
                    )

                    else -> LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(logs) { log ->
                            ServerLogItem(log)
                        }
                    }
                }
            }
        }
    }

    // 限制数量对话框
    if (showLimitDialog) {
        LogLimitDialog(
            currentLimit = logLimit,
            onDismiss = { showLimitDialog = false },
            onConfirm = { newLimit ->
                onSetLogLimit(newLimit)
                showLimitDialog = false
            }
        )
    }
}

/**
 * 服务器日志条目
 */
@Composable
private fun ServerLogItem(log: ServerLogEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 时间和级别
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = log.timestamp ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (!log.level.isNullOrEmpty()) {
                    val levelUpper = log.level.uppercase()
                    Surface(
                        color = when (levelUpper) {
                            "ERROR" -> MaterialTheme.colorScheme.errorContainer
                            "WARN" -> MaterialTheme.colorScheme.tertiaryContainer
                            "INFO" -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.secondaryContainer
                        },
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = levelUpper,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            color = when (levelUpper) {
                                "ERROR" -> MaterialTheme.colorScheme.onErrorContainer
                                "WARN" -> MaterialTheme.colorScheme.onTertiaryContainer
                                "INFO" -> MaterialTheme.colorScheme.onPrimaryContainer
                                else -> MaterialTheme.colorScheme.onSecondaryContainer
                            }
                        )
                    }
                }
            }

            // 消息
            Text(
                text = log.message ?: "",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * 模块日志区域
 */
@Composable
private fun ModuleLogsSection(
    rootAvailable: Boolean?,
    logs: LogsResponse?,
    onRefresh: () -> Unit,
    onClear: () -> Unit,
    onReadLogTail: (path: String, lines: Int, onResult: (String) -> Unit) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 工具栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "模块日志文件",
                    style = MaterialTheme.typography.titleMedium
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }

                    IconButton(
                        onClick = onClear,
                        enabled = rootAvailable == true
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "清空")
                    }
                }
            }

            HorizontalDivider()

            // 日志文件列表
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    rootAvailable != true -> EmptyState(
                        icon = Icons.Default.Lock,
                        message = "需要 Root 权限"
                    )

                    logs == null || logs.files.isEmpty() -> EmptyState(
                        icon = Icons.Default.FolderOpen,
                        message = "暂无日志文件"
                    )

                    else -> LazyColumn(
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(logs.files) { file ->
                            ModuleLogFileItem(
                                file = file,
                                onReadTail = onReadLogTail
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 模块日志文件条目
 */
@Composable
private fun ModuleLogFileItem(
    file: com.danmuapi.manager.data.model.LogFileInfo,
    onReadTail: (path: String, lines: Int, onResult: (String) -> Unit) -> Unit
) {
    var showContent by remember { mutableStateOf(false) }
    var logContent by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${formatFileSize(file.sizeBytes)} • ${file.modifiedAt}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FilledTonalButton(
                    onClick = {
                        if (!showContent) {
                            loading = true
                            onReadTail(file.path, 100) { content ->
                                logContent = content
                                loading = false
                                showContent = true
                            }
                        } else {
                            showContent = false
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = if (showContent) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (showContent) "收起" else "查看")
                    }
                }
            }

            if (showContent && logContent.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = logContent,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * 日志限制对话框
 */
@Composable
private fun LogLimitDialog(
    currentLimit: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var limitText by remember { mutableStateOf(currentLimit.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置日志数量限制") },
        text = {
            OutlinedTextField(
                value = limitText,
                onValueChange = { limitText = it.filter { c -> c.isDigit() } },
                label = { Text("最大条数") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val limit = limitText.toIntOrNull() ?: currentLimit
                    onConfirm(limit.coerceIn(10, 10000))
                }
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 格式化文件大小
 */
private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }
}

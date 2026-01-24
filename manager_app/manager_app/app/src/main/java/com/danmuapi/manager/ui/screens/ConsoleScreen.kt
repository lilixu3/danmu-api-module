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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.ui.platform.LocalClipboardManager
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
) {
    val tabs = listOf("预览", "日志", "接口", "推送", "系统")
    var selectedTab by remember { mutableIntStateOf(0) }

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
            0 -> ConfigPreviewTab(
                rootAvailable = rootAvailable,
                serviceRunning = serviceRunning,
                apiToken = apiToken,
                apiPort = apiPort,
                apiHost = apiHost,
                serverConfig = serverConfig,
                loading = serverConfigLoading,
                error = serverConfigError,
                onRefresh = { onRefreshConfig(false) },
            )

            1 -> LogsTab(
                rootAvailable = rootAvailable,
                serviceRunning = serviceRunning,
                adminToken = adminToken,
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

            2 -> ApiTestTab(
                serviceRunning = serviceRunning,
                adminToken = adminToken,
                requestApi = requestApi,
            )

            3 -> PushDanmuTab(
                serviceRunning = serviceRunning,
                apiToken = apiToken,
                apiPort = apiPort,
                requestApi = requestApi,
            )

            4 -> SystemSettingsTab(
                rootAvailable = rootAvailable,
                serviceRunning = serviceRunning,
                adminToken = adminToken,
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

// ===========================
// 预览
// ===========================

@Composable
private fun ConfigPreviewTab(
    rootAvailable: Boolean?,
    serviceRunning: Boolean,
    apiToken: String,
    apiPort: Int,
    apiHost: String,
    serverConfig: ServerConfigResponse?,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var query by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ManagerCard(title = "配置预览") {
                Text(
                    text = "用于快速查看 danmu-api 当前运行配置（与 Web UI 预览页一致）。",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("刷新")
                    }
                }

                Spacer(Modifier.height(8.dp))

                if (rootAvailable == false) {
                    Text(
                        "当前未获取 Root 权限，部分模块状态可能不可用。",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (!serviceRunning) {
                    Text(
                        "服务未运行：预览数据需要服务在线（先到仪表盘启动服务）。",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Text(
                        "当前访问：token=$apiToken  host=$apiHost  port=$apiPort",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                label = { Text("搜索键/值") },
                placeholder = { Text("例如：TOKEN / CACHE / redis") },
            )
        }

        item {
            if (loading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("加载中…")
                }
            } else if (error != null) {
                Text("加载失败：$error", color = MaterialTheme.colorScheme.error)
            } else if (serverConfig == null) {
                Text("暂无数据")
            }
        }

        val q = query.trim().lowercase(Locale.getDefault())
        serverConfig?.categorizedEnvVars?.forEach { (category, items) ->
            val filtered = if (q.isBlank()) items else items.filter {
                it.key.lowercase(Locale.getDefault()).contains(q) ||
                    it.value.lowercase(Locale.getDefault()).contains(q) ||
                    it.description.lowercase(Locale.getDefault()).contains(q)
            }
            if (filtered.isNotEmpty()) {
                item {
                    Text(
                        text = categoryLabel(category),
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                items(filtered, key = { it.key }) { item ->
                    val accent = categoryAccentColor(category)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.28f)),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    item.key,
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    item.type,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(6.dp))
                                IconButton(
                                    onClick = { clipboard.setText(AnnotatedString(item.value)) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = "复制")
                                }
                            }

                            if (item.description.isNotBlank()) {
                                Text(
                                    item.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(6.dp))
                            }

                            Text(
                                item.value.ifBlank { "(空)" },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
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

        when (selected) {
            0 -> ServerLogsView(
                serviceRunning = serviceRunning,
                adminToken = adminToken,
                logs = serverLogs,
                loading = serverLogsLoading,
                error = serverLogsError,
                onRefresh = onRefreshServerLogs,
                onClear = onClearServerLogs,
            )
            else -> ModuleLogsView(
                rootAvailable = rootAvailable,
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
    logs: List<ServerLogEntry>,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onClear: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
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
                "error" -> e.level.equals("error", true)
                "warn" -> e.level.equals("warn", true)
                "info" -> e.level.equals("info", true)
                else -> true
            }
            val okKw = kw.isBlank() ||
                e.message.lowercase(Locale.getDefault()).contains(kw) ||
                e.level.lowercase(Locale.getDefault()).contains(kw) ||
                e.timestamp.lowercase(Locale.getDefault()).contains(kw)
            okLevel && okKw
        }
    }

    // Avoid rendering thousands of cards: show as a single, selectable text panel.
    // Also cap the visible lines to keep the UI smooth.
    val maxDisplayLines = 1200
    val displayLogs = remember(filtered) {
        if (filtered.size > maxDisplayLines) filtered.takeLast(maxDisplayLines) else filtered
    }
    val truncated = filtered.size > displayLogs.size
    val displayText = remember(displayLogs) {
        displayLogs.joinToString("\n") { it.toLine() }
    }

    val scrollState = rememberScrollState()
    LaunchedEffect(displayText, followTail) {
        if (!followTail) return@LaunchedEffect
        // Let layout calculate maxValue.
        delay(10)
        try {
            scrollState.scrollTo(scrollState.maxValue)
        } catch (_: Throwable) {
        }
    }

    if (confirmCopyAll) {
        AlertDialog(
            onDismissRequest = { confirmCopyAll = false },
            title = { Text("复制全部日志？") },
            text = {
                Text(
                    "当前筛选结果共 ${filtered.size} 条。复制全部可能较大，部分机型会变慢。\n\n" +
                        "建议优先复制“当前显示”。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmCopyAll = false
                        clipboard.setText(AnnotatedString(filtered.joinToString("\n") { it.toLine() }))
                    }
                ) { Text("仍要复制") }
            },
            dismissButton = { TextButton(onClick = { confirmCopyAll = false }) { Text("取消") } }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ManagerCard(title = "服务日志") {
                Text(
                    "来自 /api/logs（与 Web UI 日志页一致）。",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(Modifier.height(8.dp))

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
                        enabled = displayText.isNotBlank()
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (truncated) "复制当前显示" else "复制")
                    }
                    if (truncated && filtered.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { confirmCopyAll = true },
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("复制全部")
                        }
                    }
                    if (adminToken.isNotBlank()) {
                        OutlinedButton(
                            onClick = onClear,
                            enabled = serviceRunning
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("清空")
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = autoRefresh, onCheckedChange = { autoRefresh = it }, enabled = serviceRunning)
                    Spacer(Modifier.width(8.dp))
                    Text("自动刷新（2s）")
                }

                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = followTail, onCheckedChange = { followTail = it })
                    Spacer(Modifier.width(8.dp))
                    Text("跟随底部")
                }

                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(selected = filter == "all", onClick = { filter = "all" }, label = { Text("全部") })
                    FilterChip(selected = filter == "info", onClick = { filter = "info" }, label = { Text("Info") })
                    FilterChip(selected = filter == "warn", onClick = { filter = "warn" }, label = { Text("Warn") })
                    FilterChip(selected = filter == "error", onClick = { filter = "error" }, label = { Text("Error") })
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("筛选") },
                    placeholder = { Text("关键词/时间/级别") }
                )

                Spacer(Modifier.height(6.dp))
                Text(
                    "当前：${displayLogs.size}${if (truncated) "（已截断显示最后 $maxDisplayLines 条/共 ${filtered.size} 条）" else " 条"}",
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
                error != null -> Text("加载失败：$error", color = MaterialTheme.colorScheme.error)
                displayLogs.isEmpty() -> Text("暂无日志")
                else -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    ) {
                        SelectionContainer {
                            Text(
                                text = displayText,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 220.dp, max = 520.dp)
                                    .verticalScroll(scrollState)
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
}

private fun ServerLogEntry.toLine(): String {
    val ts = timestamp.ifBlank { "-" }
    val lv = level.ifBlank { "" }
    return "[$ts] $lv: $message"
}

@Composable
private fun ModuleLogsView(
    rootAvailable: Boolean?,
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

                // Pretty print JSON only when it's small enough and not truncated.
                val pretty = if (!result.truncated) prettifyIfJson(result.body, maxChars = 160_000) else result.body

                // UI preview cap: large text layout can still ANR on some devices.
                val previewMaxChars = 60_000
                responsePreview = if (pretty.length > previewMaxChars) {
                    responseHint = "响应较大：仅预览前 ${previewMaxChars} 字符（可复制完整响应）。"
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
            title = { Text("复制完整响应？") },
            text = {
                val size = responseRaw.toByteArray(Charsets.UTF_8).size.toLong()
                Text(
                    "当前已读取内容约 ${humanBytes(size)}。复制到剪贴板可能会短暂卡顿。\n\n" +
                        "如果只是查看/排错，建议复制“预览”。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmCopyFull = false
                        clipboard.setText(AnnotatedString(responseRaw))
                    }
                ) { Text("仍要复制") }
            },
            dismissButton = { TextButton(onClick = { confirmCopyFull = false }) { Text("取消") } }
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
                    "在 App 内直接调用 danmu-api 接口（Compose 复刻 Web UI：接口调试页）。",
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
                            clipboard.setText(AnnotatedString(responsePreview))
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
                                // Avoid copying a huge payload by accident.
                                val size = responseRaw.length
                                if (size > 120_000 || responseTruncatedByClient) {
                                    confirmCopyFull = true
                                } else {
                                    clipboard.setText(AnnotatedString(responseRaw))
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
    requestApi: suspend (method: String, path: String, query: Map<String, String?>, bodyJson: String?, useAdminToken: Boolean) -> HttpResult,
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val lanIps = rememberLanIpv4Addresses()
    val lanIp = lanIps.firstOrNull()
    val defaultSubnet = remember(lanIp) {
        lanIp?.split('.')?.take(3)?.joinToString(".") ?: "192.168.1"
    }

    var keyword by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var animes by remember { mutableStateOf<List<AnimeItem>>(emptyList()) }
    var selectedAnime by remember { mutableStateOf<AnimeItem?>(null) }
    var episodes by remember { mutableStateOf<List<EpisodeItem>>(emptyList()) }
    var loadingEpisodes by remember { mutableStateOf(false) }

    // Push target (OK影视 9978 only)
    val okPushPath = remember { "/action?do=refresh&type=danmaku&path=" }
    var subnet by remember { mutableStateOf(defaultSubnet) }
    var lanPort by remember { mutableStateOf("9978") }

    fun buildPushTemplate(host: String, port: Int): String {
        return "http://$host:$port$okPushPath"
    }

    // Discovered 9978 devices (include localhost + this device LAN IPs)
    var scanning by remember { mutableStateOf(false) }
    var scanProgress by remember { mutableIntStateOf(0) }
    var scanTotal by remember { mutableIntStateOf(0) }
    var foundDevices by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedDevice by remember { mutableStateOf<String?>(null) }
    var pushUrl by remember { mutableStateOf(buildPushTemplate("127.0.0.1", 9978)) }
    var autoScan by remember { mutableStateOf(true) }
    var lastAutoScanKey by remember { mutableStateOf<String?>(null) }

    val localHosts = remember(lanIps) { (setOf("127.0.0.1") + lanIps).toSet() }

    fun selectDevice(host: String) {
        selectedDevice = host
        val port = lanPort.trim().toIntOrNull() ?: 9978
        pushUrl = buildPushTemplate(host, port)
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
            } catch (t: Throwable) {
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
        // If pushing to a local player (127.0.0.1 / 本机 IP), prefer loopback for maximum compatibility.
        val host = if (selectedDevice != null && localHosts.contains(selectedDevice)) {
            "127.0.0.1"
        } else {
            lanIp ?: "127.0.0.1"
        }
        return "http://$host:$apiPort/$apiToken/api/v2/comment/$episodeId?format=xml"
    }

    fun pushOne(episode: EpisodeItem) {
        val template = pushUrl.trim()
        if (template.isBlank()) return
        val commentUrl = buildCommentUrl(episode.episodeId)
        val finalUrl = template + java.net.URLEncoder.encode(commentUrl, "UTF-8")

        scope.launch {
            val res = withContext(Dispatchers.IO) {
                try {
                    // Use JVM URLConnection (no CORS issues)
                    val conn = java.net.URL(finalUrl).openConnection()
                    conn.connectTimeout = 1500
                    conn.readTimeout = 2000
                    conn.getInputStream().use { it.readBytes() }
                    true
                } catch (_: Throwable) {
                    false
                }
            }
            if (res) {
                clipboard.setText(AnnotatedString(commentUrl))
            }
        }
    }

    fun scanLan() {
        if (scanning) return

        val port = lanPort.trim().toIntOrNull() ?: 9978
        val subnetTrimmed = subnet.trim()
        val subnetOk = Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$").matches(subnetTrimmed)

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
            // Always include localhost + this device LAN IPs (do NOT exclude local addresses)
            candidates.addAll(localHosts)
            if (subnetOk) {
                for (i in 1..254) {
                    candidates.add("$subnetTrimmed.$i")
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
                selectDevice(sorted.first())
            }
        }
    }

    // Auto scan when the tab has a context (selected anime) and network parameters change.
    LaunchedEffect(selectedAnime?.animeId, subnet, lanPort, autoScan, serviceRunning) {
        val key = "${selectedAnime?.animeId}:${subnet.trim()}:${lanPort.trim()}"
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
                    "选择番剧/剧集后，将弹幕链接推送到局域网播放器（Compose 复刻 Web UI：推送弹幕页）。",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (!serviceRunning) {
                    Spacer(Modifier.height(8.dp))
                    Text("服务未运行，无法搜索/生成弹幕链接。", color = MaterialTheme.colorScheme.error)
                }
                if (lanIp == null) {
                    Spacer(Modifier.height(8.dp))
                    Text("未检测到局域网 IPv4，推送到其它设备可能不可用。", color = MaterialTheme.colorScheme.error)
                } else {
                    Spacer(Modifier.height(8.dp))
                    Text("当前局域网 IP：$lanIp", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item {
            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("搜索动漫") },
                placeholder = { Text("关键词 或 播放链接URL") },
            )
            Spacer(Modifier.height(8.dp))
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

        if (animes.isNotEmpty()) {
            item { Text("搜索结果", style = MaterialTheme.typography.titleMedium) }
            items(animes, key = { it.animeId }) { anime ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { loadEpisodes(anime) },
                    colors = CardDefaults.cardColors(containerColor = if (selectedAnime?.animeId == anime.animeId) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(anime.title, style = MaterialTheme.typography.titleSmall)
                        if (anime.typeDesc.isNotBlank()) {
                            Text(anime.typeDesc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("ID: ${anime.animeId}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        if (selectedAnime != null) {
            item {
                ManagerCard(title = "推送目标") {
                    Text(
                        "默认按 OK影视 的 9978 推送接口生成：末尾必须以 path= 结尾，应用会自动拼接并 URL 编码弹幕链接。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pushUrl,
                        onValueChange = { pushUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("推送URL（OK影视）") },
                        placeholder = { Text(buildPushTemplate("127.0.0.1", 9978)) },
                    )

                    Spacer(Modifier.height(12.dp))
                    Text("自动发现 9978 设备", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(6.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = autoScan, onCheckedChange = { autoScan = it })
                        Spacer(Modifier.width(8.dp))
                        Text("自动扫描（选中番剧/修改网段后自动刷新）", style = MaterialTheme.typography.bodySmall)
                    }

                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = subnet,
                            onValueChange = { subnet = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("网段") },
                            placeholder = { Text(defaultSubnet) },
                        )
                        OutlinedTextField(
                            value = lanPort,
                            onValueChange = { lanPort = it },
                            modifier = Modifier.width(110.dp),
                            singleLine = true,
                            label = { Text("端口") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(onClick = { scanLan() }, enabled = !scanning) {
                            Text(if (scanning) "扫描中…" else "重新扫描")
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
                        Spacer(Modifier.height(8.dp))
                        Text("发现设备（点击选择并自动填充 URL）", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            foundDevices.forEach { host ->
                                val label = when {
                                    host == "127.0.0.1" -> "本机 (127.0.0.1)"
                                    lanIps.contains(host) -> "本机 ($host)"
                                    else -> host
                                }
                                FilterChip(
                                    selected = selectedDevice == host,
                                    onClick = { selectDevice(host) },
                                    label = { Text(label) }
                                )
                            }
                        }
                    } else {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "未发现设备：请确认播放器/接收端已开启 9978 接口，或直接手动填写上方推送 URL。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (selectedDevice != null) {
                        Spacer(Modifier.height(8.dp))
                        Text("当前选择：$selectedDevice", style = MaterialTheme.typography.bodySmall)
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
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "${ep.episodeNumber} ${ep.title}".trim(),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text("弹幕ID: ${ep.episodeId}", style = MaterialTheme.typography.labelSmall)

                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { pushOne(ep) }) {
                                Text("推送")
                            }
                            OutlinedButton(onClick = { clipboard.setText(AnnotatedString(buildCommentUrl(ep.episodeId))) }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("复制链接")
                            }
                        }
                    }
                }
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
    adminToken: String,
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
    var useAdmin by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var confirmDeleteKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(adminToken) {
        // Default: if ADMIN_TOKEN exists, use it for better experience.
        useAdmin = adminToken.isNotBlank()
    }

    LaunchedEffect(useAdmin, serviceRunning) {
        if (serviceRunning) onRefreshConfig(useAdmin)
    }

    val meta = serverConfig?.envVarConfig.orEmpty()
    val original = serverConfig?.originalEnvVars.orEmpty()
    val categories = serverConfig?.categorizedEnvVars.orEmpty()

    // Effective value (includes defaults) comes from categorizedEnvVars.
    val effectiveByKey = remember(categories) {
        categories.values.flatten().associate { it.key to it.value }
    }

    // Keep per-key edits
    val edits = remember { mutableStateMapOf<String, String>() }

    fun baseline(key: String): String {
        // If it exists in .env (originalEnvVars), that's the baseline.
        // Otherwise use the effective/default value from categorizedEnvVars.
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
                TextButton(onClick = {
                    val key = confirmDeleteKey!!
                    confirmDeleteKey = null
                    onDeleteEnv(key)
                }) {
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
                    "环境变量可视化配置（Compose 复刻 Web UI：系统设置页）。",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))

                if (!serviceRunning) {
                    Text("服务未运行，无法通过 API 读取/写入配置。", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                }
                if (rootAvailable == false) {
                    Text("未获取 Root：无法使用 .env 兜底操作。", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = useAdmin, onCheckedChange = {
                        if (adminToken.isNotBlank()) {
                            useAdmin = it
                        }
                    }, enabled = adminToken.isNotBlank())
                    Spacer(Modifier.width(8.dp))
                    Text("使用管理员 Token")
                }
                if (adminToken.isBlank()) {
                    Text(
                        "未配置 ADMIN_TOKEN：部分敏感项在 /api/config 中会被脱敏。\n建议在设置里编辑 .env 添加 ADMIN_TOKEN 后再回来。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = { onRefreshConfig(useAdmin) }, enabled = serviceRunning && !loading) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("刷新")
                    }
                    OutlinedButton(onClick = onClearCache, enabled = serviceRunning && adminToken.isNotBlank()) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("清理缓存")
                    }
                    OutlinedButton(onClick = onDeploy, enabled = serviceRunning && adminToken.isNotBlank()) {
                        Icon(Icons.Filled.RestartAlt, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("重新部署")
                    }
                }

                Spacer(Modifier.height(8.dp))
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

        val q = search.trim().lowercase(Locale.getDefault())
        categories.forEach { (category, items) ->
            val filtered = if (q.isBlank()) items else items.filter {
                it.key.lowercase(Locale.getDefault()).contains(q) ||
                    getCurrent(it.key).lowercase(Locale.getDefault()).contains(q) ||
                    it.description.lowercase(Locale.getDefault()).contains(q)
            }
            if (filtered.isNotEmpty()) {
                item {
                    Text(categoryLabel(category), style = MaterialTheme.typography.titleMedium)
                }

                items(filtered, key = { it.key }) { env ->
                    val metaItem = meta[env.key] ?: EnvVarMeta(category = category, type = env.type, description = env.description)
                    val keyExistsInEnv = original.containsKey(env.key)
                    EnvEditorRow(
                        category = metaItem.category.ifBlank { category },
                        keyName = env.key,
                        description = metaItem.description.ifBlank { env.description },
                        type = metaItem.type,
                        options = metaItem.options,
                        currentValue = getCurrent(env.key),
                        isDefaultValue = !keyExistsInEnv,
                        min = metaItem.min,
                        max = metaItem.max,
                        masked = keyExistsInEnv && original[env.key].orEmpty().trim().all { it == '*' } && original[env.key].orEmpty().isNotBlank(),
                        onValueChange = { edits[env.key] = it },
                        onCopyKey = { clipboard.setText(AnnotatedString(env.key)) },
                        onCopyValue = { clipboard.setText(AnnotatedString(getCurrent(env.key))) },
                        onSave = {
                            val v = getCurrent(env.key)
                            onSetEnv(env.key, v)
                            edits.remove(env.key)
                        },
                        onReset = {
                            // If not written in .env, reset simply drops local edits.
                            if (keyExistsInEnv) {
                                confirmDeleteKey = env.key
                            } else {
                                edits.remove(env.key)
                            }
                        },
                        saveEnabled = serviceRunning && isChanged(env.key),
                        resetEnabled = serviceRunning && (keyExistsInEnv || edits.containsKey(env.key)),
                    )
                }
            }
        }
    }
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

@file:OptIn(ExperimentalMaterial3Api::class)

package com.danmuapi.manager.ui.screens.console

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.danmuapi.manager.data.model.*
import com.danmuapi.manager.network.HttpResult

/**
 * 控制台主界面 - 全新重构版本
 *
 * 设计理念：
 * - 卡片式布局，清晰的视觉层次
 * - 图标化导航，直观易懂
 * - 统一的 Material Design 3 风格
 * - 优雅的加载和错误状态处理
 */
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
    requestRecords: List<com.danmuapi.manager.data.model.RequestRecord>,
    requestRecordsLoading: Boolean,
    requestRecordsError: String?,
    todayReqNum: Int,
    onRefreshRequestRecords: () -> Unit,
    requestApi: suspend (
        method: String,
        path: String,
        query: Map<String, String?>,
        bodyJson: String?,
        useAdminToken: Boolean,
    ) -> HttpResult,
    validateAdminToken: suspend (token: String) -> Pair<Boolean, String?>,
) {
    // Tab 定义
    val tabs = remember {
        listOf(
            ConsoleTab.Logs,
            ConsoleTab.Requests,
            ConsoleTab.ApiTest,
            ConsoleTab.Push,
            ConsoleTab.System
        )
    }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val effectiveAdminToken = sessionAdminToken.trim()

    // 初始数据加载
    LaunchedEffect(serviceRunning) {
        if (serviceRunning) {
            if (serverConfig == null && !serverConfigLoading) {
                onRefreshConfig(false)
            }
            if (serverLogs.isEmpty() && !serverLogsLoading) {
                onRefreshServerLogs()
            }
            if (requestRecords.isEmpty() && !requestRecordsLoading) {
                onRefreshRequestRecords()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        // 顶部 Tab 导航
        ConsoleTabBar(
            tabs = tabs,
            selectedIndex = selectedTabIndex,
            onTabSelected = { selectedTabIndex = it }
        )

        // Tab 内容区域
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            when (tabs[selectedTabIndex]) {
                ConsoleTab.Logs -> LogsTabContent(
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

                ConsoleTab.Requests -> RequestsTabContent(
                    serviceRunning = serviceRunning,
                    records = requestRecords,
                    todayReqNum = todayReqNum,
                    loading = requestRecordsLoading,
                    error = requestRecordsError,
                    onRefresh = onRefreshRequestRecords,
                )

                ConsoleTab.ApiTest -> ApiTestTabContent(
                    serviceRunning = serviceRunning,
                    adminToken = effectiveAdminToken,
                    requestApi = requestApi,
                )

                ConsoleTab.Push -> PushTabContent(
                    serviceRunning = serviceRunning,
                    apiToken = apiToken,
                    apiPort = apiPort,
                    requestApi = requestApi,
                )

                ConsoleTab.System -> SystemTabContent(
                    rootAvailable = rootAvailable,
                    serviceRunning = serviceRunning,
                    adminTokenFromEnv = adminToken,
                    sessionAdminToken = sessionAdminToken,
                    onSetSessionAdminToken = onSetSessionAdminToken,
                    onClearSessionAdminToken = onClearSessionAdminToken,
                    serverConfig = serverConfig,
                    serverConfigLoading = serverConfigLoading,
                    serverConfigError = serverConfigError,
                    onRefreshConfig = onRefreshConfig,
                    onSetEnv = onSetEnv,
                    onDeleteEnv = onDeleteEnv,
                    onClearCache = onClearCache,
                    onDeploy = onDeploy,
                    validateAdminToken = validateAdminToken,
                )
            }
        }
    }
}

/**
 * Tab 定义
 */
private sealed class ConsoleTab(
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    data object Logs : ConsoleTab("日志", Icons.Default.Description)
    data object Requests : ConsoleTab("请求", Icons.Default.Analytics)
    data object ApiTest : ConsoleTab("接口", Icons.Default.Api)
    data object Push : ConsoleTab("推送", Icons.Default.Send)
    data object System : ConsoleTab("系统", Icons.Default.Settings)
}

/**
 * Tab 导航栏
 */
@Composable
private fun ConsoleTabBar(
    tabs: List<ConsoleTab>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        edgePadding = 8.dp,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        tabs.forEachIndexed { index, tab ->
            Tab(
                selected = selectedIndex == index,
                onClick = { onTabSelected(index) },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                text = {
                    Text(
                        text = tab.title,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            )
        }
    }
}

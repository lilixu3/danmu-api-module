@file:OptIn(ExperimentalMaterial3Api::class)

package com.danmuapi.manager.ui.screens.console

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Console main screen – modernized with PrimaryTabRow and ConsoleState/ConsoleActions.
 */
@Composable
fun ConsoleScreen(
    paddingValues: PaddingValues,
    state: ConsoleState,
    actions: ConsoleActions,
) {
    val tabs = remember {
        listOf(
            ConsoleTab.Logs, ConsoleTab.Requests,
            ConsoleTab.ApiTest, ConsoleTab.Push, ConsoleTab.System
        )
    }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val effectiveAdminToken = state.sessionAdminToken.trim()

    LaunchedEffect(state.serviceRunning) {
        if (state.serviceRunning) {
            if (state.serverConfig == null && !state.serverConfigLoading) actions.onRefreshConfig(false)
            if (state.serverLogs.isEmpty() && !state.serverLogsLoading) actions.onRefreshServerLogs()
            if (state.requestRecords.isEmpty() && !state.requestRecordsLoading) actions.onRefreshRequestRecords()
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(paddingValues)
    ) {
        PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    icon = { Icon(imageVector = tab.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    text = { Text(text = tab.title, style = MaterialTheme.typography.labelMedium) }
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
            when (tabs[selectedTabIndex]) {
                ConsoleTab.Logs -> LogsTabContent(
                    rootAvailable = state.rootAvailable,
                    serviceRunning = state.serviceRunning,
                    adminToken = effectiveAdminToken,
                    consoleLogLimit = state.consoleLogLimit,
                    onSetConsoleLogLimit = actions.onSetConsoleLogLimit,
                    serverLogs = state.serverLogs,
                    serverLogsLoading = state.serverLogsLoading,
                    serverLogsError = state.serverLogsError,
                    onRefreshServerLogs = actions.onRefreshServerLogs,
                    onClearServerLogs = actions.onClearServerLogs,
                    moduleLogs = state.moduleLogs,
                    onRefreshModuleLogs = actions.onRefreshModuleLogs,
                    onClearModuleLogs = actions.onClearModuleLogs,
                    onReadModuleLogTail = actions.onReadModuleLogTail,
                )
                ConsoleTab.Requests -> RequestsTabContent(
                    serviceRunning = state.serviceRunning,
                    records = state.requestRecords,
                    todayReqNum = state.todayReqNum,
                    loading = state.requestRecordsLoading,
                    error = state.requestRecordsError,
                    onRefresh = actions.onRefreshRequestRecords,
                )
                ConsoleTab.ApiTest -> ApiTestTabContent(
                    serviceRunning = state.serviceRunning,
                    adminToken = effectiveAdminToken,
                    requestApi = actions.requestApi,
                )
                ConsoleTab.Push -> PushTabContent(
                    serviceRunning = state.serviceRunning,
                    apiToken = state.apiToken,
                    apiPort = state.apiPort,
                    requestApi = actions.requestApi,
                )
                ConsoleTab.System -> SystemTabContent(
                    rootAvailable = state.rootAvailable,
                    serviceRunning = state.serviceRunning,
                    adminTokenFromEnv = state.adminToken,
                    sessionAdminToken = state.sessionAdminToken,
                    onSetSessionAdminToken = actions.onSetSessionAdminToken,
                    onClearSessionAdminToken = actions.onClearSessionAdminToken,
                    serverConfig = state.serverConfig,
                    serverConfigLoading = state.serverConfigLoading,
                    serverConfigError = state.serverConfigError,
                    onRefreshConfig = actions.onRefreshConfig,
                    onSetEnv = actions.onSetEnv,
                    onDeleteEnv = actions.onDeleteEnv,
                    onClearCache = actions.onClearCache,
                    onDeploy = actions.onDeploy,
                    validateAdminToken = actions.validateAdminToken,
                )
            }
        }
    }
}

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

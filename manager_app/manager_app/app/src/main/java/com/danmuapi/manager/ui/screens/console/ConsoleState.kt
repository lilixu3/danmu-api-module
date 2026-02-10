package com.danmuapi.manager.ui.screens.console

import com.danmuapi.manager.data.model.LogsResponse
import com.danmuapi.manager.data.model.RequestRecord
import com.danmuapi.manager.data.model.ServerConfigResponse
import com.danmuapi.manager.data.model.ServerLogEntry
import com.danmuapi.manager.network.HttpResult

/**
 * Aggregated read-only state for the entire Console screen.
 */
data class ConsoleState(
    val rootAvailable: Boolean?,
    val serviceRunning: Boolean,
    val apiToken: String,
    val apiPort: Int,
    val apiHost: String,
    val adminToken: String,
    val sessionAdminToken: String,
    val consoleLogLimit: Int,
    val serverConfig: ServerConfigResponse?,
    val serverConfigLoading: Boolean,
    val serverConfigError: String?,
    val serverLogs: List<ServerLogEntry>,
    val serverLogsLoading: Boolean,
    val serverLogsError: String?,
    val moduleLogs: LogsResponse?,
    val requestRecords: List<RequestRecord>,
    val requestRecordsLoading: Boolean,
    val requestRecordsError: String?,
    val todayReqNum: Int,
)

/**
 * Aggregated callbacks for the entire Console screen.
 */
data class ConsoleActions(
    val onSetConsoleLogLimit: (Int) -> Unit,
    val onSetSessionAdminToken: (String) -> Unit,
    val onClearSessionAdminToken: () -> Unit,
    val onRefreshConfig: (useAdminToken: Boolean) -> Unit,
    val onRefreshServerLogs: () -> Unit,
    val onClearServerLogs: () -> Unit,
    val onSetEnv: (key: String, value: String) -> Unit,
    val onDeleteEnv: (key: String) -> Unit,
    val onClearCache: () -> Unit,
    val onDeploy: () -> Unit,
    val onRefreshModuleLogs: () -> Unit,
    val onClearModuleLogs: () -> Unit,
    val onReadModuleLogTail: (path: String, lines: Int, onResult: (String) -> Unit) -> Unit,
    val onRefreshRequestRecords: () -> Unit,
    val requestApi: suspend (
        method: String,
        path: String,
        query: Map<String, String?>,
        bodyJson: String?,
        useAdminToken: Boolean,
    ) -> HttpResult,
    val validateAdminToken: suspend (token: String) -> Pair<Boolean, String?>,
)

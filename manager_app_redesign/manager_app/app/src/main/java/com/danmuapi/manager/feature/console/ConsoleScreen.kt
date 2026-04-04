@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.danmuapi.manager.feature.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danmuapi.manager.app.state.ManagerViewModel
import com.danmuapi.manager.core.data.network.HttpResult
import com.danmuapi.manager.core.designsystem.component.ActionHintRow
import com.danmuapi.manager.core.designsystem.component.CodeBlock
import com.danmuapi.manager.core.designsystem.component.EmptyHint
import com.danmuapi.manager.core.designsystem.component.InfoRow
import com.danmuapi.manager.core.designsystem.component.MetricPill
import com.danmuapi.manager.core.designsystem.component.PageHeader
import com.danmuapi.manager.core.designsystem.component.SectionCard
import com.danmuapi.manager.core.designsystem.component.StatusTag
import com.danmuapi.manager.core.designsystem.component.StatusTone
import com.danmuapi.manager.core.designsystem.component.formatSizeLabel
import com.danmuapi.manager.core.designsystem.theme.DanmuMonoFamily
import com.danmuapi.manager.core.model.EnvVarItem
import com.danmuapi.manager.core.model.LogFileEntry
import kotlinx.coroutines.launch

private enum class MonitorTab(val title: String) {
    Overview("概览"),
    Logs("日志"),
    Requests("请求"),
    Debug("调试"),
    Environment("环境"),
}

private enum class LogSource(val title: String) {
    Service("服务日志"),
    Module("模块日志"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsoleScreen(
    contentPadding: PaddingValues,
    viewModel: ManagerViewModel,
) {
    val consoleLogLimit by viewModel.consoleLogLimit.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableStateOf(MonitorTab.Overview) }
    var selectedLogSource by rememberSaveable { mutableStateOf(LogSource.Service) }

    LaunchedEffect(selectedTab, selectedLogSource, viewModel.status?.isRunning, viewModel.logs, viewModel.moduleLogPath) {
        when (selectedTab) {
            MonitorTab.Overview -> {
                if (viewModel.status?.isRunning == true && viewModel.serverLogs.isEmpty() && !viewModel.serverLogsLoading) {
                    viewModel.refreshServerLogs()
                }
                if (viewModel.status?.isRunning == true && viewModel.requestRecords.isEmpty() && !viewModel.requestRecordsLoading) {
                    viewModel.refreshRequestRecords()
                }
                if (viewModel.logs == null) {
                    viewModel.refreshLogs()
                }
            }

            MonitorTab.Logs -> {
                if (selectedLogSource == LogSource.Service) {
                    if (viewModel.status?.isRunning == true && viewModel.serverLogs.isEmpty() && !viewModel.serverLogsLoading) {
                        viewModel.refreshServerLogs()
                    }
                } else {
                    if (viewModel.logs == null) {
                        viewModel.refreshLogs()
                    }
                    val firstFile = viewModel.logs?.files?.firstOrNull()
                    if (firstFile != null && viewModel.moduleLogPath == null) {
                        viewModel.loadModuleLog(firstFile.path)
                    }
                }
            }

            MonitorTab.Requests -> {
                if (viewModel.status?.isRunning == true && viewModel.requestRecords.isEmpty() && !viewModel.requestRecordsLoading) {
                    viewModel.refreshRequestRecords()
                }
            }

            MonitorTab.Debug -> Unit
            MonitorTab.Environment -> {
                if (viewModel.status?.isRunning == true && viewModel.serverConfig == null && !viewModel.serverConfigLoading) {
                    viewModel.refreshServerConfig(useAdminToken = viewModel.sessionAdminToken.isNotBlank())
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PageHeader(
                title = "监控",
                subtitle = "先看概览，再钻到日志、请求、调试和环境。高级能力不再和主信息抢层级。",
            )
        }

        PrimaryTabRow(selectedTabIndex = MonitorTab.entries.indexOf(selectedTab)) {
            MonitorTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    text = { Text(tab.title) },
                )
            }
        }

        when (selectedTab) {
            MonitorTab.Overview -> MonitorOverviewTab(viewModel = viewModel)
            MonitorTab.Logs -> LogsTab(
                viewModel = viewModel,
                consoleLogLimit = consoleLogLimit,
                selectedLogSource = selectedLogSource,
                onLogSourceChange = { selectedLogSource = it },
            )

            MonitorTab.Requests -> RequestsTab(viewModel = viewModel)
            MonitorTab.Debug -> ApiDebugTab(viewModel = viewModel)
            MonitorTab.Environment -> SystemEnvTab(viewModel = viewModel)
        }
    }
}

@Composable
private fun MonitorOverviewTab(
    viewModel: ManagerViewModel,
) {
    val latestLog = viewModel.serverLogs.lastOrNull()
    val activeCore = viewModel.status?.activeCore
    val activeUpdate = activeCore?.id?.let(viewModel.updateInfo::get)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionCard(
            title = "运行概览",
            subtitle = "把监控首屏改成观察页，而不是直接扔给你一整屏日志。",
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MetricPill(label = "服务", value = if (viewModel.status?.isRunning == true) "运行中" else "已停止")
                MetricPill(label = "请求", value = viewModel.todayReqNum.toString())
                MetricPill(label = "日志文件", value = viewModel.logs?.files?.size?.toString() ?: "0")
                MetricPill(label = "当前核心", value = activeCore?.repoDisplayName ?: "未激活")
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusTag(
                    text = if (viewModel.rootAvailable == true) "Root 已就绪" else "Root 未就绪",
                    tone = if (viewModel.rootAvailable == true) StatusTone.Positive else StatusTone.Warning,
                )
                StatusTag(
                    text = if (viewModel.sessionAdminToken.isBlank()) "普通模式" else "管理员模式",
                    tone = if (viewModel.sessionAdminToken.isBlank()) StatusTone.Neutral else StatusTone.Info,
                )
                if (activeUpdate?.updateAvailable == true) {
                    StatusTag(text = "当前核心可更新", tone = StatusTone.Warning)
                }
                if (viewModel.moduleUpdateInfo?.hasUpdate == true) {
                    StatusTag(text = "模块可更新", tone = StatusTone.Warning)
                }
            }
        }

        SectionCard(
            title = "近期事件",
            subtitle = "这里只放最值得关注的几条变化和异常，不铺满原始日志。",
        ) {
            val events = buildList {
                add(
                    if (viewModel.status?.isRunning == true) {
                        Triple("服务运行中", "当前可以继续检查请求、日志和环境配置。", StatusTone.Positive)
                    } else {
                        Triple("服务已停止", "请求与调试会受影响，必要时先回主页启动服务。", StatusTone.Negative)
                    },
                )
                latestLog?.let { log ->
                    add(
                        Triple(
                            "最近日志 ${log.level.ifBlank { "INFO" }}",
                            "${log.timestamp} ${log.message}",
                            when (log.level) {
                                "ERROR" -> StatusTone.Negative
                                "WARN" -> StatusTone.Warning
                                else -> StatusTone.Neutral
                            },
                        ),
                    )
                }
                if (viewModel.requestRecords.isNotEmpty()) {
                    val latestRequest = viewModel.requestRecords.firstOrNull()
                    if (latestRequest != null) {
                        add(
                            Triple(
                                "最近请求 ${latestRequest.method}",
                                "${latestRequest.timestamp} ${latestRequest.path}",
                                StatusTone.Info,
                            ),
                        )
                    }
                }
                if (viewModel.logs?.files.isNullOrEmpty()) {
                    add(
                        Triple(
                            "模块日志为空",
                            "尚未发现模块日志文件，安装、切换或启动后这里才会出现内容。",
                            StatusTone.Neutral,
                        ),
                    )
                }
            }

            if (events.isEmpty()) {
                EmptyHint(title = "还没有事件", detail = "先让服务运行一段时间，监控页才会沉淀出更有价值的摘要。")
            } else {
                events.take(4).forEach { (title, detail, tone) ->
                    ActionHintRow(
                        title = title,
                        detail = detail,
                        tone = tone,
                    )
                }
            }
        }

        SectionCard(
            title = "快捷维护",
            subtitle = "把常用维护动作保留在监控页，但不与日志正文混排。",
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ElevatedButton(onClick = viewModel::refreshServerLogs) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Text("刷新服务日志")
                }
                FilledTonalButton(onClick = viewModel::refreshRequestRecords) {
                    Text("刷新请求记录")
                }
                OutlinedButton(onClick = {
                    viewModel.refreshServerConfig(useAdminToken = viewModel.sessionAdminToken.isNotBlank())
                }) {
                    Text("刷新环境配置")
                }
            }
        }
    }
}

@Composable
private fun LogsTab(
    viewModel: ManagerViewModel,
    consoleLogLimit: Int,
    selectedLogSource: LogSource,
    onLogSourceChange: (LogSource) -> Unit,
) {
    val moduleFiles = viewModel.logs?.files.orEmpty()
    val selectedFile = moduleFiles.firstOrNull { it.path == viewModel.moduleLogPath } ?: moduleFiles.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionCard(
            title = "日志浏览",
            subtitle = "先选来源，再读内容，避免把服务日志和模块日志混成一个大列表。",
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LogSource.entries.forEach { source ->
                    AssistChip(
                        onClick = { onLogSourceChange(source) },
                        label = { Text(source.title) },
                        leadingIcon = if (selectedLogSource == source) {
                            { Icon(Icons.Filled.Description, contentDescription = null) }
                        } else {
                            null
                        },
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ElevatedButton(
                    onClick = {
                        if (selectedLogSource == LogSource.Service) {
                            viewModel.refreshServerLogs()
                        } else {
                            viewModel.refreshLogs()
                        }
                    },
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Text("刷新")
                }
                FilledTonalButton(
                    onClick = {
                        if (selectedLogSource == LogSource.Service) {
                            viewModel.clearServerLogs()
                        } else {
                            viewModel.clearLogs()
                        }
                    },
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Text(if (selectedLogSource == LogSource.Service) "清空服务日志" else "清空模块日志")
                }
            }
            InfoRow(label = "显示上限", value = "$consoleLogLimit 行")
        }

        if (selectedLogSource == LogSource.Service) {
            when {
                viewModel.serverLogsLoading -> EmptyHint(title = "日志加载中", detail = "正在读取服务日志。")
                viewModel.serverLogsError != null -> EmptyHint(title = "读取失败", detail = viewModel.serverLogsError.orEmpty())
                viewModel.serverLogs.isEmpty() -> EmptyHint(title = "暂无服务日志", detail = "服务启动后，这里会出现最近运行日志。")
                else -> {
                    viewModel.serverLogs.takeLast(consoleLogLimit).reversed().forEach { entry ->
                        SectionCard(
                            title = entry.level.ifBlank { "日志" },
                            subtitle = entry.timestamp,
                        ) {
                            CodeBlock(text = entry.message)
                        }
                    }
                }
            }
        } else {
            if (moduleFiles.isEmpty()) {
                EmptyHint(title = "未发现模块日志", detail = "先执行安装、切换或启动动作，日志目录才会产生内容。")
            } else {
                SectionCard(
                    title = "日志文件",
                    subtitle = "先选择文件，再在下方查看正文。",
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        moduleFiles.forEach { file ->
                            AssistChip(
                                onClick = { viewModel.loadModuleLog(file.path) },
                                label = { Text(file.name) },
                                leadingIcon = if (viewModel.moduleLogPath == file.path) {
                                    { Icon(Icons.Filled.Description, contentDescription = null) }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }

                if (selectedFile != null) {
                    ModuleLogDetail(
                        file = selectedFile,
                        content = viewModel.moduleLogText,
                        loading = viewModel.moduleLogLoading,
                        error = viewModel.moduleLogError,
                        consoleLogLimit = consoleLogLimit,
                    )
                }
            }
        }
    }
}

@Composable
private fun ModuleLogDetail(
    file: LogFileEntry,
    content: String,
    loading: Boolean,
    error: String?,
    consoleLogLimit: Int,
) {
    SectionCard(
        title = file.name,
        subtitle = "读取方式：tail 最近 $consoleLogLimit 行",
    ) {
        InfoRow(label = "路径", value = file.path)
        InfoRow(label = "体积", value = file.sizeBytes.formatSizeLabel())
        InfoRow(label = "修改时间", value = file.modifiedAt ?: "--")
        when {
            loading -> Text("正在读取日志内容…", style = MaterialTheme.typography.bodyMedium)
            error != null -> EmptyHint(title = "读取失败", detail = error)
            content.isBlank() -> EmptyHint(title = "日志为空", detail = "该文件当前没有可显示内容。")
            else -> CodeBlock(text = content)
        }
    }
}

@Composable
private fun RequestsTab(
    viewModel: ManagerViewModel,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionCard(
            title = "请求记录",
            subtitle = "请求页只负责看调用轨迹，不再混入别的调试功能。",
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ElevatedButton(onClick = viewModel::refreshRequestRecords) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Text("刷新记录")
                }
            }
            InfoRow(label = "今日请求数", value = viewModel.todayReqNum.toString())
            InfoRow(label = "当前列表项", value = viewModel.requestRecords.size.toString())
        }

        when {
            viewModel.requestRecordsLoading -> EmptyHint(title = "请求记录加载中", detail = "正在请求服务端记录。")
            viewModel.requestRecordsError != null -> EmptyHint(title = "读取失败", detail = viewModel.requestRecordsError.orEmpty())
            viewModel.requestRecords.isEmpty() -> EmptyHint(title = "暂无请求记录", detail = "服务可能尚未收到请求，或者服务还没有运行。")
            else -> {
                viewModel.requestRecords.forEach { record ->
                    SectionCard(
                        title = "${record.method} ${record.path}",
                        subtitle = record.timestamp,
                    ) {
                        InfoRow(label = "客户端", value = record.clientIp.ifBlank { "--" })
                        record.params?.takeIf { it.isNotBlank() }?.let { params ->
                            CodeBlock(text = params)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ApiDebugTab(
    viewModel: ManagerViewModel,
) {
    var method by rememberSaveable { mutableStateOf("GET") }
    var path by rememberSaveable { mutableStateOf("/api/config") }
    var queryLines by rememberSaveable { mutableStateOf("") }
    var bodyJson by rememberSaveable { mutableStateOf("{}") }
    var useAdminToken by rememberSaveable { mutableStateOf(false) }
    val methods = listOf("GET", "POST", "PUT", "PATCH", "DELETE")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionCard(
            title = "API 调试",
            subtitle = "这是高级能力，保留但不再占据监控首页的主位置。",
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                methods.forEach { candidate ->
                    AssistChip(
                        onClick = { method = candidate },
                        label = { Text(candidate) },
                        leadingIcon = if (candidate == method) {
                            { Icon(Icons.Filled.Api, contentDescription = null) }
                        } else {
                            null
                        },
                    )
                }
            }
            OutlinedTextField(
                value = path,
                onValueChange = { path = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("接口路径") },
                singleLine = true,
            )
            OutlinedTextField(
                value = queryLines,
                onValueChange = { queryLines = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Query 参数，每行 key=value") },
                minLines = 3,
                maxLines = 6,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = DanmuMonoFamily),
            )
            OutlinedTextField(
                value = bodyJson,
                onValueChange = { bodyJson = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("JSON Body") },
                minLines = 4,
                maxLines = 12,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = DanmuMonoFamily),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("使用管理员 Token")
                Switch(
                    checked = useAdminToken,
                    onCheckedChange = { useAdminToken = it },
                )
            }
            ElevatedButton(
                onClick = {
                    viewModel.runApiDebugRequest(
                        method = method,
                        path = path,
                        queryPairs = parseQueryPairs(queryLines),
                        bodyJson = bodyJson.takeIf { it.isNotBlank() },
                        useAdminToken = useAdminToken,
                    )
                },
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                Text("发送请求")
            }
        }

        if (viewModel.apiDebugRequestSummary.isNotBlank()) {
            SectionCard(title = "请求摘要") {
                CodeBlock(text = viewModel.apiDebugRequestSummary)
            }
        }

        when {
            viewModel.apiDebugLoading -> EmptyHint(title = "请求发送中", detail = "等待接口返回结果。")
            viewModel.apiDebugError != null -> EmptyHint(title = "请求失败", detail = viewModel.apiDebugError.orEmpty())
            viewModel.apiDebugResult != null -> HttpResultCard(result = viewModel.apiDebugResult!!)
        }
    }
}

@Composable
private fun HttpResultCard(result: HttpResult) {
    SectionCard(title = "响应结果") {
        InfoRow(label = "HTTP", value = result.code.toString())
        InfoRow(label = "耗时", value = "${result.durationMs} ms")
        InfoRow(label = "Content-Type", value = result.contentType ?: "--")
        InfoRow(label = "保留字节", value = result.bodyBytesKept.toString())
        if (result.truncated) {
            StatusTag(text = "响应内容已截断", tone = StatusTone.Warning)
        }
        CodeBlock(text = result.body.ifBlank { "(empty body)" })
    }
}

@Composable
private fun SystemEnvTab(
    viewModel: ManagerViewModel,
) {
    val scope = rememberCoroutineScope()
    var tokenCandidate by remember(viewModel.sessionAdminToken) { mutableStateOf(viewModel.sessionAdminToken) }
    var tokenFeedback by rememberSaveable { mutableStateOf<String?>(null) }
    var tokenVerifying by rememberSaveable { mutableStateOf(false) }
    var editingEnv by remember { mutableStateOf<EnvVarItem?>(null) }
    var editValue by rememberSaveable { mutableStateOf("") }
    var deletingEnv by remember { mutableStateOf<EnvVarItem?>(null) }

    if (editingEnv != null) {
        val target = editingEnv!!
        AlertDialog(
            onDismissRequest = { editingEnv = null },
            title = { Text("编辑 ${target.key}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = target.description.ifBlank { "修改后会立即调用服务端接口更新环境变量。" },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (target.options.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            target.options.forEach { option ->
                                AssistChip(
                                    onClick = { editValue = option },
                                    label = { Text(option) },
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = editValue,
                        onValueChange = { editValue = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("变量值") },
                        minLines = if (target.type == "json" || target.value.length > 60) 3 else 1,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setServerEnvVar(target.key, editValue)
                        editingEnv = null
                    },
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingEnv = null }) {
                    Text("取消")
                }
            },
        )
    }

    if (deletingEnv != null) {
        val target = deletingEnv!!
        AlertDialog(
            onDismissRequest = { deletingEnv = null },
            title = { Text("删除环境变量") },
            text = { Text("确认删除 ${target.key}？该操作会直接调用服务端接口。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteServerEnvVar(target.key)
                        deletingEnv = null
                    },
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingEnv = null }) {
                    Text("取消")
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionCard(
            title = "管理员模式",
            subtitle = "管理员 Token 只保存在当前会话，不写入本地持久存储。",
        ) {
            OutlinedTextField(
                value = tokenCandidate,
                onValueChange = { tokenCandidate = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("会话 ADMIN_TOKEN") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ElevatedButton(
                    onClick = {
                        tokenVerifying = true
                        tokenFeedback = null
                        scope.launch {
                            val (ok, error) = viewModel.validateAdminToken(tokenCandidate)
                            if (ok) {
                                viewModel.setSessionAdminToken(tokenCandidate)
                                viewModel.refreshServerConfig(useAdminToken = true)
                                tokenFeedback = "管理员模式已启用"
                            } else {
                                tokenFeedback = error
                            }
                            tokenVerifying = false
                        }
                    },
                    enabled = !tokenVerifying,
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null)
                    Text(if (tokenVerifying) "验证中" else "验证并启用")
                }
                FilledTonalButton(
                    onClick = {
                        tokenCandidate = ""
                        tokenFeedback = "已清除会话 Token"
                        viewModel.clearSessionAdminToken()
                        viewModel.refreshServerConfig(useAdminToken = false)
                    },
                ) {
                    Text("退出管理员模式")
                }
            }
            InfoRow(label = "环境内置 ADMIN_TOKEN", value = if (viewModel.adminToken.isBlank()) "未配置" else "已配置")
            InfoRow(label = "会话状态", value = if (viewModel.sessionAdminToken.isBlank()) "普通模式" else "管理员模式")
            tokenFeedback?.let {
                StatusTag(
                    text = it,
                    tone = if (it.contains("已")) StatusTone.Positive else StatusTone.Warning,
                )
            }
        }

        SectionCard(
            title = "系统动作",
            subtitle = "这里只放服务端环境相关动作，不与日志或请求混排。",
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ElevatedButton(
                    onClick = {
                        viewModel.refreshServerConfig(useAdminToken = viewModel.sessionAdminToken.isNotBlank())
                    },
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Text("刷新配置")
                }
                FilledTonalButton(onClick = viewModel::clearServerCache) {
                    Text("清理缓存")
                }
                FilledTonalButton(onClick = viewModel::deployServer) {
                    Text("重新部署")
                }
            }
            viewModel.serverConfig?.let { config ->
                InfoRow(label = "服务版本", value = config.version ?: "--")
                InfoRow(label = "仓库", value = config.repository ?: "--")
                InfoRow(label = "分类数", value = config.categorizedEnvVars.size.toString())
            }
        }

        when {
            viewModel.serverConfigLoading -> EmptyHint(title = "配置加载中", detail = "正在请求服务端配置。")
            viewModel.serverConfigError != null -> EmptyHint(title = "读取配置失败", detail = viewModel.serverConfigError.orEmpty())
            viewModel.serverConfig == null -> EmptyHint(title = "暂无系统配置", detail = "先刷新配置，或确认服务已经运行。")
            else -> {
                val serverConfig = viewModel.serverConfig!!
                serverConfig.notice?.takeIf { it.isNotBlank() }?.let { notice ->
                    SectionCard(title = "服务通知") {
                        CodeBlock(text = notice)
                    }
                }
                serverConfig.categorizedEnvVars.forEach { (category, items) ->
                    SectionCard(title = category.ifBlank { "未分类" }) {
                        if (items.isEmpty()) {
                            EmptyHint(title = "该分类为空", detail = "服务端没有返回该分类的环境变量。")
                        } else {
                            items.forEach { item ->
                                EnvVarCard(
                                    item = item,
                                    onEdit = {
                                        editingEnv = item
                                        editValue = item.value
                                    },
                                    onDelete = { deletingEnv = item },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EnvVarCard(
    item: EnvVarItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.key,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssistChip(onClick = onEdit, label = { Text("编辑") })
                AssistChip(onClick = onDelete, label = { Text("删除") })
            }
        }
        item.description.takeIf { it.isNotBlank() }?.let { description ->
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (item.options.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item.options.forEach { option ->
                    StatusTag(text = option, tone = StatusTone.Neutral)
                }
            }
        }
        CodeBlock(text = item.value.ifBlank { "(empty)" })
    }
}

private fun parseQueryPairs(input: String): List<Pair<String, String>> {
    return input.lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map { line ->
            val index = line.indexOf('=')
            if (index < 0) {
                line to ""
            } else {
                line.substring(0, index).trim() to line.substring(index + 1).trim()
            }
        }
        .toList()
}

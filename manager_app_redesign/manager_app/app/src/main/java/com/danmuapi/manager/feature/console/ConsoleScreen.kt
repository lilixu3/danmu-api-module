package com.danmuapi.manager.feature.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danmuapi.manager.app.state.ManagerViewModel
import com.danmuapi.manager.core.data.network.HttpResult
import com.danmuapi.manager.core.designsystem.component.CodeBlock
import com.danmuapi.manager.core.designsystem.component.EmptyHint
import com.danmuapi.manager.core.designsystem.component.InfoRow
import com.danmuapi.manager.core.designsystem.component.SectionCard
import com.danmuapi.manager.core.designsystem.component.StatusTag
import com.danmuapi.manager.core.designsystem.component.StatusTone
import com.danmuapi.manager.core.designsystem.component.formatSizeLabel
import com.danmuapi.manager.core.designsystem.theme.DanmuMonoFamily
import com.danmuapi.manager.core.model.EnvVarItem
import com.danmuapi.manager.core.model.LogFileEntry
import kotlinx.coroutines.launch

private enum class ConsoleTab(
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    ServiceLogs("服务日志", Icons.Filled.Description),
    ModuleLogs("模块日志", Icons.AutoMirrored.Filled.Send),
    Requests("请求记录", Icons.Filled.Analytics),
    ApiDebug("API 调试", Icons.Filled.Api),
    System("系统环境", Icons.Filled.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsoleScreen(
    contentPadding: PaddingValues,
    viewModel: ManagerViewModel,
) {
    val consoleLogLimit by viewModel.consoleLogLimit.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableStateOf(ConsoleTab.ServiceLogs) }

    LaunchedEffect(selectedTab, viewModel.status?.isRunning, viewModel.logs, viewModel.moduleLogPath) {
        when (selectedTab) {
            ConsoleTab.ServiceLogs -> {
                if (viewModel.status?.isRunning == true &&
                    viewModel.serverLogs.isEmpty() &&
                    !viewModel.serverLogsLoading
                ) {
                    viewModel.refreshServerLogs()
                }
            }

            ConsoleTab.ModuleLogs -> {
                if (viewModel.logs == null) {
                    viewModel.refreshLogs()
                }
                val firstFile = viewModel.logs?.files?.firstOrNull()
                if (firstFile != null && viewModel.moduleLogPath == null) {
                    viewModel.loadModuleLog(firstFile.path)
                }
            }

            ConsoleTab.Requests -> {
                if (viewModel.status?.isRunning == true &&
                    viewModel.requestRecords.isEmpty() &&
                    !viewModel.requestRecordsLoading
                ) {
                    viewModel.refreshRequestRecords()
                }
            }

            ConsoleTab.ApiDebug -> Unit
            ConsoleTab.System -> {
                if (viewModel.status?.isRunning == true &&
                    viewModel.serverConfig == null &&
                    !viewModel.serverConfigLoading
                ) {
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
        PrimaryTabRow(selectedTabIndex = ConsoleTab.entries.indexOf(selectedTab)) {
            ConsoleTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    icon = {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = null,
                        )
                    },
                    text = { Text(tab.title) },
                )
            }
        }

        when (selectedTab) {
            ConsoleTab.ServiceLogs -> ServiceLogsTab(
                viewModel = viewModel,
                consoleLogLimit = consoleLogLimit,
            )

            ConsoleTab.ModuleLogs -> ModuleLogsTab(
                viewModel = viewModel,
                consoleLogLimit = consoleLogLimit,
            )

            ConsoleTab.Requests -> RequestsTab(viewModel = viewModel)
            ConsoleTab.ApiDebug -> ApiDebugTab(viewModel = viewModel)
            ConsoleTab.System -> SystemEnvTab(viewModel = viewModel)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServiceLogsTab(
    viewModel: ManagerViewModel,
    consoleLogLimit: Int,
) {
    val displayLogs = remember(viewModel.serverLogs, consoleLogLimit) {
        viewModel.serverLogs.takeLast(consoleLogLimit)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionCard(title = "服务日志") {
            Text(
                text = "读取 danmu-api 运行时日志，可用于快速定位接口错误、管理员操作与部署状态。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ElevatedButton(onClick = viewModel::refreshServerLogs) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Text("刷新")
                }
                FilledTonalButton(onClick = viewModel::clearServerLogs) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Text("清空服务日志")
                }
            }
            InfoRow(label = "服务状态", value = if (viewModel.status?.isRunning == true) "Running" else "Stopped")
            InfoRow(label = "显示行数上限", value = consoleLogLimit.toString())
            if (viewModel.sessionAdminToken.isBlank()) {
                StatusTag(
                    text = "未启用管理员 Token，清空服务日志可能会失败",
                    tone = StatusTone.Warning,
                )
            }
        }

        when {
            viewModel.serverLogsLoading -> {
                EmptyHint(
                    title = "日志加载中",
                    detail = "正在读取服务日志，请稍候。",
                )
            }

            viewModel.serverLogsError != null -> {
                EmptyHint(
                    title = "读取失败",
                    detail = viewModel.serverLogsError.orEmpty(),
                )
            }

            displayLogs.isEmpty() -> {
                EmptyHint(
                    title = "暂无服务日志",
                    detail = "如果服务尚未启动，先回到总览页启动服务再回来查看。",
                )
            }

            else -> {
                displayLogs.forEach { entry ->
                    SectionCard(title = entry.timestamp.ifBlank { "无时间戳" }) {
                        if (entry.level.isNotBlank()) {
                            StatusTag(
                                text = entry.level,
                                tone = when (entry.level) {
                                    "ERROR" -> StatusTone.Negative
                                    "WARN" -> StatusTone.Warning
                                    "INFO" -> StatusTone.Info
                                    else -> StatusTone.Neutral
                                },
                            )
                        }
                        CodeBlock(text = entry.message)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModuleLogsTab(
    viewModel: ManagerViewModel,
    consoleLogLimit: Int,
) {
    val files = viewModel.logs?.files.orEmpty()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionCard(title = "模块日志") {
            Text(
                text = "读取模块本地日志文件并执行 tail，适合排查核心安装、切换与 CLI 调用问题。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ElevatedButton(onClick = viewModel::refreshLogs) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Text("刷新文件列表")
                }
                FilledTonalButton(onClick = viewModel::clearLogs) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Text("清空模块日志")
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(100, 300, 500, 1000).forEach { limit ->
                    AssistChip(
                        onClick = { viewModel.setConsoleLogLimit(limit) },
                        label = { Text("$limit 行") },
                    )
                }
            }
        }

        if (files.isEmpty()) {
            EmptyHint(
                title = "未发现日志文件",
                detail = "先执行一次核心安装、启动或切换动作，日志目录才会产生内容。",
            )
        } else {
            SectionCard(title = "日志文件") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    files.forEach { file ->
                        AssistChip(
                            onClick = { viewModel.loadModuleLog(file.path) },
                            label = { Text(file.name) },
                            leadingIcon = if (viewModel.moduleLogPath == file.path) {
                                {
                                    Icon(Icons.Filled.Description, contentDescription = null)
                                }
                            } else {
                                null
                            },
                        )
                    }
                }
            }

            files.firstOrNull { it.path == viewModel.moduleLogPath }?.let { selected ->
                ModuleLogDetail(
                    file = selected,
                    content = viewModel.moduleLogText,
                    loading = viewModel.moduleLogLoading,
                    error = viewModel.moduleLogError,
                    consoleLogLimit = consoleLogLimit,
                )
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
    SectionCard(title = file.name) {
        InfoRow(label = "路径", value = file.path)
        InfoRow(label = "体积", value = file.sizeBytes.formatSizeLabel())
        InfoRow(label = "修改时间", value = file.modifiedAt ?: "--")
        InfoRow(label = "读取方式", value = "tail 最近 $consoleLogLimit 行")
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
        SectionCard(title = "请求记录") {
            Text(
                text = "实时查看 API 请求轨迹、调用来源和参数快照，便于确认服务是否被外部客户端正确访问。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ElevatedButton(
                    onClick = viewModel::refreshRequestRecords,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Text("刷新记录")
                }
            }
            InfoRow(label = "今日请求数", value = viewModel.todayReqNum.toString())
            InfoRow(label = "当前列表项", value = viewModel.requestRecords.size.toString())
        }

        when {
            viewModel.requestRecordsLoading -> {
                EmptyHint(title = "请求记录加载中", detail = "正在请求服务端记录，请稍候。")
            }

            viewModel.requestRecordsError != null -> {
                EmptyHint(title = "读取失败", detail = viewModel.requestRecordsError.orEmpty())
            }

            viewModel.requestRecords.isEmpty() -> {
                EmptyHint(
                    title = "暂无请求记录",
                    detail = "服务可能还未收到任何请求，或者服务尚未运行。",
                )
            }

            else -> {
                viewModel.requestRecords.forEach { record ->
                    SectionCard(title = "${record.method} ${record.path}") {
                        InfoRow(label = "时间", value = record.timestamp)
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

@OptIn(ExperimentalLayoutApi::class)
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
        SectionCard(title = "API 调试器") {
            Text(
                text = "直接构造 danmu-api 请求，验证路径、参数、管理员模式和响应体。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                methods.forEach { candidate ->
                    AssistChip(
                        onClick = { method = candidate },
                        label = { Text(candidate) },
                        leadingIcon = if (candidate == method) {
                            {
                                Icon(Icons.Filled.Api, contentDescription = null)
                            }
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
            viewModel.apiDebugLoading -> {
                EmptyHint(title = "请求发送中", detail = "等待接口返回结果。")
            }

            viewModel.apiDebugError != null -> {
                EmptyHint(title = "请求失败", detail = viewModel.apiDebugError.orEmpty())
            }

            viewModel.apiDebugResult != null -> {
                HttpResultCard(result = viewModel.apiDebugResult!!)
            }
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

@OptIn(ExperimentalLayoutApi::class)
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
        SectionCard(title = "管理员模式") {
            Text(
                text = "管理员 Token 只保存在当前会话里，不写入本地持久存储，用于访问高权限接口。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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

        SectionCard(title = "系统动作") {
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
            viewModel.serverConfigLoading -> {
                EmptyHint(title = "配置加载中", detail = "正在请求服务端配置。")
            }

            viewModel.serverConfigError != null -> {
                EmptyHint(title = "读取配置失败", detail = viewModel.serverConfigError.orEmpty())
            }

            viewModel.serverConfig == null -> {
                EmptyHint(title = "暂无系统配置", detail = "先刷新配置，或确认服务已经运行。")
            }

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

@OptIn(ExperimentalLayoutApi::class)
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
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
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

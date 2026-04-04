@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.danmuapi.manager.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.danmuapi.manager.app.state.ManagerViewModel
import com.danmuapi.manager.core.data.network.HttpResult
import com.danmuapi.manager.core.designsystem.theme.DanmuMonoFamily
import com.danmuapi.manager.core.model.EnvVarItem
import kotlinx.coroutines.launch

@Composable
fun ApiDebugScreen(
    contentPadding: PaddingValues,
    viewModel: ManagerViewModel,
    onBack: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    var method by rememberSaveable { mutableStateOf("GET") }
    var path by rememberSaveable { mutableStateOf("/api/config") }
    var queryLines by rememberSaveable { mutableStateOf("") }
    var bodyJson by rememberSaveable { mutableStateOf("{}") }
    var useAdminToken by rememberSaveable { mutableStateOf(false) }
    val methods = listOf("GET", "POST", "PUT", "PATCH", "DELETE")

    SettingsScrollablePage(
        contentPadding = contentPadding,
        palette = palette,
    ) {
        SettingsImmersiveHeader(
            title = "API 调试",
            subtitle = "用紧凑工具页构造请求、验证参数并直接查看响应。",
            palette = palette,
            leading = {
                SettingsHeaderIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    palette = palette,
                    onClick = onBack,
                )
            },
        )

        if (viewModel.apiDebugLoading) {
            SettingsBusyStrip(
                message = "请求发送中…",
                palette = palette,
            )
        }

        SettingsPanel(palette = palette) {
            SettingsPanelHeader(
                title = "请求参数",
                subtitle = "路径、Query 和 Body 都保留在一处，方便连续调试。",
                palette = palette,
                trailing = {
                    SettingsTag(
                        text = method,
                        palette = palette,
                        tone = palette.accent,
                    )
                },
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                methods.forEach { candidate ->
                    androidx.compose.material3.FilterChip(
                        selected = candidate == method,
                        onClick = { method = candidate },
                        label = { Text(candidate) },
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
                maxLines = 10,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = DanmuMonoFamily),
            )
            SettingsPlainRow(
                title = "使用管理员 Token",
                subtitle = "需要调试高权限接口时再打开，默认仍走普通 Token。",
                palette = palette,
                trailing = {
                    Switch(
                        checked = useAdminToken,
                        onCheckedChange = { useAdminToken = it },
                    )
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                FilledTonalButton(
                    onClick = {
                        viewModel.runApiDebugRequest(
                            method = method,
                            path = path,
                            queryPairs = parseQueryPairs(queryLines),
                            bodyJson = bodyJson.takeIf { it.isNotBlank() },
                            useAdminToken = useAdminToken,
                        )
                    },
                    enabled = !viewModel.apiDebugLoading,
                ) {
                    Text(if (viewModel.apiDebugLoading) "发送中" else "发送请求")
                }
            }
        }

        if (viewModel.apiDebugRequestSummary.isNotBlank()) {
            SettingsPanel(palette = palette) {
                SettingsPanelHeader(
                    title = "请求摘要",
                    subtitle = "当前请求的完整访问地址。",
                    palette = palette,
                )
                SettingsCodeBlock(
                    text = viewModel.apiDebugRequestSummary,
                    palette = palette,
                )
            }
        }

        when {
            viewModel.apiDebugError != null -> {
                SettingsPanel(palette = palette) {
                    SettingsPanelHeader(
                        title = "请求失败",
                        subtitle = "接口返回错误，先确认路径、参数和 Token。",
                        palette = palette,
                        trailing = {
                            SettingsTag(
                                text = "失败",
                                palette = palette,
                                tone = palette.danger,
                                containerColor = palette.dangerContainer,
                            )
                        },
                    )
                    SettingsCodeBlock(
                        text = viewModel.apiDebugError.orEmpty(),
                        palette = palette,
                    )
                }
            }

            viewModel.apiDebugResult != null -> {
                ApiDebugResultPanel(
                    result = viewModel.apiDebugResult!!,
                    palette = palette,
                )
            }
        }
    }
}

@Composable
private fun ApiDebugResultPanel(
    result: HttpResult,
    palette: SettingsPalette,
) {
    SettingsPanel(palette = palette) {
        SettingsPanelHeader(
            title = "响应结果",
            subtitle = "保留必要元信息，正文放在下方代码区。",
            palette = palette,
            trailing = {
                SettingsTag(
                    text = "HTTP ${result.code}",
                    palette = palette,
                    tone = if (result.isSuccessful) palette.positive else palette.danger,
                    containerColor = if (result.isSuccessful) palette.positiveContainer else palette.dangerContainer,
                )
            },
        )
        SettingsValueRow(label = "耗时", value = "${result.durationMs} ms", palette = palette)
        SettingsValueRow(label = "类型", value = result.contentType ?: "--", palette = palette)
        SettingsValueRow(label = "保留", value = "${result.bodyBytesKept} bytes", palette = palette)
        if (result.truncated) {
            SettingsTag(
                text = "响应内容已截断",
                palette = palette,
                tone = palette.warning,
                containerColor = palette.warningContainer,
            )
        }
        SettingsCodeBlock(
            text = result.body.ifBlank { "(empty body)" },
            palette = palette,
        )
    }
}

@Composable
fun ServerEnvScreen(
    contentPadding: PaddingValues,
    viewModel: ManagerViewModel,
    onBack: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    val scope = rememberCoroutineScope()
    var tokenCandidate by remember(viewModel.sessionAdminToken) { mutableStateOf(viewModel.sessionAdminToken) }
    var tokenFeedback by rememberSaveable { mutableStateOf<String?>(null) }
    var tokenVerifying by rememberSaveable { mutableStateOf(false) }
    var editingEnv by remember { mutableStateOf<EnvVarItem?>(null) }
    var editValue by rememberSaveable { mutableStateOf("") }
    var deletingEnv by remember { mutableStateOf<EnvVarItem?>(null) }

    LaunchedEffect(viewModel.status?.isRunning) {
        if (viewModel.status?.isRunning == true &&
            viewModel.serverConfig == null &&
            !viewModel.serverConfigLoading
        ) {
            viewModel.refreshServerConfig(useAdminToken = viewModel.sessionAdminToken.isNotBlank())
        }
    }

    if (editingEnv != null) {
        val target = editingEnv!!
        AlertDialog(
            onDismissRequest = { editingEnv = null },
            title = { Text("编辑 ${target.key}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    target.description.takeIf { it.isNotBlank() }?.let { description ->
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (target.options.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            target.options.forEach { option ->
                                androidx.compose.material3.FilterChip(
                                    selected = editValue == option,
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

    SettingsScrollablePage(
        contentPadding = contentPadding,
        palette = palette,
    ) {
        SettingsImmersiveHeader(
            title = "服务端环境",
            subtitle = "管理员模式、系统动作和环境变量都压进更清晰的工具页结构。",
            palette = palette,
            leading = {
                SettingsHeaderIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    palette = palette,
                    onClick = onBack,
                )
            },
        )

        viewModel.busyMessage?.takeIf { it.isNotBlank() }?.let { message ->
            SettingsBusyStrip(message = message, palette = palette)
        }

        SettingsPanel(palette = palette) {
            SettingsPanelHeader(
                title = "管理员模式",
                subtitle = "管理员 Token 只保存在当前会话，不写入本地持久存储。",
                palette = palette,
                trailing = {
                    SettingsTag(
                        text = if (viewModel.sessionAdminToken.isBlank()) "普通模式" else "管理员模式",
                        palette = palette,
                        tone = if (viewModel.sessionAdminToken.isBlank()) palette.subtleText else palette.positive,
                    )
                },
            )
            OutlinedTextField(
                value = tokenCandidate,
                onValueChange = { tokenCandidate = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("会话 ADMIN_TOKEN") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilledTonalButton(
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
                    Text(if (tokenVerifying) "验证中" else "验证并启用")
                }
                OutlinedButton(
                    onClick = {
                        tokenCandidate = ""
                        tokenFeedback = "已退出管理员模式"
                        viewModel.clearSessionAdminToken()
                        viewModel.refreshServerConfig(useAdminToken = false)
                    },
                ) {
                    Text("退出管理员模式")
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SettingsInfoPill(
                    label = "环境内置",
                    value = if (viewModel.adminToken.isBlank()) "未配置" else "已配置",
                    palette = palette,
                    tone = if (viewModel.adminToken.isBlank()) palette.subtleText else palette.accent,
                )
                SettingsInfoPill(
                    label = "当前会话",
                    value = if (viewModel.sessionAdminToken.isBlank()) "普通" else "管理员",
                    palette = palette,
                    tone = if (viewModel.sessionAdminToken.isBlank()) palette.subtleText else palette.positive,
                )
            }
            tokenFeedback?.takeIf { it.isNotBlank() }?.let { feedback ->
                SettingsTag(
                    text = feedback,
                    palette = palette,
                    tone = if (feedback.contains("已")) palette.positive else palette.warning,
                    containerColor = if (feedback.contains("已")) palette.positiveContainer else palette.warningContainer,
                )
            }
        }

        SettingsPanel(palette = palette) {
            SettingsPanelHeader(
                title = "系统动作",
                subtitle = "所有高风险操作统一放在这里，不和其它内容混排。",
                palette = palette,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilledTonalButton(
                    onClick = {
                        viewModel.refreshServerConfig(useAdminToken = viewModel.sessionAdminToken.isNotBlank())
                    },
                ) {
                    Text("刷新配置")
                }
                OutlinedButton(onClick = viewModel::clearServerCache) {
                    Text("清理缓存")
                }
                OutlinedButton(onClick = viewModel::deployServer) {
                    Text("重新部署")
                }
            }
            viewModel.serverConfig?.let { config ->
                SettingsDivider(palette)
                SettingsValueRow(label = "服务版本", value = config.version ?: "--", palette = palette)
                SettingsValueRow(label = "仓库", value = config.repository ?: "--", palette = palette)
                SettingsValueRow(
                    label = "分类数",
                    value = config.categorizedEnvVars.size.toString(),
                    palette = palette,
                )
            }
        }

        when {
            viewModel.serverConfigLoading && viewModel.serverConfig == null -> {
                SettingsPanel(palette = palette) {
                    SettingsPanelHeader(
                        title = "读取配置中",
                        subtitle = "正在请求服务端环境配置。",
                        palette = palette,
                    )
                }
            }

            viewModel.serverConfigError != null -> {
                SettingsPanel(palette = palette) {
                    SettingsPanelHeader(
                        title = "读取配置失败",
                        subtitle = viewModel.serverConfigError.orEmpty(),
                        palette = palette,
                        trailing = {
                            SettingsTag(
                                text = "失败",
                                palette = palette,
                                tone = palette.danger,
                                containerColor = palette.dangerContainer,
                            )
                        },
                    )
                }
            }

            viewModel.serverConfig == null -> {
                SettingsPanel(palette = palette) {
                    SettingsPanelHeader(
                        title = "暂无系统配置",
                        subtitle = "先刷新配置，或确认服务已经运行。",
                        palette = palette,
                    )
                }
            }

            else -> {
                val config = viewModel.serverConfig!!
                config.notice?.takeIf { it.isNotBlank() }?.let { notice ->
                    SettingsPanel(palette = palette) {
                        SettingsPanelHeader(
                            title = "服务通知",
                            subtitle = "接口返回的提示信息。",
                            palette = palette,
                        )
                        SettingsCodeBlock(text = notice, palette = palette)
                    }
                }

                config.categorizedEnvVars.forEach { (category, items) ->
                    SettingsPanel(palette = palette) {
                        SettingsPanelHeader(
                            title = category.ifBlank { "未分类" },
                            subtitle = "变量列表已压缩成更紧凑的维护视图。",
                            palette = palette,
                            trailing = {
                                SettingsTag(
                                    text = "${items.size} 项",
                                    palette = palette,
                                )
                            },
                        )
                        if (items.isEmpty()) {
                            Text(
                                text = "该分类为空。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.subtleText,
                            )
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                items.forEachIndexed { index, item ->
                                    EnvVarRow(
                                        item = item,
                                        palette = palette,
                                        onEdit = {
                                            editingEnv = item
                                            editValue = item.value
                                        },
                                        onDelete = { deletingEnv = item },
                                    )
                                    if (index != items.lastIndex) {
                                        SettingsDivider(palette)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EnvVarRow(
    item: EnvVarItem,
    palette: SettingsPalette,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.key,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    item.type.takeIf { it.isNotBlank() && it != "text" }?.let { type ->
                        SettingsTag(
                            text = type.uppercase(),
                            palette = palette,
                            tone = palette.accent,
                            containerColor = palette.accentContainer,
                        )
                    }
                }
                item.description.takeIf { it.isNotBlank() }?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.subtleText,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onEdit) {
                    Text("编辑")
                }
                TextButton(onClick = onDelete) {
                    Text("删除")
                }
            }
        }

        EnvValuePreview(
            value = item.value,
            palette = palette,
        )

        if (item.options.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item.options.forEach { option ->
                    SettingsTag(
                        text = option,
                        palette = palette,
                    )
                }
            }
        }
    }
}

@Composable
private fun EnvValuePreview(
    value: String,
    palette: SettingsPalette,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = palette.cardMuted,
                shape = RoundedCornerShape(18.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = value.ifBlank { "(empty)" },
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = DanmuMonoFamily),
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
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

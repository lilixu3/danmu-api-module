@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.danmuapi.manager.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danmuapi.manager.app.state.ManagerViewModel
import com.danmuapi.manager.core.model.CoreUpdateInfo
import com.danmuapi.manager.core.model.CoreUpdateState
import com.danmuapi.manager.core.model.ModuleUpdateInfo
import com.danmuapi.manager.core.root.DanmuPaths
import com.danmuapi.manager.core.designsystem.theme.DanmuMonoFamily

@Composable
fun SettingsAccessScreen(
    contentPadding: PaddingValues,
    viewModel: ManagerViewModel,
    onBack: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    val githubToken by viewModel.githubToken.collectAsStateWithLifecycle()
    var githubTokenInput by remember(githubToken) { mutableStateOf(githubToken) }
    val moduleVersion = viewModel.status?.module?.version?.takeIf { it.isNotBlank() } ?: "--"
    val installedCoreCount = viewModel.cores?.cores.orEmpty().size

    SettingsScrollablePage(
        contentPadding = contentPadding,
        palette = palette,
    ) {
        SettingsImmersiveHeader(
            title = "访问与更新",
            subtitle = "把 GitHub 访问凭据和更新检查集中管理，不再混入其它设置。",
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
                title = "GitHub 访问",
                subtitle = "仅在检查更新时使用，避免匿名请求配额过低。",
                palette = palette,
            )
            OutlinedTextField(
                value = githubTokenInput,
                onValueChange = { githubTokenInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("GitHub Token") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                FilledTonalButton(onClick = { viewModel.setGithubToken(githubTokenInput) }) {
                    Text("保存 Token")
                }
            }
        }

        SettingsPanel(palette = palette) {
            SettingsPanelHeader(
                title = "更新检查",
                subtitle = "先看模块，再检查已安装核心的远端状态。",
                palette = palette,
                trailing = {
                    SettingsTag(
                        text = "$installedCoreCount 个核心",
                        palette = palette,
                    )
                },
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilledTonalButton(onClick = viewModel::checkModuleUpdate) {
                    Text("检查模块更新")
                }
                OutlinedButton(onClick = viewModel::checkUpdates) {
                    Text("检查核心更新")
                }
            }
            SettingsDivider(palette)
            SettingsValueRow(
                label = "当前模块",
                value = moduleVersion,
                palette = palette,
            )
            SettingsValueRow(
                label = "模块状态",
                value = buildModuleUpdateSummary(viewModel.moduleUpdateInfo),
                palette = palette,
            )
            SettingsValueRow(
                label = "核心状态",
                value = buildCoreUpdateSummary(viewModel.updateInfo),
                palette = palette,
            )
        }
    }
}

@Composable
fun SettingsBackupScreen(
    contentPadding: PaddingValues,
    viewModel: ManagerViewModel,
    onBack: () -> Unit,
    onOpenEnvEditor: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    val webDavUrl by viewModel.webDavUrl.collectAsStateWithLifecycle()
    val webDavUsername by viewModel.webDavUsername.collectAsStateWithLifecycle()
    val webDavPassword by viewModel.webDavPassword.collectAsStateWithLifecycle()
    val webDavPath by viewModel.webDavPath.collectAsStateWithLifecycle()

    var webDavUrlInput by remember(webDavUrl) { mutableStateOf(webDavUrl) }
    var webDavUsernameInput by remember(webDavUsername) { mutableStateOf(webDavUsername) }
    var webDavPasswordInput by remember(webDavPassword) { mutableStateOf(webDavPassword) }
    var webDavPathInput by remember(webDavPath) {
        mutableStateOf(webDavPath.ifBlank { "danmuapi/danmu_api.env" })
    }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var showLocalImportConfirm by rememberSaveable { mutableStateOf(false) }
    var showWebDavImportConfirm by rememberSaveable { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri != null) {
            viewModel.exportEnvToUri(uri)
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            pendingImportUri = uri
            showLocalImportConfirm = true
        }
    }

    if (showLocalImportConfirm && pendingImportUri != null) {
        AlertDialog(
            onDismissRequest = {
                showLocalImportConfirm = false
                pendingImportUri = null
            },
            title = { Text("覆盖导入本地配置") },
            text = { Text("会使用选中的文件覆盖当前 .env，建议先导出一份备份。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingImportUri?.let(viewModel::importEnvFromUri)
                        pendingImportUri = null
                        showLocalImportConfirm = false
                    },
                    enabled = viewModel.rootAvailable == true,
                ) {
                    Text("确认导入")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showLocalImportConfirm = false
                        pendingImportUri = null
                    },
                ) {
                    Text("取消")
                }
            },
        )
    }

    if (showWebDavImportConfirm) {
        AlertDialog(
            onDismissRequest = { showWebDavImportConfirm = false },
            title = { Text("从 WebDAV 恢复配置") },
            text = { Text("会下载远程文件并覆盖当前 .env。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        persistWebDavSettings(
                            viewModel = viewModel,
                            url = webDavUrlInput,
                            username = webDavUsernameInput,
                            password = webDavPasswordInput,
                            path = webDavPathInput,
                        )
                        viewModel.importEnvFromWebDav()
                        showWebDavImportConfirm = false
                    },
                    enabled = viewModel.rootAvailable == true,
                ) {
                    Text("确认恢复")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWebDavImportConfirm = false }) {
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
            title = "备份与恢复",
            subtitle = "远程和本地配置都收进同一页，避免操作路径分散。",
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
                title = "WebDAV",
                subtitle = "保存连接信息后，可以直接上传配置或从远端恢复。",
                palette = palette,
                trailing = {
                    SettingsTag(
                        text = if (webDavUrlInput.isBlank()) "未配置" else "已填写连接",
                        palette = palette,
                        tone = if (webDavUrlInput.isBlank()) palette.subtleText else palette.positive,
                    )
                },
            )
            OutlinedTextField(
                value = webDavUrlInput,
                onValueChange = { webDavUrlInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("WebDAV 地址") },
                singleLine = true,
            )
            OutlinedTextField(
                value = webDavUsernameInput,
                onValueChange = { webDavUsernameInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("用户名") },
                singleLine = true,
            )
            OutlinedTextField(
                value = webDavPasswordInput,
                onValueChange = { webDavPasswordInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("密码") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
            )
            OutlinedTextField(
                value = webDavPathInput,
                onValueChange = { webDavPathInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("远程路径 / URL") },
                singleLine = true,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilledTonalButton(
                    onClick = {
                        persistWebDavSettings(
                            viewModel = viewModel,
                            url = webDavUrlInput,
                            username = webDavUsernameInput,
                            password = webDavPasswordInput,
                            path = webDavPathInput,
                        )
                    },
                ) {
                    Text("保存设置")
                }
                OutlinedButton(
                    onClick = {
                        persistWebDavSettings(
                            viewModel = viewModel,
                            url = webDavUrlInput,
                            username = webDavUsernameInput,
                            password = webDavPasswordInput,
                            path = webDavPathInput,
                        )
                        viewModel.exportEnvToWebDav()
                    },
                ) {
                    Text("上传配置")
                }
                OutlinedButton(
                    onClick = {
                        persistWebDavSettings(
                            viewModel = viewModel,
                            url = webDavUrlInput,
                            username = webDavUsernameInput,
                            password = webDavPasswordInput,
                            path = webDavPathInput,
                        )
                        showWebDavImportConfirm = true
                    },
                ) {
                    Text("远端恢复")
                }
            }
        }

        SettingsPanel(palette = palette) {
            SettingsPanelHeader(
                title = "本地配置",
                subtitle = "全屏编辑 .env，导入导出都放在同一区域完成。",
                palette = palette,
            )
            SettingsValueRow(
                label = "路径",
                value = DanmuPaths.ENV_FILE,
                palette = palette,
            )
            SettingsDivider(palette)
            SettingsPlainRow(
                title = "打开 .env 编辑器",
                subtitle = "进入全屏编辑页，直接修改当前环境文件。",
                palette = palette,
                onClick = onOpenEnvEditor,
                trailing = {
                    TextButton(onClick = onOpenEnvEditor) {
                        Text("打开")
                    }
                },
            )
            SettingsDivider(palette)
            SettingsPlainRow(
                title = "导出到本地",
                subtitle = "生成一份当前配置的本地副本。",
                palette = palette,
                onClick = { exportLauncher.launch("danmu_api.env") },
                trailing = {
                    TextButton(onClick = { exportLauncher.launch("danmu_api.env") }) {
                        Text("导出")
                    }
                },
            )
            SettingsDivider(palette)
            SettingsPlainRow(
                title = "从本地导入",
                subtitle = "使用外部文件覆盖当前 .env。",
                palette = palette,
                onClick = { importLauncher.launch(arrayOf("text/plain", "*/*")) },
                trailing = {
                    TextButton(onClick = { importLauncher.launch(arrayOf("text/plain", "*/*")) }) {
                        Text("导入")
                    }
                },
            )
        }
    }
}

@Composable
fun SettingsAppearanceScreen(
    contentPadding: PaddingValues,
    viewModel: ManagerViewModel,
    onBack: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()

    SettingsScrollablePage(
        contentPadding = contentPadding,
        palette = palette,
    ) {
        SettingsImmersiveHeader(
            title = "外观与显示",
            subtitle = "只保留影响整体观感的开关，不混入其它偏好项。",
            palette = palette,
            leading = {
                SettingsHeaderIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    palette = palette,
                    onClick = onBack,
                )
            },
        )

        SettingsPanel(palette = palette) {
            SettingsPanelHeader(
                title = "主题模式",
                subtitle = "使用紧凑切换，不额外重复显示当前值。",
                palette = palette,
                trailing = {
                    SettingsTag(
                        text = themeModeLabel(themeMode),
                        palette = palette,
                        tone = palette.accent,
                    )
                },
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    0 to "跟随系统",
                    1 to "浅色",
                    2 to "深色",
                ).forEach { (mode, label) ->
                    FilterChip(
                        selected = themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        label = { Text(label) },
                    )
                }
            }
            SettingsDivider(palette)
            SettingsPlainRow(
                title = "动态配色",
                subtitle = "允许系统色板微调主色，但不改变页面结构。",
                palette = palette,
                trailing = {
                    Switch(
                        checked = dynamicColor,
                        onCheckedChange = viewModel::setDynamicColor,
                    )
                },
            )
        }
    }
}

@Composable
fun SettingsMaintenanceScreen(
    contentPadding: PaddingValues,
    viewModel: ManagerViewModel,
    onBack: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    val logCleanIntervalDays by viewModel.logCleanIntervalDays.collectAsStateWithLifecycle()
    val consoleLogLimit by viewModel.consoleLogLimit.collectAsStateWithLifecycle()

    SettingsScrollablePage(
        contentPadding = contentPadding,
        palette = palette,
    ) {
        SettingsImmersiveHeader(
            title = "日志与维护",
            subtitle = "只保留日志清理周期和记录页展示范围，避免重复说明。",
            palette = palette,
            leading = {
                SettingsHeaderIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    palette = palette,
                    onClick = onBack,
                )
            },
        )

        SettingsPanel(palette = palette) {
            SettingsPanelHeader(
                title = "自动清理",
                subtitle = "到期自动清空历史日志，避免长期堆积。",
                palette = palette,
                trailing = {
                    SettingsTag(
                        text = if (logCleanIntervalDays <= 0) "已关闭" else "$logCleanIntervalDays 天",
                        palette = palette,
                        tone = if (logCleanIntervalDays <= 0) palette.subtleText else palette.warning,
                    )
                },
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(0, 3, 7, 14, 30).forEach { days ->
                    FilterChip(
                        selected = logCleanIntervalDays == days,
                        onClick = { viewModel.setLogCleanIntervalDays(days) },
                        label = { Text(if (days == 0) "关闭" else "$days 天") },
                    )
                }
            }
        }

        SettingsPanel(palette = palette) {
            SettingsPanelHeader(
                title = "日志显示范围",
                subtitle = "控制记录页一次能看到多少行内容。",
                palette = palette,
                trailing = {
                    SettingsTag(
                        text = "$consoleLogLimit 行",
                        palette = palette,
                        tone = palette.accent,
                    )
                },
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(100, 300, 500, 1000).forEach { limit ->
                    FilterChip(
                        selected = consoleLogLimit == limit,
                        onClick = { viewModel.setConsoleLogLimit(limit) },
                        label = { Text("$limit 行") },
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsAdvancedScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenApiDebug: () -> Unit,
    onOpenServerEnv: () -> Unit,
) {
    val palette = rememberSettingsPalette()

    SettingsScrollablePage(
        contentPadding = contentPadding,
        palette = palette,
    ) {
        SettingsImmersiveHeader(
            title = "高级工具",
            subtitle = "低频但专业的能力放到深层入口，日常设置保持清晰。",
            palette = palette,
            leading = {
                SettingsHeaderIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    palette = palette,
                    onClick = onBack,
                )
            },
        )

        SettingsPanel(palette = palette) {
            SettingsPanelHeader(
                title = "工具入口",
                subtitle = "只保留真正需要单独页面的高级能力。",
                palette = palette,
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SettingsNavCard(
                    title = "API 调试",
                    subtitle = "构造请求、验证参数、查看响应结果。",
                    summary = "工具页",
                    icon = com.danmuapi.manager.app.navigation.AppDestination.ApiDebug.icon,
                    palette = palette,
                    onClick = onOpenApiDebug,
                )
                SettingsNavCard(
                    title = "服务端环境",
                    subtitle = "查看服务端配置、启用管理员模式并维护环境变量。",
                    summary = "工具页",
                    icon = com.danmuapi.manager.app.navigation.AppDestination.ServerEnv.icon,
                    palette = palette,
                    onClick = onOpenServerEnv,
                )
            }
        }
    }
}

@Composable
fun EnvEditorScreen(
    contentPadding: PaddingValues,
    viewModel: ManagerViewModel,
    onBack: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    var envText by rememberSaveable { mutableStateOf("") }
    var lastSavedText by rememberSaveable { mutableStateOf("") }
    var envLoading by rememberSaveable { mutableStateOf(true) }
    val dirty = envText != lastSavedText

    fun reload() {
        envLoading = true
        viewModel.loadEnvFile { text ->
            envText = text
            lastSavedText = text
            envLoading = false
        }
    }

    LaunchedEffect(Unit) {
        reload()
    }

    SettingsBackdrop(palette = palette) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = contentPadding.calculateBottomPadding() + 18.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true)
                    .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.statusBars)
                    .padding(horizontal = 20.dp, vertical = 18.dp)
                    .widthIn(max = 860.dp)
                    .align(Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SettingsImmersiveHeader(
                    title = ".env 编辑",
                    subtitle = "长文本改成全屏编辑，不再塞进底部弹窗里。",
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

                SettingsPanel(
                    palette = palette,
                    modifier = Modifier.weight(1f, fill = true),
                ) {
                    SettingsPanelHeader(
                        title = "服务端环境文件",
                        subtitle = "保存后会直接写回当前模块配置。",
                        palette = palette,
                        trailing = {
                            SettingsTag(
                                text = if (dirty) "已修改" else "未修改",
                                palette = palette,
                                tone = if (dirty) palette.warning else palette.subtleText,
                            )
                        },
                    )
                    SettingsValueRow(
                        label = "路径",
                        value = DanmuPaths.ENV_FILE,
                        palette = palette,
                    )
                    OutlinedTextField(
                        value = envText,
                        onValueChange = { envText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = true)
                            .heightIn(min = 320.dp),
                        enabled = !envLoading && viewModel.rootAvailable == true,
                        minLines = 18,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = DanmuMonoFamily),
                        label = { Text(if (envLoading) "读取中…" else ".env 内容") },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = when {
                                envLoading -> "正在读取配置…"
                                viewModel.rootAvailable != true -> "Root 不可用，当前无法写入。"
                                dirty -> "你有未保存的修改。"
                                else -> "内容已和当前文件保持一致。"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.subtleText,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = { reload() },
                                enabled = !envLoading,
                            ) {
                                Text("重新读取")
                            }
                            FilledTonalButton(
                                onClick = {
                                    viewModel.saveEnvFile(envText)
                                    lastSavedText = envText
                                },
                                enabled = !envLoading && dirty && viewModel.rootAvailable == true,
                            ) {
                                Text("保存")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun buildModuleUpdateSummary(info: ModuleUpdateInfo?): String {
    return when {
        info == null -> "未检查"
        info.hasUpdate -> "可更新到 ${info.latestRelease?.tagName ?: "--"}"
        else -> "已是最新"
    }
}

private fun buildCoreUpdateSummary(updateInfo: Map<String, CoreUpdateInfo>): String {
    if (updateInfo.isEmpty()) return "未检查"
    val updateCount = updateInfo.values.count { it.updateAvailable }
    val unknownCount = updateInfo.values.count { it.state == CoreUpdateState.Unknown }
    return when {
        updateCount > 0 && unknownCount > 0 -> "$updateCount 个可更新，$unknownCount 个未确认"
        updateCount > 0 -> "$updateCount 个可更新"
        unknownCount > 0 -> "$unknownCount 个未确认"
        else -> "全部已是最新"
    }
}

private fun persistWebDavSettings(
    viewModel: ManagerViewModel,
    url: String,
    username: String,
    password: String,
    path: String,
) {
    viewModel.setWebDavSettings(
        url = url,
        username = username,
        password = password,
        path = path,
    )
}

private fun themeModeLabel(themeMode: Int): String {
    return when (themeMode) {
        1 -> "浅色"
        2 -> "深色"
        else -> "跟随系统"
    }
}

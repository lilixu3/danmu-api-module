@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.danmuapi.manager.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
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
import com.danmuapi.manager.BuildConfig
import com.danmuapi.manager.app.state.ManagerViewModel
import com.danmuapi.manager.core.designsystem.component.EmptyHint
import com.danmuapi.manager.core.designsystem.component.InfoRow
import com.danmuapi.manager.core.designsystem.component.PageHeader
import com.danmuapi.manager.core.designsystem.component.SectionCard
import com.danmuapi.manager.core.designsystem.component.StatusTag
import com.danmuapi.manager.core.designsystem.component.StatusTone
import com.danmuapi.manager.core.designsystem.theme.DanmuMonoFamily
import com.danmuapi.manager.core.root.DanmuPaths

private enum class SettingsCategory(
    val title: String,
    val subtitle: String,
) {
    Access(
        title = "访问与更新",
        subtitle = "GitHub Token、模块更新、核心更新入口。",
    ),
    Backup(
        title = "备份与恢复",
        subtitle = "WebDAV、本地导入导出与 .env 编辑。",
    ),
    Appearance(
        title = "显示与主题",
        subtitle = "主题模式和动态配色。",
    ),
    Maintenance(
        title = "日志与维护",
        subtitle = "自动清理周期和日志显示上限。",
    ),
    Advanced(
        title = "高级设置",
        subtitle = "状态信息与危险操作提示。",
    ),
}

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    viewModel: ManagerViewModel,
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val logCleanIntervalDays by viewModel.logCleanIntervalDays.collectAsStateWithLifecycle()
    val githubToken by viewModel.githubToken.collectAsStateWithLifecycle()
    val webDavUrl by viewModel.webDavUrl.collectAsStateWithLifecycle()
    val webDavUsername by viewModel.webDavUsername.collectAsStateWithLifecycle()
    val webDavPassword by viewModel.webDavPassword.collectAsStateWithLifecycle()
    val webDavPath by viewModel.webDavPath.collectAsStateWithLifecycle()
    val consoleLogLimit by viewModel.consoleLogLimit.collectAsStateWithLifecycle()

    var githubTokenInput by remember(githubToken) { mutableStateOf(githubToken) }
    var webDavUrlInput by remember(webDavUrl) { mutableStateOf(webDavUrl) }
    var webDavUsernameInput by remember(webDavUsername) { mutableStateOf(webDavUsername) }
    var webDavPasswordInput by remember(webDavPassword) { mutableStateOf(webDavPassword) }
    var webDavPathInput by remember(webDavPath) { mutableStateOf(webDavPath.ifBlank { "danmuapi/danmu_api.env" }) }

    var selectedCategory by rememberSaveable { mutableStateOf(SettingsCategory.Access) }
    var showEnvEditor by rememberSaveable { mutableStateOf(false) }
    var envText by rememberSaveable { mutableStateOf("") }
    var envLoading by rememberSaveable { mutableStateOf(false) }
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

    LaunchedEffect(showEnvEditor) {
        if (showEnvEditor) {
            envLoading = true
            viewModel.loadEnvFile { text ->
                envText = text
                envLoading = false
            }
        }
    }

    if (showEnvEditor) {
        ModalBottomSheet(onDismissRequest = { showEnvEditor = false }) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "编辑 .env 配置",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = DanmuPaths.ENV_FILE,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = if (envLoading) "加载中…" else envText,
                    onValueChange = { envText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 280.dp, max = 560.dp),
                    label = { Text(".env 内容") },
                    enabled = !envLoading && viewModel.rootAvailable == true,
                    minLines = 16,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = DanmuMonoFamily),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { showEnvEditor = false }) {
                        Text("关闭")
                    }
                    TextButton(
                        onClick = {
                            viewModel.saveEnvFile(envText)
                            showEnvEditor = false
                        },
                        enabled = !envLoading && viewModel.rootAvailable == true,
                    ) {
                        Text("保存")
                    }
                }
            }
        }
    }

    if (showLocalImportConfirm && pendingImportUri != null) {
        AlertDialog(
            onDismissRequest = {
                showLocalImportConfirm = false
                pendingImportUri = null
            },
            title = { Text("覆盖导入本地配置") },
            text = { Text("将使用选中的文件覆盖当前 ${DanmuPaths.ENV_FILE}，建议先导出一份备份。") },
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
            text = { Text("将从 WebDAV 下载文件并覆盖当前 .env 配置。") },
            confirmButton = {
                TextButton(
                    onClick = {
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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        val useTwoPane = maxWidth >= 920.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PageHeader(
                title = "设置",
                subtitle = "不再堆成长表单。先选分类，再进入对应配置区。",
                modifier = Modifier.padding(top = 12.dp),
            )

            SectionCard(
                title = "应用状态",
                subtitle = "把应用与模块状态集中放在顶部，避免散落在各分类里。",
            ) {
                InfoRow(
                    label = "Root",
                    value = when (viewModel.rootAvailable) {
                        true -> "已就绪"
                        false -> "不可用"
                        null -> "检测中"
                    },
                )
                InfoRow(label = "App Version", value = BuildConfig.VERSION_NAME)
                InfoRow(label = "Version Code", value = BuildConfig.VERSION_CODE.toString())
                InfoRow(label = "模块版本", value = viewModel.status?.module?.version ?: "--")
                InfoRow(label = "配置文件", value = DanmuPaths.ENV_FILE)
            }

            if (useTwoPane) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    SettingsCategoryMenu(
                        selectedCategory = selectedCategory,
                        onSelect = { selectedCategory = it },
                        modifier = Modifier.weight(0.36f),
                    )
                    SettingsCategoryContent(
                        category = selectedCategory,
                        viewModel = viewModel,
                        themeMode = themeMode,
                        dynamicColor = dynamicColor,
                        logCleanIntervalDays = logCleanIntervalDays,
                        githubTokenInput = githubTokenInput,
                        onGithubTokenInputChange = { githubTokenInput = it },
                        consoleLogLimit = consoleLogLimit,
                        webDavUrlInput = webDavUrlInput,
                        onWebDavUrlInputChange = { webDavUrlInput = it },
                        webDavUsernameInput = webDavUsernameInput,
                        onWebDavUsernameInputChange = { webDavUsernameInput = it },
                        webDavPasswordInput = webDavPasswordInput,
                        onWebDavPasswordInputChange = { webDavPasswordInput = it },
                        webDavPathInput = webDavPathInput,
                        onWebDavPathInputChange = { webDavPathInput = it },
                        onShowEnvEditor = { showEnvEditor = true },
                        onExportLocal = { exportLauncher.launch("danmu_api.env") },
                        onImportLocal = { importLauncher.launch(arrayOf("text/plain", "*/*")) },
                        onShowWebDavImport = { showWebDavImportConfirm = true },
                        modifier = Modifier.weight(0.64f),
                    )
                }
            } else {
                SettingsCategoryMenu(
                    selectedCategory = selectedCategory,
                    onSelect = { selectedCategory = it },
                )
                SettingsCategoryContent(
                    category = selectedCategory,
                    viewModel = viewModel,
                    themeMode = themeMode,
                    dynamicColor = dynamicColor,
                    logCleanIntervalDays = logCleanIntervalDays,
                    githubTokenInput = githubTokenInput,
                    onGithubTokenInputChange = { githubTokenInput = it },
                    consoleLogLimit = consoleLogLimit,
                    webDavUrlInput = webDavUrlInput,
                    onWebDavUrlInputChange = { webDavUrlInput = it },
                    webDavUsernameInput = webDavUsernameInput,
                    onWebDavUsernameInputChange = { webDavUsernameInput = it },
                    webDavPasswordInput = webDavPasswordInput,
                    onWebDavPasswordInputChange = { webDavPasswordInput = it },
                    webDavPathInput = webDavPathInput,
                    onWebDavPathInputChange = { webDavPathInput = it },
                    onShowEnvEditor = { showEnvEditor = true },
                    onExportLocal = { exportLauncher.launch("danmu_api.env") },
                    onImportLocal = { importLauncher.launch(arrayOf("text/plain", "*/*")) },
                    onShowWebDavImport = { showWebDavImportConfirm = true },
                )
            }
        }
    }
}

@Composable
private fun SettingsCategoryMenu(
    selectedCategory: SettingsCategory,
    onSelect: (SettingsCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(
        title = "设置分类",
        subtitle = "手机端一次只看一个分类，大屏再扩展成双栏。",
        modifier = modifier,
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SettingsCategory.entries.forEach { category ->
                AssistChip(
                    onClick = { onSelect(category) },
                    label = { Text(category.title) },
                    leadingIcon = if (selectedCategory == category) {
                        { Icon(Icons.Filled.Info, contentDescription = null) }
                    } else {
                        null
                    },
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsCategory.entries.forEach { category ->
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(
                        width = if (selectedCategory == category) 1.5.dp else 1.dp,
                        color = if (selectedCategory == category) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        },
                    ),
                    onClick = { onSelect(category) },
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(text = category.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = category.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCategoryContent(
    category: SettingsCategory,
    viewModel: ManagerViewModel,
    themeMode: Int,
    dynamicColor: Boolean,
    logCleanIntervalDays: Int,
    githubTokenInput: String,
    onGithubTokenInputChange: (String) -> Unit,
    consoleLogLimit: Int,
    webDavUrlInput: String,
    onWebDavUrlInputChange: (String) -> Unit,
    webDavUsernameInput: String,
    onWebDavUsernameInputChange: (String) -> Unit,
    webDavPasswordInput: String,
    onWebDavPasswordInputChange: (String) -> Unit,
    webDavPathInput: String,
    onWebDavPathInputChange: (String) -> Unit,
    onShowEnvEditor: () -> Unit,
    onExportLocal: () -> Unit,
    onImportLocal: () -> Unit,
    onShowWebDavImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (category) {
        SettingsCategory.Access -> {
            SectionCard(
                title = category.title,
                subtitle = category.subtitle,
                modifier = modifier,
            ) {
                OutlinedTextField(
                    value = githubTokenInput,
                    onValueChange = onGithubTokenInputChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("GitHub Token") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ElevatedButton(onClick = { viewModel.setGithubToken(githubTokenInput) }) {
                        Icon(Icons.Filled.Save, contentDescription = null)
                        Text("保存 Token")
                    }
                    FilledTonalButton(onClick = viewModel::checkModuleUpdate) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Text("检查模块更新")
                    }
                    OutlinedButton(onClick = viewModel::checkUpdates) {
                        Text("检查核心更新")
                    }
                }
                viewModel.moduleUpdateInfo?.let { updateInfo ->
                    StatusTag(
                        text = if (updateInfo.hasUpdate) {
                            "模块可更新至 ${updateInfo.latestRelease?.tagName ?: "--"}"
                        } else {
                            "模块已是最新"
                        },
                        tone = if (updateInfo.hasUpdate) StatusTone.Warning else StatusTone.Positive,
                    )
                } ?: EmptyHint(
                    title = "尚未检查模块更新",
                    detail = "保存 Token 后可加快 GitHub 相关请求，必要时再执行检查。",
                )
            }
        }

        SettingsCategory.Backup -> {
            SectionCard(
                title = category.title,
                subtitle = category.subtitle,
                modifier = modifier,
            ) {
                OutlinedTextField(
                    value = webDavUrlInput,
                    onValueChange = onWebDavUrlInputChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("WebDAV 地址") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = webDavUsernameInput,
                    onValueChange = onWebDavUsernameInputChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("用户名") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = webDavPasswordInput,
                    onValueChange = onWebDavPasswordInputChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("密码") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = webDavPathInput,
                    onValueChange = onWebDavPathInputChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("远程路径 / URL") },
                    singleLine = true,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ElevatedButton(
                        onClick = {
                            viewModel.setWebDavSettings(
                                url = webDavUrlInput,
                                username = webDavUsernameInput,
                                password = webDavPasswordInput,
                                path = webDavPathInput,
                            )
                        },
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null)
                        Text("保存 WebDAV")
                    }
                    FilledTonalButton(
                        onClick = {
                            viewModel.setWebDavSettings(
                                url = webDavUrlInput,
                                username = webDavUsernameInput,
                                password = webDavPasswordInput,
                                path = webDavPathInput,
                            )
                            viewModel.exportEnvToWebDav()
                        },
                    ) {
                        Icon(Icons.Filled.CloudUpload, contentDescription = null)
                        Text("上传配置")
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.setWebDavSettings(
                                url = webDavUrlInput,
                                username = webDavUsernameInput,
                                password = webDavPasswordInput,
                                path = webDavPathInput,
                            )
                            onShowWebDavImport()
                        },
                    ) {
                        Icon(Icons.Filled.CloudDownload, contentDescription = null)
                        Text("从 WebDAV 恢复")
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ElevatedButton(onClick = onShowEnvEditor) {
                        Icon(Icons.Filled.Edit, contentDescription = null)
                        Text("编辑 .env")
                    }
                    FilledTonalButton(onClick = onExportLocal) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null)
                        Text("导出到本地")
                    }
                    OutlinedButton(onClick = onImportLocal) {
                        Icon(Icons.Filled.CloudDownload, contentDescription = null)
                        Text("从本地导入")
                    }
                }
            }
        }

        SettingsCategory.Appearance -> {
            SectionCard(
                title = category.title,
                subtitle = category.subtitle,
                modifier = modifier,
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        0 to "跟随系统",
                        1 to "浅色",
                        2 to "深色",
                    ).forEach { (mode, label) ->
                        AssistChip(
                            onClick = { viewModel.setThemeMode(mode) },
                            label = { Text(label) },
                            leadingIcon = if (themeMode == mode) {
                                { Icon(Icons.Filled.Info, contentDescription = null) }
                            } else {
                                null
                            },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("动态配色")
                    Switch(
                        checked = dynamicColor,
                        onCheckedChange = viewModel::setDynamicColor,
                    )
                }
                Text(
                    text = "V2 改成低认知负担的运维台风格，动态配色只作为主色辅助，不再主导整个界面。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SettingsCategory.Maintenance -> {
            SectionCard(
                title = category.title,
                subtitle = category.subtitle,
                modifier = modifier,
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(0, 3, 7, 14, 30).forEach { days ->
                        AssistChip(
                            onClick = { viewModel.setLogCleanIntervalDays(days) },
                            label = { Text(if (days == 0) "关闭自动清理" else "$days 天") },
                            leadingIcon = if (logCleanIntervalDays == days) {
                                { Icon(Icons.Filled.Refresh, contentDescription = null) }
                            } else {
                                null
                            },
                        )
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
                            leadingIcon = if (consoleLogLimit == limit) {
                                { Icon(Icons.Filled.Info, contentDescription = null) }
                            } else {
                                null
                            },
                        )
                    }
                }
                InfoRow(label = "当前自动清理", value = if (logCleanIntervalDays == 0) "关闭" else "$logCleanIntervalDays 天")
                InfoRow(label = "当前控制台行数", value = consoleLogLimit.toString())
            }
        }

        SettingsCategory.Advanced -> {
            SectionCard(
                title = category.title,
                subtitle = category.subtitle,
                modifier = modifier,
            ) {
                StatusTag(
                    text = "任何导入和覆盖操作都建议先导出备份",
                    tone = StatusTone.Warning,
                )
                if (viewModel.rootAvailable != true) {
                    EmptyHint(
                        title = "Root 当前不可用",
                        detail = "没有 Root 时，配置写入、导入恢复和核心管理都可能失败。",
                    )
                } else {
                    Text(
                        text = "当前 Root 可用，但仍建议在执行覆盖恢复前先停止服务并留存一份本地备份。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                InfoRow(label = "模块版本", value = viewModel.status?.module?.version ?: "--")
                InfoRow(label = "当前核心", value = viewModel.status?.activeCore?.repoDisplayName ?: "未激活")
                InfoRow(label = "配置路径", value = DanmuPaths.ENV_FILE)
            }
        }
    }
}

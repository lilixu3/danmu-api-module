package com.danmuapi.manager.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import com.danmuapi.manager.ui.components.ManagerCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.danmuapi.manager.root.DanmuPaths

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    paddingValues: PaddingValues,
    rootAvailable: Boolean?,
    logAutoCleanDays: Int,
    githubToken: String,
    webDavUrl: String,
    webDavUsername: String,
    webDavPassword: String,
    webDavPath: String,
    themeMode: Int,
    dynamicColor: Boolean,
    onSetThemeMode: (Int) -> Unit,
    onSetDynamicColor: (Boolean) -> Unit,
    onOpenAbout: () -> Unit,
    onSetLogAutoCleanDays: (Int) -> Unit,
    onSetGithubToken: (String) -> Unit,
    onSetWebDavSettings: (url: String, username: String, password: String, path: String) -> Unit,
    onLoadEnv: (onResult: (String) -> Unit) -> Unit,
    onSaveEnv: (String) -> Unit,
    onExportEnvToUri: (Uri) -> Unit,
    onImportEnvFromUri: (Uri) -> Unit,
    onExportEnvToWebDav: () -> Unit,
    onImportEnvFromWebDav: () -> Unit,
) {
    var tokenInput by remember(githubToken) { mutableStateOf(githubToken) }

    // WebDAV inputs
    var davUrlInput by remember(webDavUrl) { mutableStateOf(webDavUrl) }
    var davUserInput by remember(webDavUsername) { mutableStateOf(webDavUsername) }
    var davPassInput by remember(webDavPassword) { mutableStateOf(webDavPassword) }
    var davPathInput by remember(webDavPath) { mutableStateOf(webDavPath.ifBlank { "danmuapi/danmu_api.env" }) }

    // Config editor
    var showEnvEditor by remember { mutableStateOf(false) }
    var envText by remember { mutableStateOf("") }
    var envLoading by remember { mutableStateOf(false) }

    // Local import confirmation
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var showImportConfirm by remember { mutableStateOf(false) }

    // WebDAV import confirmation
    var showWebDavImportConfirm by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
        onResult = { uri ->
            if (uri != null) onExportEnvToUri(uri)
        },
    )

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                pendingImportUri = uri
                showImportConfirm = true
            }
        },
    )

    if (showImportConfirm && pendingImportUri != null) {
        AlertDialog(
            onDismissRequest = {
                showImportConfirm = false
                pendingImportUri = null
            },
            title = { Text("导入配置？") },
            text = {
                Text(
                    "将覆盖当前配置文件：\n${DanmuPaths.ENV_FILE}\n\n建议先导出一份备份，避免导入错误导致服务无法启动。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val uri = pendingImportUri
                        showImportConfirm = false
                        pendingImportUri = null
                        if (uri != null) onImportEnvFromUri(uri)
                    },
                    enabled = rootAvailable == true,
                ) { Text("覆盖导入") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showImportConfirm = false
                        pendingImportUri = null
                    },
                ) { Text("取消") }
            },
        )
    }

    if (showWebDavImportConfirm) {
        AlertDialog(
            onDismissRequest = { showWebDavImportConfirm = false },
            title = { Text("从 WebDAV 恢复？") },
            text = {
                Text(
                    "将从 WebDAV 下载并覆盖当前配置文件：\n${DanmuPaths.ENV_FILE}\n\n请确认 WebDAV 地址/路径填写正确。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showWebDavImportConfirm = false
                        onImportEnvFromWebDav()
                    },
                    enabled = rootAvailable == true,
                ) { Text("确认恢复") }
            },
            dismissButton = { TextButton(onClick = { showWebDavImportConfirm = false }) { Text("取消") } },
        )
    }

    LaunchedEffect(showEnvEditor) {
        if (showEnvEditor) {
            envLoading = true
            onLoadEnv { text ->
                envText = text
                envLoading = false
            }
        }
    }

    if (showEnvEditor) {
        ModalBottomSheet(
            onDismissRequest = { showEnvEditor = false },
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = "编辑配置文件", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "路径：${DanmuPaths.ENV_FILE}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "保存后建议在“仪表盘”里重启服务使配置生效。",
                    style = MaterialTheme.typography.bodySmall,
                )

                OutlinedTextField(
                    value = if (envLoading) "加载中…" else envText,
                    onValueChange = { envText = it },
                    enabled = !envLoading && rootAvailable == true,
                    label = { Text(".env 内容") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp, max = 520.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    maxLines = 32,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { showEnvEditor = false }) { Text("关闭") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            onSaveEnv(envText)
                            showEnvEditor = false
                        },
                        enabled = !envLoading && rootAvailable == true,
                    ) { Text("保存") }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .padding(paddingValues)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "设置", style = MaterialTheme.typography.titleLarge)

        ManagerCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Security, contentDescription = null)
                    Text(text = "Root 权限", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    text = when (rootAvailable) {
                        true -> "已授予 / 可用"
                        false -> "不可用（请确认 Magisk 授权）"
                        null -> "检测中…"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "模块数据目录：${DanmuPaths.PERSIST_DIR}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        // Appearance
        ManagerCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.BatterySaver, contentDescription = null)
                    Text(text = "外观", style = MaterialTheme.typography.titleMedium)
                }

                // Theme mode dropdown
                var themeMenuExpanded by remember { mutableStateOf(false) }
                val themeLabel = when (themeMode) {
                    1 -> "浅色"
                    2 -> "深色"
                    else -> "跟随系统"
                }

                ExposedDropdownMenuBox(
                    expanded = themeMenuExpanded,
                    onExpandedChange = { themeMenuExpanded = !themeMenuExpanded },
                ) {
                    OutlinedTextField(
                        value = themeLabel,
                        onValueChange = { },
                        readOnly = true,
                        label = { Text("主题") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = themeMenuExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = themeMenuExpanded,
                        onDismissRequest = { themeMenuExpanded = false },
                    ) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("跟随系统") },
                            onClick = {
                                themeMenuExpanded = false
                                onSetThemeMode(0)
                            },
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("浅色") },
                            onClick = {
                                themeMenuExpanded = false
                                onSetThemeMode(1)
                            },
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("深色") },
                            onClick = {
                                themeMenuExpanded = false
                                onSetThemeMode(2)
                            },
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("动态取色", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Android 12+ 会根据壁纸生成配色",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    androidx.compose.material3.Switch(
                        checked = dynamicColor,
                        onCheckedChange = { onSetDynamicColor(it) },
                    )
                }
            }
        }

        // Config file
        ManagerCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Description, contentDescription = null)
                    Text(text = "配置文件", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    text = "编辑/导入/导出 ${DanmuPaths.ENV_FILE}（需要 Root）。",
                    style = MaterialTheme.typography.bodyMedium,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = { showEnvEditor = true },
                        enabled = rootAvailable == true,
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("编辑")
                    }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = { exportLauncher.launch("danmu_api.env") },
                        enabled = rootAvailable == true,
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("导出")
                    }
                }

                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { importLauncher.launch(arrayOf("text/*", "*/*")) },
                    enabled = rootAvailable == true,
                ) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("导入（覆盖当前配置）")
                }
            }
        }

        // Log auto-clean
        ManagerCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.BatterySaver, contentDescription = null)
                    Text(text = "日志历史清理", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    text = "自动删除轮转日志（如 server.log.1 / server.log.2）中超过指定天数的旧文件，不会自动清空当前 *.log。",
                    style = MaterialTheme.typography.bodyMedium,
                )

                val options = listOf(
                    0 to "关闭",
                    1 to "保留 1 天",
                    3 to "保留 3 天",
                    7 to "保留 7 天",
                    30 to "保留 30 天",
                )

                var expanded by remember { mutableStateOf(false) }
                val selectedLabel = options.firstOrNull { it.first == logAutoCleanDays }?.second ?: "关闭"

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                ) {
                    OutlinedTextField(
                        value = selectedLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("保留天数") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor(),
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        options.forEach { (days, label) ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    expanded = false
                                    onSetLogAutoCleanDays(days)
                                },
                            )
                        }
                    }
                }
            }
        }

        // GitHub token
        ManagerCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Key, contentDescription = null)
                    Text(text = "GitHub Token（可选）", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    text = "用于“检查更新”提高 API 限额（不填也能用）。建议创建最小权限的 Fine-grained token，仅勾选 public repo 读取。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = { tokenInput = it },
                    singleLine = true,
                    label = { Text("Token") },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { tokenInput = ""; onSetGithubToken("") }) { Text("清空") }
                    TextButton(onClick = { onSetGithubToken(tokenInput.trim()) }) { Text("保存") }
                }
            }
        }

        // WebDAV
        ManagerCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.CloudUpload, contentDescription = null)
                    Text(text = "WebDAV 备份（可选）", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    text = "可将 .env 上传到 WebDAV 做备份，也可从 WebDAV 恢复。建议先在本地导出一份备份再恢复。",
                    style = MaterialTheme.typography.bodyMedium,
                )

                OutlinedTextField(
                    value = davUrlInput,
                    onValueChange = { davUrlInput = it },
                    singleLine = true,
                    label = { Text("WebDAV 目录 URL") },
                    placeholder = { Text("例如：https://example.com/dav/") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = davPathInput,
                    onValueChange = { davPathInput = it },
                    singleLine = true,
                    label = { Text("远程路径（目录或文件）") },
                    placeholder = { Text("例如：danmuapi/ 或 backups/danmu_api.env（也可粘贴完整文件URL）") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "提示：如果只填目录（如 danmuapi），将自动使用 danmu_api.env 作为文件名。",
                    style = MaterialTheme.typography.bodySmall,
                )

                OutlinedTextField(
                    value = davUserInput,
                    onValueChange = { davUserInput = it },
                    singleLine = true,
                    label = { Text("用户名") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = davPassInput,
                    onValueChange = { davPassInput = it },
                    singleLine = true,
                    label = { Text("密码") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = {
                            onSetWebDavSettings(
                                davUrlInput.trim(),
                                davUserInput,
                                davPassInput,
                                davPathInput.trim(),
                            )
                        },
                    ) { Text("保存 WebDAV 设置") }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = onExportEnvToWebDav,
                        enabled = rootAvailable == true,
                    ) {
                        Icon(Icons.Filled.CloudUpload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("上传备份")
                    }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = { showWebDavImportConfirm = true },
                        enabled = rootAvailable == true,
                    ) {
                        Icon(Icons.Filled.CloudDownload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("恢复备份")
                    }
                }

                Text(
                    text = "提示：WebDAV 密码将保存在应用私有目录（DataStore）中，属于明文存储。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        // About (move to the bottom)
        ManagerCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Info, contentDescription = null)
                    Text(text = "关于", style = MaterialTheme.typography.titleMedium)
                }
                OutlinedButton(
                    onClick = onOpenAbout,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("查看版本与说明")
                }
            }
        }
    }
}

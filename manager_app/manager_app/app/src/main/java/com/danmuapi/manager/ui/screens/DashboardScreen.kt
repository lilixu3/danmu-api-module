package com.danmuapi.manager.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import com.danmuapi.manager.ui.components.ManagerCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.danmuapi.manager.data.CoreUpdateInfo
import com.danmuapi.manager.data.model.CoreListResponse
import com.danmuapi.manager.data.model.ModuleUpdateInfo
import com.danmuapi.manager.data.model.ReleaseAsset
import com.danmuapi.manager.data.model.StatusResponse
import com.danmuapi.manager.util.rememberLanIpv4Addresses

@Composable
fun DashboardScreen(
    paddingValues: PaddingValues,
    rootAvailable: Boolean?,
    status: StatusResponse?,
    apiToken: String,
    apiPort: Int,
    apiHost: String,
    cores: CoreListResponse?,
    activeUpdate: CoreUpdateInfo?,
    moduleUpdateInfo: ModuleUpdateInfo?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit,
    onAutostartChange: (Boolean) -> Unit,
    onActivateCore: (String) -> Unit,
    onCheckActiveCoreUpdate: () -> Unit,
    onCheckModuleUpdate: () -> Unit,
    onDownloadModuleZip: (ReleaseAsset, (Int) -> Unit, (String?) -> Unit) -> Unit,
    onInstallModuleZip: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.size(8.dp))
        
        ServiceStatusCard(
            status = status,
            rootAvailable = rootAvailable,
            onStart = onStart,
            onStop = onStop,
            onRestart = onRestart,
            moduleUpdateInfo = moduleUpdateInfo,
            onCheckModuleUpdate = onCheckModuleUpdate,
            onDownloadModuleZip = onDownloadModuleZip,
            onInstallModuleZip = onInstallModuleZip,
        )

        AccessInfoCard(
            apiToken = apiToken,
            apiPort = apiPort,
            apiHost = apiHost,
            serviceRunning = status?.service?.running == true,
        )

        CurrentCoreCard(
            status = status,
            cores = cores,
            activeUpdate = activeUpdate,
            onActivateCore = onActivateCore,
            onCheckActiveUpdate = onCheckActiveCoreUpdate,
        )

        AutostartCard(
            status = status,
            onAutostartChange = onAutostartChange,
        )

        Spacer(Modifier.size(8.dp))
    }
}

@Composable
private fun ServiceStatusCard(
    status: StatusResponse?,
    rootAvailable: Boolean?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit,
    moduleUpdateInfo: ModuleUpdateInfo?,
    onCheckModuleUpdate: () -> Unit,
    onDownloadModuleZip: (ReleaseAsset, (Int) -> Unit, (String?) -> Unit) -> Unit,
    onInstallModuleZip: (String) -> Unit,
) {
    val running = status?.service?.running == true
    val pid = status?.service?.pid
    val moduleEnabled = status?.module?.enabled
    val version = status?.module?.version ?: "-"

    val ctx = LocalContext.current
    var showUpdateDialog by remember { mutableStateOf(false) }
    var selectedAssetName by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableStateOf(0) }
    var downloadedPath by remember { mutableStateOf<String?>(null) }
    var downloading by remember { mutableStateOf(false) }

    if (showUpdateDialog) {
        ModuleUpdateDialog(
            info = moduleUpdateInfo,
            currentVersion = version,
            selectedAssetName = selectedAssetName,
            downloadProgress = downloadProgress,
            downloading = downloading,
            downloadedPath = downloadedPath,
            onDismiss = {
                showUpdateDialog = false
                downloading = false
                downloadProgress = 0
                downloadedPath = null
            },
            onRefresh = {
                // Reset stale download state when re-checking.
                downloading = false
                downloadProgress = 0
                downloadedPath = null
                onCheckModuleUpdate()
            },
            onSelectAsset = { selectedAssetName = it },
            onOpenReleasePage = {
                openUrl(ctx, "https://github.com/lilixu3/danmu-api-module/releases")
            },
            onDownload = { asset ->
                if (downloading) return@ModuleUpdateDialog
                downloading = true
                downloadProgress = 0
                downloadedPath = null
                // NOTE: onDownloadModuleZip is a function type; Kotlin does not allow named arguments here.
                onDownloadModuleZip(
                    asset,
                    { p -> downloadProgress = p.coerceIn(0, 100) },
                    { path ->
                        downloading = false
                        downloadedPath = path
                        if (path == null) {
                            Toast.makeText(ctx, "下载失败", Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            },
            onInstall = {
                val path = downloadedPath
                if (path.isNullOrBlank()) {
                    Toast.makeText(ctx, "请先下载安装包", Toast.LENGTH_SHORT).show()
                } else {
                    showUpdateDialog = false
                    onInstallModuleZip(path)
                }
            },
        )
    }

    ManagerCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "服务状态",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(8.dp))
                        // A subtle update-check button next to the title.
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            IconButton(
                                modifier = Modifier.size(34.dp),
                                onClick = {
                                    showUpdateDialog = true
                                    onCheckModuleUpdate()
                                },
                            ) {
                                Icon(
                                    Icons.Filled.SystemUpdate,
                                    contentDescription = "检查模块更新",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                    Text(
                        text = "模块 v$version",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (running) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (running) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    }
                                ),
                        )
                        Text(
                            text = if (running) "运行中" else "已停止",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (running) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusChip(
                    modifier = Modifier.weight(1f),
                    label = "Root",
                    ok = rootAvailable,
                )
                StatusChip(
                    modifier = Modifier.weight(1f),
                    label = "模块",
                    ok = moduleEnabled,
                )
            }

            if (running && !pid.isNullOrBlank()) {
                Text(
                    text = "进程 ID: $pid",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilledTonalButton(
                    modifier = Modifier.weight(1f),
                    onClick = if (running) onStop else onStart,
                ) {
                    Icon(
                        Icons.Filled.PowerSettingsNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (running) "停止" else "启动")
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onRestart,
                    enabled = running,
                ) {
                    Icon(
                        Icons.Filled.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("重启")
                }
            }
        }
    }
}

@Composable
private fun StatusChip(
    modifier: Modifier = Modifier,
    label: String,
    ok: Boolean?,
) {
    val statusText = when (ok) {
        null -> "检测中"
        true -> "正常"
        false -> "异常"
    }
    
    val containerColor = when (ok) {
        true -> MaterialTheme.colorScheme.primaryContainer
        false -> MaterialTheme.colorScheme.errorContainer
        null -> MaterialTheme.colorScheme.surfaceVariant
    }
    
    val contentColor = when (ok) {
        true -> MaterialTheme.colorScheme.onPrimaryContainer
        false -> MaterialTheme.colorScheme.onErrorContainer
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = when (ok) {
                    true -> Icons.Filled.CheckCircle
                    false -> Icons.Filled.Error
                    null -> Icons.Filled.Info
                },
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = contentColor,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "$label·$statusText",
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ModuleUpdateDialog(
    info: ModuleUpdateInfo?,
    currentVersion: String,
    selectedAssetName: String?,
    downloadProgress: Int,
    downloading: Boolean,
    downloadedPath: String?,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onSelectAsset: (String) -> Unit,
    onOpenReleasePage: () -> Unit,
    onDownload: (ReleaseAsset) -> Unit,
    onInstall: () -> Unit,
) {
    val release = info?.latestRelease
    val assets = release?.assets.orEmpty().let { list ->
        val zips = list.filter { it.name.lowercase().endsWith(".zip") }
        if (zips.isNotEmpty()) zips else list
    }

    // Pick a sensible default asset once we have release info.
    androidx.compose.runtime.LaunchedEffect(release?.tagName, assets.size) {
        if (selectedAssetName.isNullOrBlank() && assets.isNotEmpty()) {
            onSelectAsset(assets.first().name)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("模块更新") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "当前版本：v${currentVersion}",
                    style = MaterialTheme.typography.bodyMedium,
                )

                if (info == null) {
                    Text(
                        text = "正在获取更新信息…（如果长时间没有响应，可点右下角刷新）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (release == null || release.tagName.isBlank()) {
                    Text(
                        text = "未获取到发布信息",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "最新版本：${release.tagName}",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        )
                        val pub = release.publishedAt.takeIf { it.isNotBlank() }
                        if (!pub.isNullOrBlank()) {
                            Text(
                                text = "发布时间：$pub",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (info.hasUpdate) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    ) {
                        Text(
                            text = if (info.hasUpdate) "可更新" else "已是最新",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (info.hasUpdate) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }

                if (release.body.isNotBlank()) {
                    Text(
                        text = release.body.trim(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 14,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (info.hasUpdate) {
                    if (assets.isEmpty()) {
                        Text(
                            text = "未找到可下载的安装包（请点“发布页”手动下载）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = "选择安装包",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            assets.forEach { a ->
                                val checked = a.name == selectedAssetName
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectAsset(a.name) }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    RadioButton(
                                        selected = checked,
                                        onClick = { onSelectAsset(a.name) },
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = a.name,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        val mb = (a.size / 1024.0 / 1024.0)
                                        Text(
                                            text = String.format("%.1f MB", mb),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (downloading) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            LinearProgressIndicator(progress = downloadProgress / 100f)
                            Text(
                                text = "下载中… ${downloadProgress.coerceIn(0, 100)}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else if (!downloadedPath.isNullOrBlank()) {
                        Text(
                            text = "安装包已下载，可点击右下角“安装”开始刷入。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // (rest of dialog content continues)
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onOpenReleasePage) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("发布页")
                }
                TextButton(onClick = onRefresh) { Text("刷新") }

                if (info?.hasUpdate == true && assets.isNotEmpty()) {
                    val selected = assets.firstOrNull { it.name == selectedAssetName } ?: assets.firstOrNull()
                    if (!downloadedPath.isNullOrBlank()) {
                        FilledTonalButton(onClick = onInstall) { Text("安装") }
                    } else {
                        FilledTonalButton(
                            onClick = { if (selected != null) onDownload(selected) },
                            enabled = !downloading && selected != null,
                        ) { Text("下载") }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@Composable
private fun AccessInfoCard(
    apiToken: String,
    apiPort: Int,
    apiHost: String,
    serviceRunning: Boolean,
) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val token = apiToken.trim().ifBlank { "87654321" }
    val port = apiPort.coerceIn(1, 65535)

    val lanIps = rememberLanIpv4Addresses()
    val lanIp = lanIps.firstOrNull().orEmpty()

    var revealToken by remember { mutableStateOf(false) }

    val localUrl = buildHttpUrl(host = "127.0.0.1", port = port, token = token)
    val lanUrl = if (lanIp.isBlank()) "" else buildHttpUrl(host = lanIp, port = port, token = token)

    ManagerCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "访问地址",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "端口：$port · Token：${if (revealToken) token else "******"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AssistChip(
                    onClick = { revealToken = !revealToken },
                    label = { Text(if (revealToken) "隐藏" else "显示") },
                )
            }

            UrlRow(
                title = "本机（127.0.0.1）",
                url = localUrl,
                displayUrl = if (revealToken) localUrl else maskTokenInUrl(localUrl, token),
                enabled = serviceRunning,
                subtitle = if (!serviceRunning) "服务未运行，启动后才可访问" else null,
                onCopy = {
                    clipboard.setText(AnnotatedString(localUrl))
                    Toast.makeText(ctx, "已复制本机地址", Toast.LENGTH_SHORT).show()
                },
                onOpen = {
                    openUrl(ctx, localUrl)
                },
            )

            UrlRow(
                title = "局域网（LAN）",
                url = lanUrl,
                displayUrl = if (revealToken) lanUrl else maskTokenInUrl(lanUrl, token),
                enabled = serviceRunning && lanUrl.isNotBlank(),
                subtitle = when {
                    !serviceRunning -> "服务未运行，启动后才可访问"
                    lanUrl.isBlank() -> "未获取到局域网 IPv4（请连接 Wi-Fi / 有线网络）"
                    else -> null
                },
                onCopy = {
                    clipboard.setText(AnnotatedString(lanUrl))
                    Toast.makeText(ctx, "已复制局域网地址", Toast.LENGTH_SHORT).show()
                },
                onOpen = {
                    openUrl(ctx, lanUrl)
                },
            )

            AnimatedVisibility(
                visible = !serviceRunning,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(8.dp),
                        )
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "提示：服务当前已停止。请先在上方点击\"启动\"，再使用以上地址访问。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            if (apiHost.trim() == "127.0.0.1" || apiHost.trim().equals("localhost", ignoreCase = true)) {
                Text(
                    text = "提示：当前 DANMU_API_HOST=$apiHost（仅本机可访问）。如需局域网访问，请在 .env 中设置 DANMU_API_HOST=0.0.0.0 后重启服务。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun UrlRow(
    title: String,
    url: String,
    displayUrl: String,
    enabled: Boolean = true,
    subtitle: String? = null,
    onCopy: () -> Unit,
    onOpen: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = displayUrl,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onCopy, enabled = enabled) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "copy")
            }
            IconButton(onClick = onOpen, enabled = enabled) {
                Icon(Icons.Filled.OpenInNew, contentDescription = "open")
            }
        }
    }
}

private fun buildHttpUrl(host: String, port: Int, token: String): String {
    val t = token.trim().trim('/').ifBlank { "" }
    return if (t.isBlank()) {
        "http://$host:$port/"
    } else {
        "http://$host:$port/$t/"
    }
}

private fun maskTokenInUrl(url: String, token: String): String {
    val t = token.trim().trim('/').ifBlank { return url }
    return url.replace("/$t/", "/******/")
}

private fun openUrl(context: android.content.Context, url: String) {
    try {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i)
    } catch (_: Throwable) {
        Toast.makeText(context, "无法打开：$url", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun CurrentCoreCard(
    status: StatusResponse?,
    cores: CoreListResponse?,
    activeUpdate: CoreUpdateInfo?,
    onActivateCore: (String) -> Unit,
    onCheckActiveUpdate: () -> Unit,
) {
    val core = status?.activeCore
    val activeId = status?.activeCoreId
    val list = cores?.cores.orEmpty()

    var showSwitch by remember { mutableStateOf(false) }
    var selectedId by remember(activeId, list.size) {
        mutableStateOf(activeId ?: list.firstOrNull()?.id.orEmpty())
    }

    if (showSwitch) {
        AlertDialog(
            onDismissRequest = { showSwitch = false },
            title = { Text("切换核心") },
            text = {
                if (list.isEmpty()) {
                    Text("暂无已安装核心")
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        list.forEach { c ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedId = c.id }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                RadioButton(
                                    selected = c.id == selectedId,
                                    onClick = { selectedId = c.id },
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = c.repo,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val ver = c.version?.takeIf { it.isNotBlank() } ?: "-"
                                    Text(
                                        text = "Ref: ${c.ref} · v$ver",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (c.id == activeId) {
                                    AssistChip(onClick = {}, label = { Text("当前") })
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSwitch = false
                        if (selectedId.isNotBlank()) onActivateCore(selectedId)
                    },
                    enabled = selectedId.isNotBlank() && selectedId != activeId,
                ) { Text("切换") }
            },
            dismissButton = { TextButton(onClick = { showSwitch = false }) { Text("取消") } },
        )
    }

    ManagerCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "当前核心",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (core == null) {
                Text(
                    text = "未检测到核心（请先下载/安装）",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = core.repo,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(text = "Ref: ${core.ref}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "版本: ${core.version ?: "-"}", style = MaterialTheme.typography.bodyMedium)
                val sha = core.shaShort ?: core.sha
                if (!sha.isNullOrBlank()) {
                    Text(
                        text = "Commit: $sha",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                if (!core.installedAt.isNullOrBlank()) {
                    Text(
                        text = "安装时间: ${core.installedAt}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (core != null) {
                if (activeUpdate == null) {
                    Text(
                        text = "尚未检查更新",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val remoteVer = activeUpdate.latestVersion
                    val remoteShaShort = activeUpdate.latestCommit?.sha?.take(7)
                    val text = if (activeUpdate.updateAvailable) {
                        "可更新" + (remoteVer?.let { "：v$it" } ?: "")
                    } else {
                        "已是最新"
                    }
                    Text(text = text, style = MaterialTheme.typography.bodyMedium)
                    if (!remoteShaShort.isNullOrBlank()) {
                        Text(
                            text = "Remote: $remoteShaShort",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { showSwitch = true },
                    enabled = list.isNotEmpty(),
                ) {
                    Icon(Icons.Filled.SwapHoriz, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("切换")
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onCheckActiveUpdate,
                    enabled = core != null,
                ) {
                    Icon(Icons.Filled.SystemUpdate, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("检查更新")
                }
            }
        }
    }
}

@Composable
private fun AutostartCard(
    status: StatusResponse?,
    onAutostartChange: (Boolean) -> Unit,
) {
    val on = status?.autostart == "on"

    ManagerCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "开机自启动",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (on) "设备重启后自动启动服务" else "不会自动启动（更省电）",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = on,
                onCheckedChange = { onAutostartChange(it) },
            )
        }
    }
}

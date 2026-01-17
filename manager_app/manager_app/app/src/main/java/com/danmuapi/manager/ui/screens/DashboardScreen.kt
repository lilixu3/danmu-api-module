package com.danmuapi.manager.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.danmuapi.manager.data.CoreUpdateInfo
import com.danmuapi.manager.data.model.CoreListResponse
import com.danmuapi.manager.data.model.CoreMeta
import com.danmuapi.manager.data.model.StatusResponse
import java.net.Inet4Address
import java.net.NetworkInterface

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
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit,
    onAutostartChange: (Boolean) -> Unit,
    onActivateCore: (String) -> Unit,
    onCheckActiveCoreUpdate: () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(paddingValues)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OverviewCard(
            rootAvailable = rootAvailable,
            status = status,
            onStart = onStart,
            onStop = onStop,
            onRestart = onRestart,
        )

        AccessCard(
            apiToken = apiToken,
            apiPort = apiPort,
            apiHost = apiHost,
        )

        CoreCard(
            status = status,
            cores = cores,
            activeUpdate = activeUpdate,
            onActivateCore = onActivateCore,
            onCheckActiveUpdate = onCheckActiveCoreUpdate,
        )
        AutostartCard(status = status, onAutostartChange = onAutostartChange)
    }
}

@Composable
private fun OverviewCard(
    rootAvailable: Boolean?,
    status: StatusResponse?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit,
) {
    val running = status?.service?.running == true
    val pid = status?.service?.pid
    val moduleEnabled = status?.module?.enabled
    val version = status?.module?.version ?: "-"

    Card {
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
                    Text(text = "运行概览", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "模块 v$version",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                AssistChip(
                    onClick = {},
                    label = { Text(if (running) "运行中" else "已停止") },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusChip(label = "Root", ok = rootAvailable)
                StatusChip(label = "模块", ok = moduleEnabled)
                StatusChip(label = "服务", ok = running)
            }

            Text(
                text = if (running) "PID: ${pid ?: "?"}" else "服务未运行",
                style = MaterialTheme.typography.bodyMedium,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilledTonalButton(
                    modifier = Modifier.weight(1f),
                    onClick = if (running) onStop else onStart,
                ) {
                    Icon(Icons.Filled.PowerSettingsNew, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (running) "停止" else "启动")
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onRestart,
                    enabled = running,
                ) {
                    Icon(Icons.Filled.RestartAlt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("重启")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusChip(label: String, ok: Boolean?) {
    val text = when (ok) {
        null -> "$label: 检测中"
        true -> "$label: 正常"
        false -> "$label: 异常"
    }
    AssistChip(
        onClick = {},
        label = { Text(text) },
        leadingIcon = {
            val icon = when (ok) {
                true -> Icons.Filled.CheckCircle
                false -> Icons.Filled.Error
                null -> Icons.Filled.Error
            }
            Icon(icon, contentDescription = null)
        },
    )
}

@Composable
private fun AccessCard(
    apiToken: String,
    apiPort: Int,
    apiHost: String,
) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val token = apiToken.trim().ifBlank { "87654321" }
    val port = apiPort.coerceIn(1, 65535)

    val lanIps = remember { getLanIpv4Addresses() }
    val lanIp = lanIps.firstOrNull().orEmpty()

    var revealToken by remember { mutableStateOf(false) }

    val localUrl = buildHttpUrl(host = "127.0.0.1", port = port, token = token)
    val lanUrl = if (lanIp.isBlank()) "" else buildHttpUrl(host = lanIp, port = port, token = token)

    Card {
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
                    Text(text = "访问地址", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "端口：$port · Token：${if (revealToken) token else "******"}",
                        style = MaterialTheme.typography.bodySmall,
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
                enabled = lanUrl.isNotBlank(),
                subtitle = if (lanUrl.isBlank()) "未获取到局域网 IPv4（请连接 Wi-Fi / 有线网络）" else null,
                onCopy = {
                    clipboard.setText(AnnotatedString(lanUrl))
                    Toast.makeText(ctx, "已复制局域网地址", Toast.LENGTH_SHORT).show()
                },
                onOpen = {
                    openUrl(ctx, lanUrl)
                },
            )

            if (apiHost.trim() == "127.0.0.1" || apiHost.trim().equals("localhost", ignoreCase = true)) {
                Text(
                    text = "提示：当前 DANMU_API_HOST=$apiHost（仅本机可访问）。如需局域网访问，请在 .env 中设置 DANMU_API_HOST=0.0.0.0 后重启服务。",
                    style = MaterialTheme.typography.bodySmall,
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
        Text(text = title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
        if (!subtitle.isNullOrBlank()) {
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall)
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

private fun getLanIpv4Addresses(): List<String> {
    return try {
        val result = mutableListOf<String>()
        val ifaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
        for (iface in ifaces) {
            // Skip down interfaces / loopback / tunnels.
            if (!iface.isUp || iface.isLoopback) continue
            val name = iface.name ?: ""
            if (name.startsWith("lo") || name.startsWith("tun") || name.startsWith("dummy")) continue

            val addrs = iface.inetAddresses
            while (addrs.hasMoreElements()) {
                val addr = addrs.nextElement()
                if (addr is Inet4Address && !addr.isLoopbackAddress) {
                    val ip = addr.hostAddress ?: continue
                    // Skip link-local (169.254.x.x)
                    if (ip.startsWith("169.254.")) continue
                    result.add(ip)
                }
            }
        }
        // Prefer common interfaces first.
        result.distinct().sortedWith(compareBy({
            when {
                it.startsWith("192.168.") -> 0
                it.startsWith("10.") -> 1
                it.startsWith("172.16.") -> 2
                else -> 3
            }
        }, { it }))
    } catch (_: Throwable) {
        emptyList()
    }
}

@Composable
private fun RootCard(rootAvailable: Boolean?) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val ok = rootAvailable == true
            Icon(
                imageVector = if (ok) Icons.Filled.CheckCircle else Icons.Filled.Error,
                contentDescription = null,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Root 权限", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = when (rootAvailable) {
                        null -> "检测中…"
                        true -> "已获取 Root（Magisk su）"
                        false -> "未获取 Root / su 不可用"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun ModuleCard(status: StatusResponse?) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Magisk 模块", style = MaterialTheme.typography.titleMedium)
                val enabled = status?.module?.enabled
                Text(
                    text = when (enabled) {
                        null -> "未知"
                        true -> "已启用"
                        false -> "已禁用（请在 Magisk 中启用后重启）"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            val version = status?.module?.version ?: "-"
            AssistChip(onClick = {}, label = { Text("v$version") })
        }
    }
}

@Composable
private fun ServiceCard(
    status: StatusResponse?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit,
) {
    val running = status?.service?.running == true
    val pid = status?.service?.pid

    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = "核心服务", style = MaterialTheme.typography.titleMedium)
                AssistChip(
                    onClick = {},
                    label = { Text(if (running) "运行中" else "已停止") },
                )
            }

            Text(
                text = if (running) "PID: ${pid ?: "?"}" else "当前未运行",
                style = MaterialTheme.typography.bodyMedium,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilledTonalButton(
                    modifier = Modifier.weight(1f),
                    onClick = if (running) onStop else onStart,
                ) {
                    Icon(Icons.Filled.PowerSettingsNew, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (running) "停止" else "启动")
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onRestart,
                    enabled = running,
                ) {
                    Icon(Icons.Filled.RestartAlt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("重启")
                }
            }
        }
    }
}

@Composable
private fun CoreCard(
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

    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = "当前核心", style = MaterialTheme.typography.titleMedium)
            if (core == null) {
                Text(text = "未检测到核心（请先下载/安装）", style = MaterialTheme.typography.bodyMedium)
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
                    Text(text = "Commit: $sha", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
                if (!core.installedAt.isNullOrBlank()) {
                    Text(text = "安装时间: ${core.installedAt}", style = MaterialTheme.typography.bodySmall)
                }
            }

            // Active core update hint
            if (core != null) {
                if (activeUpdate == null) {
                    Text(text = "尚未检查更新", style = MaterialTheme.typography.bodySmall)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutostartCard(
    status: StatusResponse?,
    onAutostartChange: (Boolean) -> Unit,
) {
    val on = status?.autostart == "on"

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "开机自启动", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (on) "设备重启后自动启动服务" else "不会自动启动（更省电）",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Switch(
                checked = on,
                onCheckedChange = { onAutostartChange(it) },
            )
        }
    }
}

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.danmuapi.manager.feature.overview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Troubleshoot
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danmuapi.manager.app.state.ManagerViewModel
import com.danmuapi.manager.core.designsystem.theme.DanmuMonoFamily
import com.danmuapi.manager.core.model.CoreRecord
import com.danmuapi.manager.core.util.rememberLanIpv4Addresses

private data class OverviewColors(
    val backdropTop: Color,
    val backdropMid: Color,
    val backdropBottom: Color,
    val haloPrimary: Color,
    val haloSecondary: Color,
    val card: Color,
    val cardStrong: Color,
    val cardBorder: Color,
    val mutedBorder: Color,
    val subtleText: Color,
    val accent: Color,
    val accentContainer: Color,
    val positive: Color,
    val positiveContainer: Color,
    val danger: Color,
    val dangerContainer: Color,
    val disabledContainer: Color,
    val cardMuted: Color,
    val chip: Color,
    val chipBorder: Color,
)

@Composable
fun OverviewScreen(
    contentPadding: PaddingValues,
    viewModel: ManagerViewModel,
    onOpenSettings: () -> Unit,
    onOpenCoreHub: () -> Unit,
    onOpenConsole: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    val colors = rememberOverviewColors()
    val lanIpv4Addresses = rememberLanIpv4Addresses()
    val status = viewModel.status
    val running = status?.isRunning == true
    val rootReady = viewModel.rootAvailable == true
    val autostartEnabled = status?.isAutostartEnabled == true
    val activeCore = status?.activeCore ?: remember(status, viewModel.cores) {
        val activeId = status?.activeCoreId ?: viewModel.cores?.activeCoreId
        viewModel.cores?.cores.orEmpty().firstOrNull { it.id == activeId }
    }
    val activeCoreName = activeCore.shortDisplayName()
    val activeUpdate = activeCore?.id?.let(viewModel.updateInfo::get)
    val moduleUpdate = viewModel.moduleUpdateInfo
    var tokenVisible by rememberSaveable { mutableStateOf(false) }
    val localAccessUrl = remember(viewModel.apiPort, viewModel.apiToken) {
        "http://127.0.0.1:${viewModel.apiPort}/${viewModel.apiToken}"
    }
    val primaryLanAccessUrl = remember(lanIpv4Addresses, viewModel.apiPort, viewModel.apiToken) {
        lanIpv4Addresses.firstOrNull()?.let { "http://$it:${viewModel.apiPort}/${viewModel.apiToken}" }
    }
    val displayToken = remember(viewModel.apiToken, tokenVisible) {
        if (tokenVisible) viewModel.apiToken else maskToken(viewModel.apiToken)
    }
    val displayLocalAccessUrl = remember(localAccessUrl, viewModel.apiToken, tokenVisible) {
        if (tokenVisible) localAccessUrl else maskAccessUrlToken(localAccessUrl, viewModel.apiToken)
    }
    val displayLanAccessUrl = remember(primaryLanAccessUrl, viewModel.apiToken, tokenVisible) {
        primaryLanAccessUrl?.let { url ->
            if (tokenVisible) url else maskAccessUrlToken(url, viewModel.apiToken)
        }
    }
    val moduleVersion = status?.module?.version?.takeIf { it.isNotBlank() }
        ?: moduleUpdate?.currentVersion?.takeIf { it.isNotBlank() }
        ?: "--"
    val updateAvailable = activeUpdate?.updateAvailable == true || moduleUpdate?.hasUpdate == true

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        colors.backdropTop,
                        colors.backdropMid,
                        colors.backdropBottom,
                    ),
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .offset(x = 220.dp, y = (-54).dp)
                .size(230.dp)
                .clip(CircleShape)
                .background(colors.haloPrimary),
        )
        Box(
            modifier = Modifier
                .offset(x = (-72).dp, y = 104.dp)
                .size(188.dp)
                .clip(CircleShape)
                .background(colors.haloSecondary),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = contentPadding.calculateBottomPadding() + 28.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                HomeTopBar(
                    colors = colors,
                    onOpenSettings = onOpenSettings,
                )

                viewModel.busyMessage?.takeIf { it.isNotBlank() }?.let { message ->
                    HomeBusyStrip(
                        message = message,
                        colors = colors,
                    )
                }

                MainControlCard(
                    running = running,
                    rootReady = rootReady,
                    activeCoreName = activeCoreName,
                    hasActiveCore = activeCore != null,
                    busy = viewModel.busy,
                    colors = colors,
                    onToggle = {
                        if (running) {
                            viewModel.stopService()
                        } else {
                            viewModel.startService()
                        }
                    },
                    onRestart = viewModel::restartService,
                    onRefresh = viewModel::refreshAll,
                )

                HomeAccessCard(
                    localAccessUrl = displayLocalAccessUrl,
                    lanAccessUrl = displayLanAccessUrl,
                    autostartEnabled = autostartEnabled,
                    autostartClickable = rootReady && !viewModel.busy,
                    tokenVisible = tokenVisible,
                    colors = colors,
                    onCopyLocal = { clipboardManager.setText(AnnotatedString(localAccessUrl)) },
                    onCopyLan = {
                        primaryLanAccessUrl?.let { clipboardManager.setText(AnnotatedString(it)) }
                    },
                    onToggleTokenVisibility = { tokenVisible = !tokenVisible },
                    onToggleAutostart = { viewModel.setAutostart(!autostartEnabled) },
                )

                HomeSystemInfoCard(
                    requestCount = viewModel.todayReqNum.toString(),
                    port = viewModel.apiPort.toString(),
                    token = displayToken,
                    version = moduleVersion,
                    colors = colors,
                    onToggleTokenVisibility = { tokenVisible = !tokenVisible },
                )

                HomeManagementCard(
                    updateAvailable = updateAvailable,
                    colors = colors,
                    onOpenCoreHub = onOpenCoreHub,
                    onCheckUpdates = viewModel::checkUpdates,
                    onOpenConsole = onOpenConsole,
                )
            }
        }
    }
}

@Composable
private fun HomeTopBar(
    colors: OverviewColors,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "弹幕 API",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = 31.sp,
                lineHeight = 35.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.6).sp,
            ),
        )
        Surface(
            onClick = onOpenSettings,
            shape = CircleShape,
            color = colors.card,
            border = BorderStroke(1.dp, colors.cardBorder),
            shadowElevation = 1.dp,
        ) {
            Box(
                modifier = Modifier.size(42.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "设置",
                )
            }
        }
    }
}

@Composable
private fun HomeBusyStrip(
    message: String,
    colors: OverviewColors,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = colors.cardStrong,
        border = BorderStroke(1.dp, colors.cardBorder),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.subtleText,
            )
        }
    }
}

@Composable
private fun MainControlCard(
    running: Boolean,
    rootReady: Boolean,
    activeCoreName: String,
    hasActiveCore: Boolean,
    busy: Boolean,
    colors: OverviewColors,
    onToggle: () -> Unit,
    onRestart: () -> Unit,
    onRefresh: () -> Unit,
) {
    val canToggle = if (running) {
        rootReady && !busy
    } else {
        rootReady && hasActiveCore && !busy
    }
    val canRestart = running && rootReady && !busy
    val canRefresh = !busy

    val title = when {
        !rootReady -> "当前不可控制"
        !hasActiveCore -> "未选择核心"
        running -> "服务运行中"
        else -> "服务已停止"
    }
    val detail = when {
        !rootReady -> "Root 未就绪，当前仅支持查看与刷新状态。"
        !hasActiveCore -> "请先前往核心管理选择活动核心。"
        running -> "主服务已启动，可直接执行停止或重启。"
        else -> "当前条件已满足，可随时启动服务。"
    }
    val primaryLabel = when {
        busy -> "处理中"
        running -> "停止服务"
        else -> "启动服务"
    }
    val primaryTone = when {
        !canToggle -> colors.subtleText
        running -> colors.danger
        else -> colors.accent
    }
    val primaryContainer = when {
        !canToggle -> colors.disabledContainer
        running -> colors.dangerContainer
        else -> colors.accentContainer
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        color = colors.cardStrong,
        border = BorderStroke(1.dp, colors.cardBorder),
        shadowElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "服务控制",
                style = MaterialTheme.typography.labelMedium,
                color = colors.subtleText,
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 28.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.4).sp,
                    ),
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    ),
                    color = colors.subtleText,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ControlMetaCard(
                    modifier = Modifier.weight(1f),
                    label = "当前核心",
                    value = activeCoreName,
                    colors = colors,
                )
                ControlMetaCard(
                    modifier = Modifier.weight(1f),
                    label = "Root",
                    value = if (rootReady) "已就绪" else "受限",
                    valueColor = if (rootReady) colors.positive else colors.danger,
                    colors = colors,
                )
            }

            HomePrimaryActionButton(
                label = primaryLabel,
                enabled = canToggle,
                busy = busy,
                tone = primaryTone,
                containerColor = primaryContainer,
                colors = colors,
                onClick = onToggle,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HomeCompactActionButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.RestartAlt,
                    label = "重启",
                    enabled = canRestart,
                    colors = colors,
                    onClick = onRestart,
                )
                HomeCompactActionButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Refresh,
                    label = "刷新",
                    enabled = canRefresh,
                    colors = colors,
                    onClick = onRefresh,
                )
            }
        }
    }
}

@Composable
private fun ControlMetaCard(
    label: String,
    value: String,
    colors: OverviewColors,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = colors.cardMuted,
        border = BorderStroke(1.dp, colors.cardBorder),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = colors.subtleText,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = valueColor,
            )
        }
    }
}

@Composable
private fun HomePrimaryActionButton(
    label: String,
    enabled: Boolean,
    busy: Boolean,
    tone: Color,
    containerColor: Color,
    colors: OverviewColors,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(24.dp),
        color = containerColor,
        border = BorderStroke(1.dp, colors.mutedBorder),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(colors.cardStrong.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center,
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = tone,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.PowerSettingsNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = tone,
                    )
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                ),
                color = tone,
            )
        }
    }
}

@Composable
private fun HomeCompactActionButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    colors: OverviewColors,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier,
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(22.dp),
        color = if (enabled) colors.cardStrong else colors.disabledContainer,
        border = BorderStroke(1.dp, colors.cardBorder),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (enabled) colors.accent else colors.subtleText,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = if (enabled) MaterialTheme.colorScheme.onSurface else colors.subtleText,
            )
        }
    }
}

@Composable
private fun HomeAccessCard(
    localAccessUrl: String,
    lanAccessUrl: String?,
    autostartEnabled: Boolean,
    autostartClickable: Boolean,
    tokenVisible: Boolean,
    colors: OverviewColors,
    onCopyLocal: () -> Unit,
    onCopyLan: () -> Unit,
    onToggleTokenVisibility: () -> Unit,
    onToggleAutostart: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        color = colors.cardStrong,
        border = BorderStroke(1.dp, colors.cardBorder),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "访问地址",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.subtleText,
                )
                Surface(
                    onClick = onToggleTokenVisibility,
                    shape = CircleShape,
                    color = colors.cardMuted,
                    border = BorderStroke(1.dp, colors.cardBorder),
                    shadowElevation = 0.dp,
                ) {
                    Box(
                        modifier = Modifier.size(28.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (tokenVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = if (tokenVisible) "隐藏 Token" else "显示 Token",
                            modifier = Modifier.size(15.dp),
                            tint = colors.subtleText,
                        )
                    }
                }
            }

            AccessAddressRow(
                title = "本机",
                value = localAccessUrl,
                colors = colors,
                onCopy = onCopyLocal,
            )

            DividerLine(colors)

            AccessAddressRow(
                title = "局域网",
                value = lanAccessUrl ?: "当前未检测到局域网地址",
                colors = colors,
                enabled = lanAccessUrl != null,
                emphasize = lanAccessUrl != null,
                onCopy = onCopyLan,
            )

            DividerLine(colors)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "自启动",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                    Text(
                        text = "开机后自动拉起服务",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.subtleText,
                    )
                }
                HomeAutostartPill(
                    checked = autostartEnabled,
                    onClick = onToggleAutostart,
                    enabled = autostartClickable,
                    colors = colors,
                )
            }
        }
    }
}

@Composable
private fun HomeAutostartPill(
    checked: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    colors: OverviewColors,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = if (checked) colors.accentContainer else colors.chip,
        border = BorderStroke(1.dp, if (checked) colors.accent.copy(alpha = 0.14f) else colors.chipBorder),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (checked) colors.accent else colors.subtleText),
            )
            Text(
                text = if (checked) "已开启" else "已关闭",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (checked) colors.accent else colors.subtleText,
            )
        }
    }
}

@Composable
private fun AccessAddressRow(
    title: String,
    value: String,
    colors: OverviewColors,
    onCopy: () -> Unit,
    enabled: Boolean = true,
    emphasize: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.subtleText,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = DanmuMonoFamily,
                ),
                color = when {
                    !enabled -> colors.subtleText
                    emphasize -> colors.accent
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
        Surface(
            onClick = onCopy,
            enabled = enabled,
            shape = CircleShape,
            color = if (enabled) colors.cardStrong else colors.disabledContainer,
            border = BorderStroke(1.dp, colors.cardBorder),
            shadowElevation = 0.dp,
        ) {
            Box(
                modifier = Modifier.size(30.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (enabled) colors.accent else colors.subtleText,
                )
            }
        }
    }
}

@Composable
private fun HomeSystemInfoCard(
    requestCount: String,
    port: String,
    token: String,
    version: String,
    colors: OverviewColors,
    onToggleTokenVisibility: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        color = colors.cardStrong,
        border = BorderStroke(1.dp, colors.cardBorder),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "系统信息",
                style = MaterialTheme.typography.labelMedium,
                color = colors.subtleText,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HomeMetricCard(
                    modifier = Modifier.weight(1f),
                    label = "请求量",
                    value = requestCount,
                    colors = colors,
                )
                HomeMetricCard(
                    modifier = Modifier.weight(1f),
                    label = "端口",
                    value = port,
                    colors = colors,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HomeMetricCard(
                    modifier = Modifier.weight(1f),
                    label = "Token",
                    value = token,
                    colors = colors,
                    monospace = true,
                    onClick = onToggleTokenVisibility,
                )
                HomeMetricCard(
                    modifier = Modifier.weight(1f),
                    label = "版本",
                    value = version,
                    colors = colors,
                )
            }
        }
    }
}

@Composable
private fun HomeMetricCard(
    label: String,
    value: String,
    colors: OverviewColors,
    modifier: Modifier = Modifier,
    monospace: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    if (onClick != null) {
        Surface(
            modifier = modifier,
            onClick = onClick,
            shape = RoundedCornerShape(22.dp),
            color = colors.cardMuted,
            border = BorderStroke(1.dp, colors.cardBorder),
            shadowElevation = 0.dp,
        ) {
            HomeMetricCardContent(
                label = label,
                value = value,
                colors = colors,
                monospace = monospace,
            )
        }
    } else {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(22.dp),
            color = colors.cardMuted,
            border = BorderStroke(1.dp, colors.cardBorder),
            shadowElevation = 0.dp,
        ) {
            HomeMetricCardContent(
                label = label,
                value = value,
                colors = colors,
                monospace = monospace,
            )
        }
    }
}

@Composable
private fun HomeMetricCardContent(
    label: String,
    value: String,
    colors: OverviewColors,
    monospace: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.subtleText,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 20.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Black,
                fontFamily = if (monospace) DanmuMonoFamily else null,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HomeManagementCard(
    updateAvailable: Boolean,
    colors: OverviewColors,
    onOpenCoreHub: () -> Unit,
    onCheckUpdates: () -> Unit,
    onOpenConsole: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        color = colors.cardStrong,
        border = BorderStroke(1.dp, colors.cardBorder),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "更多管理",
                style = MaterialTheme.typography.labelMedium,
                color = colors.subtleText,
            )
            Spacer(modifier = Modifier.height(4.dp))
            HomeManagementRow(
                icon = Icons.Filled.CloudDownload,
                title = "核心管理",
                colors = colors,
                onClick = onOpenCoreHub,
            )
            DividerLine(colors)
            HomeManagementRow(
                icon = Icons.Filled.SystemUpdateAlt,
                title = "检查更新",
                colors = colors,
                badge = if (updateAvailable) "有更新" else null,
                onClick = onCheckUpdates,
            )
            DividerLine(colors)
            HomeManagementRow(
                icon = Icons.Filled.Troubleshoot,
                title = "日志查看",
                colors = colors,
                onClick = onOpenConsole,
            )
        }
    }
}

@Composable
private fun HomeManagementRow(
    icon: ImageVector,
    title: String,
    colors: OverviewColors,
    onClick: () -> Unit,
    badge: String? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        color = Color.Transparent,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(colors.cardMuted)
                    .border(1.dp, colors.cardBorder, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = colors.accent,
                )
            }
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            )
            badge?.let {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colors.dangerContainer)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.danger,
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.subtleText,
            )
        }
    }
}

@Composable
private fun DividerLine(colors: OverviewColors) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(colors.cardBorder),
    )
}

@Composable
private fun rememberOverviewColors(): OverviewColors {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return remember(isDark) {
        if (isDark) {
            OverviewColors(
                backdropTop = Color(0xFF0F1721),
                backdropMid = Color(0xFF131B26),
                backdropBottom = Color(0xFF11151A),
                haloPrimary = Color(0xFF2C4E75).copy(alpha = 0.34f),
                haloSecondary = Color(0xFF18304A).copy(alpha = 0.44f),
                card = Color(0xFF171F29).copy(alpha = 0.96f),
                cardStrong = Color(0xFF1B2430).copy(alpha = 0.98f),
                cardBorder = Color(0xFF293341),
                mutedBorder = Color(0xFF222B36),
                subtleText = Color(0xFFAFBBC9),
                accent = Color(0xFF8EB8E8),
                accentContainer = Color(0xFF203244),
                positive = Color(0xFF7DCDA5),
                positiveContainer = Color(0xFF173126),
                danger = Color(0xFFF1A08F),
                dangerContainer = Color(0xFF382725),
                disabledContainer = Color(0xFF28313D),
                cardMuted = Color(0xFF17202A).copy(alpha = 0.88f),
                chip = Color(0xFF202A35),
                chipBorder = Color(0xFF2A3542),
            )
        } else {
            OverviewColors(
                backdropTop = Color(0xFFE9F0F8),
                backdropMid = Color(0xFFF6F9FC),
                backdropBottom = Color(0xFFF2F5F8),
                haloPrimary = Color(0xFFBDD2EA).copy(alpha = 0.52f),
                haloSecondary = Color(0xFFD8E5F3).copy(alpha = 0.78f),
                card = Color(0xFFFBFDFF).copy(alpha = 0.92f),
                cardStrong = Color(0xFFFFFFFF).copy(alpha = 0.97f),
                cardBorder = Color(0xFFD8E2EC),
                mutedBorder = Color(0xFFE5EBF2),
                subtleText = Color(0xFF667386),
                accent = Color(0xFF5F83A8),
                accentContainer = Color(0xFFE7EFF8),
                positive = Color(0xFF2E8661),
                positiveContainer = Color(0xFFE5F2EB),
                danger = Color(0xFFD86F5A),
                dangerContainer = Color(0xFFF8E8E3),
                disabledContainer = Color(0xFFDCE4EC),
                cardMuted = Color(0xFFF8FBFE),
                chip = Color(0xFFF3F7FB),
                chipBorder = Color(0xFFE5ECF3),
            )
        }
    }
}

private fun maskToken(token: String): String {
    return when {
        token.length <= 4 -> token
        token.length <= 8 -> token.take(2) + "••" + token.takeLast(2)
        else -> token.take(4) + "••••" + token.takeLast(2)
    }
}

private fun maskAccessUrlToken(url: String, token: String): String {
    if (token.isBlank()) return url
    val suffix = "/$token"
    return if (url.endsWith(suffix)) {
        url.removeSuffix(suffix) + "/" + maskToken(token)
    } else {
        url.replace(token, maskToken(token))
    }
}

private fun CoreRecord?.shortDisplayName(): String {
    val source = this?.repoDisplayName?.takeIf { it.isNotBlank() } ?: return "未选择"
    return source.substringAfterLast('/')
}

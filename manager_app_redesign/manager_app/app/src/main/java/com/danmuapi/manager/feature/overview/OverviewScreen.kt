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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.danmuapi.manager.core.model.ManagerStatus
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
) {
    val clipboardManager = LocalClipboardManager.current
    val colors = rememberOverviewColors()
    val lanIpv4Addresses = rememberLanIpv4Addresses()
    val status = viewModel.status
    val running = status?.isRunning == true
    val rootReady = viewModel.rootAvailable == true
    val autostartEnabled = isAutostartEnabled(status?.autostart)
    val activeCore = status?.activeCore ?: remember(status, viewModel.cores) {
        val activeId = status?.activeCoreId ?: viewModel.cores?.activeCoreId
        viewModel.cores?.cores.orEmpty().firstOrNull { it.id == activeId }
    }
    val activeUpdate = activeCore?.id?.let(viewModel.updateInfo::get)
    val moduleUpdate = viewModel.moduleUpdateInfo
    val displayCoreName = activeCore.shortDisplayName()
    val maskedToken = maskToken(viewModel.apiToken)
    val localAccessUrl = remember(viewModel.apiPort, viewModel.apiToken) {
        "http://127.0.0.1:${viewModel.apiPort}/${viewModel.apiToken}"
    }
    val moduleVersion = status?.module?.version?.takeIf { it.isNotBlank() }
        ?: moduleUpdate?.currentVersion?.takeIf { it.isNotBlank() }
        ?: "--"
    val primaryLanAccessUrl = remember(lanIpv4Addresses, viewModel.apiPort, viewModel.apiToken) {
        lanIpv4Addresses.firstOrNull()?.let { "http://$it:${viewModel.apiPort}/${viewModel.apiToken}" }
    }

    val canToggleAutostart = !viewModel.busy && rootReady
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
                HomeHeader(
                    subtitle = buildHeaderSubtitle(
                        coreName = displayCoreName,
                        running = running,
                        pid = status?.service?.pid,
                        autostartEnabled = autostartEnabled,
                    ),
                    running = running,
                    colors = colors,
                    onOpenSettings = onOpenSettings,
                )

                viewModel.busyMessage?.takeIf { it.isNotBlank() }?.let { message ->
                    HomeBusyStrip(
                        message = message,
                        colors = colors,
                    )
                }

                HomeHeroCard(
                    running = running,
                    rootReady = rootReady,
                    activeCore = activeCore,
                    status = status,
                    autostartEnabled = autostartEnabled,
                    todayReqNum = viewModel.todayReqNum,
                    busy = viewModel.busy,
                    colors = colors,
                    onToggle = {
                        if (running) {
                            viewModel.stopService()
                        } else {
                            viewModel.startService()
                        }
                    },
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        HomeMetricCard(
                            modifier = Modifier.weight(1f),
                            label = "请求量",
                            value = viewModel.todayReqNum.toString(),
                            colors = colors,
                        )
                        HomeMetricCard(
                            modifier = Modifier.weight(1f),
                            label = "端口",
                            value = viewModel.apiPort.toString(),
                            colors = colors,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        HomeMetricCard(
                            modifier = Modifier.weight(1f),
                            label = "Token",
                            value = maskedToken,
                            colors = colors,
                            monospace = true,
                        )
                        HomeMetricCard(
                            modifier = Modifier.weight(1f),
                            label = "版本",
                            value = moduleVersion,
                            colors = colors,
                        )
                    }
                }

                HomeQuickActionsCard(
                    running = running,
                    activeCore = activeCore,
                    updateAvailable = updateAvailable,
                    colors = colors,
                    onRefreshOrRestart = {
                        if (running) {
                            viewModel.restartService()
                        } else {
                            viewModel.refreshAll()
                        }
                    },
                    onOpenCoreHub = onOpenCoreHub,
                    onCheckUpdates = viewModel::checkUpdates,
                    busy = viewModel.busy,
                )

                HomeAccessCard(
                    localAccessUrl = localAccessUrl,
                    lanAccessUrl = primaryLanAccessUrl,
                    autostartEnabled = autostartEnabled,
                    autostartClickable = canToggleAutostart,
                    colors = colors,
                    onCopyLocal = { clipboardManager.setText(AnnotatedString(localAccessUrl)) },
                    onCopyLan = {
                        primaryLanAccessUrl?.let {
                            clipboardManager.setText(AnnotatedString(it))
                        }
                    },
                    onToggleAutostart = { viewModel.setAutostart(!autostartEnabled) },
                )
            }
        }
    }
}

@Composable
private fun HomeHeader(
    subtitle: String,
    running: Boolean,
    colors: OverviewColors,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "弹幕 API",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 36.sp,
                    lineHeight = 40.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.8).sp,
                ),
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                ),
                color = colors.subtleText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HomeStatusPill(
                text = if (running) "在线" else "离线",
                dotColor = if (running) colors.positive else colors.danger,
                containerColor = if (running) colors.positiveContainer else colors.dangerContainer,
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
                        contentDescription = null,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeStatusPill(
    text: String,
    dotColor: Color,
    containerColor: Color,
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(containerColor)
            .border(1.dp, dotColor.copy(alpha = 0.08f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = dotColor,
        )
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
private fun HomeHeroCard(
    running: Boolean,
    rootReady: Boolean,
    activeCore: CoreRecord?,
    status: ManagerStatus?,
    autostartEnabled: Boolean,
    todayReqNum: Int,
    busy: Boolean,
    colors: OverviewColors,
    onToggle: () -> Unit,
) {
    val canToggle = if (running) !busy else !busy && rootReady && activeCore != null
    val heroTitle = if (running) "服务运行中" else "服务已停止"
    val heroDescription = when {
        !rootReady -> "Root 未就绪，当前只能查看状态，暂时无法执行控制。"
        activeCore == null -> "还没有活动核心，先去切换核心，再启动服务。"
        running -> "接口已准备好，今天已处理 $todayReqNum 次请求。"
        else -> "实例已准备好，轻触右侧主按钮即可启动。"
    }
    val controlLabel = when {
        busy -> "处理中"
        running -> "停止"
        canToggle -> "启动"
        else -> "锁定"
    }
    val controlColor = when {
        running -> colors.danger
        canToggle -> colors.accent
        else -> colors.subtleText
    }
    val controlContainer = when {
        running -> colors.dangerContainer
        canToggle -> colors.accentContainer
        else -> colors.disabledContainer
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(34.dp),
        color = colors.cardStrong,
        border = BorderStroke(1.dp, colors.cardBorder),
        shadowElevation = 4.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            colors.accentContainer.copy(alpha = 0.72f),
                            colors.cardStrong,
                            colors.cardMuted,
                        ),
                    ),
                ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = heroTitle,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontSize = 28.sp,
                                lineHeight = 33.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-0.5).sp,
                            ),
                        )
                        Text(
                            text = heroDescription,
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
                        HeroMetaPill(
                            modifier = Modifier.weight(1f),
                            label = "Root",
                            value = when {
                                status == null -> "加载中"
                                rootReady -> "已就绪"
                                else -> "受限"
                            },
                            colors = colors,
                            tone = if (rootReady) colors.positive else colors.danger,
                        )
                        HeroMetaPill(
                            modifier = Modifier.weight(1f),
                            label = "自启",
                            value = if (autostartEnabled) "已开启" else "未开启",
                            colors = colors,
                            tone = if (autostartEnabled) colors.accent else colors.subtleText,
                        )
                    }
                }
                Surface(
                    onClick = onToggle,
                    enabled = canToggle,
                    modifier = Modifier.size(92.dp),
                    shape = CircleShape,
                    color = controlContainer,
                    border = BorderStroke(1.dp, colors.mutedBorder),
                    shadowElevation = 1.dp,
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(74.dp)
                                .clip(CircleShape)
                                .background(controlColor.copy(alpha = 0.08f))
                                .border(1.dp, controlColor.copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                if (busy) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.4.dp,
                                        color = controlColor,
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.PowerSettingsNew,
                                        contentDescription = null,
                                        tint = controlColor,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = controlLabel,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = controlColor,
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
private fun HeroMetaPill(
    label: String,
    value: String,
    colors: OverviewColors,
    tone: Color? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.chip)
            .border(1.dp, colors.chipBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tone?.let {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(it),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.subtleText,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HomeQuickActionsCard(
    running: Boolean,
    activeCore: CoreRecord?,
    updateAvailable: Boolean,
    colors: OverviewColors,
    onRefreshOrRestart: () -> Unit,
    onOpenCoreHub: () -> Unit,
    onCheckUpdates: () -> Unit,
    busy: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HomeQuickActionButton(
            modifier = Modifier.weight(1f),
            icon = if (running) Icons.Filled.RestartAlt else Icons.Filled.Refresh,
            label = if (running) "重启" else "刷新",
            enabled = !busy,
            colors = colors,
            onClick = onRefreshOrRestart,
        )
        HomeQuickActionButton(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.CloudDownload,
            label = "核心",
            enabled = !busy,
            colors = colors,
            onClick = onOpenCoreHub,
            selected = activeCore != null,
        )
        HomeQuickActionButton(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.SystemUpdateAlt,
            label = "更新",
            enabled = !busy,
            colors = colors,
            emphasized = updateAvailable,
            onClick = onCheckUpdates,
        )
    }
}

@Composable
private fun HomeMetricCard(
    label: String,
    value: String,
    colors: OverviewColors,
    modifier: Modifier = Modifier,
    monospace: Boolean = false,
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
            verticalArrangement = Arrangement.spacedBy(3.dp),
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
}

@Composable
private fun HomeQuickActionButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    colors: OverviewColors,
    onClick: () -> Unit,
    emphasized: Boolean = false,
    selected: Boolean = false,
) {
    Surface(
        modifier = modifier,
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(22.dp),
        color = when {
            !enabled -> colors.disabledContainer
            emphasized -> colors.dangerContainer
            selected -> colors.accentContainer
            else -> colors.cardStrong
        },
        border = BorderStroke(
            1.dp,
            when {
                emphasized -> colors.danger.copy(alpha = 0.14f)
                selected -> colors.accent.copy(alpha = 0.14f)
                else -> colors.cardBorder
            },
        ),
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(
                        if (!enabled) {
                            colors.cardStrong
                        } else if (emphasized) {
                            colors.danger.copy(alpha = 0.10f)
                        } else {
                            colors.cardStrong
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = if (!enabled) {
                        colors.subtleText
                    } else if (emphasized) {
                        colors.danger
                    } else {
                        colors.accent
                    },
                )
            }
            Text(
                modifier = Modifier.weight(1f),
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else colors.subtleText,
            )
            if (selected || emphasized) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (emphasized) colors.danger else colors.accent),
                )
            }
        }
    }
}

@Composable
private fun HomeAccessCard(
    localAccessUrl: String,
    lanAccessUrl: String?,
    autostartEnabled: Boolean,
    autostartClickable: Boolean,
    colors: OverviewColors,
    onCopyLocal: () -> Unit,
    onCopyLan: () -> Unit,
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
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "访问地址",
                        style = MaterialTheme.typography.labelMedium,
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
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AccessAddressRow(
                    title = "本机",
                    value = localAccessUrl,
                    colors = colors,
                    onCopy = onCopyLocal,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.cardBorder),
                )
                AccessAddressRow(
                    title = "局域网",
                    value = lanAccessUrl ?: "未发现局域网地址",
                    colors = colors,
                    enabled = lanAccessUrl != null,
                    emphasize = lanAccessUrl != null,
                    onCopy = onCopyLan,
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
                text = if (checked) "自启动 开" else "自启动 关",
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

private fun buildHeaderSubtitle(
    coreName: String,
    running: Boolean,
    pid: String?,
    autostartEnabled: Boolean,
): String {
    val stateLabel = when {
        running && !pid.isNullOrBlank() -> "PID $pid"
        running -> "服务已就绪"
        autostartEnabled -> "等待拉起"
        else -> "待手动启动"
    }
    return "$coreName · $stateLabel"
}

private fun maskToken(token: String): String {
    return when {
        token.length <= 4 -> token
        token.length <= 8 -> token.take(2) + "••" + token.takeLast(2)
        else -> token.take(4) + "••••" + token.takeLast(2)
    }
}

private fun isAutostartEnabled(raw: String?): Boolean {
    return raw == "1" || raw.equals("true", ignoreCase = true) || raw.equals("enabled", ignoreCase = true)
}

private fun CoreRecord?.shortDisplayName(): String {
    val source = this?.repoDisplayName?.takeIf { it.isNotBlank() } ?: return "还没有活动核心"
    return source.substringAfterLast('/')
}

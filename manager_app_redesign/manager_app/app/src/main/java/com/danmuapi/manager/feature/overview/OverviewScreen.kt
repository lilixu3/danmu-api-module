@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.danmuapi.manager.feature.overview

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danmuapi.manager.app.state.ManagerViewModel
import com.danmuapi.manager.core.designsystem.theme.DanmuMonoFamily
import com.danmuapi.manager.core.designsystem.theme.ImmersivePalette
import com.danmuapi.manager.core.designsystem.theme.rememberImmersivePalette
import com.danmuapi.manager.core.util.rememberLanIpv4Addresses
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

private typealias OverviewColors = ImmersivePalette

private data class OverviewMetricSpec(
    val label: String,
    val value: String,
    val ringLabel: String,
    val progress: Float,
    val tone: Color,
)

@Suppress("UNUSED_PARAMETER")
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
    val activeCore = status?.activeCore ?: remember(status, viewModel.cores) {
        val activeId = status?.activeCoreId ?: viewModel.cores?.activeCoreId
        viewModel.cores?.cores.orEmpty().firstOrNull { it.id == activeId }
    }
    var tokenVisible by rememberSaveable { mutableStateOf(false) }

    val localAccessUrl = remember(viewModel.apiPort, viewModel.apiToken) {
        "http://127.0.0.1:${viewModel.apiPort}/${viewModel.apiToken}"
    }
    val primaryLanAccessUrl = remember(lanIpv4Addresses, viewModel.apiPort, viewModel.apiToken) {
        lanIpv4Addresses.firstOrNull()?.let { "http://$it:${viewModel.apiPort}/${viewModel.apiToken}" }
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
        ?: viewModel.moduleUpdateInfo?.currentVersion?.takeIf { it.isNotBlank() }
        ?: "--"
    val liveElapsedSeconds = rememberLiveElapsedSeconds(
        running = running,
        pid = status?.service?.pid,
        baseElapsedSeconds = viewModel.serviceElapsedSeconds,
    )
    val runtimeSummaryItems = remember(
        running,
        status?.service?.pid,
        activeCore?.version,
        liveElapsedSeconds,
    ) {
        OverviewRuntimeSummaryFormatter.buildItems(
            running = running,
            pid = status?.service?.pid,
            coreVersion = activeCore?.version,
            elapsedSeconds = liveElapsedSeconds,
        )
    }

    val metrics = listOf(
        OverviewMetricSpec(
            label = "今日请求",
            value = viewModel.todayReqNum.toString(),
            ringLabel = "24H",
            progress = 0.78f,
            tone = colors.accent,
        ),
        OverviewMetricSpec(
            label = "模块版本",
            value = moduleVersion,
            ringLabel = "V",
            progress = 0.58f,
            tone = Color(0xFF7C3AED),
        ),
        OverviewMetricSpec(
            label = "Root 加载",
            value = if (rootReady) "已就绪" else "受限",
            ringLabel = "SU",
            progress = if (rootReady) 0.88f else 0.34f,
            tone = if (rootReady) Color(0xFF16A34A) else colors.danger,
        ),
        OverviewMetricSpec(
            label = "核心状态",
            value = if (activeCore != null) "已加载" else "未加载",
            ringLabel = "核",
            progress = if (activeCore != null) 0.66f else 0.28f,
            tone = Color(0xFFD97706),
        ),
    )

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
                .padding(bottom = contentPadding.calculateBottomPadding() + 24.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
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

                HomeStatusCard(
                    running = running,
                    rootReady = rootReady,
                    hasActiveCore = activeCore != null,
                    port = viewModel.apiPort,
                    colors = colors,
                )

                HomeSummaryGrid(
                    metrics = metrics,
                    colors = colors,
                )

                HomeActionStrip(
                    running = running,
                    rootReady = rootReady,
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
                    tokenVisible = tokenVisible,
                    colors = colors,
                    onCopyLocal = { clipboardManager.setText(AnnotatedString(localAccessUrl)) },
                    onCopyLan = {
                        primaryLanAccessUrl?.let { clipboardManager.setText(AnnotatedString(it)) }
                    },
                    onToggleTokenVisibility = { tokenVisible = !tokenVisible },
                )

                HomeRuntimeSummaryCard(
                    items = runtimeSummaryItems,
                    colors = colors,
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
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = 24.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp,
            ),
            color = MaterialTheme.colorScheme.onBackground,
        )
        Surface(
            onClick = onOpenSettings,
            shape = CircleShape,
            color = colors.card,
            border = BorderStroke(1.dp, colors.cardBorder),
            shadowElevation = 0.dp,
        ) {
            Box(
                modifier = Modifier.size(38.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "设置",
                    modifier = Modifier.size(18.dp),
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
        shape = RoundedCornerShape(18.dp),
        color = colors.cardStrong,
        border = BorderStroke(1.dp, colors.cardBorder),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                ),
                color = colors.subtleText,
            )
        }
    }
}

@Composable
private fun HomeStatusCard(
    running: Boolean,
    rootReady: Boolean,
    hasActiveCore: Boolean,
    port: Int,
    colors: OverviewColors,
) {
    val title = OverviewSummaryFormatter.serviceTitle(
        running = running,
        rootReady = rootReady,
        hasActiveCore = hasActiveCore,
    )
    val detail = OverviewSummaryFormatter.serviceSummary(
        running = running,
        rootReady = rootReady,
        hasActiveCore = hasActiveCore,
        port = port,
    )
    val orbLabel = OverviewSummaryFormatter.serviceBadge(
        running = running,
        rootReady = rootReady,
        hasActiveCore = hasActiveCore,
    )
    val orbTone = when {
        !rootReady -> colors.danger
        !hasActiveCore -> Color(0xFFD97706)
        running -> colors.accent
        else -> colors.subtleText
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = colors.cardMuted,
        border = BorderStroke(1.dp, colors.cardBorder),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = "服务状态",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = colors.subtleText,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 22.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.Black,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    ),
                    color = colors.subtleText,
                )
            }
            StatusOrb(
                label = orbLabel,
                tone = orbTone,
                colors = colors,
            )
        }
    }
}

@Composable
private fun StatusOrb(
    label: String,
    tone: Color,
    colors: OverviewColors,
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(tone.copy(alpha = 0.12f))
            .padding(1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(colors.cardStrong)
                .padding(1.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(tone.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                    ),
                    color = tone,
                )
            }
        }
    }
}

@Composable
private fun HomeSummaryGrid(
    metrics: List<OverviewMetricSpec>,
    colors: OverviewColors,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        metrics.chunked(2).forEach { rowMetrics ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowMetrics.forEach { metric ->
                    OverviewMetricCard(
                        modifier = Modifier.weight(1f),
                        metric = metric,
                        colors = colors,
                    )
                }
                if (rowMetrics.size == 1) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun OverviewMetricCard(
    metric: OverviewMetricSpec,
    colors: OverviewColors,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = colors.cardMuted,
        border = BorderStroke(1.dp, colors.cardBorder),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = metric.label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = colors.subtleText,
                )
                Text(
                    text = metric.value,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            OrbitRing(
                label = metric.ringLabel,
                progress = metric.progress,
                tone = metric.tone,
                colors = colors,
            )
        }
    }
}

@Composable
private fun OrbitRing(
    label: String,
    progress: Float,
    tone: Color,
    colors: OverviewColors,
) {
    val transition = rememberInfiniteTransition(label = "overviewMetricRing")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "orbitAngle",
    )

    Box(
        modifier = Modifier.size(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 2.8.dp.toPx()
            drawArc(
                color = colors.cardBorder,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            drawArc(
                color = tone,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )

            val radius = (size.minDimension / 2f) - strokeWidth / 2f
            val radians = Math.toRadians((angle - 90f).toDouble())
            val point = Offset(
                x = center.x + radius * cos(radians).toFloat(),
                y = center.y + radius * sin(radians).toFloat(),
            )
            drawCircle(
                color = tone,
                radius = 2.2.dp.toPx(),
                center = point,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 7.sp,
                fontWeight = FontWeight.Black,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun HomeActionStrip(
    running: Boolean,
    rootReady: Boolean,
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
    val primaryLabel = when {
        busy -> "处理中"
        running -> "停止服务"
        else -> "启动服务"
    }
    val primaryColor = if (running) colors.danger else colors.accent

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = colors.cardMuted,
        border = BorderStroke(1.dp, colors.cardBorder),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FlatActionButton(
                modifier = Modifier.weight(1.12f),
                label = primaryLabel,
                enabled = canToggle,
                colors = colors,
                containerColor = if (canToggle) primaryColor else colors.disabledContainer,
                contentColor = if (canToggle) Color.White else colors.subtleText,
                showProgress = busy,
                onClick = onToggle,
            )
            FlatActionButton(
                modifier = Modifier.weight(0.94f),
                label = "重启",
                enabled = canRestart,
                colors = colors,
                icon = Icons.Filled.RestartAlt,
                onClick = onRestart,
            )
            FlatActionButton(
                modifier = Modifier.weight(0.94f),
                label = "刷新",
                enabled = canRefresh,
                colors = colors,
                icon = Icons.Filled.Refresh,
                onClick = onRefresh,
            )
        }
    }
}

@Composable
private fun FlatActionButton(
    label: String,
    enabled: Boolean,
    colors: OverviewColors,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    containerColor: Color = if (enabled) colors.cardStrong else colors.disabledContainer,
    contentColor: Color = if (enabled) MaterialTheme.colorScheme.onSurface else colors.subtleText,
    showProgress: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier,
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = BorderStroke(1.dp, if (enabled) colors.cardBorder else colors.mutedBorder),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                showProgress -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = contentColor,
                    )
                }

                icon != null -> {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = contentColor,
                    )
                }
            }
            if (showProgress || icon != null) {
                Box(modifier = Modifier.size(6.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = contentColor,
            )
        }
    }
}

@Composable
private fun HomeAccessCard(
    localAccessUrl: String,
    lanAccessUrl: String?,
    tokenVisible: Boolean,
    colors: OverviewColors,
    onCopyLocal: () -> Unit,
    onCopyLan: () -> Unit,
    onToggleTokenVisibility: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = colors.cardMuted,
        border = BorderStroke(1.dp, colors.cardBorder),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "访问地址",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = colors.subtleText,
                )
                Surface(
                    onClick = onToggleTokenVisibility,
                    shape = CircleShape,
                    color = colors.cardStrong,
                    border = BorderStroke(1.dp, colors.cardBorder),
                    shadowElevation = 0.dp,
                ) {
                    Box(
                        modifier = Modifier.size(26.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (tokenVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = if (tokenVisible) "隐藏 Token" else "显示 Token",
                            modifier = Modifier.size(14.dp),
                            tint = colors.subtleText,
                        )
                    }
                }
            }

            AccessAddressRow(
                title = "本机地址",
                value = localAccessUrl,
                colors = colors,
                enabled = true,
                onCopy = onCopyLocal,
            )

            DividerLine(colors)

            AccessAddressRow(
                title = "局域网地址",
                value = lanAccessUrl ?: "当前未检测到局域网地址",
                colors = colors,
                enabled = lanAccessUrl != null,
                onCopy = onCopyLan,
            )
        }
    }
}

@Composable
private fun HomeRuntimeSummaryCard(
    items: List<OverviewRuntimeSummaryItem>,
    colors: OverviewColors,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = colors.cardMuted,
        border = BorderStroke(1.dp, colors.cardBorder),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "运行摘要",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.2.sp,
                ),
                color = colors.subtleText,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items.forEach { item ->
                    RuntimeSummaryValueCard(
                        modifier = Modifier.weight(1f),
                        item = item,
                        colors = colors,
                    )
                }
            }
        }
    }
}

@Composable
private fun RuntimeSummaryValueCard(
    item: OverviewRuntimeSummaryItem,
    colors: OverviewColors,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = colors.cardStrong,
        border = BorderStroke(1.dp, colors.cardBorder),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = item.label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = colors.subtleText,
            )
            Text(
                text = item.value,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun AccessAddressRow(
    title: String,
    value: String,
    colors: OverviewColors,
    enabled: Boolean,
    onCopy: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = if (enabled) MaterialTheme.colorScheme.onSurface else colors.subtleText,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    fontFamily = DanmuMonoFamily,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = if (enabled) colors.subtleText else colors.subtleText.copy(alpha = 0.88f),
            )
        }
        Surface(
            onClick = onCopy,
            enabled = enabled,
            shape = RoundedCornerShape(12.dp),
            color = if (enabled) colors.cardStrong else colors.disabledContainer,
            border = BorderStroke(1.dp, colors.cardBorder),
            shadowElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = if (enabled) colors.accent else colors.subtleText,
                )
                Text(
                    text = "复制",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = if (enabled) colors.accent else colors.subtleText,
                )
            }
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
private fun rememberOverviewColors(): OverviewColors = rememberImmersivePalette()

@Composable
private fun rememberLiveElapsedSeconds(
    running: Boolean,
    pid: String?,
    baseElapsedSeconds: Long?,
): Long? {
    val liveElapsed by produceState<Long?>(
        initialValue = baseElapsedSeconds,
        running,
        pid,
        baseElapsedSeconds,
    ) {
        if (!running || pid.isNullOrBlank()) {
            value = null
            return@produceState
        }

        val startedAtMs = System.currentTimeMillis()
        val base = baseElapsedSeconds ?: 0L
        while (true) {
            val deltaSeconds = ((System.currentTimeMillis() - startedAtMs) / 1000L).coerceAtLeast(0L)
            value = base + deltaSeconds
            delay(1_000L)
        }
    }
    return liveElapsed
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

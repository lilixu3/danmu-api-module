@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.danmuapi.manager.feature.corehub

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danmuapi.manager.app.state.ManagerViewModel
import com.danmuapi.manager.core.designsystem.component.formatSizeLabel
import com.danmuapi.manager.core.designsystem.theme.DanmuMonoFamily
import com.danmuapi.manager.core.model.CoreRecord
import com.danmuapi.manager.core.model.CoreUpdateInfo
import com.danmuapi.manager.core.model.CoreUpdateState

private enum class CoreFilter(val label: String) {
    All("全部"),
    Active("当前"),
    Updates("可更新"),
}

private data class CoreHubColors(
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
    val warning: Color,
    val warningContainer: Color,
    val danger: Color,
    val dangerContainer: Color,
    val disabledContainer: Color,
    val cardMuted: Color,
    val chip: Color,
    val chipBorder: Color,
)

private data class InstallPreset(
    val label: String,
    val repo: String,
    val ref: String,
)

@Composable
fun CoreHubScreen(
    contentPadding: PaddingValues,
    viewModel: ManagerViewModel,
    onOpenCore: (String) -> Unit,
) {
    val colors = rememberCoreHubColors()
    val allCores = viewModel.cores?.cores.orEmpty()
    val activeId = viewModel.cores?.activeCoreId ?: viewModel.status?.activeCoreId
    val activeCore = remember(allCores, activeId) { allCores.firstOrNull { it.id == activeId } }
    val activeUpdate = activeCore?.id?.let(viewModel.updateInfo::get)
    val updateCount = remember(allCores, viewModel.updateInfo) {
        allCores.count { core -> viewModel.updateInfo[core.id]?.updateAvailable == true }
    }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf(CoreFilter.All) }
    var repoInput by rememberSaveable { mutableStateOf("huangxd-/danmu_api") }
    var refInput by rememberSaveable { mutableStateOf("main") }
    var showInstallSheet by rememberSaveable { mutableStateOf(false) }

    val filteredCores = remember(allCores, activeId, selectedFilter, query, viewModel.updateInfo) {
        allCores.filter { core ->
            val matchesQuery = query.isBlank() ||
                core.repo.contains(query, ignoreCase = true) ||
                core.ref.contains(query, ignoreCase = true) ||
                core.version.orEmpty().contains(query, ignoreCase = true) ||
                core.commitLabel.orEmpty().contains(query, ignoreCase = true)
            val matchesFilter = when (selectedFilter) {
                CoreFilter.All -> true
                CoreFilter.Active -> core.id == activeId
                CoreFilter.Updates -> viewModel.updateInfo[core.id]?.updateAvailable == true
            }
            matchesQuery && matchesFilter
        }.sortedWith(
            compareByDescending<CoreRecord> { it.id == activeId }
                .thenByDescending { viewModel.updateInfo[it.id]?.updateAvailable == true }
                .thenBy { it.repo.lowercase() }
                .thenBy { it.ref.lowercase() },
        )
    }

    if (showInstallSheet) {
        ModalBottomSheet(
            onDismissRequest = { showInstallSheet = false },
            containerColor = colors.cardStrong,
        ) {
            InstallCoreSheet(
                repoInput = repoInput,
                onRepoChange = { repoInput = it },
                refInput = refInput,
                onRefChange = { refInput = it },
                colors = colors,
                onInstall = {
                    showInstallSheet = false
                    viewModel.installCore(repoInput, refInput.ifBlank { "main" })
                },
                onUsePreset = { repo, ref ->
                    repoInput = repo
                    refInput = ref
                },
                onCheckUpdates = viewModel::checkUpdates,
            )
        }
    }

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
                .offset(x = 212.dp, y = (-56).dp)
                .size(224.dp)
                .clip(CircleShape)
                .background(colors.haloPrimary),
        )
        Box(
            modifier = Modifier
                .offset(x = (-64).dp, y = 168.dp)
                .size(180.dp)
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
                    .padding(horizontal = 20.dp, vertical = 18.dp)
                    .widthIn(max = 860.dp)
                    .align(Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CoreHubHeader(
                    subtitle = buildHubHeaderSubtitle(activeCore, allCores.size),
                    updateCount = updateCount,
                    colors = colors,
                )

                viewModel.busyMessage?.takeIf { it.isNotBlank() }?.let { message ->
                    HubBusyStrip(
                        message = message,
                        colors = colors,
                    )
                }

                CurrentCoreHeroCard(
                    core = activeCore,
                    allCoreCount = allCores.size,
                    updateInfo = activeUpdate,
                    busy = viewModel.busy,
                    colors = colors,
                    onPrimaryAction = {
                        if (activeCore == null) {
                            showInstallSheet = true
                        } else {
                            viewModel.installCore(activeCore.repo, activeCore.ref)
                        }
                    },
                    onOpenDetail = { activeCore?.id?.let(onOpenCore) },
                    onOpenInstall = { showInstallSheet = true },
                )

                HubActionRow(
                    busy = viewModel.busy,
                    colors = colors,
                    onCheckUpdates = viewModel::checkUpdates,
                    onShowInstall = { showInstallSheet = true },
                )

                InstalledCorePanel(
                    cores = filteredCores,
                    allCount = allCores.size,
                    activeId = activeId,
                    query = query,
                    onQueryChange = { query = it },
                    selectedFilter = selectedFilter,
                    onFilterChange = { selectedFilter = it },
                    updateInfo = viewModel.updateInfo,
                    colors = colors,
                    onOpenCore = onOpenCore,
                )
            }
        }
    }
}

@Composable
fun CoreDetailScreen(
    contentPadding: PaddingValues,
    viewModel: ManagerViewModel,
    coreId: String,
) {
    val colors = rememberCoreHubColors()
    val allCores = viewModel.cores?.cores.orEmpty()
    val activeId = viewModel.cores?.activeCoreId ?: viewModel.status?.activeCoreId
    val core = remember(allCores, coreId) { allCores.firstOrNull { it.id == coreId } }
    val isActive = core?.id == activeId
    val updateInfo = core?.id?.let(viewModel.updateInfo::get)
    var deleteTarget by remember { mutableStateOf<CoreRecord?>(null) }

    if (deleteTarget != null) {
        val target = deleteTarget!!
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除核心") },
            text = { Text("确认删除 ${target.repoDisplayName}@${target.ref}？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget = null
                        viewModel.deleteCore(target.id)
                    },
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("取消")
                }
            },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        colors.backdropMid,
                        colors.backdropBottom,
                    ),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .padding(horizontal = 20.dp, vertical = 18.dp)
                .widthIn(max = 760.dp)
                .align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (core == null) {
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
                            .padding(horizontal = 20.dp, vertical = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "没有找到这个核心",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        )
                        Text(
                            text = "它可能已经被删除，或者列表数据还没有刷新。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.subtleText,
                        )
                    }
                }
            } else {
                CoreDetailSummaryCard(
                    core = core,
                    isActive = isActive,
                    updateInfo = updateInfo,
                    colors = colors,
                )

                CoreInfoCard(
                    core = core,
                    updateInfo = updateInfo,
                    colors = colors,
                )

                CoreActionCard(
                    core = core,
                    isActive = isActive,
                    updateInfo = updateInfo,
                    busy = viewModel.busy,
                    colors = colors,
                    onInstall = { viewModel.installCore(core.repo, core.ref) },
                    onActivate = { viewModel.activateCore(core.id) },
                )

                DangerActionCard(
                    core = core,
                    colors = colors,
                    onDelete = { deleteTarget = core },
                )
            }
        }
    }
}

@Composable
private fun CoreHubHeader(
    subtitle: String,
    updateCount: Int,
    colors: CoreHubColors,
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
                text = "核心",
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
            )
        }
        HubStatusPill(
            text = if (updateCount > 0) "$updateCount 个可更新" else "已对齐",
            toneColor = if (updateCount > 0) colors.warning else colors.positive,
            containerColor = if (updateCount > 0) colors.warningContainer else colors.positiveContainer,
        )
    }
}

@Composable
private fun HubStatusPill(
    text: String,
    toneColor: Color,
    containerColor: Color,
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(containerColor)
            .border(1.dp, toneColor.copy(alpha = 0.10f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(toneColor),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = toneColor,
        )
    }
}

@Composable
private fun HubBusyStrip(
    message: String,
    colors: CoreHubColors,
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
private fun CurrentCoreHeroCard(
    core: CoreRecord?,
    allCoreCount: Int,
    updateInfo: CoreUpdateInfo?,
    busy: Boolean,
    colors: CoreHubColors,
    onPrimaryAction: () -> Unit,
    onOpenDetail: () -> Unit,
    onOpenInstall: () -> Unit,
) {
    val hasCore = core != null
    val updateAvailable = updateInfo?.updateAvailable == true
    val updateUnknown = updateInfo != null && updateInfo.state == CoreUpdateState.Unknown
    val primaryLabel = when {
        !hasCore -> "安装核心"
        updateAvailable -> "安装更新"
        else -> "重新安装"
    }
    val title = core?.shortDisplayName() ?: "先安装一个核心"
    val subtitle = when {
        core == null && allCoreCount == 0 -> "当前还没有已安装核心。先添加一个，再继续管理和切换。"
        core == null -> "已经安装 $allCoreCount 个核心，但当前还没有活动项。先选择一个，或直接安装新的核心。"
        updateAvailable -> "发现新版本，主操作会按当前仓库与分支拉取并覆盖安装。"
        else -> "当前核心已经就绪，点卡片可以进入详情页，查看完整信息和危险操作。"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            if (hasCore) onOpenDetail() else onOpenInstall()
        },
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
                            colors.accentContainer.copy(alpha = 0.76f),
                            colors.cardStrong,
                            colors.cardMuted,
                        ),
                    ),
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HeroTag(
                        text = if (hasCore) "当前核心" else "未选择核心",
                        color = if (hasCore) colors.accent else colors.subtleText,
                        container = if (hasCore) colors.accentContainer else colors.chip,
                    )
                    if (updateAvailable) {
                        HeroTag(
                            text = "可更新",
                            color = colors.warning,
                            container = colors.warningContainer,
                        )
                    } else if (updateUnknown) {
                        HeroTag(
                            text = "未确认",
                            color = colors.subtleText,
                            container = colors.chip,
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 30.sp,
                            lineHeight = 34.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.5).sp,
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        ),
                        color = colors.subtleText,
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HeroMetaTag(
                        label = if (core == null) "已安装" else "仓库",
                        value = if (core == null) "$allCoreCount 个" else core.repoDisplayName,
                        colors = colors,
                    )
                    HeroMetaTag(
                        label = if (core == null) "建议" else "分支",
                        value = if (core == null) "main" else core.ref,
                        colors = colors,
                    )
                    HeroMetaTag(
                        label = if (updateAvailable) "最新" else "版本",
                        value = buildVersionLabel(core, updateInfo),
                        colors = colors,
                    )
                }

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
                            text = if (hasCore) "点卡片可进入详情页" else "支持 owner/repo + 分支 / tag / commit",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.subtleText,
                        )
                        Text(
                            text = if (hasCore) {
                                if (updateAvailable) {
                                    "建议先安装更新，再继续切换或删除。"
                                } else {
                                    "主操作会对当前仓库重新执行安装。"
                                }
                            } else {
                                "推荐直接使用预设仓库开始。"
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        )
                    }
                    PrimaryActionButton(
                        label = if (busy) "处理中" else primaryLabel,
                        icon = if (updateAvailable) Icons.Filled.SystemUpdateAlt else Icons.Filled.CloudDownload,
                        busy = busy,
                        enabled = !busy,
                        colors = colors,
                        onClick = onPrimaryAction,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroTag(
    text: String,
    color: Color,
    container: Color,
) {
    Text(
        text = text,
        modifier = Modifier
            .clip(CircleShape)
            .background(container)
            .border(1.dp, color.copy(alpha = 0.08f), CircleShape)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color = color,
    )
}

@Composable
private fun HeroMetaTag(
    label: String,
    value: String,
    colors: CoreHubColors,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(colors.chip)
            .border(1.dp, colors.chipBorder, RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.subtleText,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HubActionRow(
    busy: Boolean,
    colors: CoreHubColors,
    onCheckUpdates: () -> Unit,
    onShowInstall: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HubActionButton(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.Refresh,
            label = "检查全部更新",
            enabled = !busy,
            colors = colors,
            onClick = onCheckUpdates,
        )
        HubActionButton(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.CloudDownload,
            label = "安装新核心",
            enabled = !busy,
            colors = colors,
            emphasized = true,
            onClick = onShowInstall,
        )
    }
}

@Composable
private fun HubActionButton(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    colors: CoreHubColors,
    emphasized: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier,
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(24.dp),
        color = when {
            !enabled -> colors.disabledContainer
            emphasized -> colors.accentContainer
            else -> colors.cardStrong
        },
        border = BorderStroke(
            1.dp,
            when {
                emphasized -> colors.accent.copy(alpha = 0.14f)
                else -> colors.cardBorder
            },
        ),
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(colors.cardStrong)
                    .border(1.dp, colors.cardBorder, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) colors.accent else colors.subtleText,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else colors.subtleText,
            )
        }
    }
}

@Composable
private fun InstalledCorePanel(
    cores: List<CoreRecord>,
    allCount: Int,
    activeId: String?,
    query: String,
    onQueryChange: (String) -> Unit,
    selectedFilter: CoreFilter,
    onFilterChange: (CoreFilter) -> Unit,
    updateInfo: Map<String, CoreUpdateInfo>,
    colors: CoreHubColors,
    onOpenCore: (String) -> Unit,
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "已安装核心",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        text = if (allCount == 0) "点条目进入详情页，危险操作都收进详情里。" else "保留清晰列表，不堆叠大卡片。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.subtleText,
                    )
                }
                HubCountBadge(
                    value = "$allCount 个",
                    colors = colors,
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = colors.subtleText,
                    )
                },
                placeholder = {
                    Text(
                        text = "搜索仓库、分支、版本或提交",
                        color = colors.subtleText,
                    )
                },
                shape = RoundedCornerShape(22.dp),
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CoreFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = filter == selectedFilter,
                        onClick = { onFilterChange(filter) },
                        label = { Text(filter.label) },
                    )
                }
            }

            if (cores.isEmpty()) {
                EmptyCoreState(
                    query = query,
                    colors = colors,
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    cores.forEachIndexed { index, core ->
                        CoreListRow(
                            core = core,
                            isActive = core.id == activeId,
                            updateInfo = updateInfo[core.id],
                            colors = colors,
                            onClick = { onOpenCore(core.id) },
                        )
                        if (index != cores.lastIndex) {
                            HorizontalDivider(color = colors.cardBorder)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HubCountBadge(
    value: String,
    colors: CoreHubColors,
) {
    Text(
        text = value,
        modifier = Modifier
            .clip(CircleShape)
            .background(colors.chip)
            .border(1.dp, colors.chipBorder, CircleShape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color = colors.subtleText,
    )
}

@Composable
private fun EmptyCoreState(
    query: String,
    colors: CoreHubColors,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = colors.cardMuted,
        border = BorderStroke(1.dp, colors.cardBorder),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "没有匹配的核心",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                text = if (query.isBlank()) {
                    "当前筛选条件下没有内容，试试切回全部，或者直接安装新的核心。"
                } else {
                    "换个关键词试试，或者清空搜索后重新浏览。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.subtleText,
            )
        }
    }
}

@Composable
private fun CoreListRow(
    core: CoreRecord,
    isActive: Boolean,
    updateInfo: CoreUpdateInfo?,
    colors: CoreHubColors,
    onClick: () -> Unit,
) {
    val updateAvailable = updateInfo?.updateAvailable == true
    val updateUnknown = updateInfo != null && updateInfo.state == CoreUpdateState.Unknown
    val rowColor = when {
        isActive -> colors.accentContainer.copy(alpha = 0.72f)
        updateAvailable -> colors.warningContainer.copy(alpha = 0.85f)
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(rowColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = core.repoDisplayName,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${core.ref} · ${buildVersionLabel(core, updateInfo = null)}",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.subtleText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
            StateBadge(
                text = when {
                    isActive -> "当前"
                    updateAvailable -> "更新"
                    updateUnknown -> "未确认"
                    else -> "详情"
                },
                color = when {
                    isActive -> colors.accent
                    updateAvailable -> colors.warning
                else -> colors.subtleText
            },
            container = when {
                isActive -> colors.accentContainer
                updateAvailable -> colors.warningContainer
                else -> colors.chip
            },
        )
    }
}

@Composable
private fun StateBadge(
    text: String,
    color: Color,
    container: Color,
) {
    Text(
        text = text,
        modifier = Modifier
            .clip(CircleShape)
            .background(container)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color = color,
    )
}

@Composable
private fun InstallCoreSheet(
    repoInput: String,
    onRepoChange: (String) -> Unit,
    refInput: String,
    onRefChange: (String) -> Unit,
    colors: CoreHubColors,
    onInstall: () -> Unit,
    onUsePreset: (String, String) -> Unit,
    onCheckUpdates: () -> Unit,
) {
    val presets = remember {
        listOf(
            InstallPreset("主线", "huangxd-/danmu_api", "main"),
            InstallPreset("开发", "lilixu3/danmu_api", "main"),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "安装新核心",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        )
        Text(
            text = "主线默认走稳定镜像仓库，开发预设切到主仓库 main。仓库和分支填好后直接安装。",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.subtleText,
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            presets.forEach { preset ->
                Surface(
                    onClick = { onUsePreset(preset.repo, preset.ref) },
                    shape = RoundedCornerShape(18.dp),
                    color = colors.chip,
                    border = BorderStroke(1.dp, colors.chipBorder),
                    shadowElevation = 0.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = preset.label,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        )
                        Text(
                            text = "${preset.repo}@${preset.ref}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.subtleText,
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = repoInput,
            onValueChange = onRepoChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("仓库 owner/repo") },
            shape = RoundedCornerShape(22.dp),
        )
        OutlinedTextField(
            value = refInput,
            onValueChange = onRefChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("分支 / Tag / Commit") },
            shape = RoundedCornerShape(22.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PrimaryActionButton(
                modifier = Modifier.weight(1f),
                label = "下载并安装",
                icon = Icons.Filled.CloudDownload,
                busy = false,
                enabled = repoInput.isNotBlank(),
                colors = colors,
                onClick = onInstall,
            )
            Surface(
                modifier = Modifier.weight(1f),
                onClick = onCheckUpdates,
                shape = RoundedCornerShape(22.dp),
                color = colors.cardMuted,
                border = BorderStroke(1.dp, colors.cardBorder),
                shadowElevation = 0.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = "检查全部更新",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
    }
}

@Composable
private fun CoreDetailSummaryCard(
    core: CoreRecord,
    isActive: Boolean,
    updateInfo: CoreUpdateInfo?,
    colors: CoreHubColors,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        color = colors.cardStrong,
        border = BorderStroke(1.dp, colors.cardBorder),
        shadowElevation = 2.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            colors.accentContainer.copy(alpha = 0.70f),
                            colors.cardStrong,
                        ),
                    ),
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HeroTag(
                        text = if (isActive) "当前正在使用" else "已安装核心",
                        color = if (isActive) colors.accent else colors.subtleText,
                        container = if (isActive) colors.accentContainer else colors.chip,
                    )
                if (updateInfo?.updateAvailable == true) {
                    HeroTag(
                        text = "可更新",
                        color = colors.warning,
                        container = colors.warningContainer,
                    )
                } else if (updateInfo?.state == CoreUpdateState.Unknown) {
                    HeroTag(
                        text = "未确认",
                        color = colors.subtleText,
                        container = colors.chip,
                    )
                }
            }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = core.repoDisplayName,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.3).sp,
                        ),
                    )
                    Text(
                        text = "${core.ref} · ${buildVersionLabel(core, updateInfo = null)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.subtleText,
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HeroMetaTag(
                        label = "提交",
                        value = core.commitLabel ?: "--",
                        colors = colors,
                    )
                    HeroMetaTag(
                        label = "安装时间",
                        value = core.installedAt ?: "--",
                        colors = colors,
                    )
                    HeroMetaTag(
                        label = "大小",
                        value = core.sizeBytes?.formatSizeLabel() ?: "--",
                        colors = colors,
                    )
                }
            }
        }
    }
}

@Composable
private fun CoreInfoCard(
    core: CoreRecord,
    updateInfo: CoreUpdateInfo?,
    colors: CoreHubColors,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
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
            Text(
                text = "核心信息",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
            CoreInfoRow(title = "仓库", value = core.repo, colors = colors)
            CoreInfoRow(title = "分支 / Ref", value = core.ref, colors = colors)
            CoreInfoRow(title = "版本", value = core.version ?: "--", colors = colors)
            CoreInfoRow(
                title = "提交",
                value = core.sha ?: core.commitLabel ?: "--",
                colors = colors,
                monospace = true,
            )
            CoreInfoRow(title = "安装时间", value = core.installedAt ?: "--", colors = colors)
            CoreInfoRow(
                title = "大小",
                value = core.sizeBytes?.formatSizeLabel() ?: "--",
                colors = colors,
            )
            CoreInfoRow(
                title = "更新状态",
                value = buildUpdateStatusLabel(updateInfo),
                colors = colors,
            )
        }
    }
}

@Composable
private fun CoreInfoRow(
    title: String,
    value: String,
    colors: CoreHubColors,
    monospace: Boolean = false,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = colors.subtleText,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.SemiBold,
                fontFamily = if (monospace) DanmuMonoFamily else null,
            ),
        )
    }
}

@Composable
private fun CoreActionCard(
    core: CoreRecord,
    isActive: Boolean,
    updateInfo: CoreUpdateInfo?,
    busy: Boolean,
    colors: CoreHubColors,
    onInstall: () -> Unit,
    onActivate: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
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
            Text(
                text = "操作",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                text = "把高频操作放在这里，删除单独放到底部，避免误触。",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.subtleText,
            )
            PrimaryActionButton(
                modifier = Modifier.fillMaxWidth(),
                label = when {
                    busy -> "处理中"
                    updateInfo?.updateAvailable == true -> "安装更新"
                    else -> "重新安装"
                },
                icon = if (updateInfo?.updateAvailable == true) Icons.Filled.SystemUpdateAlt else Icons.Filled.CloudDownload,
                busy = busy,
                enabled = !busy,
                colors = colors,
                onClick = onInstall,
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                onClick = onActivate,
                enabled = !busy && !isActive,
                shape = RoundedCornerShape(22.dp),
                color = if (isActive) colors.disabledContainer else colors.cardMuted,
                border = BorderStroke(1.dp, colors.cardBorder),
                shadowElevation = 0.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        tint = if (isActive) colors.subtleText else colors.accent,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = if (isActive) "当前正在使用" else "切换为当前核心",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isActive) colors.subtleText else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Text(
                text = "${core.repoDisplayName}@${core.ref}",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = DanmuMonoFamily),
                color = colors.subtleText,
            )
        }
    }
}

@Composable
private fun DangerActionCard(
    core: CoreRecord,
    colors: CoreHubColors,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = colors.dangerContainer,
        border = BorderStroke(1.dp, colors.danger.copy(alpha = 0.14f)),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "删除核心",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.danger,
                )
                Text(
                    text = "删除 ${core.repoDisplayName}@${core.ref} 后，需要重新安装或切换到其他核心。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.danger,
                )
            }
            Surface(
                onClick = onDelete,
                shape = CircleShape,
                color = colors.cardStrong,
                border = BorderStroke(1.dp, colors.danger.copy(alpha = 0.12f)),
                shadowElevation = 0.dp,
            ) {
                Box(
                    modifier = Modifier.size(42.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null,
                        tint = colors.danger,
                    )
                }
            }
        }
    }
}

@Composable
private fun PrimaryActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    busy: Boolean,
    enabled: Boolean,
    colors: CoreHubColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(22.dp),
        color = if (enabled) colors.accent else colors.disabledContainer,
        border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.14f)),
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = colors.cardStrong,
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.cardStrong,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = colors.cardStrong,
            )
        }
    }
}

@Composable
private fun rememberCoreHubColors(): CoreHubColors {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return remember(isDark) {
        if (isDark) {
            CoreHubColors(
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
                accent = Color(0xFF5F83A8),
                accentContainer = Color(0xFF203244),
                positive = Color(0xFF7DCDA5),
                positiveContainer = Color(0xFF173126),
                warning = Color(0xFFE1B35C),
                warningContainer = Color(0xFF3B3019),
                danger = Color(0xFFF1A08F),
                dangerContainer = Color(0xFF382725),
                disabledContainer = Color(0xFF28313D),
                cardMuted = Color(0xFF17202A).copy(alpha = 0.88f),
                chip = Color(0xFF202A35),
                chipBorder = Color(0xFF2A3542),
            )
        } else {
            CoreHubColors(
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
                warning = Color(0xFFB97917),
                warningContainer = Color(0xFFFFF1D9),
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

private fun buildHubHeaderSubtitle(activeCore: CoreRecord?, installedCount: Int): String {
    return when {
        activeCore != null -> "当前使用 ${activeCore.shortDisplayName()}，一共安装了 $installedCount 个核心。"
        installedCount == 0 -> "还没有安装任何核心，先添加一个，再继续切换和管理。"
        else -> "已经安装 $installedCount 个核心，但当前没有活动项。"
    }
}

private fun buildVersionLabel(core: CoreRecord?, updateInfo: CoreUpdateInfo?): String {
    if (core == null) return "等待安装"
    return when {
        updateInfo?.updateAvailable == true -> {
            updateInfo.latestVersion
                ?: updateInfo.latestCommit?.sha?.take(7)
                ?: core.version
                ?: core.commitLabel
                ?: "--"
        }
        !core.version.isNullOrBlank() -> core.version
        !core.commitLabel.isNullOrBlank() -> core.commitLabel.orEmpty()
        else -> "--"
    }
}

private fun buildUpdateStatusLabel(updateInfo: CoreUpdateInfo?): String {
    return when {
        updateInfo == null -> "未检查"
        updateInfo.updateAvailable -> {
            val latest = updateInfo.latestVersion ?: updateInfo.latestCommit?.sha?.take(7) ?: "新版本"
            "可更新到 $latest"
        }
        updateInfo.state == CoreUpdateState.UpToDate -> "当前已是最新"
        else -> "暂时无法确认是否最新，建议配置 GitHub Token 后重试"
    }
}

private fun CoreRecord.shortDisplayName(): String {
    return repoDisplayName.substringAfterLast('/').ifBlank { repoDisplayName }
}

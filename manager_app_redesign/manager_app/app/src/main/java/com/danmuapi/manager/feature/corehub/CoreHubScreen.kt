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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danmuapi.manager.app.state.ManagerViewModel
import com.danmuapi.manager.core.designsystem.component.formatSizeLabel
import com.danmuapi.manager.core.designsystem.theme.DanmuMonoFamily
import com.danmuapi.manager.core.designsystem.theme.ImmersivePalette
import com.danmuapi.manager.core.designsystem.theme.rememberImmersivePalette
import com.danmuapi.manager.core.model.CoreRecord
import com.danmuapi.manager.core.model.CoreUpdateInfo
import com.danmuapi.manager.core.model.CoreUpdateState
import com.danmuapi.manager.core.model.RollbackCommitItem

private enum class CoreFilter(val label: String) {
    All("全部"),
    Active("当前"),
    Updates("可更新"),
}

private typealias CoreHubColors = ImmersivePalette

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
            contentColor = MaterialTheme.colorScheme.onSurface,
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
                .offset(x = 236.dp, y = (-32).dp)
                .size(168.dp)
                .clip(CircleShape)
                .background(colors.haloPrimary),
        )
        Box(
            modifier = Modifier
                .offset(x = (-42).dp, y = 172.dp)
                .size(122.dp)
                .clip(CircleShape)
                .background(colors.haloSecondary),
        )

        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
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
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                        .widthIn(max = 860.dp)
                        .align(Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CoreHubHeader(
                        subtitle = CoreHubSummaryFormatter.headerSubtitle(activeCore, allCores.size),
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

                    InstalledCorePanel(
                        cores = filteredCores,
                        allCount = allCores.size,
                        activeId = activeId,
                        query = query,
                        onQueryChange = { query = it },
                        selectedFilter = selectedFilter,
                        onFilterChange = { selectedFilter = it },
                        updateInfo = viewModel.updateInfo,
                        busy = viewModel.busy,
                        colors = colors,
                        onCheckUpdates = viewModel::checkUpdates,
                        onShowInstall = { showInstallSheet = true },
                        onOpenCore = onOpenCore,
                    )
                }
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
    var showRollbackSheet by remember { mutableStateOf(false) }
    var selectedCommit by remember { mutableStateOf<RollbackCommitItem?>(null) }
    var confirmRollbackCommit by remember { mutableStateOf<RollbackCommitItem?>(null) }
    var rollbackQuery by rememberSaveable { mutableStateOf("") }

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

    if (confirmRollbackCommit != null && core != null) {
        val commit = confirmRollbackCommit!!
        AlertDialog(
            onDismissRequest = { confirmRollbackCommit = null },
            title = { Text("确认回退") },
            text = { Text("将安装 ${core.repoDisplayName}@${commit.shortSha}（${commit.versionLabel}）作为新的本地核心版本。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRollbackCommit = null
                        viewModel.rollbackToCommit(core.id, commit.sha)
                    },
                ) {
                    Text("继续")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRollbackCommit = null }) {
                    Text("取消")
                }
            },
        )
    }

    if (showRollbackSheet && core != null) {
        ModalBottomSheet(
            onDismissRequest = {
                showRollbackSheet = false
                selectedCommit = null
                viewModel.resetRollbackState()
            },
            containerColor = colors.cardStrong,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            RollbackSheet(
                core = core,
                query = rollbackQuery,
                onQueryChange = { rollbackQuery = it },
                commits = viewModel.rollbackCommits,
                snapshotText = buildRollbackSnapshotText(viewModel.rollbackSearchSnapshot.scannedCount, viewModel.rollbackSearchSnapshot.matchedCount, rollbackQuery),
                loading = viewModel.rollbackLoading,
                hasNextPage = viewModel.rollbackHasNextPage,
                error = viewModel.rollbackError,
                colors = colors,
                onSearch = { viewModel.loadRollbackCommits(core.id, rollbackQuery, append = false) },
                onClear = {
                    rollbackQuery = ""
                    viewModel.loadRollbackCommits(core.id, "", append = false)
                },
                onLoadMore = { viewModel.loadRollbackCommits(core.id, rollbackQuery, append = true) },
                onViewDetail = { selectedCommit = it },
                onRollback = { confirmRollbackCommit = it },
            )
        }
    }

    if (selectedCommit != null) {
        val commit = selectedCommit!!
        AlertDialog(
            onDismissRequest = { selectedCommit = null },
            title = { Text(commit.versionLabel) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("SHA: ${commit.sha}")
                    Text("标题: ${commit.titleLabel}")
                    commit.authorName?.takeIf { it.isNotBlank() }?.let { Text("作者: $it") }
                    commit.date?.takeIf { it.isNotBlank() }?.let { Text("时间: $it") }
                    commit.body?.takeIf { it.isNotBlank() }?.let { Text("说明: $it") }
                    commit.htmlUrl?.takeIf { it.isNotBlank() }?.let { Text("链接: $it") }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedCommit = null
                        confirmRollbackCommit = commit
                    },
                ) {
                    Text("回退到此版本")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedCommit = null }) {
                    Text("关闭")
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
                    busy = viewModel.busy,
                    colors = colors,
                    onInstall = { viewModel.installCore(core.repo, core.ref) },
                    onActivate = { viewModel.activateCore(core.id) },
                )

                CoreInfoCard(
                    core = core,
                    updateInfo = updateInfo,
                    colors = colors,
                )

                RollbackActionCard(
                    currentVersion = core.version,
                    currentCommit = core.commitLabel,
                    colors = colors,
                    busy = viewModel.busy || viewModel.rollbackLoading,
                    onOpen = {
                        showRollbackSheet = true
                        rollbackQuery = ""
                        viewModel.resetRollbackState()
                        viewModel.loadRollbackCommits(core.id, "", append = false)
                    },
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
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "核心",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 28.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.6).sp,
                ),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
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
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(toneColor),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
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
                style = MaterialTheme.typography.bodySmall,
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
    val primaryLabel = CoreHubSummaryFormatter.primaryActionLabel(core, updateInfo)
    val title = core?.repoDisplayName ?: "还没有活动核心"
    val subtitle = CoreHubSummaryFormatter.currentCoreMetaLine(core)
    val stateText = CoreHubSummaryFormatter.currentStateBadge(core, updateInfo)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            if (hasCore) onOpenDetail() else onOpenInstall()
        },
        shape = RoundedCornerShape(26.dp),
        color = colors.cardStrong,
        border = BorderStroke(1.dp, colors.cardBorder),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            colors.accentContainer.copy(alpha = 0.28f),
                            colors.cardStrong,
                        ),
                    ),
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "当前核心",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.subtleText,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(colors.accent)
                        .border(4.dp, colors.accent.copy(alpha = 0.12f), CircleShape),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            lineHeight = 20.sp,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                        ),
                        color = colors.subtleText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                StateBadge(
                    text = stateText,
                    color = when {
                        !hasCore || updateUnknown -> colors.subtleText
                        updateAvailable -> colors.warning
                        else -> colors.positive
                    },
                    container = when {
                        !hasCore || updateUnknown -> colors.chip
                        updateAvailable -> colors.warningContainer
                        else -> colors.positiveContainer
                    },
                )
            }

            if (hasCore) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HubActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                        label = "查看详情",
                        enabled = !busy,
                        colors = colors,
                        emphasized = true,
                        onClick = onOpenDetail,
                    )
                    HubActionButton(
                        modifier = Modifier.weight(1f),
                        icon = if (updateAvailable) Icons.Filled.SystemUpdateAlt else Icons.Filled.CloudDownload,
                        label = if (busy) "处理中" else primaryLabel,
                        enabled = !busy,
                        colors = colors,
                        onClick = onPrimaryAction,
                    )
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = if (allCoreCount == 0) {
                            "还没有安装核心，先添加一个开始管理。"
                        } else {
                            "已安装 $allCoreCount 个核心，先选择一个或直接安装新的核心。"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                        ),
                        color = colors.subtleText,
                    )
                    HubActionButton(
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Filled.CloudDownload,
                        label = if (busy) "处理中" else primaryLabel,
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
            .clip(RoundedCornerShape(14.dp))
            .background(colors.chip)
            .border(1.dp, colors.chipBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = colors.subtleText,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground,
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
        shape = RoundedCornerShape(16.dp),
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
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(if (emphasized) colors.cardStrong else colors.cardMuted)
                    .border(1.dp, colors.cardBorder, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) colors.accent else colors.subtleText,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                ),
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
    busy: Boolean,
    colors: CoreHubColors,
    onCheckUpdates: () -> Unit,
    onShowInstall: () -> Unit,
    onOpenCore: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = colors.cardStrong,
        border = BorderStroke(1.dp, colors.cardBorder),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "核心库",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = if (allCount == 0) {
                            "安装后会在这里统一管理和切换核心。"
                        } else {
                            "当前核心置顶，可更新项优先显示。"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                        ),
                        color = colors.subtleText,
                    )
                }
                HubCountBadge(
                    value = if (cores.size == allCount) "$allCount 个" else "${cores.size} / $allCount",
                    colors = colors,
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                ),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = colors.subtleText,
                        modifier = Modifier.size(18.dp),
                    )
                },
                placeholder = {
                    Text(
                        text = "搜索仓库、分支、版本或提交",
                        color = colors.subtleText,
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                shape = RoundedCornerShape(18.dp),
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CoreFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = filter == selectedFilter,
                        onClick = { onFilterChange(filter) },
                        label = {
                            Text(
                                text = filter.label,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            )
                        },
                    )
                }
            }

            HubActionRow(
                busy = busy,
                colors = colors,
                onCheckUpdates = onCheckUpdates,
                onShowInstall = onShowInstall,
            )

            if (cores.isEmpty()) {
                EmptyCoreState(
                    query = query,
                    colors = colors,
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    cores.forEach { core ->
                        CoreListRow(
                            core = core,
                            isActive = core.id == activeId,
                            updateInfo = updateInfo[core.id],
                            colors = colors,
                            onClick = { onOpenCore(core.id) },
                        )
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
            .padding(horizontal = 10.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
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
        shape = RoundedCornerShape(18.dp),
        color = colors.cardMuted,
        border = BorderStroke(1.dp, colors.cardBorder),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "没有匹配的核心",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = if (query.isBlank()) {
                    "当前筛选条件下没有内容，试试切回全部，或者直接安装新的核心。"
                } else {
                    "换个关键词试试，或者清空搜索后重新浏览。"
                },
                style = MaterialTheme.typography.bodySmall,
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
        isActive -> colors.accentContainer.copy(alpha = 0.56f)
        updateAvailable -> colors.warningContainer.copy(alpha = 0.72f)
        else -> colors.cardMuted
    }
    val borderColor = when {
        isActive -> colors.accent.copy(alpha = 0.12f)
        updateAvailable -> colors.warning.copy(alpha = 0.12f)
        else -> colors.cardBorder
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(rowColor)
            .border(1.dp, borderColor, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = core.repoDisplayName,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    lineHeight = 17.sp,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "${core.ref} · ${buildVersionLabel(core, updateInfo = null)}",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                ),
                color = colors.subtleText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StateBadge(
                text = when {
                    isActive -> "当前"
                    updateAvailable -> "可更新"
                    updateUnknown -> "未确认"
                    else -> "已安装"
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
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(colors.cardStrong)
                    .border(1.dp, colors.cardBorder, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "›",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.subtleText,
                )
            }
        }
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
            .border(1.dp, color.copy(alpha = 0.08f), CircleShape)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
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
    busy: Boolean,
    colors: CoreHubColors,
    onInstall: () -> Unit,
    onActivate: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = colors.cardStrong,
        border = BorderStroke(1.dp, colors.cardBorder),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            colors.accentContainer.copy(alpha = 0.32f),
                            colors.cardStrong,
                        ),
                    ),
                )
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "核心详情",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.subtleText,
                    )
                    Text(
                        text = core.repoDisplayName,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.3).sp,
                        ),
                    )
                }
                StateBadge(
                    text = when {
                        updateInfo?.updateAvailable == true -> "可更新"
                        updateInfo?.state == CoreUpdateState.Unknown -> "未确认"
                        isActive -> "当前"
                        else -> "已安装"
                    },
                    color = when {
                        updateInfo?.updateAvailable == true -> colors.warning
                        updateInfo?.state == CoreUpdateState.Unknown -> colors.subtleText
                        isActive -> colors.accent
                        else -> colors.subtleText
                    },
                    container = when {
                        updateInfo?.updateAvailable == true -> colors.warningContainer
                        updateInfo?.state == CoreUpdateState.Unknown -> colors.chip
                        isActive -> colors.accentContainer
                        else -> colors.chip
                    },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HubActionButton(
                    modifier = Modifier.weight(1f),
                    icon = if (updateInfo?.updateAvailable == true) Icons.Filled.SystemUpdateAlt else Icons.Filled.CloudDownload,
                    label = if (busy) "处理中" else if (updateInfo?.updateAvailable == true) "安装更新" else "重新安装",
                    enabled = !busy,
                    colors = colors,
                    emphasized = true,
                    onClick = onInstall,
                )
                HubActionButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    label = if (isActive) "当前正在使用" else "切换为当前",
                    enabled = !busy && !isActive,
                    colors = colors,
                    onClick = onActivate,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CoreHubSummaryFormatter.detailQuickStats(core).forEach { stat ->
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        color = colors.cardMuted,
                        border = BorderStroke(1.dp, colors.cardBorder),
                        shadowElevation = 0.dp,
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                text = stat.label,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = colors.subtleText,
                            )
                            Text(
                                text = stat.value,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
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
        shape = RoundedCornerShape(24.dp),
        color = colors.cardStrong,
        border = BorderStroke(1.dp, colors.cardBorder),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "核心信息",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
            )
            CoreInfoRow(title = "仓库", value = core.repo, colors = colors)
            CoreInfoRow(title = "版本", value = core.version ?: "--", colors = colors)
            CoreInfoRow(title = "安装时间", value = core.installedAt ?: "--", colors = colors)
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
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = colors.subtleText,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = if (monospace) DanmuMonoFamily else null,
                lineHeight = 19.sp,
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
        shape = RoundedCornerShape(22.dp),
        color = colors.dangerContainer,
        border = BorderStroke(1.dp, colors.danger.copy(alpha = 0.14f)),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "删除核心",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = colors.danger,
                )
                Text(
                    text = "删除 ${core.repoDisplayName}@${core.ref} 后，需要重新安装或切换到其他核心。",
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp),
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
                    modifier = Modifier.size(38.dp),
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
private fun RollbackActionCard(
    currentVersion: String?,
    currentCommit: String?,
    colors: CoreHubColors,
    busy: Boolean,
    onOpen: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = colors.cardStrong,
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
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "版本回退",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                )
                Text(
                    text = "先看版本号，再选提交回退，避免只看 SHA 误选。",
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp),
                    color = colors.subtleText,
                )
                Text(
                    text = buildRollbackCurrentHint(currentVersion, currentCommit),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.accent,
                )
            }
            HubActionButton(
                icon = Icons.Filled.Info,
                label = if (busy) "处理中" else "打开回退列表",
                enabled = !busy,
                colors = colors,
                emphasized = true,
                onClick = onOpen,
            )
        }
    }
}

@Composable
private fun RollbackSheet(
    core: CoreRecord,
    query: String,
    onQueryChange: (String) -> Unit,
    commits: List<RollbackCommitItem>,
    snapshotText: String,
    loading: Boolean,
    hasNextPage: Boolean,
    error: String?,
    colors: CoreHubColors,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onLoadMore: () -> Unit,
    onViewDetail: (RollbackCommitItem) -> Unit,
    onRollback: (RollbackCommitItem) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "版本回退 · ${core.repoDisplayName}",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
        )
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("输入版本号，如 1.10.2") },
            shape = RoundedCornerShape(18.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HubActionButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Search,
                label = "搜索",
                enabled = !loading,
                colors = colors,
                emphasized = true,
                onClick = onSearch,
            )
            HubActionButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Refresh,
                label = "清空搜索",
                enabled = !loading,
                colors = colors,
                onClick = onClear,
            )
        }
        Text(
            text = snapshotText,
            style = MaterialTheme.typography.bodySmall,
            color = colors.subtleText,
        )
        error?.takeIf { it.isNotBlank() }?.let {
            Text(text = it, color = colors.danger, style = MaterialTheme.typography.bodySmall)
        }
        commits.forEach { commit ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = colors.cardMuted,
                border = BorderStroke(1.dp, colors.cardBorder),
                shadowElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(commit.versionLabel, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    Text(
                        commit.titleLabel,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        buildRollbackMetaLine(commit),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.subtleText,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HubActionButton(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Filled.Info,
                            label = "查看详情",
                            enabled = !loading,
                            colors = colors,
                            onClick = { onViewDetail(commit) },
                        )
                        HubActionButton(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Filled.CloudDownload,
                            label = "回退到此版本",
                            enabled = !loading,
                            colors = colors,
                            emphasized = true,
                            onClick = { onRollback(commit) },
                        )
                    }
                }
            }
        }
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        }
        if (hasNextPage && !loading) {
            HubActionButton(
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Filled.Refresh,
                label = "下一页",
                enabled = true,
                colors = colors,
                onClick = onLoadMore,
            )
        }
    }
}

private fun buildRollbackSnapshotText(scanned: Int, matched: Int, query: String): String {
    return if (query.isBlank()) {
        "已加载 $scanned 条提交，可直接按版本号和提交时间挑选"
    } else {
        "已扫描 $scanned 条提交，找到 $matched 条版本为 $query 的提交"
    }
}

private fun buildRollbackCurrentHint(currentVersion: String?, currentCommit: String?): String {
    val versionLabel = currentVersion?.takeIf { it.isNotBlank() } ?: "版本未知"
    val commitLabel = currentCommit?.takeIf { it.isNotBlank() } ?: "提交未知"
    return "当前版本：$versionLabel · 当前提交：$commitLabel"
}

private fun buildRollbackMetaLine(commit: RollbackCommitItem): String {
    val parts = buildList {
        add(commit.shortSha)
        commit.date?.takeIf { it.isNotBlank() }?.let(::add)
        commit.authorName?.takeIf { it.isNotBlank() }?.let(::add)
    }
    return parts.joinToString(" · ")
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
    return rememberImmersivePalette()
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

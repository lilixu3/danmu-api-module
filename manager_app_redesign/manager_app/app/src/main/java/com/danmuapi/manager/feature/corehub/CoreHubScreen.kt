@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.danmuapi.manager.feature.corehub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.danmuapi.manager.app.state.ManagerViewModel
import com.danmuapi.manager.core.designsystem.component.EmptyHint
import com.danmuapi.manager.core.designsystem.component.InfoRow
import com.danmuapi.manager.core.designsystem.component.MetricPill
import com.danmuapi.manager.core.designsystem.component.PageHeader
import com.danmuapi.manager.core.designsystem.component.SectionCard
import com.danmuapi.manager.core.designsystem.component.StatusTag
import com.danmuapi.manager.core.designsystem.component.StatusTone
import com.danmuapi.manager.core.designsystem.component.formatSizeLabel
import com.danmuapi.manager.core.model.CoreRecord
import com.danmuapi.manager.core.model.CoreUpdateInfo

private enum class CoreFilter(val label: String) {
    All("全部"),
    Active("当前"),
    Update("可更新"),
    Custom("自定义"),
}

@Composable
fun CoreHubScreen(
    contentPadding: PaddingValues,
    viewModel: ManagerViewModel,
) {
    val allCores = viewModel.cores?.cores.orEmpty()
    val activeId = viewModel.cores?.activeCoreId ?: viewModel.status?.activeCoreId
    var query by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf(CoreFilter.All) }
    var selectedCoreId by rememberSaveable(activeId, allCores) { mutableStateOf(activeId ?: allCores.firstOrNull()?.id) }
    var repoInput by rememberSaveable { mutableStateOf("lilixu3/danmu_api") }
    var refInput by rememberSaveable { mutableStateOf("main") }
    var showInstallSheet by rememberSaveable { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<CoreRecord?>(null) }

    val filteredCores = remember(allCores, activeId, query, selectedFilter, viewModel.updateInfo) {
        allCores.filter { core ->
            val matchesQuery = query.isBlank() ||
                core.repo.contains(query, ignoreCase = true) ||
                core.ref.contains(query, ignoreCase = true) ||
                core.version.orEmpty().contains(query, ignoreCase = true) ||
                core.commitLabel.orEmpty().contains(query, ignoreCase = true)

            val matchesFilter = when (selectedFilter) {
                CoreFilter.All -> true
                CoreFilter.Active -> core.id == activeId
                CoreFilter.Update -> viewModel.updateInfo[core.id]?.updateAvailable == true
                CoreFilter.Custom -> !isRecommendedRepo(core.repo)
            }

            matchesQuery && matchesFilter
        }.sortedWith(
            compareByDescending<CoreRecord> { it.id == activeId }
                .thenBy { it.repo.lowercase() }
                .thenBy { it.ref.lowercase() },
        )
    }

    val selectedCore = remember(filteredCores, allCores, selectedCoreId, activeId) {
        filteredCores.firstOrNull { it.id == selectedCoreId }
            ?: allCores.firstOrNull { it.id == selectedCoreId }
            ?: allCores.firstOrNull { it.id == activeId }
            ?: filteredCores.firstOrNull()
    }

    if (deleteTarget != null) {
        val target = deleteTarget!!
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除核心") },
            text = { Text("确认删除 ${target.repoDisplayName}@${target.ref}？删除后需要重新安装或切换其他核心。") },
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

    if (showInstallSheet) {
        ModalBottomSheet(onDismissRequest = { showInstallSheet = false }) {
            InstallCoreSheet(
                repoInput = repoInput,
                onRepoChange = { repoInput = it },
                refInput = refInput,
                onRefChange = { refInput = it },
                onUsePreset = { repo, ref ->
                    repoInput = repo
                    refInput = ref
                },
                onInstall = {
                    showInstallSheet = false
                    viewModel.installCore(repoInput, refInput.ifBlank { "main" })
                },
                onCheckUpdates = viewModel::checkUpdates,
            )
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        val useTwoPane = maxWidth >= 920.dp

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                PageHeader(
                    title = "核心",
                    subtitle = "默认先看已安装核心，把安装流程收进次级入口，避免首屏变成表单墙。",
                    modifier = Modifier.padding(top = 12.dp),
                )

                SectionCard(
                    title = "核心概览",
                    subtitle = "先判断现状，再决定是安装、切换还是更新。",
                    action = {
                        OutlinedButton(onClick = { showInstallSheet = true }) {
                            Text("安装核心")
                        }
                    },
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        MetricPill(label = "已安装", value = allCores.size.toString())
                        MetricPill(
                            label = "当前",
                            value = allCores.firstOrNull { it.id == activeId }?.repoDisplayName ?: "未激活",
                        )
                        MetricPill(
                            label = "可更新",
                            value = viewModel.updateInfo.values.count { it.updateAvailable }.toString(),
                        )
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("搜索仓库 / Ref / 版本 / Commit") },
                        singleLine = true,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CoreFilter.entries.forEach { filter ->
                            AssistChip(
                                onClick = { selectedFilter = filter },
                                label = { Text(filter.label) },
                                leadingIcon = if (selectedFilter == filter) {
                                    { Icon(Icons.Filled.SwapHoriz, contentDescription = null) }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }

                if (useTwoPane) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        CoreListSection(
                            cores = filteredCores,
                            activeId = activeId,
                            selectedCoreId = selectedCore?.id,
                            updateInfo = viewModel.updateInfo,
                            onSelect = { selectedCoreId = it },
                            modifier = Modifier.weight(0.46f),
                        )
                        CoreDetailSection(
                            core = selectedCore,
                            isActive = selectedCore?.id == activeId,
                            updateInfo = selectedCore?.id?.let(viewModel.updateInfo::get),
                            onActivate = {
                                selectedCore?.let { viewModel.activateCore(it.id) }
                            },
                            onRefreshUpdate = {
                                selectedCore?.let { viewModel.installCore(it.repo, it.ref) }
                            },
                            onDelete = {
                                selectedCore?.let { deleteTarget = it }
                            },
                            modifier = Modifier.weight(0.54f),
                        )
                    }
                } else {
                    CoreListSection(
                        cores = filteredCores,
                        activeId = activeId,
                        selectedCoreId = selectedCore?.id,
                        updateInfo = viewModel.updateInfo,
                        onSelect = { selectedCoreId = it },
                    )
                    CoreDetailSection(
                        core = selectedCore,
                        isActive = selectedCore?.id == activeId,
                        updateInfo = selectedCore?.id?.let(viewModel.updateInfo::get),
                        onActivate = {
                            selectedCore?.let { viewModel.activateCore(it.id) }
                        },
                        onRefreshUpdate = {
                            selectedCore?.let { viewModel.installCore(it.repo, it.ref) }
                        },
                        onDelete = {
                            selectedCore?.let { deleteTarget = it }
                        },
                    )
                }
            }

            ExtendedFloatingActionButton(
                onClick = { showInstallSheet = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("安装核心") },
            )
        }
    }
}

@Composable
private fun InstallCoreSheet(
    repoInput: String,
    onRepoChange: (String) -> Unit,
    refInput: String,
    onRefChange: (String) -> Unit,
    onUsePreset: (String, String) -> Unit,
    onInstall: () -> Unit,
    onCheckUpdates: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(text = "安装新核心", style = MaterialTheme.typography.titleLarge)
        Text(
            text = "安装流程收进二级入口，默认不再占据核心页首屏。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                "lilixu3/danmu_api" to "main",
                "huangxd-/danmu_api" to "main",
                "lilixu3/danmu_api" to "dev",
            ).forEach { (repo, ref) ->
                AssistChip(
                    onClick = { onUsePreset(repo, ref) },
                    label = { Text("$repo@$ref") },
                    leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) },
                )
            }
        }
        OutlinedTextField(
            value = repoInput,
            onValueChange = onRepoChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("仓库地址 / owner/repo") },
            singleLine = true,
        )
        OutlinedTextField(
            value = refInput,
            onValueChange = onRefChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("分支 / Tag / Commit") },
            singleLine = true,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ElevatedButton(onClick = onInstall) {
                Icon(Icons.Filled.Download, contentDescription = null)
                Text("下载并安装")
            }
            FilledTonalButton(onClick = onCheckUpdates) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Text("检查全部更新")
            }
        }
    }
}

@Composable
private fun CoreListSection(
    cores: List<CoreRecord>,
    activeId: String?,
    selectedCoreId: String?,
    updateInfo: Map<String, CoreUpdateInfo>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(
        title = "已安装核心",
        subtitle = "默认先浏览资源列表，具体操作放到右侧或下方详情区。",
        modifier = modifier,
    ) {
        if (cores.isEmpty()) {
            EmptyHint(
                title = "还没有已安装核心",
                detail = "点击右下角“安装核心”，下载首个核心后再进行切换和更新。",
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                items(cores, key = { it.id }) { core ->
                    CoreListItem(
                        core = core,
                        isActive = core.id == activeId,
                        isSelected = core.id == selectedCoreId,
                        updateInfo = updateInfo[core.id],
                        onSelect = { onSelect(core.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CoreListItem(
    core: CoreRecord,
    isActive: Boolean,
    isSelected: Boolean,
    updateInfo: CoreUpdateInfo?,
    onSelect: () -> Unit,
) {
    OutlinedCard(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(text = core.repoDisplayName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Ref ${core.ref}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = core.version ?: "--",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (isActive) {
                    StatusTag(text = "当前启用", tone = StatusTone.Positive)
                }
                if (updateInfo?.updateAvailable == true) {
                    StatusTag(text = "可更新", tone = StatusTone.Warning)
                }
                if (!isRecommendedRepo(core.repo)) {
                    StatusTag(text = "自定义源", tone = StatusTone.Info)
                }
            }
            Text(
                text = "Commit ${core.commitLabel ?: "--"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CoreDetailSection(
    core: CoreRecord?,
    isActive: Boolean,
    updateInfo: CoreUpdateInfo?,
    onActivate: () -> Unit,
    onRefreshUpdate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(
        title = "核心详情",
        subtitle = "列表里只看概况，具体操作收束到这个详情区。",
        modifier = modifier,
    ) {
        if (core == null) {
            EmptyHint(
                title = "没有选中核心",
                detail = "从左侧列表中选择一个核心后，再查看详情或执行切换。",
            )
            return@SectionCard
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isActive) {
                StatusTag(text = "当前启用", tone = StatusTone.Positive)
            }
            if (updateInfo?.updateAvailable == true) {
                StatusTag(text = "发现更新", tone = StatusTone.Warning)
            } else {
                StatusTag(text = "更新状态：${if (updateInfo == null) "未检查" else "已是最新"}", tone = StatusTone.Neutral)
            }
        }
        InfoRow(label = "仓库", value = core.repoDisplayName)
        InfoRow(label = "Ref", value = core.ref)
        InfoRow(label = "版本", value = core.version ?: "--")
        InfoRow(label = "Commit", value = core.commitLabel ?: "--")
        InfoRow(label = "安装时间", value = core.installedAt ?: "--")
        InfoRow(label = "体积", value = core.sizeBytes?.formatSizeLabel() ?: "--")
        updateInfo?.latestVersion?.let { InfoRow(label = "远端版本", value = it) }
        updateInfo?.latestCommit?.sha?.take(7)?.let { InfoRow(label = "远端 Commit", value = it) }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!isActive) {
                ElevatedButton(onClick = onActivate) {
                    Icon(Icons.Filled.SwapHoriz, contentDescription = null)
                    Text("切换为当前")
                }
            }
            FilledTonalButton(onClick = onRefreshUpdate) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Text(if (updateInfo?.updateAvailable == true) "更新核心" else "重装核心")
            }
            OutlinedButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = null)
                Text("删除")
            }
        }
    }
}

private fun isRecommendedRepo(repo: String): Boolean {
    return repo.trim().lowercase() in setOf(
        "lilixu3/danmu_api",
        "huangxd-/danmu_api",
    )
}

package com.danmuapi.manager.feature.corehub

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.danmuapi.manager.app.state.ManagerViewModel
import com.danmuapi.manager.core.designsystem.component.EmptyHint
import com.danmuapi.manager.core.designsystem.component.InfoRow
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CoreHubScreen(
    contentPadding: PaddingValues,
    viewModel: ManagerViewModel,
) {
    val cores = viewModel.cores?.cores.orEmpty()
    val activeId = viewModel.cores?.activeCoreId ?: viewModel.status?.activeCoreId
    var query by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf(CoreFilter.All) }
    var repoInput by rememberSaveable { mutableStateOf("lilixu3/danmu_api") }
    var refInput by rememberSaveable { mutableStateOf("main") }
    var deleteTarget by remember { mutableStateOf<CoreRecord?>(null) }

    val filtered = remember(cores, activeId, query, selectedFilter, viewModel.updateInfo) {
        cores.filter { core ->
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

    if (deleteTarget != null) {
        val target = deleteTarget!!
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除核心") },
            text = {
                Text(
                    text = "确认删除 ${target.repoDisplayName}@${target.ref}？\n删除后需要重新切换或重新安装。",
                )
            },
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
    ) {
        item {
            SectionCard(title = "安装新核心") {
                Text(
                    text = "保持旧目录不动，新的核心中心负责下载、切换、更新与清理。安装动作会直接调用 Root 侧 CLI。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    listOf(
                        "lilixu3/danmu_api" to "main",
                        "huangxd-/danmu_api" to "main",
                        "lilixu3/danmu_api" to "dev",
                    ).forEach { (repo, ref) ->
                        AssistChip(
                            onClick = {
                                repoInput = repo
                                refInput = ref
                            },
                            label = { Text("$repo@$ref") },
                            leadingIcon = {
                                Icon(Icons.Filled.Star, contentDescription = null)
                            },
                        )
                    }
                }
                OutlinedTextField(
                    value = repoInput,
                    onValueChange = { repoInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("仓库地址 / owner/repo") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = refInput,
                    onValueChange = { refInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("分支 / Tag / Commit") },
                    singleLine = true,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ElevatedButton(
                        onClick = {
                            viewModel.installCore(repoInput, refInput.ifBlank { "main" })
                        },
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null)
                        Text("下载并安装")
                    }
                    FilledTonalButton(onClick = viewModel::checkUpdates) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Text("检查全部更新")
                    }
                    OutlinedButton(onClick = viewModel::refreshAll) {
                        Text("刷新核心列表")
                    }
                }
            }
        }

        item {
            SectionCard(title = "核心总览") {
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
                                {
                                    Icon(Icons.Filled.Star, contentDescription = null)
                                }
                            } else {
                                null
                            },
                        )
                    }
                }
                InfoRow(label = "已安装核心", value = cores.size.toString())
                InfoRow(
                    label = "活动核心",
                    value = cores.firstOrNull { it.id == activeId }?.repoDisplayName ?: "未激活",
                )
                InfoRow(
                    label = "可更新",
                    value = viewModel.updateInfo.values.count { it.updateAvailable }.toString(),
                )
            }
        }

        if (filtered.isEmpty()) {
            item {
                EmptyHint(
                    title = if (cores.isEmpty()) "还没有已安装核心" else "没有匹配的核心",
                    detail = if (cores.isEmpty()) {
                        "先在上方填写仓库并执行安装，新的核心会出现在这里。"
                    } else {
                        "调整搜索词或筛选条件后再试。"
                    },
                )
            }
        } else {
            items(filtered, key = { it.id }) { core ->
                CoreCard(
                    core = core,
                    isActive = core.id == activeId,
                    updateInfo = viewModel.updateInfo[core.id],
                    onActivate = { viewModel.activateCore(core.id) },
                    onRefreshUpdate = {
                        viewModel.installCore(core.repo, core.ref)
                    },
                    onDelete = { deleteTarget = core },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CoreCard(
    core: CoreRecord,
    isActive: Boolean,
    updateInfo: CoreUpdateInfo?,
    onActivate: () -> Unit,
    onRefreshUpdate: () -> Unit,
    onDelete: () -> Unit,
) {
    SectionCard(title = core.repoDisplayName) {
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
        InfoRow(label = "Ref", value = core.ref)
        InfoRow(label = "版本", value = core.version ?: "--")
        InfoRow(label = "Commit", value = core.commitLabel ?: "--")
        InfoRow(label = "安装时间", value = core.installedAt ?: "--")
        InfoRow(label = "体积", value = core.sizeBytes?.formatSizeLabel() ?: "--")
        updateInfo?.latestVersion?.let {
            InfoRow(label = "远端版本", value = it)
        }
        updateInfo?.latestCommit?.sha?.take(7)?.let {
            InfoRow(label = "远端 Commit", value = it)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!isActive) {
                ElevatedButton(onClick = onActivate) {
                    Text("切换到此核心")
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

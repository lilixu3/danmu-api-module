package com.danmuapi.manager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.danmuapi.manager.data.CoreUpdateInfo
import com.danmuapi.manager.data.model.CoreListResponse
import com.danmuapi.manager.data.model.CoreMeta
import com.danmuapi.manager.ui.components.ManagerCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoresScreen(
    paddingValues: PaddingValues,
    cores: CoreListResponse?,
    updateInfo: Map<String, CoreUpdateInfo>,
    onCheckUpdates: () -> Unit,
    onInstall: (repo: String, ref: String) -> Unit,
    onActivate: (id: String) -> Unit,
    onDelete: (id: String) -> Unit,
) {
    var showSheet by remember { mutableStateOf(false) }
    var repoInput by remember { mutableStateOf("huangxd-/danmu_api") }
    var refInput by remember { mutableStateOf("main") }
    var deleteTarget by remember { mutableStateOf<CoreMeta?>(null) }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = "下载/更新核心", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "建议优先使用稳定分支或 Release Tag。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(text = "快速选择", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { repoInput = "huangxd-/danmu_api" }, label = { Text("huangxd-") })
                    AssistChip(onClick = { repoInput = "lilixu3/danmu_api" }, label = { Text("lilixu3") })
                }

                OutlinedTextField(
                    value = repoInput,
                    onValueChange = { repoInput = it },
                    label = { Text("仓库 (owner/repo)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = refInput,
                    onValueChange = { refInput = it },
                    label = { Text("分支 / Tag / Commit") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { showSheet = false }) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            showSheet = false
                            onInstall(repoInput.trim(), refInput.trim().ifBlank { "main" })
                        },
                    ) { Text("开始下载") }
                }
            }
        }
    }

    if (deleteTarget != null) {
        val t = deleteTarget!!
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除核心？") },
            text = {
                Text("将删除：\n${t.repo}@${t.ref}\n${t.version ?: "-"} (${t.shaShort ?: t.sha ?: ""})")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget = null
                        onDelete(t.id)
                    },
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }

    val list = cores?.cores ?: emptyList()
    val activeId = cores?.activeCoreId
    val sorted = list.sortedWith(
        compareByDescending<CoreMeta> { it.id == activeId }
            .thenBy { it.repo.lowercase() }
            .thenBy { it.ref.lowercase() },
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Header (cleaner, less clutter)
        ManagerCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "核心管理", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            text = "下载、切换、更新与清理已安装核心。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { showSheet = true }) {
                            Icon(Icons.Filled.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("下载")
                        }
                        FilledTonalIconButton(
                            onClick = onCheckUpdates,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        ) {
                            BadgedBox(
                                badge = {
                                    // If any core has update available, show a small dot.
                                    val hasUpdate = sorted.any { updateInfo[it.id]?.updateAvailable == true }
                                    if (hasUpdate) Badge()
                                },
                            ) {
                                Icon(Icons.Filled.SystemUpdate, contentDescription = "检查更新", modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }

                if (sorted.isEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "暂无已安装核心，点击右上角“下载”获取一个核心。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            items(sorted, key = { it.id }) { core ->
                CoreItemNew(
                    core = core,
                    isActive = core.id == activeId,
                    update = updateInfo[core.id],
                    onActivate = { onActivate(core.id) },
                    onUpdate = { onInstall(core.repo, core.ref) },
                    onDelete = { deleteTarget = core },
                )
            }
        }
    }
}

@Composable
private fun CoreItemNew(
    core: CoreMeta,
    isActive: Boolean,
    update: CoreUpdateInfo?,
    onActivate: () -> Unit,
    onUpdate: () -> Unit,
    onDelete: () -> Unit,
) {
    val sha = core.shaShort ?: core.sha
    val version = core.version ?: "-"

    val updateAvailable = update?.updateAvailable == true
    val latestVer = update?.latestVersion
    val latestShaShort = update?.latestCommit?.sha?.take(7)

    ManagerCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = core.repo,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${core.ref} · v$version" + (if (!sha.isNullOrBlank()) " · ${sha.take(7)}" else ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = if (sha.isNullOrBlank()) FontFamily.Default else FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (isActive) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Done,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                text = "当前",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }

            // Update info (single, clean line)
            val updateLine = when {
                update == null -> "未检查更新"
                updateAvailable -> "发现更新" + (latestVer?.let { " · v$it" } ?: "") + (latestShaShort?.let { " · $it" } ?: "")
                else -> "已是最新" + (latestVer?.let { " · v$it" } ?: "")
            }
            val updateContainer = when {
                update == null -> MaterialTheme.colorScheme.surfaceVariant
                updateAvailable -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.secondaryContainer
            }
            val updateContent = when {
                update == null -> MaterialTheme.colorScheme.onSurfaceVariant
                updateAvailable -> MaterialTheme.colorScheme.onTertiaryContainer
                else -> MaterialTheme.colorScheme.onSecondaryContainer
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = updateContainer,
            ) {
                Text(
                    text = updateLine,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = updateContent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(
                    modifier = Modifier.weight(1f),
                    onClick = onActivate,
                    enabled = !isActive,
                ) {
                    Icon(Icons.Filled.Done, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isActive) "已启用" else "启用")
                }

                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onUpdate,
                ) {
                    Icon(Icons.Filled.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (updateAvailable) "更新" else "重装")
                }

                FilledTonalIconButton(
                    onClick = onDelete,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = "删除", modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

package com.danmuapi.manager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
                modifier = Modifier
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "下载/更新核心",
                    style = MaterialTheme.typography.titleLarge,
                )

                Text(
                    text = "推荐仓库",
                    style = MaterialTheme.typography.titleMedium,
                )
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
                    ) {
                        Text("开始下载")
                    }
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
                Text(
                    "将删除：\n${t.repo}@${t.ref}\n${t.version ?: "-"} (${t.shaShort ?: t.sha ?: ""})",
                )
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

    Column(
        modifier = Modifier
            .padding(paddingValues)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "已安装核心", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "下载后可切换/删除；如需省电，建议在仪表盘关闭自启动并停止服务。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showSheet = true }) {
                    Icon(Icons.Filled.SystemUpdate, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("下载核心")
                }
                OutlinedButton(onClick = onCheckUpdates) {
                    Icon(Icons.Filled.SystemUpdate, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("检查更新")
                }
            }
        }

        val list = cores?.cores ?: emptyList()
        val activeId = cores?.activeCoreId

        if (list.isEmpty()) {
            Card {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(text = "暂无已安装核心", style = MaterialTheme.typography.titleMedium)
                    Text(text = "点击右上角“下载核心”获取一个核心。", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(list, key = { it.id }) { core ->
                    CoreItem(
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

}

@Composable
private fun CoreItem(
    core: CoreMeta,
    isActive: Boolean,
    update: CoreUpdateInfo?,
    onActivate: () -> Unit,
    onUpdate: () -> Unit,
    onDelete: () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = core.repo,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Ref: ${core.ref}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (isActive) {
                    AssistChip(onClick = {}, label = { Text("当前") })
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text("v${core.version ?: "-"}") })
                val sha = core.shaShort ?: core.sha
                if (!sha.isNullOrBlank()) {
                    AssistChip(onClick = {}, label = { Text(sha, fontFamily = FontFamily.Monospace) })
                }
            }

            if (update != null) {
                val text = if (update.hasUpdate) {
                    "有更新：${update.remoteVersion ?: "?"}"
                } else {
                    "已是最新"
                }
                Text(text = text, style = MaterialTheme.typography.bodyMedium)
                if (!update.remoteShaShort.isNullOrBlank()) {
                    Text(
                        text = "Remote: ${update.remoteShaShort}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onActivate,
                    enabled = !isActive,
                ) {
                    Icon(Icons.Filled.Done, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("切换")
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onUpdate,
                ) {
                    Icon(Icons.Filled.SystemUpdate, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("更新")
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onDelete,
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("删除")
                }
            }
        }
    }
}

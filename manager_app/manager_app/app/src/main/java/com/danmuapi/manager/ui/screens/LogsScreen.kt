package com.danmuapi.manager.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import com.danmuapi.manager.data.model.LogFileInfo
import com.danmuapi.manager.data.model.LogsResponse

@Composable
fun LogsScreen(
    paddingValues: PaddingValues,
    logs: LogsResponse?,
    onClearAll: () -> Unit,
    onReadTail: (path: String, lines: Int, onResult: (String) -> Unit) -> Unit,
) {
    var showClearConfirm by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<LogFileInfo?>(null) }
    var viewingText by remember { mutableStateOf("") }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空日志？") },
            text = { Text("将清空模块运行日志与服务输出日志。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        onClearAll()
                    },
                ) { Text("清空") }
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("取消") } },
        )
    }

    if (viewing != null) {
        val file = viewing!!
        AlertDialog(
            onDismissRequest = { viewing = null },
            title = { Text(file.name) },
            text = {
                Text(
                    text = viewingText.ifBlank { "(空)" },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            },
            confirmButton = { TextButton(onClick = { viewing = null }) { Text("关闭") } },
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
                Text(text = "日志", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "可用于排查启动失败、核心切换与更新问题。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            OutlinedButton(onClick = { showClearConfirm = true }) {
                Icon(Icons.Filled.DeleteSweep, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("清空")
            }
        }

        val files = logs?.files ?: emptyList()
        if (files.isEmpty()) {
            Card {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(text = "暂无日志文件", style = MaterialTheme.typography.titleMedium)
                    Text(text = "启动服务后会生成日志。", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(files, key = { it.path }) { file ->
                    LogItem(
                        file = file,
                        onClick = {
                            viewing = file
                            viewingText = "加载中…"
                            onReadTail(file.path, 200) { viewingText = it }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LogItem(
    file: LogFileInfo,
    onClick: () -> Unit,
) {
    Card(modifier = Modifier.clickable { onClick() }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Description, contentDescription = null)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = file.name, style = MaterialTheme.typography.titleMedium)
                val size = humanBytes(file.sizeBytes)
                val m = file.modifiedAt?.takeIf { it.isNotBlank() } ?: "-"
                Text(text = "$size · $m", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun humanBytes(bytes: Long?): String {
    val b = (bytes ?: 0L).coerceAtLeast(0L)
    val units = arrayOf("B", "KB", "MB", "GB")
    var v = b.toDouble()
    var i = 0
    while (v >= 1024.0 && i < units.lastIndex) {
        v /= 1024.0
        i++
    }
    return if (i == 0) "${b}${units[i]}" else String.format("%.1f%s", v, units[i])
}

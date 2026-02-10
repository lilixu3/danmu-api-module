package com.danmuapi.manager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.danmuapi.manager.data.model.LogFileInfo
import com.danmuapi.manager.data.model.LogsResponse
import com.danmuapi.manager.ui.screens.console.components.ConsoleCard

@Composable
fun LogsScreen(
    paddingValues: PaddingValues,
    logs: LogsResponse?,
    tailLines: Int = 300,
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
                val warnColor = remember { Color(0xFFFFC107) }
                val errorColor = MaterialTheme.colorScheme.error
                val normalColor = MaterialTheme.colorScheme.onSurface
                val errorRegex = remember { Regex("""\b(ERROR|FATAL)\b""", RegexOption.IGNORE_CASE) }
                val warnRegex = remember { Regex("""\bWARN(ING)?\b""", RegexOption.IGNORE_CASE) }

                val raw = viewingText.ifBlank { "(空)" }
                val lines = remember(raw) { raw.split("\n") }

                val listState = rememberLazyListState()
                LaunchedEffect(lines.size) {
                    if (lines.isNotEmpty()) {
                        try {
                            listState.scrollToItem(lines.lastIndex)
                        } catch (_: Throwable) {
                        }
                    }
                }

                fun lineColor(line: String): Color {
                    return when {
                        errorRegex.containsMatchIn(line) -> errorColor
                        warnRegex.containsMatchIn(line) -> warnColor
                        else -> normalColor
                    }
                }

                SelectionContainer {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(lines) { line ->
                            Text(
                                text = line,
                                color = lineColor(line),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
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
        ConsoleCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "日志", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "可用于排查启动失败、核心切换与更新问题。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = { showClearConfirm = true }) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("清空")
                }
            }
        }

        val files = logs?.files ?: emptyList()
        if (files.isEmpty()) {
            ConsoleCard {
                Text(text = "暂无日志文件", style = MaterialTheme.typography.titleSmall)
                Text(text = "启动服务后会生成日志。", style = MaterialTheme.typography.bodySmall)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(files, key = { it.path }) { file ->
                    LogItem(
                        file = file,
                        onClick = {
                            viewing = file
                            viewingText = "加载中…"
                            val safe = tailLines.coerceIn(50, 5000)
                            onReadTail(file.path, safe) { viewingText = it }
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
    ConsoleCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Description, contentDescription = null)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = file.name, style = MaterialTheme.typography.titleMedium)
                val size = humanBytes(file.sizeBytes)
                Text(
                    text = "$size • ${file.modifiedAt}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

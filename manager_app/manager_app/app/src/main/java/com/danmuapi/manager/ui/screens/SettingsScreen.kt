package com.danmuapi.manager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberExpandedState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.danmuapi.manager.root.DanmuPaths

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    paddingValues: PaddingValues,
    rootAvailable: Boolean?,
    logAutoCleanDays: Int,
    githubToken: String,
    onSetLogAutoCleanDays: (Int) -> Unit,
    onSetGithubToken: (String) -> Unit,
) {
    var tokenInput by remember(githubToken) { mutableStateOf(githubToken) }

    Column(
        modifier = Modifier
            .padding(paddingValues)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "设置", style = MaterialTheme.typography.titleLarge)

        Card {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Security, contentDescription = null)
                    Text(text = "Root 权限", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    text = when (rootAvailable) {
                        true -> "已授予 / 可用"
                        false -> "不可用（请确认 Magisk 授权）"
                        null -> "检测中…"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "模块数据目录：${DanmuPaths.PERSIST_DIR}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        // Log auto-clean
        Card {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.BatterySaver, contentDescription = null)
                    Text(text = "日志自动清理", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    text = "使用 WorkManager 定时执行（无轮询）。建议设置为 3/7 天，既省电也不占空间。",
                    style = MaterialTheme.typography.bodyMedium,
                )

                val options = listOf(
                    0 to "关闭",
                    1 to "每 1 天",
                    3 to "每 3 天",
                    7 to "每 7 天",
                    30 to "每 30 天",
                )

                var expanded by remember { mutableStateOf(false) }
                val selectedLabel = options.firstOrNull { it.first == logAutoCleanDays }?.second ?: "关闭"

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                ) {
                    OutlinedTextField(
                        value = selectedLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("清理周期") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor(),
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        options.forEach { (days, label) ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    expanded = false
                                    onSetLogAutoCleanDays(days)
                                },
                            )
                        }
                    }
                }
            }
        }

        // GitHub token
        Card {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Key, contentDescription = null)
                    Text(text = "GitHub Token（可选）", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    text = "用于“检查更新”提高 API 限额（不填也能用）。建议创建最小权限的 Fine-grained token，仅勾选 public repo 读取。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = { tokenInput = it },
                    singleLine = true,
                    label = { Text("Token") },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { tokenInput = ""; onSetGithubToken("") }) { Text("清空") }
                    TextButton(onClick = { onSetGithubToken(tokenInput.trim()) }) { Text("保存") }
                }
            }
        }
    }
}

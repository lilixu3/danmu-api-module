package com.danmuapi.manager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.danmuapi.manager.data.model.RequestRecord
import com.danmuapi.manager.ui.components.ManagerCard

@Composable
fun RequestRecordsScreen(
    paddingValues: PaddingValues,
    serviceRunning: Boolean,
    records: List<RequestRecord>,
    todayReqNum: Int,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ManagerCard {
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
                            Text(text = "请求记录", style = MaterialTheme.typography.titleLarge)
                            Text(
                                text = "仅统计核心接口（/api/v2/...）的请求记录。",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        OutlinedButton(onClick = onRefresh) {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("刷新")
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = "今日请求：$todayReqNum",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "记录总数：${records.size}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        if (!serviceRunning) {
            item {
                ManagerCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(text = "服务未运行", style = MaterialTheme.typography.titleMedium)
                        Text(text = "启动服务后才能获取请求记录。", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        } else {
            if (loading && records.isEmpty()) {
                item {
                    ManagerCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            Text(text = "正在加载请求记录...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            if (!error.isNullOrBlank()) {
                item {
                    ManagerCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(text = "加载失败", style = MaterialTheme.typography.titleMedium)
                            Text(text = error, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            if (records.isEmpty() && !loading && error.isNullOrBlank()) {
                item {
                    ManagerCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(text = "暂无请求记录", style = MaterialTheme.typography.titleMedium)
                            Text(text = "产生请求后会自动记录。", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            items(records) { record ->
                RequestRecordCard(record = record)
            }
        }
    }
}

@Composable
private fun RequestRecordCard(record: RequestRecord) {
    val method = record.method.uppercase()
    val methodColor = when (method) {
        "GET" -> MaterialTheme.colorScheme.primary
        "POST" -> MaterialTheme.colorScheme.tertiary
        "PUT", "PATCH" -> MaterialTheme.colorScheme.secondary
        "DELETE" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }

    ManagerCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = method,
                    color = methodColor,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = record.path.ifBlank { "(未提供路径)" },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (record.timestamp.isNotBlank()) {
                Text(
                    text = "时间：${record.timestamp}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (record.clientIp.isNotBlank()) {
                Text(
                    text = "来源：${record.clientIp}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            val params = record.params?.trim().orEmpty()
            if (params.isNotBlank()) {
                Text(text = "参数：", style = MaterialTheme.typography.bodySmall)
                SelectionContainer {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(
                            text = params,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}

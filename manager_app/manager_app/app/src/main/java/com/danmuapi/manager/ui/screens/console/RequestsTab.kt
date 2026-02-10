package com.danmuapi.manager.ui.screens.console

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.danmuapi.manager.data.model.RequestRecord
import com.danmuapi.manager.ui.screens.console.components.*

/**
 * 请求记录 Tab
 */
@Composable
fun RequestsTabContent(
    serviceRunning: Boolean,
    records: List<RequestRecord>,
    todayReqNum: Int,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 统计卡片
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            InfoCard(
                title = "今日请求",
                value = todayReqNum.toString(),
                icon = Icons.Default.TrendingUp,
                modifier = Modifier.weight(1f)
            )
            InfoCard(
                title = "总记录",
                value = records.size.toString(),
                icon = Icons.Default.Storage,
                modifier = Modifier.weight(1f)
            )
        }

        // 请求列表
        Card(
            modifier = Modifier.fillMaxSize(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 工具栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "请求历史",
                        style = MaterialTheme.typography.titleMedium
                    )

                    IconButton(
                        onClick = onRefresh,
                        enabled = serviceRunning && !loading
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }

                HorizontalDivider()

                // 列表内容
                Box(modifier = Modifier.fillMaxSize()) {
                    when {
                        !serviceRunning -> EmptyState(
                            icon = Icons.Default.CloudOff,
                            message = "服务未运行"
                        )

                        loading && records.isEmpty() -> LoadingState()

                        error != null -> ErrorState(
                            message = error,
                            onRetry = onRefresh
                        )

                        records.isEmpty() -> EmptyState(
                            icon = Icons.Default.Inbox,
                            message = "暂无请求记录"
                        )

                        else -> LazyColumn(
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(records) { record ->
                                RequestRecordItem(record)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 请求记录条目
 */
@Composable
private fun RequestRecordItem(record: RequestRecord) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        onClick = { expanded = !expanded }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // 基本信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // HTTP 方法
                        Surface(
                            color = when (record.method?.uppercase()) {
                                "GET" -> MaterialTheme.colorScheme.primaryContainer
                                "POST" -> MaterialTheme.colorScheme.secondaryContainer
                                "PUT" -> MaterialTheme.colorScheme.tertiaryContainer
                                "DELETE" -> MaterialTheme.colorScheme.errorContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = record.method?.uppercase() ?: "?",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }

                        // 路径
                        Text(
                            text = record.path ?: "/",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    // 时间和 IP
                    Text(
                        text = "${record.timestamp ?: ""} • ${record.ip ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 展开详情
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                // 查询参数
                if (!record.query.isNullOrEmpty()) {
                    Text(
                        text = "查询参数",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = record.query,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // User-Agent
                if (!record.userAgent.isNullOrEmpty()) {
                    Text(
                        text = "User-Agent",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = record.userAgent,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

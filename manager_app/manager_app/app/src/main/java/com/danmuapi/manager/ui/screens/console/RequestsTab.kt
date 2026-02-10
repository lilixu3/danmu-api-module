package com.danmuapi.manager.ui.screens.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.danmuapi.manager.data.model.RequestRecord
import com.danmuapi.manager.ui.screens.console.components.ConsoleCard
import com.danmuapi.manager.ui.screens.console.components.MethodBadge
import com.danmuapi.manager.ui.screens.console.components.StatCard

@Composable
fun RequestsTabContent(
    serviceRunning: Boolean,
    records: List<RequestRecord>,
    todayReqNum: Int,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("请求记录", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onRefresh, enabled = serviceRunning && !loading) { Icon(Icons.Default.Refresh, contentDescription = "刷新") }
            }
        }

        // Stats
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(title = "今日请求", value = todayReqNum.toString(), icon = Icons.Default.TrendingUp, modifier = Modifier.weight(1f))
                StatCard(title = "总记录", value = records.size.toString(), icon = Icons.Default.Storage, modifier = Modifier.weight(1f))
            }
        }

        if (!serviceRunning) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.CloudOff, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text("启动服务后才能获取请求记录。", style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            if (loading && records.isEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("正在加载…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (!error.isNullOrBlank()) {
                item { Text("加载失败：$error", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
            if (records.isEmpty() && !loading && error.isNullOrBlank()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Inbox, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("暂无请求记录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(records) { record -> RequestRecordCard(record = record) }
        }
    }
}

@Composable
private fun RequestRecordCard(record: RequestRecord) {
    var expanded by remember { mutableStateOf(false) }

    ConsoleCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded },
        contentPadding = PaddingValues(10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    MethodBadge(method = record.method)
                    Text(record.path.ifBlank { "/" }, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
                }
                Spacer(Modifier.height(4.dp))
                Text("${record.timestamp.ifBlank { "-" }} · ${record.clientIp.ifBlank { "-" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }

        if (expanded) {
            val params = record.params?.trim().orEmpty()
            if (params.isNotBlank()) {
                Text("查询参数", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                    Text(params, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.padding(8.dp))
                }
            } else {
                Text("无参数", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

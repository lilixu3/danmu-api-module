package com.danmuapi.manager.feature.corehub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.danmuapi.manager.core.designsystem.component.SectionCard

private data class CoreCardModel(
    val name: String,
    val detail: String,
    val badge: String,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CoreHubScreen(
    contentPadding: PaddingValues,
) {
    var query by remember { mutableStateOf("") }
    val items = remember {
        listOf(
            CoreCardModel("lilixu3/danmu_api", "main · v2.4.1 · abc1234", "当前"),
            CoreCardModel("huangxd-/danmu_api", "main · v2.3.9 · 98ef712", "可更新"),
            CoreCardModel("custom/source", "release · 未安装", "自定义"),
        )
    }.filter { item ->
        query.isBlank() || item.name.contains(query, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("搜索核心 / owner/repo") },
            singleLine = true,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("全部", "已安装", "当前", "可更新", "自定义").forEach { label ->
                AssistChip(
                    onClick = {},
                    label = { Text(label) },
                )
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            items(items) { item ->
                SectionCard(title = item.name) {
                    Text(
                        text = item.detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = item.badge,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

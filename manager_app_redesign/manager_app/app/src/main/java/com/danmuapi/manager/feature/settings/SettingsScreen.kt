package com.danmuapi.manager.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.danmuapi.manager.core.designsystem.component.SectionCard

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
) {
    val dynamicColor = remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard(title = "外观") {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("动态配色")
                Switch(
                    checked = dynamicColor.value,
                    onCheckedChange = { dynamicColor.value = it },
                )
            }
        }

        SectionCard(title = "日志与省电") {
            Text(
                text = "自动清理、日志行数与省电建议将在这里集中管理。",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        SectionCard(title = "GitHub 与更新") {
            Text(
                text = "模块更新、GitHub Token 与下载策略入口会收敛到这个区域。",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        SectionCard(title = "WebDAV 备份恢复") {
            Text(
                text = "本地导出、WebDAV 备份、恢复确认都将统一在这里。",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        SectionCard(title = "危险操作") {
            Text(
                text = "覆盖导入、删除核心、强制安装等操作会独立隔离。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

package com.danmuapi.manager.feature.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.danmuapi.manager.core.designsystem.component.SectionCard
import com.danmuapi.manager.core.designsystem.theme.DanmuMonoFamily

@Composable
fun ConsoleScreen(
    contentPadding: PaddingValues,
) {
    val tabs = listOf("服务日志", "模块日志", "请求记录", "API 调试", "系统环境")
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(title) },
                )
            }
        }

        SectionCard(
            title = tabs[selectedTabIndex],
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Text(
                text = "10:22:03 INFO  Console redesign scaffold ready\n10:22:08 WARN  Real log source will be connected next\n10:22:10 INFO  Structured tabs are now isolated by feature",
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = DanmuMonoFamily),
            )
        }
    }
}

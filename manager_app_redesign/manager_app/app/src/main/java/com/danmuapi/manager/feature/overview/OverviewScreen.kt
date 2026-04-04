package com.danmuapi.manager.feature.overview

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.danmuapi.manager.core.designsystem.component.HeroCard
import com.danmuapi.manager.core.designsystem.component.MetricPill
import com.danmuapi.manager.core.designsystem.component.SectionCard

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OverviewScreen(
    contentPadding: PaddingValues,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        HeroCard(
            title = "Danmu API Manager",
            subtitle = "专业命令中心风的全新管理器骨架已经建立，下一步开始接 Root、核心与日志能力。",
            statusLabel = "Scaffold Ready",
            actions = {
                FilledTonalButton(onClick = {}) { Text("启动服务") }
                FilledTonalButton(onClick = {}) { Text("检查更新") }
            },
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MetricPill(label = "服务", value = "Stopped")
            MetricPill(label = "模块", value = "v1.0.0")
            MetricPill(label = "核心", value = "未接入")
        }

        SectionCard(title = "访问入口") {
            Text(
                text = "127.0.0.1:9321\n192.168.1.8:9321\nTOKEN · 待接入",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(title = "当前核心") {
            Text(
                text = "核心中心会在下一阶段接入真实仓库、ref、版本与更新状态。",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        SectionCard(title = "任务与提醒") {
            Text(
                text = "当前已完成新目录初始化、应用壳重建与设计系统基线。",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.danmuapi.manager.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Troubleshoot
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danmuapi.manager.BuildConfig
import com.danmuapi.manager.app.state.ManagerViewModel

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    viewModel: ManagerViewModel,
    onBack: () -> Unit,
    onOpenAccess: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenMaintenance: () -> Unit,
    onOpenAdvanced: () -> Unit,
) {
    val palette = rememberSettingsPalette()
    val webDavUrl by viewModel.webDavUrl.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val logCleanIntervalDays by viewModel.logCleanIntervalDays.collectAsStateWithLifecycle()
    val consoleLogLimit by viewModel.consoleLogLimit.collectAsStateWithLifecycle()

    val rootLabel = when (viewModel.rootAvailable) {
        true -> "已就绪"
        false -> "受限"
        null -> "检测中"
    }
    val moduleVersion = viewModel.status?.module?.version?.takeIf { it.isNotBlank() } ?: "--"
    val themeLabel = when (themeMode) {
        1 -> "浅色"
        2 -> "深色"
        else -> "跟随系统"
    }
    val accessSummary = remember(viewModel.moduleUpdateInfo, viewModel.updateInfo) {
        val updateCount = viewModel.updateInfo.values.count { it.updateAvailable }
        when {
            viewModel.moduleUpdateInfo?.hasUpdate == true -> "模块有更新"
            updateCount > 0 -> "$updateCount 个核心可更新"
            else -> "Token / 模块 / 核心"
        }
    }
    val backupSummary = if (webDavUrl.isBlank()) {
        "本地备份 / WebDAV"
    } else {
        "WebDAV 已配置"
    }
    val appearanceSummary = if (dynamicColor) {
        "$themeLabel · 动态配色开启"
    } else {
        "$themeLabel · 动态配色关闭"
    }
    val maintenanceSummary = if (logCleanIntervalDays <= 0) {
        "$consoleLogLimit 行 · 自动清理关闭"
    } else {
        "$consoleLogLimit 行 · $logCleanIntervalDays 天清理"
    }

    SettingsScrollablePage(
        contentPadding = contentPadding,
        palette = palette,
    ) {
        SettingsImmersiveHeader(
            title = "设置中心",
            subtitle = "按使用场景拆开配置，常用项在前，高级工具单独下沉。",
            palette = palette,
            leading = {
                SettingsHeaderIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    palette = palette,
                    onClick = onBack,
                )
            },
        )

        viewModel.busyMessage?.takeIf { it.isNotBlank() }?.let { message ->
            SettingsBusyStrip(
                message = message,
                palette = palette,
            )
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SettingsInfoPill(
                label = "Root",
                value = rootLabel,
                palette = palette,
                tone = when (viewModel.rootAvailable) {
                    true -> palette.positive
                    false -> palette.danger
                    null -> null
                },
            )
            SettingsInfoPill(
                label = "模块版本",
                value = moduleVersion,
                palette = palette,
            )
            SettingsInfoPill(
                label = "App",
                value = BuildConfig.VERSION_NAME,
                palette = palette,
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SettingsNavCard(
                title = "访问与更新",
                subtitle = "管理 GitHub Token，并检查模块与核心的更新状态。",
                icon = Icons.Filled.SystemUpdateAlt,
                summary = accessSummary,
                palette = palette,
                onClick = onOpenAccess,
            )
            SettingsNavCard(
                title = "备份与恢复",
                subtitle = "集中处理 WebDAV 和本地导入导出，保持入口克制。",
                icon = Icons.Filled.CloudDownload,
                summary = backupSummary,
                palette = palette,
                onClick = onOpenBackup,
            )
            SettingsNavCard(
                title = "外观与显示",
                subtitle = "只保留主题模式和动态配色，不堆其它配置。",
                icon = Icons.Filled.Settings,
                summary = appearanceSummary,
                palette = palette,
                onClick = onOpenAppearance,
            )
            SettingsNavCard(
                title = "日志与维护",
                subtitle = "调整日志自动清理周期和记录页的显示范围。",
                icon = Icons.Filled.Troubleshoot,
                summary = maintenanceSummary,
                palette = palette,
                onClick = onOpenMaintenance,
            )
            SettingsNavCard(
                title = "高级工具",
                subtitle = "把 API 调试和服务端环境收进深层入口，保持主路径简洁。",
                icon = Icons.Filled.Settings,
                summary = "API 调试 / 服务端环境",
                palette = palette,
                onClick = onOpenAdvanced,
            )
        }
    }
}

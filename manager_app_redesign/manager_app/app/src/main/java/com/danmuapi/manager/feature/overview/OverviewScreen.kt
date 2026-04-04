package com.danmuapi.manager.feature.overview

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.danmuapi.manager.app.state.ManagerViewModel
import com.danmuapi.manager.core.designsystem.component.CodeBlock
import com.danmuapi.manager.core.designsystem.component.EmptyHint
import com.danmuapi.manager.core.designsystem.component.HeroCard
import com.danmuapi.manager.core.designsystem.component.InfoRow
import com.danmuapi.manager.core.designsystem.component.MetricPill
import com.danmuapi.manager.core.designsystem.component.SectionCard
import com.danmuapi.manager.core.designsystem.component.StatusTag
import com.danmuapi.manager.core.designsystem.component.StatusTone
import com.danmuapi.manager.core.designsystem.component.formatSizeLabel
import com.danmuapi.manager.core.designsystem.component.statusToneForFlag
import com.danmuapi.manager.core.model.CoreRecord
import com.danmuapi.manager.core.util.rememberLanIpv4Addresses

private data class AccessEntry(
    val label: String,
    val url: String,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OverviewScreen(
    contentPadding: PaddingValues,
    viewModel: ManagerViewModel,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val lanIpv4Addresses = rememberLanIpv4Addresses()
    val status = viewModel.status
    val roots = viewModel.rootAvailable
    val activeCore = status?.activeCore ?: remember(status, viewModel.cores) {
        val activeId = status?.activeCoreId ?: viewModel.cores?.activeCoreId
        viewModel.cores?.cores.orEmpty().firstOrNull { it.id == activeId }
    }
    val activeUpdate = activeCore?.id?.let(viewModel.updateInfo::get)
    val accessEntries = remember(viewModel.apiPort, viewModel.apiToken, lanIpv4Addresses) {
        buildList {
            add(
                AccessEntry(
                    label = "本机",
                    url = "http://127.0.0.1:${viewModel.apiPort}/${viewModel.apiToken}",
                ),
            )
            lanIpv4Addresses.forEach { address ->
                add(
                    AccessEntry(
                        label = "局域网",
                        url = "http://$address:${viewModel.apiPort}/${viewModel.apiToken}",
                    ),
                )
            }
        }.distinctBy { it.url }
    }
    val heroStatusLabel = when {
        roots != true -> "ROOT MISSING"
        status?.isRunning == true -> "RUNNING"
        else -> "STOPPED"
    }
    val heroSubtitle = buildString {
        append(
            when {
                roots != true -> "当前未检测到可用 Root，服务控制、核心安装与配置写入都会受限。"
                status?.isRunning == true -> "服务正在运行，当前可以直接查看请求记录、控制台日志与 API 调试结果。"
                else -> "服务当前未运行，可以在这里直接启动，或先检查核心与配置是否准备完成。"
            },
        )
        activeCore?.let {
            append(" 活动核心：")
            append(it.repoDisplayName)
            append('@')
            append(it.ref)
        }
    }
    val reminders = remember(roots, status, activeUpdate, viewModel.moduleUpdateInfo, viewModel.apiHost) {
        buildList {
            if (roots != true) add("需要 Root 权限才能执行服务与核心管理。")
            if (status?.isRunning != true) add("服务未运行，请先启动后再检查在线接口与请求记录。")
            if (status != null && !isAutostartEnabled(status.autostart)) add("自启动当前关闭，重启设备后服务不会自动拉起。")
            if (activeUpdate?.updateAvailable == true) add("当前核心检测到更新，可在总览或核心页一键更新。")
            if (viewModel.moduleUpdateInfo?.hasUpdate == true) add("模块存在新版本，建议在维护窗口内完成升级。")
            if (viewModel.apiHost == "127.0.0.1") add("当前仅监听本机地址，局域网设备无法访问管理接口。")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HeroCard(
            title = "Danmu API Command Center",
            subtitle = heroSubtitle,
            statusLabel = heroStatusLabel,
            actions = {
                if (status?.isRunning == true) {
                    FilledTonalButton(onClick = { viewModel.stopService() }) {
                        Icon(Icons.Filled.StopCircle, contentDescription = null)
                        Text("停止")
                    }
                    ElevatedButton(onClick = { viewModel.restartService() }) {
                        Icon(Icons.Filled.RestartAlt, contentDescription = null)
                        Text("重启")
                    }
                } else {
                    ElevatedButton(onClick = { viewModel.startService() }, enabled = roots == true) {
                        Text("启动服务")
                    }
                }
                FilledTonalIconButton(onClick = { viewModel.refreshAll() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                }
            },
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MetricPill(
                label = "服务",
                value = if (status?.isRunning == true) "Running" else "Stopped",
            )
            MetricPill(
                label = "Root",
                value = when (roots) {
                    true -> "Ready"
                    false -> "Missing"
                    null -> "Checking"
                },
            )
            MetricPill(
                label = "模块",
                value = status?.module?.version ?: "--",
            )
            MetricPill(
                label = "今日请求",
                value = viewModel.todayReqNum.toString(),
            )
            MetricPill(
                label = "活动核心",
                value = activeCore?.repoDisplayName ?: "未激活",
            )
        }

        SectionCard(title = "服务状态") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusTag(
                    text = if (status?.isRunning == true) "服务运行中" else "服务已停止",
                    tone = statusToneForFlag(status?.isRunning),
                )
                StatusTag(
                    text = if (isAutostartEnabled(status?.autostart)) "已开启自启动" else "未开启自启动",
                    tone = if (isAutostartEnabled(status?.autostart)) StatusTone.Info else StatusTone.Neutral,
                )
            }
            InfoRow(label = "模块开关", value = if (status?.module?.enabled == true) "已启用" else "未启用")
            InfoRow(label = "进程 PID", value = status?.service?.pid ?: "--")
            InfoRow(label = "接口监听", value = "${viewModel.apiHost}:${viewModel.apiPort}")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "开机自启动",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(
                    checked = isAutostartEnabled(status?.autostart),
                    onCheckedChange = viewModel::setAutostart,
                    enabled = roots == true,
                )
            }
        }

        SectionCard(title = "访问入口") {
            InfoRow(label = "TOKEN", value = viewModel.apiToken)
            accessEntries.forEach { entry ->
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CodeBlock(text = entry.url)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilledTonalButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(viewModel.apiToken))
                    },
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null)
                    Text("复制 TOKEN")
                }
                OutlinedButton(
                    onClick = {
                        accessEntries.firstOrNull()?.url?.let { url ->
                            clipboardManager.setText(AnnotatedString(url))
                        }
                    },
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null)
                    Text("复制首页链接")
                }
                OutlinedButton(
                    onClick = {
                        accessEntries.firstOrNull()?.url?.let { url ->
                            val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            runCatching { context.startActivity(intent) }
                        }
                    },
                ) {
                    Icon(Icons.Filled.OpenInBrowser, contentDescription = null)
                    Text("浏览器打开")
                }
            }
        }

        SectionCard(title = "当前核心") {
            if (activeCore == null) {
                EmptyHint(
                    title = "暂无活动核心",
                    detail = "请前往“核心”页面安装或切换核心后再启动服务。",
                )
            } else {
                ActiveCoreSummary(
                    core = activeCore,
                    updateAvailable = activeUpdate?.updateAvailable == true,
                    latestVersion = activeUpdate?.latestVersion,
                    latestSha = activeUpdate?.latestCommit?.sha?.take(7),
                    onCheckUpdate = viewModel::checkActiveCoreUpdate,
                )
            }
        }

        SectionCard(title = "模块更新") {
            val moduleUpdate = viewModel.moduleUpdateInfo
            val latestRelease = moduleUpdate?.latestRelease
            InfoRow(label = "当前版本", value = moduleUpdate?.currentVersion ?: status?.module?.version ?: "--")
            InfoRow(label = "最新版本", value = latestRelease?.tagName ?: "--")
            if (latestRelease != null) {
                InfoRow(label = "发布时间", value = latestRelease.publishedAt.ifBlank { "--" })
                InfoRow(label = "发布资源", value = latestRelease.assets.size.toString())
            }
            StatusTag(
                text = when {
                    moduleUpdate == null -> "未检查"
                    moduleUpdate.hasUpdate -> "可更新"
                    else -> "已是最新"
                },
                tone = when {
                    moduleUpdate == null -> StatusTone.Neutral
                    moduleUpdate.hasUpdate -> StatusTone.Warning
                    else -> StatusTone.Positive
                },
            )
            FilledTonalButton(onClick = viewModel::checkModuleUpdate) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Text("检查模块更新")
            }
        }

        SectionCard(title = "任务与提醒") {
            if (reminders.isEmpty()) {
                EmptyHint(
                    title = "当前没有阻塞项",
                    detail = "Root、核心、服务与模块状态都比较健康，可以继续进行接口调试或配置维护。",
                )
            } else {
                reminders.forEach { reminder ->
                    Text(
                        text = "• $reminder",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveCoreSummary(
    core: CoreRecord,
    updateAvailable: Boolean,
    latestVersion: String?,
    latestSha: String?,
    onCheckUpdate: () -> Unit,
) {
    InfoRow(label = "仓库", value = core.repoDisplayName)
    InfoRow(label = "Ref", value = core.ref)
    InfoRow(label = "版本", value = core.version ?: "--")
    InfoRow(label = "Commit", value = core.commitLabel ?: "--")
    InfoRow(label = "体积", value = core.sizeBytes?.formatSizeLabel() ?: "--")
    InfoRow(label = "安装时间", value = core.installedAt ?: "--")
    StatusTag(
        text = when {
            updateAvailable && latestVersion != null -> "发现更新 · $latestVersion"
            updateAvailable -> "发现更新"
            else -> "当前已是最新"
        },
        tone = if (updateAvailable) StatusTone.Warning else StatusTone.Positive,
    )
    latestSha?.let {
        InfoRow(label = "最新 Commit", value = it)
    }
    FilledTonalButton(onClick = onCheckUpdate) {
        Icon(Icons.Filled.Refresh, contentDescription = null)
        Text("检查当前核心更新")
    }
}

private fun isAutostartEnabled(raw: String?): Boolean {
    val value = raw.orEmpty().trim().lowercase()
    return value in setOf("on", "enabled", "enable", "true", "1", "yes")
}

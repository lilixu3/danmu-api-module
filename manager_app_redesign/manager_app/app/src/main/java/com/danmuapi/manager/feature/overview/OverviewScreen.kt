package com.danmuapi.manager.feature.overview

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.danmuapi.manager.app.state.ManagerViewModel
import com.danmuapi.manager.core.designsystem.component.ActionHintRow
import com.danmuapi.manager.core.designsystem.component.CodeBlock
import com.danmuapi.manager.core.designsystem.component.EmptyHint
import com.danmuapi.manager.core.designsystem.component.InfoRow
import com.danmuapi.manager.core.designsystem.component.MetricPill
import com.danmuapi.manager.core.designsystem.component.PageHeader
import com.danmuapi.manager.core.designsystem.component.SectionCard
import com.danmuapi.manager.core.designsystem.component.StatusTag
import com.danmuapi.manager.core.designsystem.component.StatusTone
import com.danmuapi.manager.core.model.CoreRecord
import com.danmuapi.manager.core.util.rememberLanIpv4Addresses

private data class AccessEntry(
    val label: String,
    val url: String,
)

private data class HomeReminder(
    val title: String,
    val detail: String,
    val tone: StatusTone,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
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
    val rootReady = viewModel.rootAvailable == true
    val activeCore = status?.activeCore ?: remember(status, viewModel.cores) {
        val activeId = status?.activeCoreId ?: viewModel.cores?.activeCoreId
        viewModel.cores?.cores.orEmpty().firstOrNull { it.id == activeId }
    }
    val activeUpdate = activeCore?.id?.let(viewModel.updateInfo::get)
    val moduleUpdate = viewModel.moduleUpdateInfo
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri != null) {
            viewModel.exportEnvToUri(uri)
        }
    }

    val accessEntries = remember(viewModel.apiPort, viewModel.apiToken, viewModel.apiHost, lanIpv4Addresses) {
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

    val reminders = buildList {
        if (!rootReady) {
            add(
                HomeReminder(
                    title = "Root 未就绪",
                    detail = "Root 不可用时，服务控制、核心管理和配置写入都会受限。",
                    tone = StatusTone.Warning,
                    actionLabel = "重新刷新",
                    onAction = viewModel::refreshAll,
                ),
            )
        }
        if (status?.isRunning != true) {
            add(
                HomeReminder(
                    title = "服务已停止",
                    detail = "当前无法接收请求，建议先启动服务再看日志和请求记录。",
                    tone = StatusTone.Negative,
                    actionLabel = "启动服务",
                    onAction = if (rootReady) viewModel::startService else null,
                ),
            )
        }
        if (status != null && !isAutostartEnabled(status.autostart)) {
            add(
                HomeReminder(
                    title = "未开启自启动",
                    detail = "设备重启后服务不会自动拉起。",
                    tone = StatusTone.Neutral,
                    actionLabel = if (rootReady) "立即开启" else null,
                    onAction = if (rootReady) ({ viewModel.setAutostart(true) }) else null,
                ),
            )
        }
        if (activeUpdate?.updateAvailable == true) {
            add(
                HomeReminder(
                    title = "核心可更新",
                    detail = "当前核心检测到新版本，可先检查变更后再执行更新。",
                    tone = StatusTone.Warning,
                    actionLabel = "检查当前核心",
                    onAction = viewModel::checkActiveCoreUpdate,
                ),
            )
        }
        if (moduleUpdate?.hasUpdate == true) {
            add(
                HomeReminder(
                    title = "模块可更新",
                    detail = "模块存在新版本，建议在空闲时段执行升级。",
                    tone = StatusTone.Warning,
                    actionLabel = "检查模块更新",
                    onAction = viewModel::checkModuleUpdate,
                ),
            )
        }
        if (activeCore == null) {
            add(
                HomeReminder(
                    title = "没有活动核心",
                    detail = "先安装或切换核心，再启动服务。",
                    tone = StatusTone.Warning,
                ),
            )
        }
    }.take(3)

    var showAccessPanel by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PageHeader(
            title = "主页",
            subtitle = "先看服务状态，再决定是启动、排查还是维护。",
            modifier = Modifier.padding(top = 12.dp),
        )

        SectionCard(
            title = "状态总览",
            subtitle = "把最重要的状态压缩在一个区块里，避免首屏堆满说明和链接。",
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MetricPill(
                    label = "服务",
                    value = if (status?.isRunning == true) "运行中" else "已停止",
                )
                MetricPill(
                    label = "Root",
                    value = when (viewModel.rootAvailable) {
                        true -> "已就绪"
                        false -> "不可用"
                        null -> "检测中"
                    },
                )
                MetricPill(
                    label = "当前核心",
                    value = activeCore?.repoDisplayName ?: "未激活",
                )
                MetricPill(
                    label = "今日请求",
                    value = viewModel.todayReqNum.toString(),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatusTag(
                        text = if (status?.isRunning == true) "服务运行中" else "服务已停止",
                        tone = if (status?.isRunning == true) StatusTone.Positive else StatusTone.Negative,
                    )
                    StatusTag(
                        text = if (isAutostartEnabled(status?.autostart)) "已开启自启动" else "未开启自启动",
                        tone = if (isAutostartEnabled(status?.autostart)) StatusTone.Info else StatusTone.Neutral,
                    )
                }
                Switch(
                    checked = isAutostartEnabled(status?.autostart),
                    onCheckedChange = viewModel::setAutostart,
                    enabled = rootReady,
                )
            }
        }

        SectionCard(
            title = "主控制",
            subtitle = "只保留高频操作，其他维护动作放到下面的快捷任务。",
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
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
                    ElevatedButton(onClick = { viewModel.startService() }, enabled = rootReady) {
                        Text("启动服务")
                    }
                }
                OutlinedButton(onClick = viewModel::refreshAll) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Text("刷新状态")
                }
            }
        }

        SectionCard(
            title = "当前实例",
            subtitle = "这里保留当前模块、核心与接口的摘要，不混入低频设置项。",
        ) {
            ActiveInstanceSummary(
                activeCore = activeCore,
                apiHost = viewModel.apiHost,
                apiPort = viewModel.apiPort,
                moduleVersion = status?.module?.version ?: "--",
                activeUpdateText = when {
                    activeUpdate?.updateAvailable == true -> "检测到更新"
                    activeUpdate != null -> "已是最新"
                    else -> "未检查"
                },
            )
        }

        SectionCard(
            title = "最近提醒",
            subtitle = "只展示当前最需要处理的 3 件事。",
        ) {
            if (reminders.isEmpty()) {
                EmptyHint(
                    title = "当前没有阻塞项",
                    detail = "服务、核心和模块状态都比较稳定，可以继续监控或维护。",
                )
            } else {
                reminders.forEach { reminder ->
                    ActionHintRow(
                        title = reminder.title,
                        detail = reminder.detail,
                        tone = reminder.tone,
                        actionLabel = reminder.actionLabel,
                        onAction = reminder.onAction,
                    )
                }
            }
        }

        SectionCard(
            title = "快捷任务",
            subtitle = "把更新检查和配置导出保留在首页，但不和主控制混排。",
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilledTonalButton(onClick = viewModel::checkActiveCoreUpdate, enabled = activeCore != null) {
                    Text("检查当前核心")
                }
                FilledTonalButton(onClick = viewModel::checkModuleUpdate) {
                    Text("检查模块更新")
                }
                OutlinedButton(onClick = { exportLauncher.launch("danmu_api.env") }) {
                    Text("导出配置")
                }
            }
        }

        SectionCard(
            title = "访问入口",
            subtitle = if (showAccessPanel) "默认收起，避免首屏被 URL 和 TOKEN 干扰。" else "需要时再展开查看 TOKEN 和访问地址。",
            action = {
                TextButton(onClick = { showAccessPanel = !showAccessPanel }) {
                    Text(if (showAccessPanel) "收起" else "展开")
                }
            },
        ) {
            if (!showAccessPanel) {
                Text(
                    text = "当前监听 ${viewModel.apiHost}:${viewModel.apiPort}，展开后可复制 TOKEN 和访问链接。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
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
                        onClick = { clipboardManager.setText(AnnotatedString(viewModel.apiToken)) },
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
                        Text("复制链接")
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
        }
    }
}

@Composable
private fun ActiveInstanceSummary(
    activeCore: CoreRecord?,
    apiHost: String,
    apiPort: Int,
    moduleVersion: String,
    activeUpdateText: String,
) {
    if (activeCore == null) {
        EmptyHint(
            title = "还没有活动核心",
            detail = "前往核心页安装或切换核心后，这里会显示版本、提交和更新时间。",
        )
        return
    }

    InfoRow(label = "模块版本", value = moduleVersion)
    InfoRow(label = "核心仓库", value = activeCore.repoDisplayName)
    InfoRow(label = "Ref", value = activeCore.ref)
    InfoRow(label = "版本", value = activeCore.version ?: "--")
    InfoRow(label = "Commit", value = activeCore.commitLabel ?: "--")
    InfoRow(label = "安装时间", value = activeCore.installedAt ?: "--")
    InfoRow(label = "体积", value = activeCore.sizeBytes?.let { "${it} B" } ?: "--")
    InfoRow(label = "接口监听", value = "$apiHost:$apiPort")
    InfoRow(label = "更新状态", value = activeUpdateText)
}

private fun isAutostartEnabled(raw: String?): Boolean {
    val value = raw.orEmpty().trim().lowercase()
    return value in setOf("on", "enabled", "enable", "true", "1", "yes")
}

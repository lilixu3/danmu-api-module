package com.danmuapi.manager.feature.corehub

import com.danmuapi.manager.core.designsystem.component.formatSizeLabel
import com.danmuapi.manager.core.model.CoreRecord
import com.danmuapi.manager.core.model.CoreUpdateInfo
import com.danmuapi.manager.core.model.CoreUpdateState

internal data class CoreDetailQuickStat(
    val label: String,
    val value: String,
)

internal object CoreHubSummaryFormatter {
    fun headerSubtitle(
        activeCore: CoreRecord?,
        installedCount: Int,
    ): String {
        return when {
            activeCore != null -> {
                val name = activeCore.repoDisplayName.substringAfterLast('/').ifBlank { activeCore.repoDisplayName }
                "当前 $name · 已安装 $installedCount 个"
            }
            installedCount == 0 -> "还没有已安装核心"
            else -> "已安装 $installedCount 个核心 · 当前未选择"
        }
    }

    fun currentCoreMetaLine(core: CoreRecord?): String {
        return currentCoreMetaLine(core = core, updateInfo = null)
    }

    fun currentCoreMetaLine(
        core: CoreRecord?,
        updateInfo: CoreUpdateInfo?,
    ): String {
        if (core == null) return "安装后可在这里查看分支、版本和提交"
        return buildList {
            add(core.ref)
            versionLabel(core, updateInfo).takeIf { it.isNotBlank() && it != "--" }?.let(::add)
            core.commitLabel?.takeIf { it.isNotBlank() }?.let(::add)
        }.joinToString(" · ")
    }

    fun currentStateBadge(
        core: CoreRecord?,
        updateInfo: CoreUpdateInfo?,
    ): String {
        return when {
            core == null -> "未安装"
            updateInfo?.updateAvailable == true -> "可更新"
            updateInfo?.state == CoreUpdateState.Unknown -> "未确认"
            else -> "已对齐"
        }
    }

    fun primaryActionLabel(
        core: CoreRecord?,
        updateInfo: CoreUpdateInfo?,
    ): String {
        return when {
            core == null -> "安装新核心"
            updateInfo?.updateAvailable == true -> "安装更新"
            else -> "重新安装"
        }
    }

    fun coreListStateBadge(
        isActive: Boolean,
        updateInfo: CoreUpdateInfo?,
    ): String {
        val updateAvailable = updateInfo?.updateAvailable == true
        val updateUnknown = updateInfo != null && updateInfo.state == CoreUpdateState.Unknown
        return when {
            isActive && updateAvailable -> "当前 · 可更新"
            updateAvailable -> "可更新"
            isActive -> "当前"
            updateUnknown -> "未确认"
            else -> "已安装"
        }
    }

    fun versionLabel(
        core: CoreRecord?,
        updateInfo: CoreUpdateInfo?,
    ): String {
        if (core == null) return "等待安装"
        val current = core.version?.takeIf { it.isNotBlank() }
            ?: core.commitLabel?.takeIf { it.isNotBlank() }
            ?: "--"
        if (updateInfo?.updateAvailable != true) return current

        val latest = updateInfo.latestVersion?.takeIf { it.isNotBlank() }
            ?: updateInfo.latestCommit?.sha?.takeIf { it.isNotBlank() }?.take(7)
            ?: "新版本"
        return if (latest == current) {
            "$current ↑ 新版本"
        } else {
            "$current ↑ $latest"
        }
    }

    fun displayCommitLabel(
        core: CoreRecord,
        updateInfo: CoreUpdateInfo?,
    ): String {
        core.commitLabel?.takeIf { it.isNotBlank() }?.let { return it }
        if (updateInfo?.state == CoreUpdateState.UpToDate && updateInfo.latestCommit?.sha?.isNotBlank() == true) {
            return updateInfo.latestCommit.sha.take(7)
        }
        updateInfo?.currentCommit?.takeIf { it.isNotBlank() }?.let { return it }
        return "提交未知"
    }

    fun detailQuickStats(core: CoreRecord): List<CoreDetailQuickStat> {
        return listOf(
            CoreDetailQuickStat("分支", core.ref.ifBlank { "--" }),
            CoreDetailQuickStat("提交", displayCommitLabel(core, updateInfo = null)),
            CoreDetailQuickStat("大小", core.sizeBytes?.formatSizeLabel() ?: "--"),
        )
    }
}

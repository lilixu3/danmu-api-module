package com.danmuapi.manager.feature.corehub

import com.danmuapi.manager.core.model.CoreRecord
import com.danmuapi.manager.core.model.CoreUpdateInfo
import com.danmuapi.manager.core.model.CoreUpdateState
import com.danmuapi.manager.core.model.LatestCommitInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CoreHubSummaryFormatterTest {
    @Test
    fun headerSubtitleShowsActiveCoreAndInstalledCount() {
        val activeCore = CoreRecord(
            id = "active",
            repo = "lilixu3/danmu_api",
            ref = "main",
        )

        val subtitle = CoreHubSummaryFormatter.headerSubtitle(
            activeCore = activeCore,
            installedCount = 3,
        )

        assertEquals("当前 danmu_api · 已安装 3 个", subtitle)
    }

    @Test
    fun currentCoreMetaLineCombinesRefVersionAndCommit() {
        val core = CoreRecord(
            id = "active",
            repo = "lilixu3/danmu_api",
            ref = "main",
            version = "v1.6.12",
            shaShort = "8f23d9a",
        )

        val summary = CoreHubSummaryFormatter.currentCoreMetaLine(core)

        assertEquals("main · v1.6.12 · 8f23d9a", summary)
    }

    @Test
    fun currentStateBadgeUsesUpdateWhenAvailable() {
        val badge = CoreHubSummaryFormatter.currentStateBadge(
            core = CoreRecord(id = "active", repo = "lilixu3/danmu_api", ref = "main"),
            updateInfo = CoreUpdateInfo(
                latestCommit = LatestCommitInfo(sha = "1234567abcdef"),
                latestVersion = "v1.6.14",
                updateAvailable = true,
                state = CoreUpdateState.UpdateAvailable,
            ),
        )

        assertEquals("可更新", badge)
    }

    @Test
    fun primaryActionFallsBackToInstallWhenNoCore() {
        val label = CoreHubSummaryFormatter.primaryActionLabel(
            core = null,
            updateInfo = null,
        )

        assertEquals("安装新核心", label)
    }

    @Test
    fun detailQuickStatsShowRefCommitAndSize() {
        val core = CoreRecord(
            id = "active",
            repo = "huangxd-/danmu_api",
            ref = "main",
            shaShort = "8f23d9a",
            sizeBytes = 32L * 1024 * 1024,
        )

        val stats = CoreHubSummaryFormatter.detailQuickStats(core)

        assertEquals(listOf("分支", "提交", "大小"), stats.map { it.label })
        assertEquals("main", stats[0].value)
        assertEquals("8f23d9a", stats[1].value)
        assertFalse(stats[2].value.isBlank())
    }
}

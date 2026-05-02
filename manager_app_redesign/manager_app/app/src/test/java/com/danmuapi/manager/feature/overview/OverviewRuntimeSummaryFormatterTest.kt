package com.danmuapi.manager.feature.overview

import com.danmuapi.manager.core.model.CoreUpdateInfo
import com.danmuapi.manager.core.model.CoreUpdateState
import com.danmuapi.manager.core.model.LatestCommitInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class OverviewRuntimeSummaryFormatterTest {
    @Test
    fun runningProcessShowsPidCoreVersionAndElapsedTime() {
        val items = OverviewRuntimeSummaryFormatter.buildItems(
            running = true,
            pid = "14528",
            coreVersion = "1.4.2",
            elapsedSeconds = 3661L,
        )

        assertEquals("服务进程", items[0].label)
        assertEquals("PID 14528", items[0].value)
        assertEquals("核心版本", items[1].label)
        assertEquals("1.4.2", items[1].value)
        assertEquals("运行时间", items[2].label)
        assertEquals("1:01:01", items[2].value)
    }

    @Test
    fun stoppedProcessShowsFallbackValues() {
        val items = OverviewRuntimeSummaryFormatter.buildItems(
            running = false,
            pid = null,
            coreVersion = null,
            elapsedSeconds = null,
        )

        assertEquals("未运行", items[0].value)
        assertEquals("--", items[1].value)
        assertEquals("未运行", items[2].value)
    }

    @Test
    fun coreVersionShowsUpdateTargetWhenAvailable() {
        val items = OverviewRuntimeSummaryFormatter.buildItems(
            running = true,
            pid = "14528",
            coreVersion = "1.4.2",
            elapsedSeconds = 3661L,
            coreUpdateInfo = CoreUpdateInfo(
                latestCommit = LatestCommitInfo(sha = "1234567abcdef"),
                latestVersion = "1.4.3",
                updateAvailable = true,
                state = CoreUpdateState.UpdateAvailable,
            ),
        )

        assertEquals("核心版本", items[1].label)
        assertEquals("1.4.2 ↑ 1.4.3", items[1].value)
    }

    @Test
    fun formatElapsedSecondsKeepsSecondPrecision() {
        assertEquals("00:59", OverviewRuntimeSummaryFormatter.formatElapsedSeconds(59))
        assertEquals("12:05", OverviewRuntimeSummaryFormatter.formatElapsedSeconds(725))
    }
}

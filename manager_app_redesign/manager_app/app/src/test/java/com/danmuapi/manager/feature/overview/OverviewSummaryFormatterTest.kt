package com.danmuapi.manager.feature.overview

import org.junit.Assert.assertEquals
import org.junit.Test

class OverviewSummaryFormatterTest {
    @Test
    fun runningServiceShowsPortOnly() {
        val summary = OverviewSummaryFormatter.serviceSummary(
            running = true,
            rootReady = true,
            hasActiveCore = true,
            port = 9321,
        )

        assertEquals("端口 9321", summary)
    }

    @Test
    fun rootUnavailableShowsLimitedHint() {
        val summary = OverviewSummaryFormatter.serviceSummary(
            running = false,
            rootReady = false,
            hasActiveCore = true,
            port = 9321,
        )

        assertEquals("Root 受限", summary)
    }

    @Test
    fun noCoreShowsMissingCoreHint() {
        val summary = OverviewSummaryFormatter.serviceSummary(
            running = false,
            rootReady = true,
            hasActiveCore = false,
            port = 9321,
        )

        assertEquals("未加载核心", summary)
    }
}

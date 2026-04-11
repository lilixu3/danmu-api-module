package com.danmuapi.manager.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoreModelsTest {
    @Test
    fun commitLabelFallsBackToShaWhenShortShaIsBlank() {
        val core = CoreRecord(
            repo = "lilixu3/danmu_api",
            ref = "main",
            sha = "1234567890abcdef1234567890abcdef12345678",
            shaShort = "",
        )

        assertEquals("1234567", core.commitLabel)
    }

    @Test
    fun commitLabelFallsBackToCommitLikeRefWhenShaIsMissing() {
        val core = CoreRecord(
            repo = "lilixu3/danmu_api",
            ref = "abcdef1234567890",
            sha = null,
            shaShort = null,
        )

        assertEquals("abcdef1", core.commitLabel)
    }

    @Test
    fun commitLabelReturnsNullWhenNoCommitInformationExists() {
        val core = CoreRecord(
            repo = "lilixu3/danmu_api",
            ref = "main",
            sha = null,
            shaShort = "",
        )

        assertNull(core.commitLabel)
    }
}

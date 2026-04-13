package com.danmuapi.manager.core.data

import com.danmuapi.manager.core.model.CoreUpdateInfo
import com.danmuapi.manager.core.model.CoreUpdateState
import com.danmuapi.manager.core.model.LatestCommitInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreUpdateCacheCodecTest {

    @Test
    fun `encode and decode round trip preserves update info`() {
        val original = linkedMapOf(
            "main" to CoreUpdateInfo(
                latestCommit = LatestCommitInfo(
                    sha = "abcdef1234567890",
                    message = "feat: refresh core metadata",
                    date = "2026-04-14T08:00:00Z",
                ),
                latestVersion = "1.6.14",
                updateAvailable = true,
                state = CoreUpdateState.UpdateAvailable,
                currentVersion = "1.6.13",
                currentCommit = "1234567",
            ),
            "stable" to CoreUpdateInfo(
                latestVersion = "1.6.13",
                updateAvailable = false,
                state = CoreUpdateState.UpToDate,
                currentVersion = "1.6.13",
                currentCommit = "7654321",
            ),
        )

        val encoded = encodeCoreUpdateInfoCache(original)
        val decoded = decodeCoreUpdateInfoCache(encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun `decode returns empty map on invalid payload`() {
        val decoded = decodeCoreUpdateInfoCache("not-json")

        assertTrue(decoded.isEmpty())
    }
}

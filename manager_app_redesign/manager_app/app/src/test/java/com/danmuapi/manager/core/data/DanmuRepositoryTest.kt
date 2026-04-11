package com.danmuapi.manager.core.data

import com.danmuapi.manager.core.model.CoreRecord
import com.danmuapi.manager.core.model.CoreUpdateState
import com.danmuapi.manager.core.model.LatestCommitInfo
import com.danmuapi.manager.core.model.RollbackCommitItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DanmuRepositoryTest {

    @Test
    fun `resolveCoreUpdateState returns update when remote sha differs`() {
        val state = resolveCoreUpdateState(
            core = CoreRecord(
                repo = "lilixu3/danmu_api",
                ref = "main",
                sha = "1111111111111111111111111111111111111111",
                version = "1.18.3",
            ),
            latestCommit = LatestCommitInfo(
                sha = "2222222222222222222222222222222222222222",
            ),
            latestVersion = "1.18.4",
        )

        assertEquals(CoreUpdateState.UpdateAvailable, state)
    }

    @Test
    fun `resolveCoreUpdateState returns up to date when sha matches`() {
        val state = resolveCoreUpdateState(
            core = CoreRecord(
                repo = "lilixu3/danmu_api",
                ref = "main",
                sha = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                version = "1.18.4",
            ),
            latestCommit = LatestCommitInfo(
                sha = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            ),
            latestVersion = "1.18.4",
        )

        assertEquals(CoreUpdateState.UpToDate, state)
    }

    @Test
    fun `resolveCoreUpdateState returns update when local version is stale even if sha is unavailable`() {
        val state = resolveCoreUpdateState(
            core = CoreRecord(
                repo = "lilixu3/danmu_api",
                ref = "main",
                sha = null,
                version = "1.6.11",
            ),
            latestCommit = null,
            latestVersion = "1.6.12",
        )

        assertEquals(CoreUpdateState.UpdateAvailable, state)
    }

    @Test
    fun `resolveCoreUpdateState falls back to version when local sha missing and remote version is newer`() {
        val state = resolveCoreUpdateState(
            core = CoreRecord(
                repo = "lilixu3/danmu_api",
                ref = "main",
                sha = null,
                version = "1.18.3",
            ),
            latestCommit = null,
            latestVersion = "1.18.4",
        )

        assertEquals(CoreUpdateState.UpdateAvailable, state)
    }

    @Test
    fun `resolveCoreUpdateState treats same version as up to date when commit is unavailable`() {
        val state = resolveCoreUpdateState(
            core = CoreRecord(
                repo = "lilixu3/danmu_api",
                ref = "main",
                sha = null,
                version = "1.18.4",
            ),
            latestCommit = null,
            latestVersion = "1.18.4",
        )

        assertEquals(CoreUpdateState.UpToDate, state)
    }

    @Test
    fun `resolveCoreUpdateState treats older remote version as up to date when commit is unavailable`() {
        val state = resolveCoreUpdateState(
            core = CoreRecord(
                repo = "lilixu3/danmu_api",
                ref = "main",
                sha = null,
                version = "1.18.4",
            ),
            latestCommit = null,
            latestVersion = "1.18.3",
        )

        assertEquals(CoreUpdateState.UpToDate, state)
    }

    @Test
    fun `rollback commit item exposes fallback labels`() {
        val item = RollbackCommitItem(
            sha = "1234567890abcdef",
            shortSha = "1234567",
        )

        assertEquals("版本未知", item.versionLabel)
        assertEquals("无提交标题", item.titleLabel)
    }

    @Test
    fun `rollback search filters by normalized version`() {
        val commits = listOf(
            RollbackCommitItem(sha = "a", shortSha = "a", version = "v1.10.2"),
            RollbackCommitItem(sha = "b", shortSha = "b", version = "1.10.1"),
            RollbackCommitItem(sha = "c", shortSha = "c", version = null),
        )
        val normalizedQuery = "v1.10.2".trim().removePrefix("v")

        val filtered = commits.filter { item ->
            item.version?.trim()?.removePrefix("v")?.equals(normalizedQuery, ignoreCase = true) == true
        }

        assertEquals(1, filtered.size)
        assertEquals("a", filtered.first().sha)
        assertFalse(filtered.any { it.sha == "b" })
        assertTrue(filtered.none { it.sha == "c" })
    }
}

package com.danmuapi.manager.core.data

import com.danmuapi.manager.core.model.CoreRecord
import com.danmuapi.manager.core.model.CoreUpdateState
import com.danmuapi.manager.core.model.LatestCommitInfo
import org.junit.Assert.assertEquals
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
}

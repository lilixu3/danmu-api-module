package com.danmuapi.manager.core.data

import com.danmuapi.manager.core.data.network.GitHubApi
import com.danmuapi.manager.core.data.network.GitHubReleaseApi
import com.danmuapi.manager.core.model.CoreRecord
import com.danmuapi.manager.core.model.CoreUpdateInfo
import com.danmuapi.manager.core.model.CoreUpdateState
import com.danmuapi.manager.core.model.LatestCommitInfo
import com.danmuapi.manager.core.model.ManagerStatus
import com.danmuapi.manager.core.model.ModuleRelease
import com.danmuapi.manager.core.model.ModuleUpdateInfo
import com.danmuapi.manager.core.model.ReleaseAsset
import com.danmuapi.manager.core.model.RollbackCommitItem
import com.danmuapi.manager.core.model.RollbackCommitPage
import com.danmuapi.manager.core.model.RollbackSearchSnapshot
import com.danmuapi.manager.core.root.DanmuCli

internal fun compareVersions(left: String, right: String): Int {
    fun parseParts(value: String): List<Int> {
        return value
            .removePrefix("v")
            .trim()
            .split('.')
            .mapNotNull { part ->
                Regex("^(\\d+)").find(part.trim())
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toIntOrNull()
            }
    }

    val leftParts = parseParts(left)
    val rightParts = parseParts(right)
    val maxLength = maxOf(leftParts.size, rightParts.size)
    for (index in 0 until maxLength) {
        val leftValue = leftParts.getOrElse(index) { 0 }
        val rightValue = rightParts.getOrElse(index) { 0 }
        if (leftValue != rightValue) {
            return leftValue.compareTo(rightValue)
        }
    }
    return 0
}

internal fun resolveCoreUpdateState(
    core: CoreRecord,
    latestCommit: LatestCommitInfo?,
    latestVersion: String?,
): CoreUpdateState {
    val localSha = core.sha?.trim().orEmpty()
    val remoteSha = latestCommit?.sha?.trim().orEmpty()
    if (localSha.isNotEmpty() && remoteSha.isNotEmpty()) {
        return if (localSha.equals(remoteSha, ignoreCase = true)) {
            CoreUpdateState.UpToDate
        } else {
            CoreUpdateState.UpdateAvailable
        }
    }

    val localVersion = core.version?.trim().orEmpty()
    val remoteVersion = latestVersion?.trim().orEmpty()
    if (localVersion.isNotEmpty() && remoteVersion.isNotEmpty()) {
        return if (compareVersions(remoteVersion, localVersion) > 0) {
            CoreUpdateState.UpdateAvailable
        } else {
            CoreUpdateState.UpToDate
        }
    }

    return CoreUpdateState.Unknown
}

class DanmuRepository(
    private val cli: DanmuCli = DanmuCli(),
    private val gitHubApi: GitHubApi = GitHubApi(),
    private val gitHubReleaseApi: GitHubReleaseApi = GitHubReleaseApi(),
) {
    suspend fun getStatus(): ManagerStatus? = cli.getStatus()

    suspend fun getProcessElapsedSeconds(pid: String): Long? = cli.getProcessElapsedSeconds(pid)

    suspend fun listCores() = cli.listCores()

    suspend fun listLogs() = cli.listLogs()

    suspend fun startService(): Boolean = cli.startService()

    suspend fun stopService(): Boolean = cli.stopService()

    suspend fun restartService(): Boolean = cli.restartService()

    suspend fun setAutostart(enabled: Boolean): Boolean = cli.setAutostart(enabled)

    suspend fun installCore(repo: String, ref: String): Boolean = cli.installCore(repo, ref)

    suspend fun activateCore(id: String): Boolean = cli.activateCore(id)

    suspend fun deleteCore(id: String): Boolean = cli.deleteCore(id)

    suspend fun clearLogs(): Boolean = cli.clearLogs()

    suspend fun tailLog(path: String, lines: Int = 200): String? = cli.tailLog(path, lines)

    suspend fun readEnvFile(): String? = cli.readEnvFile()

    suspend fun writeEnvFile(content: String): Boolean = cli.writeEnvFile(content)

    suspend fun listRollbackCommits(
        core: CoreRecord,
        page: Int,
        pageSize: Int,
        token: String?,
        versionQuery: String? = null,
    ): Pair<RollbackCommitPage, RollbackSearchSnapshot> {
        if (core.repo.isBlank() || core.ref.isBlank()) {
            return RollbackCommitPage() to RollbackSearchSnapshot(query = versionQuery.orEmpty())
        }

        val commits = gitHubApi.listCommits(
            repo = core.repo,
            ref = core.ref,
            page = page,
            perPage = pageSize,
            token = token,
        )
        val enriched = commits.map { commit ->
            commit.copy(version = gitHubApi.getRemoteCoreVersion(core.repo, commit.sha))
        }
        val normalizedQuery = versionQuery.orEmpty().trim().removePrefix("v")
        val filtered = if (normalizedQuery.isBlank()) {
            enriched
        } else {
            enriched.filter { item ->
                item.version?.trim()?.removePrefix("v")?.equals(normalizedQuery, ignoreCase = true) == true
            }
        }
        return RollbackCommitPage(
            commits = filtered,
            page = page,
            pageSize = pageSize,
            hasNextPage = commits.size >= pageSize,
        ) to RollbackSearchSnapshot(
            query = versionQuery.orEmpty(),
            scannedCount = commits.size,
            matchedCount = filtered.size,
        )
    }

    suspend fun checkUpdate(core: CoreRecord, token: String?): CoreUpdateInfo {
        if (core.repo.isBlank() || core.ref.isBlank()) {
            return CoreUpdateInfo()
        }

        val latestVersion = gitHubApi.getRemoteCoreVersion(
            repo = core.repo,
            refOrSha = core.ref,
        )
        val latestCommit = token?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { safeToken ->
                gitHubApi.getLatestCommit(core.repo, core.ref, safeToken)
            }
        val state = resolveCoreUpdateState(
            core = core,
            latestCommit = latestCommit,
            latestVersion = latestVersion,
        )

        return CoreUpdateInfo(
            latestCommit = latestCommit,
            latestVersion = latestVersion,
            updateAvailable = state == CoreUpdateState.UpdateAvailable,
            state = state,
            currentVersion = core.version,
            currentCommit = core.commitLabel,
        )
    }

    suspend fun checkModuleUpdate(currentVersion: String?): ModuleUpdateInfo {
        val latestRelease = try {
            gitHubReleaseApi.getLatestRelease("lilixu3", "danmu-api-module")
        } catch (_: Throwable) {
            null
        } ?: return ModuleUpdateInfo()

        val latestTag = latestRelease.tagName.orEmpty()
        val hasUpdate = if (currentVersion.isNullOrBlank() || latestTag.isBlank()) {
            false
        } else {
            compareVersions(latestTag, currentVersion) > 0
        }

        val assets = latestRelease.assets
            ?.mapNotNull { asset ->
                val name = asset.name ?: return@mapNotNull null
                val url = asset.browserDownloadUrl ?: return@mapNotNull null
                ReleaseAsset(
                    name = name,
                    downloadUrl = url,
                    size = asset.size ?: 0L,
                )
            }
            .orEmpty()

        return ModuleUpdateInfo(
            hasUpdate = hasUpdate,
            currentVersion = currentVersion,
            latestRelease = ModuleRelease(
                tagName = latestTag,
                name = latestRelease.name.orEmpty(),
                body = latestRelease.body.orEmpty(),
                publishedAt = latestRelease.publishedAt.orEmpty(),
                assets = assets,
            ),
        )
    }
}

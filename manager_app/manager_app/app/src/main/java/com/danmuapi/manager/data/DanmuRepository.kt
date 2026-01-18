package com.danmuapi.manager.data

import com.danmuapi.manager.data.model.CoreListResponse
import com.danmuapi.manager.data.model.CoreMeta
import com.danmuapi.manager.data.model.LogsResponse
import com.danmuapi.manager.data.model.ModuleRelease
import com.danmuapi.manager.data.model.ModuleUpdateInfo
import com.danmuapi.manager.data.model.ReleaseAsset
import com.danmuapi.manager.data.model.StatusResponse
import com.danmuapi.manager.network.GitHubApi
import com.danmuapi.manager.network.GitHubReleaseApi
import com.danmuapi.manager.network.LatestCommitInfo
import com.danmuapi.manager.root.DanmuCli

data class CoreUpdateInfo(
    val latestCommit: LatestCommitInfo? = null,
    val latestVersion: String? = null,
    val updateAvailable: Boolean = false,
)

class DanmuRepository(
    private val cli: DanmuCli,
    private val gitHubApi: GitHubApi,
) {
    private val releaseApi = GitHubReleaseApi()  // 添加这一行
    suspend fun getStatus(): StatusResponse? = cli.getStatus()

    suspend fun listCores(): CoreListResponse? = cli.listCores()

    suspend fun listLogs(): LogsResponse? = cli.listLogs()

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

    suspend fun checkUpdate(core: CoreMeta, token: String?): CoreUpdateInfo {
        if (core.repo.isBlank() || core.ref.isBlank()) {
            return CoreUpdateInfo()
        }

        val latest = gitHubApi.getLatestCommit(core.repo, core.ref, token)
        val latestVer = latest?.sha?.let { gitHubApi.getRemoteCoreVersion(core.repo, it) }
        val update = latest?.sha?.let { sha ->
            val localSha = core.sha?.trim().orEmpty()
            localSha.isNotEmpty() && !localSha.equals(sha, ignoreCase = true)
        } ?: false

        return CoreUpdateInfo(
            latestCommit = latest,
            latestVersion = latestVer,
            updateAvailable = update,
        )
    }
    suspend fun checkModuleUpdate(currentVersion: String?): ModuleUpdateInfo {
        val release = releaseApi.getLatestRelease("lilixu3", "danmu-api-module")
            ?: return ModuleUpdateInfo()

        val latestTag = release.tagName.orEmpty()
        val hasUpdate = if (currentVersion.isNullOrBlank() || latestTag.isBlank()) {
            false
        } else {
            compareVersions(latestTag, currentVersion) > 0
        }

        val assets = release.assets?.mapNotNull { asset ->
            val name = asset.name ?: return@mapNotNull null
            val url = asset.browserDownloadUrl ?: return@mapNotNull null
            ReleaseAsset(
                name = name,
                downloadUrl = url,
                size = asset.size ?: 0L,
            )
        } ?: emptyList()

        return ModuleUpdateInfo(
            hasUpdate = hasUpdate,
            currentVersion = currentVersion,
            latestRelease = ModuleRelease(
                tagName = latestTag,
                name = release.name.orEmpty(),
                body = release.body.orEmpty(),
                publishedAt = release.publishedAt.orEmpty(),
                assets = assets,
            ),
        )
    }
    private fun compareVersions(v1: String, v2: String): Int {
        val clean1 = v1.removePrefix("v").trim()
        val clean2 = v2.removePrefix("v").trim()

        fun parseParts(v: String): List<Int> {
            return v.split(".")
                .mapNotNull { part ->
                    // Accept things like "1", "1-beta", "1_rc1" by taking the leading digits.
                    val m = Regex("^(\\d+)").find(part.trim()) ?: return@mapNotNull null
                    m.groupValues.getOrNull(1)?.toIntOrNull()
                }
        }

        val parts1 = parseParts(clean1)
        val parts2 = parseParts(clean2)
        
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrNull(i) ?: 0
            val p2 = parts2.getOrNull(i) ?: 0
            if (p1 != p2) return p1.compareTo(p2)
        }
        return 0
    }
}

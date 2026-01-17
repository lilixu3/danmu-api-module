package com.danmuapi.manager.data

import com.danmuapi.manager.data.model.CoreListResponse
import com.danmuapi.manager.data.model.CoreMeta
import com.danmuapi.manager.data.model.LogsResponse
import com.danmuapi.manager.data.model.StatusResponse
import com.danmuapi.manager.network.GitHubApi
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
}

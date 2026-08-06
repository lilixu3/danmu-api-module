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
import com.danmuapi.manager.core.model.CoreDependencyRepairRequired
import com.danmuapi.manager.core.root.CoreActivationOutcome
import com.danmuapi.manager.core.root.CoreInstallOutcome
import com.danmuapi.manager.core.root.DanmuCli
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

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

internal fun resolveInstalledCoreUpdateInfo(core: CoreRecord): CoreUpdateInfo {
    val localSha = core.sha?.trim()?.takeIf { it.isNotBlank() }
    val localVersion = core.version?.trim()?.takeIf { it.isNotBlank() }
    val state = if (localSha != null || localVersion != null) {
        CoreUpdateState.UpToDate
    } else {
        CoreUpdateState.Unknown
    }

    return CoreUpdateInfo(
        latestCommit = localSha?.let(::LatestCommitInfo),
        latestVersion = localVersion,
        updateAvailable = false,
        state = state,
        currentVersion = localVersion,
        currentCommit = core.commitLabel,
    )
}

class DanmuRepository(
    private val cli: DanmuCli = DanmuCli(),
    private val gitHubApi: GitHubApi = GitHubApi(),
    private val gitHubReleaseApi: GitHubReleaseApi = GitHubReleaseApi(),
    private val runtimePackWorkDir: File = File(
        System.getProperty("java.io.tmpdir") ?: "/tmp",
        "danmu-runtime-pack",
    ),
) {
    suspend fun getStatus(): ManagerStatus? = cli.getStatus()

    suspend fun getProcessElapsedSeconds(pid: String): Long? = cli.getProcessElapsedSeconds(pid)

    suspend fun listCores() = cli.listCores()

    suspend fun listLogs() = cli.listLogs()

    suspend fun startService(): Boolean = cli.startService()

    suspend fun stopService(): Boolean = cli.stopService()

    suspend fun restartService(): Boolean = cli.restartService()

    suspend fun setAutostart(enabled: Boolean): Boolean = cli.setAutostart(enabled)

    suspend fun installCore(repo: String, ref: String): CoreInstallOutcome = cli.installCore(repo, ref)

    suspend fun activateCore(id: String): Boolean = cli.activateCore(id)

    /** 激活核心并严格区分成功、依赖阻断和普通失败。 */
    suspend fun activateCoreWithRepair(id: String): CoreActivationOutcome =
        cli.activateCoreWithDependencyRepair(id)

    /**
     * 依赖修复全流程：签名清单 → 匹配指纹 → 下载/校验/解压 → 原子安装 → 重新激活。
     * @param onProgress 阶段进度回调（0..1）
     */
    suspend fun repairCoreDependencies(
        coreId: String,
        onProgress: (RuntimePackRepairManager.RepairStage, Float) -> Unit = { _, _ -> },
    ): RuntimePackRepairManager.RepairOutcome {
        runtimePackWorkDir.mkdirs()
        // 每次修复使用独立子目录，结束时整体清理，避免旧包残留混入 cp -a。
        val sessionDir = File(runtimePackWorkDir, "repair-${System.currentTimeMillis()}")
        sessionDir.mkdirs()
        val httpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .callTimeout(180, TimeUnit.SECONDS)
            .build()
        val downloader = RuntimePackDownloader(httpClient, cli)
        val manager = RuntimePackRepairManager(
            cli = cli,
            fetchManifest = { downloader.fetchSignedManifest() },
            downloadAndExtract = { manifest, workingDir, progress ->
                val archive = File(workingDir, "node_modules.zip")
                downloader.downloadArchive(manifest, archive) { downloaded, total ->
                    if (total > 0L) {
                        progress((downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                    }
                }
                downloader.extractArchive(archive, workingDir)
            },
            workingDir = sessionDir,
        )
        return try {
            manager.repair(coreId, onProgress)
        } finally {
            sessionDir.deleteRecursively()
        }
    }

    /**
     * 自定义核心本地依赖包导入：安全解压 zip → 原子安装 → 重新激活。
     * 依赖 ID 取压缩包 SHA-256 前 16 位，保证同包幂等、异包隔离。
     */
    suspend fun importLocalDependencies(
        coreId: String,
        archive: File,
        onProgress: (RuntimePackRepairManager.RepairStage, Float) -> Unit = { _, _ -> },
    ): RuntimePackRepairManager.RepairOutcome {
        runtimePackWorkDir.mkdirs()
        val outDir = File(runtimePackWorkDir, "local-import-${System.currentTimeMillis()}")
        outDir.mkdirs()
        try {
            val importer = LocalRuntimePackImporter()
            val sourceNodeModules = importer.importArchive(archive, outDir)
                ?: return RuntimePackRepairManager.RepairOutcome.Failure(
                    RuntimePackRepairManager.RepairFailure.DownloadExtractFailed,
                    "本地依赖包导入失败",
                )
            onProgress(RuntimePackRepairManager.RepairStage.Download, 1f)

            val dependencyId = RuntimePackProtocol.sha256(archive).take(16)
            onProgress(RuntimePackRepairManager.RepairStage.Install, 0f)
            val installed = cli.installCoreDependencies(coreId, sourceNodeModules.absolutePath, dependencyId)
            if (!installed) {
                return RuntimePackRepairManager.RepairOutcome.Failure(
                    RuntimePackRepairManager.RepairFailure.InstallFailed,
                    "依赖安装失败（原子事务已回滚）",
                )
            }
            onProgress(RuntimePackRepairManager.RepairStage.Install, 1f)

            onProgress(RuntimePackRepairManager.RepairStage.Activate, 0f)
            val activation = cli.activateCoreWithDependencyRepair(coreId)
            onProgress(RuntimePackRepairManager.RepairStage.Activate, 1f)
            return when (activation) {
                CoreActivationOutcome.Activated -> RuntimePackRepairManager.RepairOutcome.Success
                is CoreActivationOutcome.RepairRequired -> {
                    val suffix = activation.repair.allNames
                        .takeIf { it.isNotEmpty() }
                        ?.joinToString()
                        ?: "未知依赖"
                    RuntimePackRepairManager.RepairOutcome.Failure(
                        RuntimePackRepairManager.RepairFailure.ActivateStillBlocked,
                        "依赖已安装但激活仍被阻断：$suffix",
                        activation.repair,
                    )
                }
                is CoreActivationOutcome.Failure -> RuntimePackRepairManager.RepairOutcome.Failure(
                    RuntimePackRepairManager.RepairFailure.ActivateFailed,
                    activation.message,
                )
            }
        } catch (error: Exception) {
            return RuntimePackRepairManager.RepairOutcome.Failure(
                RuntimePackRepairManager.RepairFailure.DownloadExtractFailed,
                error.message ?: "本地依赖包导入失败",
            )
        } finally {
            outDir.deleteRecursively()
        }
    }

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

        val latestCommit = gitHubApi.getLatestCommit(core.repo, core.ref, token)
        val latestVersion = gitHubApi.getRemoteCoreVersion(
            repo = core.repo,
            refOrSha = latestCommit?.sha?.takeIf { it.isNotBlank() } ?: core.ref,
        )
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

package com.danmuapi.manager.core.data

import com.danmuapi.manager.core.model.CoreDependencyRepairRequired
import com.danmuapi.manager.core.root.CoreDependencyRepairGateway
import java.io.File

/**
 * 核心依赖修复编排：核心激活被依赖门禁阻断（exit 78）后，
 * 依次执行 取指纹 → 取签名清单 → 匹配指纹 → 下载/解压依赖包 → 原子安装 → 重新激活。
 */
class RuntimePackRepairManager(
    private val cli: CoreDependencyRepairGateway,
    private val fetchManifest: suspend () -> RuntimePackManifest,
    private val downloadAndExtract: suspend (RuntimePackManifest, File, onProgress: (Float) -> Unit) -> File,
    private val workingDir: File,
) {
    enum class RepairStage { Fingerprint, Manifest, Download, Install, Activate }

    enum class RepairFailure {
        FingerprintUnavailable,
        FingerprintMismatch,
        ManifestUnavailable,
        DownloadExtractFailed,
        InstallFailed,
        ActivateStillBlocked,
    }

    sealed class RepairOutcome {
        object Success : RepairOutcome()
        data class Failure(
            val reason: RepairFailure,
            val message: String,
            val remainingRepair: CoreDependencyRepairRequired? = null,
        ) : RepairOutcome()
    }

    suspend fun repair(
        coreId: String,
        onProgress: (RepairStage, Float) -> Unit = { _, _ -> },
    ): RepairOutcome {
        onProgress(RepairStage.Fingerprint, 0f)
        val fingerprint = cli.getCoreFingerprint(coreId)
            ?: return RepairOutcome.Failure(RepairFailure.FingerprintUnavailable, "无法获取核心依赖指纹")
        onProgress(RepairStage.Fingerprint, 1f)

        onProgress(RepairStage.Manifest, 0f)
        val manifest = try {
            fetchManifest()
        } catch (error: Exception) {
            return RepairOutcome.Failure(
                RepairFailure.ManifestUnavailable,
                error.message ?: "签名运行时依赖清单不可用",
            )
        }
        if (manifest.dependencyFingerprint != fingerprint) {
            return RepairOutcome.Failure(
                RepairFailure.FingerprintMismatch,
                "核心依赖指纹与在线运行时依赖包不匹配（核心可能比依赖包更新）",
            )
        }
        onProgress(RepairStage.Manifest, 1f)

        onProgress(RepairStage.Download, 0f)
        val sourceNodeModules = try {
            downloadAndExtract(manifest, workingDir) { fraction ->
                onProgress(RepairStage.Download, fraction)
            }
        } catch (error: Exception) {
            return RepairOutcome.Failure(
                RepairFailure.DownloadExtractFailed,
                error.message ?: "运行时依赖包下载或解压失败",
            )
        }
        onProgress(RepairStage.Download, 1f)

        onProgress(RepairStage.Install, 0f)
        val installed = cli.installCoreDependencies(coreId, sourceNodeModules.absolutePath, fingerprint)
        if (!installed) {
            return RepairOutcome.Failure(RepairFailure.InstallFailed, "依赖安装失败（原子事务已回滚）")
        }
        onProgress(RepairStage.Install, 1f)

        onProgress(RepairStage.Activate, 0f)
        val stillBlocked = cli.activateCoreWithDependencyRepair(coreId)
        onProgress(RepairStage.Activate, 1f)
        return if (stillBlocked == null) {
            RepairOutcome.Success
        } else {
            RepairOutcome.Failure(
                RepairFailure.ActivateStillBlocked,
                "依赖已安装但激活仍被阻断：${stillBlocked.allNames}",
                stillBlocked,
            )
        }
    }
}

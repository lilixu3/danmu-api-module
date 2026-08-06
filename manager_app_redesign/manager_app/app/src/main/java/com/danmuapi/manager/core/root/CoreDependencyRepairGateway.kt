package com.danmuapi.manager.core.root

import com.danmuapi.manager.core.model.CoreDependencyRepairRequired

/** 核心激活结果：禁止用 null 同时表达成功与解析失败。 */
sealed class CoreActivationOutcome {
    object Activated : CoreActivationOutcome()
    data class RepairRequired(val repair: CoreDependencyRepairRequired) : CoreActivationOutcome()
    data class Failure(
        val message: String,
        val exitCode: Int,
    ) : CoreActivationOutcome()
}

/** 核心安装结果：install_core 落位后激活可能被依赖门禁阻断（exit 78）。 */
sealed class CoreInstallOutcome {
    object Installed : CoreInstallOutcome()
    data class Blocked(val repair: CoreDependencyRepairRequired) : CoreInstallOutcome()
    data class Failed(
        val message: String,
        val exitCode: Int,
    ) : CoreInstallOutcome()
}

/** 依赖修复编排所需的最小 CLI 网关（由 DanmuCli 实现，测试可替换） */
interface CoreDependencyRepairGateway {
    suspend fun getCoreFingerprint(id: String): String?
    suspend fun installCoreDependencies(
        coreId: String,
        sourceNodeModulesDir: String,
        dependencyId: String,
    ): Boolean
    suspend fun activateCoreWithDependencyRepair(id: String): CoreActivationOutcome
}

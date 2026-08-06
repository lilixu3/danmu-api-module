package com.danmuapi.manager.core.root

import com.danmuapi.manager.core.model.CoreDependencyRepairRequired

/** 依赖修复编排所需的最小 CLI 网关（由 DanmuCli 实现，测试可替换） */
interface CoreDependencyRepairGateway {
    suspend fun getCoreFingerprint(id: String): String?
    suspend fun installCoreDependencies(
        coreId: String,
        sourceNodeModulesDir: String,
        dependencyId: String,
    ): Boolean
    suspend fun activateCoreWithDependencyRepair(id: String): CoreDependencyRepairRequired?
}

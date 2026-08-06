package com.danmuapi.manager.app.state

/** 核心依赖修复流程的 UI 状态 */
sealed interface DependencyRepairUiState {
    data object Idle : DependencyRepairUiState

    /** 修复进行中：label 为阶段文案，progress 0..1 */
    data class Repairing(val label: String, val progress: Float) : DependencyRepairUiState

    data class Error(val message: String) : DependencyRepairUiState
}

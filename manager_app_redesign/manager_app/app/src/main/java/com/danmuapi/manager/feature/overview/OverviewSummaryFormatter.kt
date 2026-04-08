package com.danmuapi.manager.feature.overview

internal object OverviewSummaryFormatter {
    fun serviceTitle(
        running: Boolean,
        rootReady: Boolean,
        hasActiveCore: Boolean,
    ): String {
        return when {
            !rootReady -> "Root 受限"
            !hasActiveCore -> "未加载核心"
            running -> "运行良好"
            else -> "服务已停止"
        }
    }

    fun serviceSummary(
        running: Boolean,
        rootReady: Boolean,
        hasActiveCore: Boolean,
        port: Int,
    ): String {
        return when {
            !rootReady -> "Root 受限"
            !hasActiveCore -> "未加载核心"
            running -> "端口 $port"
            else -> "端口 $port"
        }
    }

    fun serviceBadge(
        running: Boolean,
        rootReady: Boolean,
        hasActiveCore: Boolean,
    ): String {
        return when {
            !rootReady -> "SU"
            !hasActiveCore -> "核"
            running -> "ON"
            else -> "OFF"
        }
    }
}

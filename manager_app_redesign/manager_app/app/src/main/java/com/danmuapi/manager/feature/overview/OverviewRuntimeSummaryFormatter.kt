package com.danmuapi.manager.feature.overview

import java.util.Locale

internal data class OverviewRuntimeSummaryItem(
    val label: String,
    val value: String,
)

internal object OverviewRuntimeSummaryFormatter {
    fun buildItems(
        running: Boolean,
        pid: String?,
        coreVersion: String?,
        elapsedSeconds: Long?,
    ): List<OverviewRuntimeSummaryItem> {
        return listOf(
            OverviewRuntimeSummaryItem(
                label = "服务进程",
                value = if (running) {
                    pid?.takeIf { it.isNotBlank() }?.let { "PID $it" } ?: "运行中"
                } else {
                    "未运行"
                },
            ),
            OverviewRuntimeSummaryItem(
                label = "核心版本",
                value = coreVersion?.takeIf { it.isNotBlank() } ?: "--",
            ),
            OverviewRuntimeSummaryItem(
                label = "运行时间",
                value = if (running) {
                    elapsedSeconds?.let(::formatElapsedSeconds) ?: "--"
                } else {
                    "未运行"
                },
            ),
        )
    }

    fun formatElapsedSeconds(seconds: Long): String {
        val safeSeconds = seconds.coerceAtLeast(0L)
        val hours = safeSeconds / 3600
        val minutes = (safeSeconds % 3600) / 60
        val remainSeconds = safeSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, remainSeconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, remainSeconds)
        }
    }
}

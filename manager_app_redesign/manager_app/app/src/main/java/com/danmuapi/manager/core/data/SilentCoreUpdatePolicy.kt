package com.danmuapi.manager.core.data

const val DEFAULT_SILENT_CORE_UPDATE_BACKGROUND_INTERVAL_MINUTES = 60
const val MIN_SILENT_CORE_UPDATE_BACKGROUND_INTERVAL_MINUTES = 15
const val SILENT_CORE_UPDATE_COOLDOWN_MILLIS = 10 * 60 * 1000L

val SILENT_CORE_UPDATE_BACKGROUND_INTERVAL_OPTIONS = listOf(15, 30, 60, 180, 360, 720)

enum class SilentCoreUpdateTrigger {
    Foreground,
    Background,
    Manual,
}

data class SilentCoreUpdateSettings(
    val foregroundEnabled: Boolean = true,
    val backgroundEnabled: Boolean = true,
    val backgroundIntervalMinutes: Int = DEFAULT_SILENT_CORE_UPDATE_BACKGROUND_INTERVAL_MINUTES,
    val lastSilentCheckAtMillis: Long = 0L,
)

fun normalizeSilentCoreUpdateBackgroundIntervalMinutes(minutes: Int): Int {
    return if (minutes < MIN_SILENT_CORE_UPDATE_BACKGROUND_INTERVAL_MINUTES) {
        DEFAULT_SILENT_CORE_UPDATE_BACKGROUND_INTERVAL_MINUTES
    } else {
        minutes
    }
}

fun shouldRunSilentCoreUpdate(
    trigger: SilentCoreUpdateTrigger,
    settings: SilentCoreUpdateSettings,
    nowMillis: Long,
): Boolean {
    val enabled = when (trigger) {
        SilentCoreUpdateTrigger.Foreground -> settings.foregroundEnabled
        SilentCoreUpdateTrigger.Background -> settings.backgroundEnabled
        SilentCoreUpdateTrigger.Manual -> true
    }
    if (!enabled) return false

    val lastCheckAtMillis = settings.lastSilentCheckAtMillis
    if (lastCheckAtMillis <= 0L) return true

    return nowMillis - lastCheckAtMillis >= SILENT_CORE_UPDATE_COOLDOWN_MILLIS
}

fun formatSilentCoreUpdateIntervalLabel(minutes: Int): String {
    val normalized = normalizeSilentCoreUpdateBackgroundIntervalMinutes(minutes)
    return if (normalized % 60 == 0) {
        "${normalized / 60} 小时"
    } else {
        "$normalized 分钟"
    }
}

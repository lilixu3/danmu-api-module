package com.danmuapi.manager.core.model

data class ManagerStatus(
    val module: ModuleStatus = ModuleStatus(),
    val service: ServiceStatus = ServiceStatus(),
    val autostart: String = "unknown",
    val activeCoreId: String? = null,
    val activeCore: CoreRecord? = null,
) {
    val isRunning: Boolean
        get() = service.running

    val isAutostartEnabled: Boolean
        get() = parseAutostartEnabled(autostart)
}

data class ModuleStatus(
    val id: String = "",
    val enabled: Boolean = true,
    val version: String? = null,
)

data class ServiceStatus(
    val running: Boolean = false,
    val pid: String? = null,
)

fun parseAutostartEnabled(raw: String?): Boolean {
    return when (raw?.trim()?.lowercase()) {
        "1", "true", "on", "enabled", "yes" -> true
        else -> false
    }
}

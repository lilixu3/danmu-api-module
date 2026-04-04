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

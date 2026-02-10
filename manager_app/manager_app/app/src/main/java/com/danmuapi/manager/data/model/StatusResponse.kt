package com.danmuapi.manager.data.model

data class StatusResponse(
    val module: ModuleInfo = ModuleInfo(),
    val service: ServiceInfo = ServiceInfo(),
    val autostart: String = "unknown", // on/off
    val activeCoreId: String? = null,
    val activeCore: CoreMeta? = null,
)
data class ModuleInfo(
    val id: String = "",
    val enabled: Boolean = true,
    val version: String? = null,
)
data class ServiceInfo(
    val running: Boolean = false,
    val pid: String? = null,
)

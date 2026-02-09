package com.danmuapi.manager.data.model

/**
 * Parsed subset of `/api/config`.
 *
 * The backend returns a lot of fields; the manager app only needs the parts
 * that are used by the Compose "控制台" screens.
 */
data class ServerConfigResponse(
    val message: String = "",
    val version: String? = null,
    val categorizedEnvVars: Map<String, List<EnvVarItem>> = emptyMap(),
    val envVarConfig: Map<String, EnvVarMeta> = emptyMap(),
    val originalEnvVars: Map<String, String> = emptyMap(),
    val hasAdminToken: Boolean = false,
    val repository: String? = null,
    val description: String? = null,
    val notice: String? = null,
)

data class EnvVarItem(
    val key: String,
    val value: String,
    val type: String = "text",
    val description: String = "",
    val options: List<String> = emptyList(),
)

data class EnvVarMeta(
    val category: String = "system",
    val type: String = "text",
    val description: String = "",
    val options: List<String> = emptyList(),
    val min: Double? = null,
    val max: Double? = null,
)

data class ServerLogEntry(
    val timestamp: String,
    val level: String,
    val message: String,
)

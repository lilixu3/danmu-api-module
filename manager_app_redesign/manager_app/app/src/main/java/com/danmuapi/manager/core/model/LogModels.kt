package com.danmuapi.manager.core.model

data class LogDirectory(
    val dir: String = "",
    val files: List<LogFileEntry> = emptyList(),
)

data class LogFileEntry(
    val name: String = "",
    val path: String = "",
    val sizeBytes: Long = 0,
    val modifiedAt: String? = null,
)

data class RequestRecord(
    val path: String,
    val method: String,
    val timestamp: String,
    val clientIp: String,
    val params: String? = null,
)

data class RequestRecordsSnapshot(
    val records: List<RequestRecord> = emptyList(),
    val todayReqNum: Int = 0,
)

data class ServerConfig(
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

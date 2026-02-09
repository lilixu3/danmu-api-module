package com.danmuapi.manager.data.model

data class LogsResponse(
    val dir: String = "",
    val files: List<LogFileInfo> = emptyList(),
)
data class LogFileInfo(
    val name: String = "",
    val path: String = "",
    val sizeBytes: Long = 0,
    val modifiedAt: String? = null,
)

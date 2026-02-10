package com.danmuapi.manager.data.model

data class CoreMeta(
    val id: String = "",
    val repo: String = "",
    val ref: String = "",
    val sha: String? = null,
    val shaShort: String? = null,
    val version: String? = null,
    val installedAt: String? = null,
    val sizeBytes: Long? = null,
)

package com.danmuapi.manager.data.model

data class CoreListResponse(
    val activeCoreId: String? = null,
    val cores: List<CoreMeta> = emptyList(),
)

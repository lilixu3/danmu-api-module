package com.danmuapi.manager.data.model

data class RequestRecord(
    val path: String,
    val method: String,
    val timestamp: String,
    val clientIp: String,
    val params: String? = null,
)

data class RequestRecordsResponse(
    val records: List<RequestRecord> = emptyList(),
    val todayReqNum: Int = 0,
)

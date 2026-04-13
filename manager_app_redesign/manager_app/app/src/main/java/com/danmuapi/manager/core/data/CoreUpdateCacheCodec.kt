package com.danmuapi.manager.core.data

import com.danmuapi.manager.core.model.CoreUpdateInfo
import com.danmuapi.manager.core.model.CoreUpdateState
import com.danmuapi.manager.core.model.LatestCommitInfo
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

private val coreUpdateCacheMoshi: Moshi = Moshi.Builder()
    .add(KotlinJsonAdapterFactory())
    .build()

private val coreUpdateCacheAdapter = coreUpdateCacheMoshi.adapter(CoreUpdateCachePayload::class.java)

@JsonClass(generateAdapter = true)
private data class CoreUpdateCachePayload(
    val entries: List<CoreUpdateCacheEntry> = emptyList(),
)

@JsonClass(generateAdapter = true)
private data class CoreUpdateCacheEntry(
    val coreId: String,
    val latestCommitSha: String? = null,
    val latestCommitMessage: String? = null,
    val latestCommitDate: String? = null,
    val latestVersion: String? = null,
    val updateAvailable: Boolean = false,
    val state: String = CoreUpdateState.Unknown.name,
    val currentVersion: String? = null,
    val currentCommit: String? = null,
)

fun encodeCoreUpdateInfoCache(updateInfo: Map<String, CoreUpdateInfo>): String {
    val payload = CoreUpdateCachePayload(
        entries = updateInfo.entries
            .sortedBy { it.key }
            .map { (coreId, info) ->
                CoreUpdateCacheEntry(
                    coreId = coreId,
                    latestCommitSha = info.latestCommit?.sha,
                    latestCommitMessage = info.latestCommit?.message,
                    latestCommitDate = info.latestCommit?.date,
                    latestVersion = info.latestVersion,
                    updateAvailable = info.updateAvailable,
                    state = info.state.name,
                    currentVersion = info.currentVersion,
                    currentCommit = info.currentCommit,
                )
            },
    )
    return coreUpdateCacheAdapter.toJson(payload)
}

fun decodeCoreUpdateInfoCache(raw: String?): Map<String, CoreUpdateInfo> {
    if (raw.isNullOrBlank()) return emptyMap()
    val payload = runCatching { coreUpdateCacheAdapter.fromJson(raw) }.getOrNull() ?: return emptyMap()
    return linkedMapOf<String, CoreUpdateInfo>().apply {
        payload.entries.forEach { entry ->
            if (entry.coreId.isBlank()) return@forEach
            put(
                entry.coreId,
                CoreUpdateInfo(
                    latestCommit = entry.latestCommitSha?.takeIf { it.isNotBlank() }?.let { sha ->
                        LatestCommitInfo(
                            sha = sha,
                            message = entry.latestCommitMessage,
                            date = entry.latestCommitDate,
                        )
                    },
                    latestVersion = entry.latestVersion,
                    updateAvailable = entry.updateAvailable,
                    state = entry.state
                        .takeIf { it.isNotBlank() }
                        ?.let { value -> runCatching { CoreUpdateState.valueOf(value) }.getOrNull() }
                        ?: CoreUpdateState.Unknown,
                    currentVersion = entry.currentVersion,
                    currentCommit = entry.currentCommit,
                ),
            )
        }
    }
}

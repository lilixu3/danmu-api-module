package com.danmuapi.manager.core.model

data class CoreCatalog(
    val activeCoreId: String? = null,
    val cores: List<CoreRecord> = emptyList(),
)

data class CoreRecord(
    val id: String = "",
    val repo: String = "",
    val ref: String = "",
    val sha: String? = null,
    val shaShort: String? = null,
    val version: String? = null,
    val installedAt: String? = null,
    val sizeBytes: Long? = null,
) {
    val repoDisplayName: String
        get() = repo.ifBlank { id }

    val commitLabel: String?
        get() = shaShort ?: sha?.take(7)
}

data class CoreUpdateInfo(
    val latestCommit: LatestCommitInfo? = null,
    val latestVersion: String? = null,
    val updateAvailable: Boolean = false,
)

data class LatestCommitInfo(
    val sha: String,
    val message: String? = null,
    val date: String? = null,
)

data class ModuleRelease(
    val tagName: String = "",
    val name: String = "",
    val body: String = "",
    val publishedAt: String = "",
    val assets: List<ReleaseAsset> = emptyList(),
)

data class ReleaseAsset(
    val name: String = "",
    val downloadUrl: String = "",
    val size: Long = 0,
)

data class ModuleUpdateInfo(
    val hasUpdate: Boolean = false,
    val currentVersion: String? = null,
    val latestRelease: ModuleRelease? = null,
)

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
    val state: CoreUpdateState = CoreUpdateState.Unknown,
)

enum class CoreUpdateState {
    Unknown,
    UpToDate,
    UpdateAvailable,
}

data class LatestCommitInfo(
    val sha: String,
    val message: String? = null,
    val date: String? = null,
)

data class RollbackCommitPage(
    val commits: List<RollbackCommitItem> = emptyList(),
    val page: Int = 1,
    val pageSize: Int = 20,
    val hasNextPage: Boolean = false,
)

data class RollbackCommitItem(
    val sha: String,
    val shortSha: String,
    val version: String? = null,
    val title: String? = null,
    val body: String? = null,
    val authorName: String? = null,
    val date: String? = null,
    val htmlUrl: String? = null,
) {
    val versionLabel: String
        get() = version?.takeIf { it.isNotBlank() } ?: "版本未知"

    val titleLabel: String
        get() = title?.takeIf { it.isNotBlank() } ?: "无提交标题"
}

data class RollbackSearchSnapshot(
    val query: String = "",
    val scannedCount: Int = 0,
    val matchedCount: Int = 0,
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

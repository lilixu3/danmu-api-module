package com.danmuapi.manager.data.model

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
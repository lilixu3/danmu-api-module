package com.danmuapi.manager.network

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

data class GitHubRelease(
    @Json(name = "tag_name") val tagName: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "body") val body: String? = null,
    @Json(name = "published_at") val publishedAt: String? = null,
    @Json(name = "assets") val assets: List<GitHubAsset>? = null,
)

data class GitHubAsset(
    @Json(name = "name") val name: String? = null,
    @Json(name = "browser_download_url") val browserDownloadUrl: String? = null,
    @Json(name = "size") val size: Long? = null,
)

class GitHubReleaseApi(private val client: OkHttpClient = OkHttpClient()) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val releaseAdapter = moshi.adapter(GitHubRelease::class.java)

    suspend fun getLatestRelease(owner: String, repo: String): GitHubRelease? = withContext(Dispatchers.IO) {
        val url = "https://api.github.com/repos/$owner/$repo/releases/latest"
        
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "DanmuApiManager")
            .header("Accept", "application/vnd.github+json")
            .build()

        try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                releaseAdapter.fromJson(body)
            }
        } catch (_: IOException) {
            null
        }
    }
}
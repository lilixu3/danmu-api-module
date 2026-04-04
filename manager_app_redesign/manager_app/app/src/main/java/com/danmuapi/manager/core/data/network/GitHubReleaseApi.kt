package com.danmuapi.manager.core.data.network

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
    val name: String? = null,
    val body: String? = null,
    @Json(name = "published_at") val publishedAt: String? = null,
    val assets: List<GitHubAsset>? = null,
)

data class GitHubAsset(
    val name: String? = null,
    @Json(name = "browser_download_url") val browserDownloadUrl: String? = null,
    val size: Long? = null,
)

class GitHubReleaseApi(
    private val client: OkHttpClient = OkHttpClient(),
) {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val releaseAdapter = moshi.adapter(GitHubRelease::class.java)

    suspend fun getLatestRelease(
        owner: String,
        repo: String,
    ): GitHubRelease? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$owner/$repo/releases/latest")
            .header("User-Agent", "DanmuApiManager")
            .header("Accept", "application/vnd.github+json")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                releaseAdapter.fromJson(body)
            }
        } catch (_: IOException) {
            null
        } catch (_: Throwable) {
            null
        }
    }
}

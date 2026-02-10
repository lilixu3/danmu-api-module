package com.danmuapi.manager.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

data class GitHubRelease(
    val tagName: String? = null,
    val name: String? = null,
    val body: String? = null,
    val publishedAt: String? = null,
    val assets: List<GitHubAsset>? = null,
)

data class GitHubAsset(
    val name: String? = null,
    val browserDownloadUrl: String? = null,
    val size: Long? = null,
)

class GitHubReleaseApi(private val client: OkHttpClient = OkHttpClient()) {
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
                parseRelease(body)
            }
        } catch (_: IOException) {
            null
        } catch (_: Throwable) {
            // Be conservative: any unexpected JSON parsing issues should not crash the app.
            null
        }
    }

    private fun parseRelease(json: String): GitHubRelease? {
        val o = JSONObject(json)
        val tag = o.optString("tag_name", "").ifBlank { null }
        val name = o.optString("name", "").ifBlank { null }
        val body = o.optString("body", "").ifBlank { null }
        val published = o.optString("published_at", "").ifBlank { null }

        val assetsArr: JSONArray? = o.optJSONArray("assets")
        val assets = if (assetsArr != null) {
            buildList {
                for (i in 0 until assetsArr.length()) {
                    val a = assetsArr.optJSONObject(i) ?: continue
                    val an = a.optString("name", "").ifBlank { null } ?: continue
                    val url = a.optString("browser_download_url", "").ifBlank { null } ?: continue
                    val size = try {
                        a.optLong("size", 0L)
                    } catch (_: Throwable) {
                        0L
                    }
                    add(GitHubAsset(name = an, browserDownloadUrl = url, size = size))
                }
            }
        } else {
            emptyList()
        }

        return GitHubRelease(
            tagName = tag,
            name = name,
            body = body,
            publishedAt = published,
            assets = assets,
        )
    }
}
package com.danmuapi.manager.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

data class LatestCommitInfo(
    val sha: String,
    val message: String? = null,
    val date: String? = null,
)

class GitHubApi(private val client: OkHttpClient = OkHttpClient()) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    private data class CommitResponse(
        val sha: String? = null,
        val commit: CommitDetail? = null,
    )
    private data class CommitDetail(
        val message: String? = null,
        val author: CommitAuthor? = null,
    )
    private data class CommitAuthor(
        val date: String? = null,
    )

    private val commitAdapter = moshi.adapter(CommitResponse::class.java)

    suspend fun getLatestCommit(repo: String, ref: String, token: String?): LatestCommitInfo? = withContext(Dispatchers.IO) {
        val cleanRepo = repo.trim().removePrefix("https://github.com/").removeSuffix(".git")
        if (!cleanRepo.contains("/")) return@withContext null
        val url = "https://api.github.com/repos/$cleanRepo/commits/${ref.trim()}"

        val reqBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", "DanmuApiManager")
            .header("Accept", "application/vnd.github+json")

        val t = token?.trim().orEmpty()
        if (t.isNotEmpty()) {
            // Classic GitHub token works with "token" prefix.
            reqBuilder.header("Authorization", "token $t")
        }

        val req = reqBuilder.build()
        try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                val parsed = commitAdapter.fromJson(body)
                val sha = parsed?.sha ?: return@withContext null
                LatestCommitInfo(
                    sha = sha,
                    message = parsed.commit?.message,
                    date = parsed.commit?.author?.date,
                )
            }
        } catch (_: IOException) {
            null
        }
    }

    suspend fun getRemoteCoreVersion(repo: String, refOrSha: String): String? = withContext(Dispatchers.IO) {
        val cleanRepo = repo.trim().removePrefix("https://github.com/").removeSuffix(".git")
        if (!cleanRepo.contains("/")) return@withContext null
        val ref = refOrSha.trim()
        val url = "https://raw.githubusercontent.com/$cleanRepo/$ref/danmu_api/configs/globals.js"

        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "DanmuApiManager")
            .build()

        try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null

                // Example line: VERSION: '1.10.2',
                val re = Regex("VERSION\\s*:\\s*['\"]([^'\"]+)['\"]")
                re.find(body)?.groupValues?.getOrNull(1)
            }
        } catch (_: IOException) {
            null
        }
    }
}

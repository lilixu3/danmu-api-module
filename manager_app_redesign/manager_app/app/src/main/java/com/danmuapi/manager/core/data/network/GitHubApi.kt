package com.danmuapi.manager.core.data.network

import com.danmuapi.manager.core.model.LatestCommitInfo
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class GitHubApi(
    private val client: OkHttpClient = OkHttpClient(),
) {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

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

    suspend fun getLatestCommit(
        repo: String,
        ref: String,
        token: String?,
    ): LatestCommitInfo? = withContext(Dispatchers.IO) {
        val cleanRepo = repo.trim()
            .removePrefix("https://github.com/")
            .removeSuffix(".git")
        if (!cleanRepo.contains('/')) return@withContext null

        val requestBuilder = Request.Builder()
            .url("https://api.github.com/repos/$cleanRepo/commits/${ref.trim()}")
            .header("User-Agent", "DanmuApiManager")
            .header("Accept", "application/vnd.github+json")

        val safeToken = token?.trim().orEmpty()
        if (safeToken.isNotEmpty()) {
            requestBuilder.header("Authorization", "token $safeToken")
        }

        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val parsed = commitAdapter.fromJson(body) ?: return@withContext null
                val sha = parsed.sha ?: return@withContext null
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

    suspend fun getRemoteCoreVersion(
        repo: String,
        refOrSha: String,
    ): String? = withContext(Dispatchers.IO) {
        val cleanRepo = repo.trim()
            .removePrefix("https://github.com/")
            .removeSuffix(".git")
        if (!cleanRepo.contains('/')) return@withContext null

        val request = Request.Builder()
            .url("https://raw.githubusercontent.com/$cleanRepo/${refOrSha.trim()}/danmu_api/configs/globals.js")
            .header("User-Agent", "DanmuApiManager")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                Regex("VERSION\\s*:\\s*['\"]([^'\"]+)['\"]")
                    .find(body)
                    ?.groupValues
                    ?.getOrNull(1)
            }
        } catch (_: IOException) {
            null
        }
    }
}

package com.danmuapi.manager.core.data.network

import com.danmuapi.manager.core.model.LatestCommitInfo
import com.danmuapi.manager.core.model.RollbackCommitItem
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class GitHubApi(
    private val client: OkHttpClient = OkHttpClient(),
) {
    @JsonClass(generateAdapter = true)
    private data class RepoResponse(
        @Json(name = "default_branch") val defaultBranch: String? = null,
    )

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private data class CommitResponse(
        val sha: String? = null,
        @Json(name = "html_url") val htmlUrl: String? = null,
        val author: UserSummary? = null,
        val commit: CommitDetail? = null,
    )

    private data class UserSummary(
        val login: String? = null,
    )

    private data class CommitDetail(
        val message: String? = null,
        val author: CommitAuthor? = null,
    )

    private data class CommitAuthor(
        val name: String? = null,
        val date: String? = null,
    )

    private val commitAdapter = moshi.adapter(CommitResponse::class.java)
    private val commitListAdapter = moshi.adapter<List<CommitResponse>>(
        Types.newParameterizedType(List::class.java, CommitResponse::class.java),
    )
    private val repoAdapter = moshi.adapter(RepoResponse::class.java)

    suspend fun getLatestCommit(
        repo: String,
        ref: String,
        token: String?,
    ): LatestCommitInfo? = withContext(Dispatchers.IO) {
        val cleanRepo = repo.trim()
            .removePrefix("https://github.com/")
            .removeSuffix(".git")
        if (!cleanRepo.contains('/')) return@withContext null

        val directCommit = getCommitByRef(cleanRepo, ref, token)
        if (directCommit != null) return@withContext directCommit

        val resolvedRef = resolveHistoryRef(cleanRepo, ref, token)
        val requestBuilder = Request.Builder()
            .url("https://api.github.com/repos/$cleanRepo/commits?sha=${resolvedRef.trim()}&per_page=1")
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
                val parsed = commitListAdapter.fromJson(body).orEmpty().firstOrNull() ?: return@withContext null
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

        val trimmedRef = refOrSha.trim()
        val candidateUrls = listOf(
            "https://raw.githubusercontent.com/$cleanRepo/$trimmedRef/danmu_api/configs/globals.js",
            "https://github.com/$cleanRepo/raw/$trimmedRef/danmu_api/configs/globals.js",
        )

        for (url in candidateUrls) {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "DanmuApiManager")
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use Unit
                    }
                    val body = response.body?.string() ?: return@use Unit
                    val version = Regex("VERSION\\s*:\\s*['\"]([^'\"]+)['\"]")
                        .find(body)
                        ?.groupValues
                        ?.getOrNull(1)
                    if (!version.isNullOrBlank()) {
                        return@withContext version
                    }
                }
            } catch (_: IOException) {
                // try next URL
            }
        }

        null
    }

    suspend fun listCommits(
        repo: String,
        ref: String,
        page: Int,
        perPage: Int,
        token: String?,
    ): List<RollbackCommitItem> = withContext(Dispatchers.IO) {
        val cleanRepo = repo.trim()
            .removePrefix("https://github.com/")
            .removeSuffix(".git")
        if (!cleanRepo.contains('/')) return@withContext emptyList()

        val resolvedRef = resolveHistoryRef(cleanRepo, ref, token)
        val safePage = page.coerceAtLeast(1)
        val safePerPage = perPage.coerceIn(1, 100)
        val requestBuilder = Request.Builder()
            .url("https://api.github.com/repos/$cleanRepo/commits?sha=${resolvedRef.trim()}&page=$safePage&per_page=$safePerPage")
            .header("User-Agent", "DanmuApiManager")
            .header("Accept", "application/vnd.github+json")

        val safeToken = token?.trim().orEmpty()
        if (safeToken.isNotEmpty()) {
            requestBuilder.header("Authorization", "token $safeToken")
        }

        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("GitHub commits API failed: ${response.code}")
                }
                val body = response.body?.string() ?: return@withContext emptyList()
                val parsed = commitListAdapter.fromJson(body).orEmpty()
                parsed.mapNotNull { item ->
                    val sha = item.sha ?: return@mapNotNull null
                    val message = item.commit?.message.orEmpty()
                    val title = message.lineSequence().firstOrNull()?.trim().orEmpty()
                    val detail = message.lineSequence().drop(1).joinToString("\n").trim().ifBlank { null }
                    RollbackCommitItem(
                        sha = sha,
                        shortSha = sha.take(7),
                        title = title.ifBlank { null },
                        body = detail,
                        authorName = item.author?.login ?: item.commit?.author?.name,
                        date = item.commit?.author?.date,
                        htmlUrl = item.htmlUrl,
                    )
                }
            }
        } catch (error: IOException) {
            throw IOException("加载 GitHub 提交历史失败：${error.message}", error)
        }
    }

    private suspend fun getCommitByRef(
        cleanRepo: String,
        ref: String,
        token: String?,
    ): LatestCommitInfo? {
        val requestBuilder = Request.Builder()
            .url("https://api.github.com/repos/$cleanRepo/commits/${ref.trim()}")
            .header("User-Agent", "DanmuApiManager")
            .header("Accept", "application/vnd.github+json")

        val safeToken = token?.trim().orEmpty()
        if (safeToken.isNotEmpty()) {
            requestBuilder.header("Authorization", "token $safeToken")
        }

        return try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string() ?: return@use null
                val parsed = commitAdapter.fromJson(body) ?: return@use null
                val sha = parsed.sha ?: return@use null
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

    private suspend fun resolveHistoryRef(
        cleanRepo: String,
        ref: String,
        token: String?,
    ): String {
        val trimmed = ref.trim()
        val looksLikeCommit = Regex("^[0-9a-fA-F]{7,40}$").matches(trimmed)
        if (!looksLikeCommit) return trimmed.ifBlank { "main" }

        val safeToken = token?.trim().orEmpty()
        val requestBuilder = Request.Builder()
            .url("https://api.github.com/repos/$cleanRepo")
            .header("User-Agent", "DanmuApiManager")
            .header("Accept", "application/vnd.github+json")
        if (safeToken.isNotEmpty()) {
            requestBuilder.header("Authorization", "token $safeToken")
        }

        return try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) return@use "main"
                val body = response.body?.string() ?: return@use "main"
                repoAdapter.fromJson(body)?.defaultBranch?.takeIf { it.isNotBlank() } ?: "main"
            }
        } catch (_: IOException) {
            "main"
        }
    }
}

package com.danmuapi.manager.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Minimal WebDAV client (GET / PUT / MKCOL) used for config backup/restore.
 *
 * Design notes:
 * - Keep feature set small (no PROPFIND) to reduce compatibility risks.
 * - "remotePath" supports both directory and file:
 *   - If user provides a directory (e.g. "danmuapi"), we will save as "danmuapi/danmu_api.env".
 *   - If user provides a file name (e.g. "backups/danmu_api.env"), we use it as-is.
 */

private const val DEFAULT_ENV_FILE_NAME = "danmu_api.env"

sealed class WebDavResult {
    data class Success(val code: Int) : WebDavResult()
    data class Error(val message: String, val code: Int? = null) : WebDavResult()
}

data class WebDavDownload(
    val result: WebDavResult,
    val text: String? = null,
)

class WebDavClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(45, TimeUnit.SECONDS)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build(),
) {

    private data class WebDavTarget(
        val fileUrl: HttpUrl,
        val isFullUrl: Boolean,
        /**
         * Normalized relative file path (when isFullUrl == false). Used for MKCOL.
         * For full URL mode this field is informational.
         */
        val normalizedRemotePath: String,
    )

    private fun authHeader(username: String?, password: String?): String? {
        val u = username?.trim().orEmpty()
        val p = password.orEmpty()
        if (u.isEmpty() && p.isEmpty()) return null
        // Credentials.basic() defaults to ISO-8859-1; use UTF-8 explicitly for better compatibility.
        return Credentials.basic(u, p, charset = Charsets.UTF_8)
    }

    private fun normalizeBaseUrl(baseUrl: String): HttpUrl? {
        val b = baseUrl.trim()
        if (b.isBlank()) return null
        val normalized = if (b.endsWith("/")) b else "$b/"
        return normalized.toHttpUrlOrNull()
    }

    private fun normalizeRelativeRemotePath(remotePath: String): String {
        val p = remotePath.trim().removePrefix("/")
        if (p.isBlank()) return DEFAULT_ENV_FILE_NAME

        // If user points to a directory, append a default file name.
        if (p.endsWith("/")) return p + DEFAULT_ENV_FILE_NAME

        val last = p.substringAfterLast("/")
        // Heuristic: if the last segment has no dot, treat it as a directory name.
        return if (last.contains(".")) p else "$p/$DEFAULT_ENV_FILE_NAME"
    }

    private fun ensureFileUrl(url: HttpUrl): HttpUrl {
        val pathEndsWithSlash = url.encodedPath.endsWith("/")
        val last = url.pathSegments.lastOrNull().orEmpty()
        val lastLooksLikeFile = last.contains(".")
        val shouldAppend = pathEndsWithSlash || last.isBlank() || !lastLooksLikeFile
        return if (shouldAppend) {
            url.newBuilder().addPathSegment(DEFAULT_ENV_FILE_NAME).build()
        } else {
            url
        }
    }

    private fun resolveTarget(baseUrl: String, remotePath: String): WebDavTarget? {
        val p = remotePath.trim()
        val isFullUrl = p.startsWith("http://") || p.startsWith("https://")

        if (isFullUrl) {
            val url = p.toHttpUrlOrNull() ?: return null
            val fileUrl = ensureFileUrl(url)
            return WebDavTarget(fileUrl = fileUrl, isFullUrl = true, normalizedRemotePath = p)
        }

        val base = normalizeBaseUrl(baseUrl) ?: return null
        val normalizedRel = normalizeRelativeRemotePath(p)
        val fileUrl = base.resolve(normalizedRel) ?: base.newBuilder().addPathSegments(normalizedRel).build()
        return WebDavTarget(fileUrl = fileUrl, isFullUrl = false, normalizedRemotePath = normalizedRel)
    }

    private fun summarizeErrorBody(body: String): String {
        val b = body.trim()
        if (b.isBlank()) return ""

        val options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        val exception = Regex("<(?:\\w+:)?exception>([^<]+)</(?:\\w+:)?exception>", options)
            .find(b)?.groupValues?.getOrNull(1)?.trim()
        val message = Regex("<(?:\\w+:)?message>([^<]+)</(?:\\w+:)?message>", options)
            .find(b)?.groupValues?.getOrNull(1)?.trim()

        val combined = listOfNotNull(exception, message)
            .joinToString(": ")
            .trim()

        if (combined.isNotBlank()) return combined.take(180)
        return b.replace(Regex("\\s+"), " ").take(200)
    }

    private suspend fun mkcol(url: HttpUrl, auth: String?): WebDavResult = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(url)
            // OkHttp requires a body for methods other than GET/HEAD.
            .method("MKCOL", ByteArray(0).toRequestBody(null, 0, 0))
            .apply {
                header("User-Agent", "DanmuApiManager")
                if (!auth.isNullOrBlank()) header("Authorization", auth)
            }
            .build()

        try {
            client.newCall(req).execute().use { resp ->
                val code = resp.code
                // 201 Created: ok
                // 405 Method Not Allowed: many servers return this when the collection already exists
                // 200/204: some servers respond success without creation
                if (code == 201 || code == 405 || code in 200..299) {
                    return@withContext WebDavResult.Success(code)
                }

                val body = summarizeErrorBody(resp.body?.string().orEmpty())
                return@withContext WebDavResult.Error(
                    message = "MKCOL 失败：HTTP $code ${resp.message} ${body}".trim(),
                    code = code,
                )
            }
        } catch (e: IOException) {
            WebDavResult.Error("网络错误：${e.message ?: "IO 异常"}")
        }
    }

    private suspend fun ensureRemoteDirs(baseUrl: String, normalizedFilePath: String, auth: String?): WebDavResult {
        val base = normalizeBaseUrl(baseUrl) ?: return WebDavResult.Error("WebDAV 地址无效")
        val clean = normalizedFilePath.trim().removePrefix("/")
        val parts = clean.split("/").filter { it.isNotBlank() }
        if (parts.size <= 1) return WebDavResult.Success(0) // no dir component

        var current = ""
        for (dir in parts.dropLast(1)) {
            current += "$dir/"
            val dirUrl = base.resolve(current) ?: return WebDavResult.Error("WebDAV 目录路径无效：$current")

            val r = mkcol(dirUrl, auth)
            if (r is WebDavResult.Error) return r
        }
        return WebDavResult.Success(0)
    }

    suspend fun uploadText(
        baseUrl: String,
        remotePath: String,
        username: String?,
        password: String?,
        text: String,
    ): WebDavResult = withContext(Dispatchers.IO) {
        val auth = authHeader(username, password)
        val target = resolveTarget(baseUrl, remotePath) ?: return@withContext WebDavResult.Error("WebDAV 目标地址无效")

        // Only attempt MKCOL when path is relative to baseUrl.
        if (!target.isFullUrl) {
            val dirRes = ensureRemoteDirs(baseUrl, target.normalizedRemotePath, auth)
            if (dirRes is WebDavResult.Error) return@withContext dirRes
        }

        val body = text.toByteArray(Charsets.UTF_8)
            .toRequestBody("text/plain; charset=utf-8".toMediaType())

        val req = Request.Builder()
            .url(target.fileUrl)
            .put(body)
            .apply {
                header("User-Agent", "DanmuApiManager")
                if (!auth.isNullOrBlank()) header("Authorization", auth)
            }
            .build()

        try {
            client.newCall(req).execute().use { resp ->
                val code = resp.code
                if (code in 200..299) return@withContext WebDavResult.Success(code)

                val rawBody = resp.body?.string().orEmpty()
                val bodySummary = summarizeErrorBody(rawBody)
                val hint = if (code == 403 && bodySummary.contains("OperationNotAllowed", ignoreCase = true)) {
                    "（可能远程路径指向目录，请填写文件名，或仅填目录让应用自动使用 $DEFAULT_ENV_FILE_NAME）"
                } else {
                    ""
                }

                return@withContext WebDavResult.Error(
                    message = "PUT 失败：HTTP $code ${resp.message} ${bodySummary}${hint}".trim(),
                    code = code,
                )
            }
        } catch (e: IOException) {
            WebDavResult.Error("网络错误：${e.message ?: "IO 异常"}")
        }
    }

    suspend fun downloadText(
        baseUrl: String,
        remotePath: String,
        username: String?,
        password: String?,
    ): WebDavDownload = withContext(Dispatchers.IO) {
        val auth = authHeader(username, password)
        val target = resolveTarget(baseUrl, remotePath) ?: return@withContext WebDavDownload(WebDavResult.Error("WebDAV 目标地址无效"))

        val req = Request.Builder()
            .url(target.fileUrl)
            .get()
            .apply {
                header("User-Agent", "DanmuApiManager")
                if (!auth.isNullOrBlank()) header("Authorization", auth)
            }
            .build()

        try {
            client.newCall(req).execute().use { resp ->
                val code = resp.code
                if (code !in 200..299) {
                    val bodySummary = summarizeErrorBody(resp.body?.string().orEmpty())
                    return@withContext WebDavDownload(
                        result = WebDavResult.Error(
                            message = "GET 失败：HTTP $code ${resp.message} ${bodySummary}".trim(),
                            code = code,
                        ),
                    )
                }

                val body = resp.body?.bytes() ?: return@withContext WebDavDownload(WebDavResult.Error("响应为空"), null)

                // Prevent accidental memory explosion (env should be small).
                if (body.size > 512 * 1024) {
                    return@withContext WebDavDownload(WebDavResult.Error("文件过大（>${512}KB），已拒绝导入"))
                }

                val text = runCatching { body.toString(Charsets.UTF_8) }.getOrNull()
                    ?: return@withContext WebDavDownload(WebDavResult.Error("文件不是有效的 UTF-8 文本"))

                WebDavDownload(WebDavResult.Success(code), text)
            }
        } catch (e: IOException) {
            WebDavDownload(WebDavResult.Error("网络错误：${e.message ?: "IO 异常"}"), null)
        }
    }
}

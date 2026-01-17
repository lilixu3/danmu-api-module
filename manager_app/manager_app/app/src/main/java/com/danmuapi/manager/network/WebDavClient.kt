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
 * Notes:
 * - This intentionally keeps the feature set small.
 * - We attempt to MKCOL parent directories when the remote path is relative to baseUrl.
 */
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

    private fun buildFileUrl(baseUrl: String, remotePath: String): Pair<HttpUrl?, Boolean> {
        val p = remotePath.trim()
        if (p.isBlank()) return null to false

        // Allow user to paste a full file URL directly into "remotePath".
        if (p.startsWith("http://") || p.startsWith("https://")) {
            return p.toHttpUrlOrNull() to true
        }

        val base = normalizeBaseUrl(baseUrl) ?: return null to false
        val clean = p.removePrefix("/")
        return (base.resolve(clean) ?: run {
            // Fallback: append as path segments.
            base.newBuilder().addPathSegments(clean).build()
        }) to false
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

                val body = resp.body?.string()?.take(200).orEmpty()
                return@withContext WebDavResult.Error(
                    message = "MKCOL 失败：HTTP $code ${resp.message} ${body}".trim(),
                    code = code,
                )
            }
        } catch (e: IOException) {
            WebDavResult.Error("网络错误：${e.message ?: "IO 异常"}")
        }
    }

    private suspend fun ensureRemoteDirs(baseUrl: String, remotePath: String, auth: String?): WebDavResult {
        val base = normalizeBaseUrl(baseUrl) ?: return WebDavResult.Error("WebDAV 地址无效")
        val clean = remotePath.trim().removePrefix("/")
        val parts = clean.split("/").filter { it.isNotBlank() }
        if (parts.size <= 1) return WebDavResult.Success(0) // no dir component

        var current = ""
        for (dir in parts.dropLast(1)) {
            current += "$dir/"
            val dirUrl = base.resolve(current)
                ?: return WebDavResult.Error("WebDAV 目录路径无效：$current")

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
        val (fileUrl, isFullUrl) = buildFileUrl(baseUrl, remotePath)
        if (fileUrl == null) return@withContext WebDavResult.Error("WebDAV 目标地址无效")

        // Only attempt MKCOL when path is relative to baseUrl.
        if (!isFullUrl) {
            val dirRes = ensureRemoteDirs(baseUrl, remotePath, auth)
            if (dirRes is WebDavResult.Error) return@withContext dirRes
        }

        val body = text.toByteArray(Charsets.UTF_8)
            .toRequestBody("text/plain; charset=utf-8".toMediaType())

        val req = Request.Builder()
            .url(fileUrl)
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

                val respBody = resp.body?.string()?.take(200).orEmpty()
                return@withContext WebDavResult.Error(
                    message = "PUT 失败：HTTP $code ${resp.message} ${respBody}".trim(),
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
        val (fileUrl, _) = buildFileUrl(baseUrl, remotePath)
        if (fileUrl == null) return@withContext WebDavDownload(WebDavResult.Error("WebDAV 目标地址无效"))

        val req = Request.Builder()
            .url(fileUrl)
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
                    val respBody = resp.body?.string()?.take(200).orEmpty()
                    return@withContext WebDavDownload(
                        result = WebDavResult.Error(
                            message = "GET 失败：HTTP $code ${resp.message} ${respBody}".trim(),
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

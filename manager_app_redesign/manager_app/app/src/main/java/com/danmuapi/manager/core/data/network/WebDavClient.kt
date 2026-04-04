package com.danmuapi.manager.core.data.network

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
        val normalizedRemotePath: String,
    )

    private fun authHeader(username: String?, password: String?): String? {
        val safeUsername = username?.trim().orEmpty()
        val safePassword = password.orEmpty()
        if (safeUsername.isEmpty() && safePassword.isEmpty()) return null
        return Credentials.basic(safeUsername, safePassword, charset = Charsets.UTF_8)
    }

    private fun normalizeBaseUrl(baseUrl: String): HttpUrl? {
        val trimmed = baseUrl.trim()
        if (trimmed.isBlank()) return null
        val normalized = if (trimmed.endsWith("/")) trimmed else "$trimmed/"
        return normalized.toHttpUrlOrNull()
    }

    private fun normalizeRelativeRemotePath(remotePath: String): String {
        val trimmed = remotePath.trim().removePrefix("/")
        if (trimmed.isBlank()) return DEFAULT_ENV_FILE_NAME
        if (trimmed.endsWith("/")) return trimmed + DEFAULT_ENV_FILE_NAME

        val lastSegment = trimmed.substringAfterLast("/")
        return if (lastSegment.contains(".")) {
            trimmed
        } else {
            "$trimmed/$DEFAULT_ENV_FILE_NAME"
        }
    }

    private fun ensureFileUrl(url: HttpUrl): HttpUrl {
        val pathEndsWithSlash = url.encodedPath.endsWith("/")
        val lastSegment = url.pathSegments.lastOrNull().orEmpty()
        val looksLikeFile = lastSegment.contains(".")
        val shouldAppendFile = pathEndsWithSlash || lastSegment.isBlank() || !looksLikeFile
        return if (shouldAppendFile) {
            url.newBuilder().addPathSegment(DEFAULT_ENV_FILE_NAME).build()
        } else {
            url
        }
    }

    private fun resolveTarget(baseUrl: String, remotePath: String): WebDavTarget? {
        val trimmed = remotePath.trim()
        val isFullUrl = trimmed.startsWith("http://") || trimmed.startsWith("https://")

        if (isFullUrl) {
            val url = trimmed.toHttpUrlOrNull() ?: return null
            return WebDavTarget(
                fileUrl = ensureFileUrl(url),
                isFullUrl = true,
                normalizedRemotePath = trimmed,
            )
        }

        val base = normalizeBaseUrl(baseUrl) ?: return null
        val normalizedPath = normalizeRelativeRemotePath(trimmed)
        val fileUrl = base.resolve(normalizedPath)
            ?: base.newBuilder().addPathSegments(normalizedPath).build()
        return WebDavTarget(
            fileUrl = fileUrl,
            isFullUrl = false,
            normalizedRemotePath = normalizedPath,
        )
    }

    private fun summarizeErrorBody(body: String): String {
        val trimmed = body.trim()
        if (trimmed.isBlank()) return ""

        val options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        val exception = Regex("<(?:\\w+:)?exception>([^<]+)</(?:\\w+:)?exception>", options)
            .find(trimmed)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
        val message = Regex("<(?:\\w+:)?message>([^<]+)</(?:\\w+:)?message>", options)
            .find(trimmed)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()

        val combined = listOfNotNull(exception, message)
            .joinToString(": ")
            .trim()
        if (combined.isNotBlank()) return combined.take(180)
        return trimmed.replace(Regex("\\s+"), " ").take(200)
    }

    private suspend fun mkcol(url: HttpUrl, auth: String?): WebDavResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .method("MKCOL", ByteArray(0).toRequestBody(null, 0, 0))
            .apply {
                header("User-Agent", "DanmuApiManager")
                if (!auth.isNullOrBlank()) header("Authorization", auth)
            }
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val code = response.code
                if (code == 201 || code == 405 || code in 200..299) {
                    return@withContext WebDavResult.Success(code)
                }

                val summary = summarizeErrorBody(response.body?.string().orEmpty())
                WebDavResult.Error(
                    message = "MKCOL 失败：HTTP $code ${response.message} $summary".trim(),
                    code = code,
                )
            }
        } catch (exception: IOException) {
            WebDavResult.Error("网络错误：${exception.message ?: "IO 异常"}")
        }
    }

    private suspend fun ensureRemoteDirs(
        baseUrl: String,
        normalizedFilePath: String,
        auth: String?,
    ): WebDavResult {
        val base = normalizeBaseUrl(baseUrl) ?: return WebDavResult.Error("WebDAV 地址无效")
        val parts = normalizedFilePath.trim()
            .removePrefix("/")
            .split("/")
            .filter { it.isNotBlank() }
        if (parts.size <= 1) return WebDavResult.Success(0)

        var currentPath = ""
        for (directory in parts.dropLast(1)) {
            currentPath += "$directory/"
            val dirUrl = base.resolve(currentPath)
                ?: return WebDavResult.Error("WebDAV 目录路径无效：$currentPath")
            val result = mkcol(dirUrl, auth)
            if (result is WebDavResult.Error) return result
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
        val target = resolveTarget(baseUrl, remotePath)
            ?: return@withContext WebDavResult.Error("WebDAV 目标地址无效")

        if (!target.isFullUrl) {
            val dirResult = ensureRemoteDirs(baseUrl, target.normalizedRemotePath, auth)
            if (dirResult is WebDavResult.Error) return@withContext dirResult
        }

        val request = Request.Builder()
            .url(target.fileUrl)
            .put(text.toByteArray(Charsets.UTF_8).toRequestBody("text/plain; charset=utf-8".toMediaType()))
            .apply {
                header("User-Agent", "DanmuApiManager")
                if (!auth.isNullOrBlank()) header("Authorization", auth)
            }
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val code = response.code
                if (code in 200..299) return@withContext WebDavResult.Success(code)

                val body = summarizeErrorBody(response.body?.string().orEmpty())
                val hint = if (code == 403 && body.contains("OperationNotAllowed", ignoreCase = true)) {
                    "（可能远程路径指向目录，请填写文件名，或仅填目录让应用自动使用 $DEFAULT_ENV_FILE_NAME）"
                } else {
                    ""
                }

                WebDavResult.Error(
                    message = "PUT 失败：HTTP $code ${response.message} $body$hint".trim(),
                    code = code,
                )
            }
        } catch (exception: IOException) {
            WebDavResult.Error("网络错误：${exception.message ?: "IO 异常"}")
        }
    }

    suspend fun downloadText(
        baseUrl: String,
        remotePath: String,
        username: String?,
        password: String?,
    ): WebDavDownload = withContext(Dispatchers.IO) {
        val auth = authHeader(username, password)
        val target = resolveTarget(baseUrl, remotePath)
            ?: return@withContext WebDavDownload(WebDavResult.Error("WebDAV 目标地址无效"))

        val request = Request.Builder()
            .url(target.fileUrl)
            .get()
            .apply {
                header("User-Agent", "DanmuApiManager")
                if (!auth.isNullOrBlank()) header("Authorization", auth)
            }
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val code = response.code
                if (code !in 200..299) {
                    return@withContext WebDavDownload(
                        result = WebDavResult.Error(
                            message = "GET 失败：HTTP $code ${response.message} ${summarizeErrorBody(response.body?.string().orEmpty())}".trim(),
                            code = code,
                        ),
                    )
                }

                val body = response.body
                    ?: return@withContext WebDavDownload(WebDavResult.Error("响应为空"))
                val charset = try {
                    body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
                } catch (_: Throwable) {
                    Charsets.UTF_8
                }
                val bytes = body.bytes()

                WebDavDownload(
                    result = WebDavResult.Success(code),
                    text = bytes.toString(charset),
                )
            }
        } catch (exception: IOException) {
            WebDavDownload(WebDavResult.Error("网络错误：${exception.message ?: "IO 异常"}"))
        }
    }
}

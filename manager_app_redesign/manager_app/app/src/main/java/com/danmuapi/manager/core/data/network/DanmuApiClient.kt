package com.danmuapi.manager.core.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

data class HttpResult(
    val code: Int,
    val body: String,
    val contentType: String?,
    val durationMs: Long,
    val error: String? = null,
    val truncated: Boolean = false,
    val bodyBytesKept: Long = 0L,
) {
    val isSuccessful: Boolean
        get() = error == null && code in 200..299
}

class DanmuApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(12, TimeUnit.SECONDS)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .build(),
) {
    private val maxBodyBytes: Long = 2_000_000L

    fun normalizeHost(host: String?): String {
        val raw = host.orEmpty().trim()
        if (raw.isBlank()) return "127.0.0.1"

        val noScheme = raw
            .removePrefix("http://")
            .removePrefix("https://")
            .trim()
            .trimEnd('/')

        if (noScheme == "0.0.0.0" || noScheme == "::" || noScheme == "0:0:0:0:0:0:0:0") {
            return "127.0.0.1"
        }

        val cleaned = if (noScheme.contains(':') && !noScheme.startsWith("[")) {
            noScheme.substringBefore(':')
        } else {
            noScheme
        }
        return cleaned.ifBlank { "127.0.0.1" }
    }

    fun buildUrl(
        host: String?,
        port: Int,
        tokenSegment: String,
        path: String,
        query: Map<String, String?> = emptyMap(),
    ): HttpUrl {
        val urlBuilder = HttpUrl.Builder()
            .scheme("http")
            .host(normalizeHost(host))
            .port(port.coerceIn(1, 65_535))

        val safeToken = tokenSegment.trim().ifBlank { "87654321" }
        urlBuilder.addPathSegment(safeToken)
        path.trim()
            .removePrefix("/")
            .split('/')
            .filter { it.isNotBlank() }
            .forEach(urlBuilder::addPathSegment)

        query.forEach { (key, value) ->
            if (key.isNotBlank() && value != null) {
                urlBuilder.addQueryParameter(key, value)
            }
        }
        return urlBuilder.build()
    }

    suspend fun request(
        method: String,
        host: String?,
        port: Int,
        tokenSegment: String,
        path: String,
        query: Map<String, String?> = emptyMap(),
        bodyJson: String? = null,
    ): HttpResult = withContext(Dispatchers.IO) {
        val url = buildUrl(host, port, tokenSegment, path, query)
        val normalizedMethod = method.trim().uppercase().ifBlank { "GET" }

        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", "DanmuApiManager")
            .header("Accept", "*/*")

        if (normalizedMethod in setOf("POST", "PUT", "PATCH")) {
            val body = (bodyJson ?: "{}").toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            requestBuilder.method(normalizedMethod, body)
        } else {
            requestBuilder.method(normalizedMethod, null)
        }

        val startNs = System.nanoTime()
        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                val durationMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(0L)
                val bodyRead = readBodyWithLimit(response)
                HttpResult(
                    code = response.code,
                    body = bodyRead.text,
                    contentType = response.header("Content-Type"),
                    durationMs = durationMs,
                    truncated = bodyRead.truncated,
                    bodyBytesKept = bodyRead.bytesKept,
                )
            }
        } catch (exception: IOException) {
            HttpResult(
                code = -1,
                body = "",
                contentType = null,
                durationMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(0L),
                error = exception.message ?: "Network error",
            )
        } catch (throwable: Throwable) {
            HttpResult(
                code = -1,
                body = "",
                contentType = null,
                durationMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(0L),
                error = throwable.message ?: "Unexpected error",
            )
        }
    }

    private data class BodyReadResult(
        val text: String,
        val truncated: Boolean,
        val bytesKept: Long,
    )

    private fun readBodyWithLimit(response: Response): BodyReadResult {
        val body = response.body ?: return BodyReadResult("", truncated = false, bytesKept = 0L)
        val charset = try {
            body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
        } catch (_: Throwable) {
            Charsets.UTF_8
        }

        val inputStream = try {
            body.byteStream()
        } catch (_: Throwable) {
            return BodyReadResult("", truncated = false, bytesKept = 0L)
        }

        val buffer = ByteArray(8 * 1024)
        val output = ByteArrayOutputStream()
        var truncated = false
        var keptBytes = 0L

        try {
            while (true) {
                val read = inputStream.read(buffer)
                if (read <= 0) break

                val remaining = (maxBodyBytes - keptBytes).toInt()
                if (remaining <= 0) {
                    truncated = true
                    break
                }

                if (read <= remaining) {
                    output.write(buffer, 0, read)
                    keptBytes += read.toLong()
                } else {
                    output.write(buffer, 0, remaining)
                    keptBytes += remaining.toLong()
                    truncated = true
                    break
                }
            }
        } catch (_: Throwable) {
        } finally {
            try {
                inputStream.close()
            } catch (_: Throwable) {
            }
        }

        val text = try {
            output.toByteArray().toString(charset)
        } catch (_: Throwable) {
            output.toByteArray().toString(Charsets.UTF_8)
        }

        return BodyReadResult(
            text = text,
            truncated = truncated,
            bytesKept = keptBytes,
        )
    }
}

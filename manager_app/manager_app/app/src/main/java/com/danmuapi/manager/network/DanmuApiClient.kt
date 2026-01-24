package com.danmuapi.manager.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

data class HttpResult(
    val code: Int,
    val body: String,
    val contentType: String?,
    val durationMs: Long,
    val error: String? = null,
    /**
     * True when the response body is truncated due to size limit.
     *
     * This is mainly to avoid OOM / ANR when endpoints return huge payloads
     * (e.g. "获取弹幕" returning megabytes of XML/JSON).
     */
    val truncated: Boolean = false,
    /** Number of bytes actually kept in [body] (best-effort). */
    val bodyBytesKept: Long = 0L,
) {
    val isSuccessful: Boolean get() = error == null && code in 200..299
}

/**
 * Minimal HTTP client for talking to the danmu-api server.
 *
 * Notes:
 * - danmu-api uses a *token as the first path segment*: http://host:port/{TOKEN}/api/...
 * - For local device access, DANMU_API_HOST may be 0.0.0.0 (bind all). That is NOT a valid
 *   client destination, so we normalize to 127.0.0.1.
 */
class DanmuApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(12, TimeUnit.SECONDS)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .build(),
) {

    private val maxBodyBytes: Long = 2_000_000L // ~2MB guard rail for mobile UI stability

    fun normalizeHost(host: String?): String {
        val raw = host.orEmpty().trim()
        if (raw.isBlank()) return "127.0.0.1"
        val noScheme = raw
            .removePrefix("http://")
            .removePrefix("https://")
            .trim()
            .trimEnd('/')

        // DANMU_API_HOST usually uses 0.0.0.0 as bind-all.
        if (noScheme == "0.0.0.0" || noScheme == "::" || noScheme == "0:0:0:0:0:0:0:0") {
            return "127.0.0.1"
        }

        // If user accidentally put host:port here, keep host only.
        val cleaned = if (noScheme.contains(":") && !noScheme.startsWith("[")) {
            noScheme.substringBefore(":")
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
        val h = normalizeHost(host)
        val safePort = port.coerceIn(1, 65535)
        val p = path.trim().removePrefix("/")
        val urlBuilder = HttpUrl.Builder()
            .scheme("http")
            .host(h)
            .port(safePort)

        val tok = tokenSegment.trim().ifBlank { "87654321" }
        urlBuilder.addPathSegment(tok)
        if (p.isNotBlank()) {
            p.split('/').filter { it.isNotBlank() }.forEach { seg ->
                urlBuilder.addPathSegment(seg)
            }
        }
        query.forEach { (k, v) ->
            if (k.isNotBlank() && v != null) urlBuilder.addQueryParameter(k, v)
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
        val m = method.trim().uppercase().ifBlank { "GET" }

        val reqBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", "DanmuApiManager")
            .header("Accept", "*/*")

        if (m == "POST" || m == "PUT" || m == "PATCH") {
            val ct = "application/json; charset=utf-8".toMediaTypeOrNull()
            val body = (bodyJson ?: "{}").toRequestBody(ct)
            reqBuilder.method(m, body)
        } else {
            reqBuilder.method(m, null)
        }

        val start = System.nanoTime()
        try {
            client.newCall(reqBuilder.build()).execute().use { resp ->
                val end = System.nanoTime()
                val durMs = ((end - start) / 1_000_000L).coerceAtLeast(0L)
                // Guard rail: never read an unbounded response into memory.
                // If the body exceeds maxBodyBytes, we keep only the first chunk and mark as truncated.
                val bodyInfo = readBodyWithLimit(resp)
                return@withContext HttpResult(
                    code = resp.code,
                    body = bodyInfo.text,
                    contentType = resp.header("Content-Type"),
                    durationMs = durMs,
                    error = null,
                    truncated = bodyInfo.truncated,
                    bodyBytesKept = bodyInfo.bytesKept,
                )
            }
        } catch (e: IOException) {
            val end = System.nanoTime()
            val durMs = ((end - start) / 1_000_000L).coerceAtLeast(0L)
            return@withContext HttpResult(
                code = -1,
                body = "",
                contentType = null,
                durationMs = durMs,
                error = e.message ?: "Network error",
            )
        } catch (t: Throwable) {
            val end = System.nanoTime()
            val durMs = ((end - start) / 1_000_000L).coerceAtLeast(0L)
            return@withContext HttpResult(
                code = -1,
                body = "",
                contentType = null,
                durationMs = durMs,
                error = t.message ?: "Unexpected error",
            )
        }
    }

    private data class BodyReadResult(
        val text: String,
        val truncated: Boolean,
        val bytesKept: Long,
    )

    private fun readBodyWithLimit(resp: okhttp3.Response): BodyReadResult {
        val body = resp.body ?: return BodyReadResult("", truncated = false, bytesKept = 0L)

        val charset = try {
            body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
        } catch (_: Throwable) {
            Charsets.UTF_8
        }

        // Read as stream and cap at maxBodyBytes.
        val input = try {
            body.byteStream()
        } catch (_: Throwable) {
            return BodyReadResult("", truncated = false, bytesKept = 0L)
        }

        val buf = ByteArray(8 * 1024)
        val out = java.io.ByteArrayOutputStream()
        var truncated = false
        var kept: Long = 0L

        try {
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break

                val remaining = (maxBodyBytes - kept).toInt()
                if (remaining <= 0) {
                    truncated = true
                    break
                }

                if (n <= remaining) {
                    out.write(buf, 0, n)
                    kept += n.toLong()
                } else {
                    out.write(buf, 0, remaining)
                    kept += remaining.toLong()
                    truncated = true
                    break
                }
            }
        } catch (_: Throwable) {
            // Best-effort: return what we already got.
        } finally {
            try {
                input.close()
            } catch (_: Throwable) {
            }
        }

        val text = try {
            out.toByteArray().toString(charset)
        } catch (_: Throwable) {
            // Fallback: treat as UTF-8.
            out.toByteArray().toString(Charsets.UTF_8)
        }

        return BodyReadResult(text = text, truncated = truncated, bytesKept = kept)
    }
}

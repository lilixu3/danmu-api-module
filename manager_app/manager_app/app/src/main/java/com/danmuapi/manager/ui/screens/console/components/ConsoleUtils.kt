package com.danmuapi.manager.ui.screens.console.components

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Human-readable byte size (e.g. "1.2MB"). */
fun humanBytes(bytes: Long): String {
    val b = bytes.coerceAtLeast(0L)
    val units = arrayOf("B", "KB", "MB", "GB")
    var v = b.toDouble()
    var i = 0
    while (v >= 1024.0 && i < units.lastIndex) {
        v /= 1024.0
        i++
    }
    return if (i == 0) "${b}${units[i]}" else String.format(Locale.getDefault(), "%.1f%s", v, units[i])
}

/** Pretty-print JSON if possible, otherwise return raw. */
fun prettifyIfJson(raw: String, maxChars: Int = 120_000): String {
    if (raw.length > maxChars) return raw
    val t = raw.trim()
    if (!(t.startsWith("{") && t.endsWith("}")) && !(t.startsWith("[") && t.endsWith("]"))) return raw
    return try {
        if (t.startsWith("{")) JSONObject(t).toString(2) else JSONArray(t).toString(2)
    } catch (_: Throwable) {
        raw
    }
}

fun sanitizeForFilenamePart(input: String?, maxLen: Int = 24): String {
    val raw = input.orEmpty().trim()
    if (raw.isBlank()) return ""
    val cleaned = raw
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .replace(Regex("\\s+"), "_")
        .replace(Regex("_+"), "_")
        .trim('_')
    return cleaned.take(maxLen)
}

fun extractHostForFilename(url: String): String {
    return try {
        val host = java.net.URI(url.trim()).host.orEmpty()
        host.replace('.', '-').trim('-')
    } catch (_: Throwable) {
        ""
    }
}
/** Whether a value should be masked in preview mode. */
fun shouldMaskInPreview(
    key: String,
    type: String,
    description: String,
    value: String,
): Boolean {
    if (type.equals("password", true)) return true

    val v = value.trim()
    if (v.isNotBlank() && v.all { it == '*' }) return true

    val k = key.trim().uppercase(Locale.getDefault())
    val hit = listOf(
        "TOKEN", "ADMIN", "PASSWORD", "PASS", "SECRET", "KEY",
        "COOKIE", "SESS", "AUTH", "BEARER", "JWT", "SIGN", "PRIVATE", "ACCESS",
    ).any { k.contains(it) }
    if (hit) return true

    val d = description.lowercase(Locale.getDefault())
    if (d.contains("token") || d.contains("password") || d.contains("secret") || d.contains("cookie")) return true
    if (d.contains("密码") || d.contains("令牌") || d.contains("密钥") || d.contains("cookie")) return true

    if (v.startsWith("eyJ") && v.count { it == '.' } >= 2) return true
    if (v.contains("://") && v.contains("@") && v.substringBefore("@").contains(":")) return true

    return false
}

fun categoryLabel(category: String): String {
    return when (category.lowercase(Locale.getDefault())) {
        "api" -> "API"
        "source" -> "数据源"
        "match" -> "匹配"
        "danmu" -> "弹幕"
        "cache" -> "缓存"
        "system" -> "系统"
        else -> category
    }
}

// ── Data classes for API test ──

data class ApiParam(
    val name: String,
    val label: String,
    val type: String = "text",
    val required: Boolean = false,
    val placeholder: String = "",
    val options: List<String> = emptyList(),
    val default: String? = null,
)

data class ApiEndpoint(
    val key: String,
    val name: String,
    val icon: String,
    val method: String,
    val path: String,
    val description: String,
    val params: List<ApiParam> = emptyList(),
    val hasBody: Boolean = false,
    val bodyTemplate: String? = null,
)

fun suggestApiExportFileName(
    endpoint: ApiEndpoint,
    params: Map<String, String>,
    contentType: String?,
    truncated: Boolean,
): String {
    val ts = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(java.util.Date())

    val ext = when {
        params["format"]?.equals("xml", true) == true -> "xml"
        params["format"]?.equals("json", true) == true -> "json"
        contentType?.contains("xml", true) == true -> "xml"
        contentType?.contains("json", true) == true -> "json"
        else -> "txt"
    }

    val base = when (endpoint.key) {
        "getComment" -> "comment-" + sanitizeForFilenamePart(params["commentId"], 16)
        "getBangumi" -> "bangumi-" + sanitizeForFilenamePart(params["animeId"], 16)
        "searchAnime" -> "search-anime-" + sanitizeForFilenamePart(params["keyword"], 18)
        "searchEpisodes" -> "search-episodes-" + sanitizeForFilenamePart(params["anime"], 18)
        "matchAnime" -> "match-" + sanitizeForFilenamePart(params["fileName"], 22)
        "getCommentByUrl" -> {
            val host = extractHostForFilename(params["url"].orEmpty())
            if (host.isNotBlank()) "comment-url-$host" else "comment-url"
        }
        "getSegmentComment" -> "segmentcomment"
        else -> sanitizeForFilenamePart(endpoint.key, 28).ifBlank { "danmu-api" }
    }.trim('-', '_')

    val suffix = if (truncated) "-partial" else ""
    val name = "$base-$ts$suffix.$ext"
    return if (name.length > 140) name.take(140) else name
}

// ── Data classes for Push tab ──

data class AnimeItem(
    val animeId: Int,
    val title: String,
    val typeDesc: String = "",
)

data class EpisodeItem(
    val episodeId: Int,
    val title: String,
    val episodeNumber: String = "",
)

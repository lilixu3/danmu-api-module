package com.danmuapi.manager.core.data

import java.security.MessageDigest

/**
 * 签名运行时依赖包协议（schema-3）。
 *
 * 与 runtime-packs 发布端（build_runtime_pack.py）及 App 端
 * （RuntimeDependencyPackProtocol）共享同一契约：
 * - 依赖指纹 = sha256(canonical_json(sorted(dependencies + optionalDependencies)))
 *   canonical_json = JSON.stringify sort_keys + separators=(",",":") + ensure_ascii=false
 * - manifest.json 由 manifest.sig（RSA SHA256withRSA）签名
 * - 依赖包为纯 JS node_modules.zip，只允许 node_modules/ 前缀路径
 */
internal object RuntimePackProtocol {
    const val SCHEMA = 3
    const val PACK_REPO = "lilixu3/danmu-api-runtime-packs"
    const val MANIFEST_PATH = "main/manifest.json"
    const val MANIFEST_SIGNATURE_PATH = "main/manifest.sig"
    const val MAX_MANIFEST_BYTES = 1024 * 1024
    const val MAX_MANIFEST_SIGNATURE_BYTES = 16 * 1024
    const val MAX_ARCHIVE_BYTES = 64L * 1024L * 1024L
    const val MAX_EXTRACTED_BYTES = 128L * 1024L * 1024L
    const val MAX_ARCHIVE_ENTRIES = 20_000

    fun dependencyFingerprint(dependencies: Map<String, String>): String {
        val sorted = dependencies.toSortedMap()
        // JSON.stringify(obj, sortedKeys) 与 Python sort_keys+separators 等价：
        // 无空格、键按字典序、值按 JSON 字符串规则转义
        val canonical = sorted.entries.joinToString(
            separator = ",",
            prefix = "{",
            postfix = "}",
        ) { (name, spec) ->
            "${jsonString(name)}:${jsonString(spec)}"
        }
        return sha256(canonical.toByteArray(Charsets.UTF_8))
    }

    fun isSafeArchivePath(path: String): Boolean {
        if (path.isBlank() || path != path.trim() || '\\' in path || '\u0000' in path) return false
        if (path.startsWith('/') || !path.startsWith("node_modules/")) return false
        val normalized = path.removeSuffix("/")
        val parts = normalized.split('/')
        return parts.size >= 2 && parts.none { it.isBlank() || it == "." || it == ".." }
    }

    fun isNativeArtifactPath(path: String): Boolean {
        val lower = path.lowercase()
        return lower.endsWith(".node") ||
            lower.endsWith(".so") ||
            lower.endsWith(".dll") ||
            lower.endsWith(".dylib") ||
            lower.endsWith("/binding.gyp") ||
            "/prebuilds/" in lower
    }

    fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun sha256(file: java.io.File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun jsonString(value: String): String {
        // 与 JSON.stringify 一致：仅转义引号/反斜杠/控制字符；非 ASCII 原样保留
        // （Python ensure_ascii=false 语义），保证跨端指纹一致
        val builder = StringBuilder("\"")
        for (char in value) {
            when (char) {
                '"' -> builder.append("\\\"")
                '\\' -> builder.append("\\\\")
                '\n' -> builder.append("\\n")
                '\r' -> builder.append("\\r")
                '\t' -> builder.append("\\t")
                '\b' -> builder.append("\\b")
                '\u000C' -> builder.append("\\f")
                else -> {
                    if (char < ' ') {
                        builder.append("\\u").append("%04x".format(char.code))
                    } else {
                        builder.append(char)
                    }
                }
            }
        }
        return builder.append('"').toString()
    }
}

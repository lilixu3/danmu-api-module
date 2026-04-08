package com.danmuapi.manager.core.root

import com.danmuapi.manager.core.model.CoreCatalog
import com.danmuapi.manager.core.model.LogDirectory
import com.danmuapi.manager.core.model.LogFileEntry
import com.danmuapi.manager.core.model.ManagerStatus
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

internal fun extractJsonObjectForTest(text: String): String? {
    val trimmed = text.trim()
    val start = trimmed.indexOf('{')
    val end = trimmed.lastIndexOf('}')
    return if (start >= 0 && end > start) {
        trimmed.substring(start, end + 1)
    } else {
        null
    }
}

class DanmuCli(
    private val runSu: suspend (String, Long) -> ShellResult = { command, timeoutMs ->
        RootShell.runSu(command, timeoutMs)
    },
    moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build(),
) {
    private val statusAdapter = moshi.adapter(ManagerStatus::class.java)
    private val coreCatalogAdapter = moshi.adapter(CoreCatalog::class.java)
    private val logDirectoryAdapter = moshi.adapter(LogDirectory::class.java)

    private fun sanitizeShellArgument(value: String): String {
        return value
            .replace("\"", "")
            .replace("'", "")
            .trim()
            .replace("\r", "")
            .replace("\n", "")
    }

    private fun <T> parseJsonSafely(adapter: JsonAdapter<T>, raw: String): T? {
        val direct = raw.trim()
        if (direct.isNotBlank()) {
            try {
                adapter.fromJson(direct)?.let { return it }
            } catch (_: Throwable) {
            }
        }

        val extracted = extractJsonObjectForTest(raw) ?: return null
        return try {
            adapter.fromJson(extracted)
        } catch (_: Throwable) {
            null
        }
    }

    suspend fun getStatus(): ManagerStatus? {
        val result = runSu("${DanmuPaths.CORE_CLI} status --json", 10_000L)
        if (result.exitCode != 0) return null
        return parseJsonSafely(statusAdapter, result.stdout)
    }

    suspend fun getProcessElapsedSeconds(pid: String): Long? {
        val safePid = sanitizeShellArgument(pid)
        if (safePid.isBlank() || safePid.any { !it.isDigit() }) return null

        val command = buildString {
            append("p='")
            append(safePid)
            append("'; ")
            append("[ -r \"/proc/${'$'}p/stat\" ] || exit 1; ")
            append("[ -r /proc/uptime ] || exit 1; ")
            append("hz=\"$(getconf CLK_TCK 2>/dev/null || echo 100)\"; ")
            append("st=\"$(awk '{print $22}' \"/proc/${'$'}p/stat\" 2>/dev/null)\"; ")
            append("up=\"$(cut -d' ' -f1 /proc/uptime 2>/dev/null)\"; ")
            append("awk -v up=\"${'$'}up\" -v st=\"${'$'}st\" -v hz=\"${'$'}hz\" ")
            append("'BEGIN { if (hz <= 0) hz = 100; elapsed = int(up - (st / hz)); if (elapsed < 0) elapsed = 0; printf \"%d\", elapsed }'")
        }

        val result = runSu(command, 5_000L)
        if (result.exitCode != 0) return null
        return result.stdout.trim().toLongOrNull()
    }

    suspend fun listCores(): CoreCatalog? {
        val result = runSu("${DanmuPaths.CORE_CLI} core list --json", 10_000L)
        if (result.exitCode != 0) return null
        return parseJsonSafely(coreCatalogAdapter, result.stdout)
    }

    suspend fun startService(): Boolean = runSu("${DanmuPaths.CONTROL_CLI} start", 10_000L).exitCode == 0

    suspend fun stopService(): Boolean = runSu("${DanmuPaths.CONTROL_CLI} stop", 10_000L).exitCode == 0

    suspend fun restartService(): Boolean = runSu("${DanmuPaths.CONTROL_CLI} restart", 10_000L).exitCode == 0

    suspend fun setAutostart(enabled: Boolean): Boolean {
        val mode = if (enabled) "on" else "off"
        return runSu("${DanmuPaths.CORE_CLI} autostart $mode", 10_000L).exitCode == 0
    }

    suspend fun installCore(repo: String, ref: String): Boolean {
        val safeRepo = sanitizeShellArgument(repo)
        val safeRef = sanitizeShellArgument(ref)
        return runSu("${DanmuPaths.CORE_CLI} core install '$safeRepo' '$safeRef'", 600_000L).exitCode == 0
    }

    suspend fun activateCore(id: String): Boolean {
        val safeId = sanitizeShellArgument(id)
        return runSu("${DanmuPaths.CORE_CLI} core activate '$safeId'", 30_000L).exitCode == 0
    }

    suspend fun deleteCore(id: String): Boolean {
        val safeId = sanitizeShellArgument(id)
        return runSu("${DanmuPaths.CORE_CLI} core delete '$safeId'", 30_000L).exitCode == 0
    }

    suspend fun listLogs(): LogDirectory? {
        val primary = runSu("${DanmuPaths.CORE_CLI} logs list", 10_000L)
        if (primary.exitCode == 0) {
            parseJsonSafely(logDirectoryAdapter, primary.stdout)?.let { return it }
        }

        val command = buildString {
            append("for f in '")
            append(DanmuPaths.LOG_DIR)
            append("'/*; do ")
            append("[ -f \"\$f\" ] || continue; ")
            append("name=\"\$(basename \"\$f\")\"; ")
            append("size=\"\$(wc -c < \"\$f\" 2>/dev/null || echo 0)\"; ")
            append("mtime=\"\$(date -r \"\$f\" '+%F %T' 2>/dev/null || echo '')\"; ")
            append("printf '%s\\t%s\\t%s\\t%s\\n' \"\$name\" \"\$f\" \"\$size\" \"\$mtime\"; ")
            append("done")
        }

        val fallback = runSu(command, 10_000L)
        val files = fallback.stdout
            .lineSequence()
            .mapNotNull { line ->
                val trimmed = line.trimEnd()
                if (trimmed.isBlank()) return@mapNotNull null
                val parts = trimmed.split('\t')
                if (parts.size < 2) return@mapNotNull null
                LogFileEntry(
                    name = parts.getOrNull(0).orEmpty(),
                    path = parts.getOrNull(1).orEmpty(),
                    sizeBytes = parts.getOrNull(2)?.toLongOrNull() ?: 0L,
                    modifiedAt = parts.getOrNull(3)?.takeIf { it.isNotBlank() },
                )
            }
            .toList()

        return LogDirectory(dir = DanmuPaths.LOG_DIR, files = files)
    }

    suspend fun clearLogs(): Boolean = runSu("${DanmuPaths.CORE_CLI} logs clear", 10_000L).exitCode == 0

    suspend fun tailLog(path: String, lines: Int = 200): String? {
        val safePath = sanitizeShellArgument(path)
        if (safePath.isBlank()) return null

        val lineCount = lines.coerceIn(10, 2_000)
        val busyboxPersist = "${DanmuPaths.BIN_DIR}/busybox"
        val busyboxModule = "/data/adb/modules/danmu_api_server/bin/busybox"

        val command = (
            "p='$safePath'; n=$lineCount; " +
                "bb=''; " +
                "if [ -x '$busyboxPersist' ]; then bb='$busyboxPersist'; " +
                "elif [ -x '$busyboxModule' ]; then bb='$busyboxModule'; fi; " +
                "export LD_LIBRARY_PATH=" +
                "'/data/adb/danmu_api_server/lib:/data/adb/danmu_api_server/bin/lib:/data/adb/modules/danmu_api_server/bin/lib:/data/adb/modules/danmu_api_server/lib:'" +
                "${'$'}{LD_LIBRARY_PATH:-}; " +
                "if [ ! -f \"${'$'}p\" ]; then echo \"file not found: ${'$'}p\" 1>&2; exit 2; fi; " +
                "if [ -n \"${'$'}bb\" ]; then \"${'$'}bb\" true >/dev/null 2>&1 && \"${'$'}bb\" tail -n \"${'$'}n\" \"${'$'}p\" 2>/dev/null && exit 0; fi; " +
                "if command -v toybox >/dev/null 2>&1; then toybox tail -n \"${'$'}n\" \"${'$'}p\" 2>/dev/null && exit 0; fi; " +
                "tail -n \"${'$'}n\" \"${'$'}p\" 2>/dev/null || cat \"${'$'}p\""
            )

        val result = runSu(command, 15_000L)
        if (result.exitCode != 0 && result.stdout.isBlank()) {
            val error = result.stderr.trim().ifBlank { "exitCode=${result.exitCode}" }
            return "（读取失败：$error）"
        }
        return result.stdout
    }

    suspend fun readEnvFile(): String? {
        val command = "if [ -f '${DanmuPaths.ENV_FILE}' ]; then cat '${DanmuPaths.ENV_FILE}'; else echo -n ''; fi"
        val result = runSu(command, 10_000L)
        return if (result.exitCode == 0) result.stdout else null
    }

    suspend fun writeEnvFile(content: String): Boolean {
        val directory = DanmuPaths.ENV_FILE.substringBeforeLast('/')
        val timestamp = System.currentTimeMillis()
        val tempFile = "${DanmuPaths.ENV_FILE}.tmp.$timestamp"
        val backupFile = "${DanmuPaths.ENV_FILE}.bak.$timestamp"
        val marker = "__DANMU_ENV_EOF__$timestamp"
        val safeContent = if (content.endsWith("\n")) content else "$content\n"

        val command = buildString {
            append("mkdir -p '")
            append(directory)
            append("' || exit 1\n")
            append("if [ -f '")
            append(DanmuPaths.ENV_FILE)
            append("' ]; then cp -f '")
            append(DanmuPaths.ENV_FILE)
            append("' '")
            append(backupFile)
            append("' 2>/dev/null || true; fi\n")
            append("cat <<'")
            append(marker)
            append("' > '")
            append(tempFile)
            append("'\n")
            append(safeContent)
            append(marker)
            append("\n")
            append("chmod 600 '")
            append(tempFile)
            append("' 2>/dev/null || true\n")
            append("mv -f '")
            append(tempFile)
            append("' '")
            append(DanmuPaths.ENV_FILE)
            append("'\n")
        }

        return runSu(command, 15_000L).exitCode == 0
    }
}

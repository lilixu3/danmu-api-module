package com.danmuapi.manager.root

import com.danmuapi.manager.data.model.CoreListResponse
import com.danmuapi.manager.data.model.LogsResponse
import com.danmuapi.manager.data.model.StatusResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class DanmuCli {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val statusAdapter = moshi.adapter(StatusResponse::class.java)
    private val coreListAdapter = moshi.adapter(CoreListResponse::class.java)
    private val logsAdapter = moshi.adapter(LogsResponse::class.java)

    suspend fun getStatus(): StatusResponse? {
        val cmd = "${DanmuPaths.CORE_CLI} status --json"
        val res = RootShell.runSu(cmd)
        if (res.exitCode != 0) return null
        return statusAdapter.fromJson(res.stdout.trim())
    }

    suspend fun listCores(): CoreListResponse? {
        val cmd = "${DanmuPaths.CORE_CLI} core list --json"
        val res = RootShell.runSu(cmd)
        if (res.exitCode != 0) return null
        return coreListAdapter.fromJson(res.stdout.trim())
    }

    suspend fun startService(): Boolean {
        val res = RootShell.runSu("${DanmuPaths.CONTROL_CLI} start")
        return res.exitCode == 0
    }

    suspend fun stopService(): Boolean {
        val res = RootShell.runSu("${DanmuPaths.CONTROL_CLI} stop")
        return res.exitCode == 0
    }

    suspend fun restartService(): Boolean {
        val res = RootShell.runSu("${DanmuPaths.CONTROL_CLI} restart")
        return res.exitCode == 0
    }

    suspend fun setAutostart(enabled: Boolean): Boolean {
        val sub = if (enabled) "on" else "off"
        val res = RootShell.runSu("${DanmuPaths.CORE_CLI} autostart $sub")
        return res.exitCode == 0
    }

    suspend fun installCore(repo: String, ref: String): Boolean {
        val safeRepo = repo.replace("\"", "")
        val safeRef = ref.replace("\"", "")
        val res = RootShell.runSu("${DanmuPaths.CORE_CLI} core install '$safeRepo' '$safeRef'")
        return res.exitCode == 0
    }

    suspend fun activateCore(id: String): Boolean {
        val safeId = id.replace("\"", "")
        val res = RootShell.runSu("${DanmuPaths.CORE_CLI} core activate '$safeId'")
        return res.exitCode == 0
    }

    suspend fun deleteCore(id: String): Boolean {
        val safeId = id.replace("\"", "")
        val res = RootShell.runSu("${DanmuPaths.CORE_CLI} core delete '$safeId'")
        return res.exitCode == 0
    }

    suspend fun listLogs(): LogsResponse? {
        // Primary: ask the module CLI for JSON.
        val res = RootShell.runSu("${DanmuPaths.CORE_CLI} logs list --json", timeoutMs = 10_000)
        if (res.exitCode == 0) {
            val txt = res.stdout.trim()
            if (txt.isNotBlank()) {
                try {
                    val parsed = logsAdapter.fromJson(txt)
                    if (parsed != null) return parsed
                } catch (_: Throwable) {
                    // fall through
                }
            }
        }

        // Fallback: manual scan of the log directory.
        // Some devices/ROMs may have issues with JSON parsing if su adds extra output,
        // or if the CLI script is temporarily unavailable.
        val logDir = DanmuPaths.LOG_DIR
        val cmd = buildString {
            append("for f in '")
            append(logDir)
            append("'/*; do ")
            append("[ -f \"$f\" ] || continue; ")
            append("name=\"$(basename \"$f\")\"; ")
            append("size=\"$(wc -c < \"$f\" 2>/dev/null || echo 0)\"; ")
            append("mtime=\"$(date -r \"$f\" '+%F %T' 2>/dev/null || echo '')\"; ")
            append("printf '%s\t%s\t%s\t%s\n' \"$name\" \"$f\" \"$size\" \"$mtime\"; ")
            append("done")
        }
        val r2 = RootShell.runSu(cmd, timeoutMs = 10_000)

        val files = r2.stdout
            .split('\n')
            .mapNotNull { line ->
                val t = line.trimEnd()
                if (t.isBlank()) return@mapNotNull null
                val parts = t.split('\t')
                if (parts.size < 2) return@mapNotNull null
                val name = parts.getOrNull(0).orEmpty()
                val path = parts.getOrNull(1).orEmpty()
                val size = parts.getOrNull(2)?.toLongOrNull() ?: 0L
                val mtime = parts.getOrNull(3)?.takeIf { it.isNotBlank() }
                com.danmuapi.manager.data.model.LogFileInfo(
                    name = name,
                    path = path,
                    sizeBytes = size,
                    modifiedAt = mtime,
                )
            }

        return LogsResponse(dir = logDir, files = files)
    }

    suspend fun clearLogs(): Boolean {
        val res = RootShell.runSu("${DanmuPaths.CORE_CLI} logs clear")
        return res.exitCode == 0
    }

    suspend fun tailLog(path: String, lines: Int = 200): String? {
        val safePath = path.replace("\"", "").trim().replace("\r", "")
        val n = lines.coerceIn(10, 2000)

        // Keep the command single-line for maximum su compatibility.
        // Prefer toybox (available on most Android builds). Fall back to tail/cat.
        val cmd = "toybox tail -n $n '$safePath' 2>/dev/null" +
            " || tail -n $n '$safePath' 2>/dev/null" +
            " || cat '$safePath' 2>/dev/null"

        val res = RootShell.runSu(cmd, timeoutMs = 15_000)
        if (res.exitCode != 0 && res.stdout.isBlank()) {
            // Surface error details in the UI (helps users diagnose missing files, etc.)
            val err = res.stderr.trim().ifBlank { "exitCode=${res.exitCode}" }
            return "（读取失败：$err）"
        }
        return res.stdout
    }

    /**
     * Reads the persistent config file (.env).
     *
     * Note: returns empty string if file does not exist.
     */
    suspend fun readEnvFile(): String? {
        val f = DanmuPaths.ENV_FILE
        val cmd = "if [ -f '$f' ]; then cat '$f'; else echo -n ''; fi"
        val res = RootShell.runSu(cmd, timeoutMs = 10_000)
        if (res.exitCode != 0) return null
        return res.stdout
    }

    /**
     * Atomically writes the persistent config file (.env) with a timestamped backup.
     */
    suspend fun writeEnvFile(content: String): Boolean {
        val file = DanmuPaths.ENV_FILE
        val dir = file.substringBeforeLast('/')
        val ts = System.currentTimeMillis()
        val tmp = "$file.tmp.$ts"
        val bak = "$file.bak.$ts"
        val marker = "__DANMU_ENV_EOF__$ts"

        // Ensure the here-doc terminator is always on its own line.
        val safeContent = if (content.endsWith("\n")) content else "$content\n"

        val cmd = buildString {
            append("mkdir -p '")
            append(dir)
            append("' || exit 1\n")

            // Backup: best-effort (do not fail the write if backup fails).
            append("if [ -f '")
            append(file)
            append("' ]; then cp -f '")
            append(file)
            append("' '")
            append(bak)
            append("' 2>/dev/null || true; fi\n")

            // Write to tmp then move.
            append("cat <<'")
            append(marker)
            append("' > '")
            append(tmp)
            append("'\n")
            append(safeContent)
            append(marker)
            append("\n")
            append("chmod 600 '")
            append(tmp)
            append("' 2>/dev/null || true\n")
            append("mv -f '")
            append(tmp)
            append("' '")
            append(file)
            append("'\n")
        }

        val res = RootShell.runSu(cmd, timeoutMs = 15_000)
        return res.exitCode == 0
    }
}

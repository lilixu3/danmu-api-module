package com.danmuapi.manager.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.danmuapi.manager.root.DanmuPaths
import com.danmuapi.manager.root.RootShell

class LogCleanupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        // NOTE:
        // The early version of the app used `logs clear`, which truncates ALL module logs to 0.
        // That behaviour is too destructive (users think "logs disappear by themselves").
        //
        // Here we implement a safer "log trim" strategy: keep the last ~1MiB of each *.log file.
        // This prevents log growth without wiping everything.
        val trimCmd = """
            LOG_DIR='${DanmuPaths.LOG_DIR}'
            MAX_BYTES=1048576
            for f in "${'$'}LOG_DIR"/*.log; do
              [ -f "${'$'}f" ] || continue
              tmp="${'$'}f.trim.tmp"
              # Keep last MAX_BYTES bytes. If `tail -c` is unsupported, fallback to last 5000 lines.
              if tail -c "${'$'}MAX_BYTES" "${'$'}f" > "${'$'}tmp" 2>/dev/null; then
                cat "${'$'}tmp" > "${'$'}f" || true
                rm -f "${'$'}tmp"
              else
                tail -n 5000 "${'$'}f" > "${'$'}tmp" 2>/dev/null || true
                [ -f "${'$'}tmp" ] && cat "${'$'}tmp" > "${'$'}f" || true
                rm -f "${'$'}tmp"
              fi
            done
        """.trimIndent()

        val res = RootShell.runSu(trimCmd, timeoutMs = 60_000)
        return if (res.exitCode == 0) Result.success() else Result.retry()
    }
}

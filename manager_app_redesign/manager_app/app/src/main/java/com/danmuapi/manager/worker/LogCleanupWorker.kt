package com.danmuapi.manager.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.danmuapi.manager.core.root.DanmuPaths
import com.danmuapi.manager.core.root.RootShell

class LogCleanupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val trimCommand = """
            LOG_DIR='${DanmuPaths.LOG_DIR}'
            MAX_BYTES=1048576
            for f in "${'$'}LOG_DIR"/*.log; do
              [ -f "${'$'}f" ] || continue
              tmp="${'$'}f.trim.tmp"
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

        val result = RootShell.runSu(trimCommand, timeoutMs = 60_000L)
        return if (result.exitCode == 0) Result.success() else Result.retry()
    }
}

package com.danmuapi.manager.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.danmuapi.manager.root.RootShell

class LogCleanupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    companion object {
        /**
         * How many days of historical (rotated) logs to keep.
         *
         * NOTE: We intentionally do NOT truncate active *.log files automatically.
         * Users can always clear logs manually from the Logs screen.
         */
        const val KEY_RETENTION_DAYS = "retention_days"
    }

    override suspend fun doWork(): Result {
        val days = inputData.getInt(KEY_RETENTION_DAYS, 0)
        if (days <= 0) return Result.success()

        // Only delete rotated logs older than N days (e.g. server.log.1, server.log.2).
        // This fixes the "logs suddenly become 0" complaint caused by full truncation.
        val cmd = """
            PERSIST="/data/adb/danmu_api_server"
            LOGDIR="${'$'}PERSIST/logs"
            DAYS="$days"
            BB="${'$'}PERSIST/bin/busybox"

            if [ -d "${'$'}LOGDIR" ]; then
              if [ -x "${'$'}BB" ]; then
                "${'$'}BB" find "${'$'}LOGDIR" -type f -name "*.log.*" -mtime +"${'$'}DAYS" -exec "${'$'}BB" rm -f {} \; 2>/dev/null || true
              else
                find "${'$'}LOGDIR" -type f -name "*.log.*" -mtime +"${'$'}DAYS" -exec rm -f {} \; 2>/dev/null || true
              fi
            fi

            echo ok
        """.trimIndent()

        val res = RootShell.runSu(cmd, timeoutMs = 60_000)
        return if (res.exitCode == 0) Result.success() else Result.retry()
    }
}

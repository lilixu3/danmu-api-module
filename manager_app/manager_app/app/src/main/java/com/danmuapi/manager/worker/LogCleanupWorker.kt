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
        val res = RootShell.runSu("${DanmuPaths.CORE_CLI} logs clear", timeoutMs = 60_000)
        return if (res.exitCode == 0) Result.success() else Result.retry()
    }
}

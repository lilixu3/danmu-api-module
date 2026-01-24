package com.danmuapi.manager.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

object LogCleanupScheduler {
    private const val WORK_NAME = "danmu_log_cleanup"

    fun schedule(context: Context, intervalDays: Int) {
        val wm = WorkManager.getInstance(context)

        if (intervalDays <= 0) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }

        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()

        val req = PeriodicWorkRequestBuilder<LogCleanupWorker>(
            intervalDays.toLong(),
            TimeUnit.DAYS,
        )
            .setInputData(
                workDataOf(LogCleanupWorker.KEY_RETENTION_DAYS to intervalDays)
            )
            .setConstraints(constraints)
            .build()

        wm.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            req,
        )
    }
}

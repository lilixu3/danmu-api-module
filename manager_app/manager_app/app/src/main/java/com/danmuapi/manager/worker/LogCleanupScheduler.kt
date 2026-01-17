package com.danmuapi.manager.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
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
            .setConstraints(constraints)
            .build()

        wm.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            req,
        )
    }
}

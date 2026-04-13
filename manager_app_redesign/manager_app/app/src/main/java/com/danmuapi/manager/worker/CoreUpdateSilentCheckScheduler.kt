package com.danmuapi.manager.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.danmuapi.manager.core.data.normalizeSilentCoreUpdateBackgroundIntervalMinutes
import java.util.concurrent.TimeUnit

object CoreUpdateSilentCheckScheduler {
    private const val WORK_NAME = "danmu_core_update_silent_check"

    fun schedule(context: Context, enabled: Boolean, intervalMinutes: Int) {
        val workManager = WorkManager.getInstance(context)
        if (!enabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }

        val normalizedIntervalMinutes = normalizeSilentCoreUpdateBackgroundIntervalMinutes(intervalMinutes)
        val request = PeriodicWorkRequestBuilder<CoreUpdateSilentCheckWorker>(
            normalizedIntervalMinutes.toLong(),
            TimeUnit.MINUTES,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}

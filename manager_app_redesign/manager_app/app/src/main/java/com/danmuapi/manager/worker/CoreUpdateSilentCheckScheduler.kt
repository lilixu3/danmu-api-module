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
                    // 仅非计费网络（Wi-Fi/以太网）执行：后台静默检查每次会对每个核心
                    // 发起 2~4 个 GitHub API 请求，移动网络下会白白消耗流量与电量。
                    .setRequiredNetworkType(NetworkType.UNMETERED)
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

package com.danmuapi.manager.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.danmuapi.manager.core.data.CoreUpdateSilentCoordinator
import com.danmuapi.manager.core.data.DanmuRepository
import com.danmuapi.manager.core.data.SettingsRepository
import com.danmuapi.manager.core.data.SilentCoreUpdateTrigger

class CoreUpdateSilentCheckWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return runCatching {
            val coordinator = CoreUpdateSilentCoordinator(
                repository = DanmuRepository(),
                settings = SettingsRepository(applicationContext),
            )
            coordinator.run(SilentCoreUpdateTrigger.Background)
            Result.success()
        }.getOrDefault(Result.success())
    }
}

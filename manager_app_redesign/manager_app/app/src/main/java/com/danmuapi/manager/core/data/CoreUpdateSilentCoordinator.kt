package com.danmuapi.manager.core.data

import com.danmuapi.manager.core.model.CoreUpdateInfo
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class CoreUpdateSyncResult(
    val executed: Boolean,
    val updateInfo: Map<String, CoreUpdateInfo>,
)

private object SilentCoreUpdateRunGate {
    val mutex = Mutex()
}

class CoreUpdateSilentCoordinator(
    private val repository: DanmuRepository,
    private val settings: SettingsRepository,
    private val nowProvider: () -> Long = System::currentTimeMillis,
) {
    suspend fun loadCachedUpdateInfo(): Map<String, CoreUpdateInfo> {
        return settings.getCachedCoreUpdateInfo()
    }

    suspend fun run(
        trigger: SilentCoreUpdateTrigger,
        force: Boolean = false,
    ): CoreUpdateSyncResult {
        return SilentCoreUpdateRunGate.mutex.withLock {
            val storedSettings = settings.getSilentCoreUpdateSettings()
            val nowMillis = nowProvider()
            if (!force && !shouldRunSilentCoreUpdate(trigger, storedSettings, nowMillis)) {
                return@withLock CoreUpdateSyncResult(
                    executed = false,
                    updateInfo = settings.getCachedCoreUpdateInfo(),
                )
            }

            val catalog = runCatching { repository.listCores() }.getOrNull()
                ?: return@withLock CoreUpdateSyncResult(
                    executed = false,
                    updateInfo = settings.getCachedCoreUpdateInfo(),
                )
            val installedCores = catalog.cores
            if (installedCores.isEmpty()) {
                settings.setCachedCoreUpdateInfo(emptyMap())
                return@withLock CoreUpdateSyncResult(
                    executed = false,
                    updateInfo = emptyMap(),
                )
            }

            val githubToken = settings.getGithubTokenValue()
            val results = linkedMapOf<String, CoreUpdateInfo>()
            installedCores.forEach { core ->
                results[core.id] = runCatching {
                    repository.checkUpdate(core, githubToken)
                }.getOrDefault(
                    CoreUpdateInfo(
                        currentVersion = core.version,
                        currentCommit = core.commitLabel,
                    ),
                )
            }

            settings.setCachedCoreUpdateInfo(results)
            settings.setLastSilentCoreUpdateCheckAt(nowMillis)
            CoreUpdateSyncResult(
                executed = true,
                updateInfo = results,
            )
        }
    }
}

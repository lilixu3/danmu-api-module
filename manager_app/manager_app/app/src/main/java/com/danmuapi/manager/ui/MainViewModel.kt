package com.danmuapi.manager.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.danmuapi.manager.data.DanmuRepository
import com.danmuapi.manager.data.SettingsRepository
import com.danmuapi.manager.data.model.CoreListResponse
import com.danmuapi.manager.data.model.LogsResponse
import com.danmuapi.manager.data.model.StatusResponse
import com.danmuapi.manager.root.RootShell
import com.danmuapi.manager.worker.LogCleanupScheduler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    private val appContext: Context,
    private val repo: DanmuRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    // UI state
    var rootAvailable: Boolean? by mutableStateOf(null)
        private set
    var status: StatusResponse? by mutableStateOf(null)
        private set
    var cores: CoreListResponse? by mutableStateOf(null)
        private set
    var logs: LogsResponse? by mutableStateOf(null)
        private set

    var busy: Boolean by mutableStateOf(false)
        private set

    /**
     * A short message describing the current long-running operation.
     * Shown under the global progress bar.
     */
    var busyMessage: String? by mutableStateOf(null)
        private set

    // Per-core update status (id -> info)
    var updateInfo: Map<String, com.danmuapi.manager.data.CoreUpdateInfo> by mutableStateOf(emptyMap())
        private set

    val logCleanIntervalDays: StateFlow<Int> = settings.logCleanIntervalDays
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val githubToken: StateFlow<String> = settings.githubToken
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val snackbars = MutableSharedFlow<String>(extraBufferCapacity = 4)

    init {
        // Keep WorkManager schedule in sync with user settings.
        viewModelScope.launch {
            logCleanIntervalDays.collectLatest { days ->
                LogCleanupScheduler.schedule(appContext, days)
            }
        }
        refreshAll()
    }

    private suspend fun refreshAllInternal() {
        rootAvailable = try {
            RootShell.isRootAvailable()
        } catch (_: Throwable) {
            false
        }
        status = repo.getStatus()
        cores = repo.listCores()
        logs = repo.listLogs()
    }

    private suspend fun <T> withBusy(message: String? = null, block: suspend () -> T): T {
        busy = true
        busyMessage = message
        return try {
            block()
        } finally {
            busyMessage = null
            busy = false
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            withBusy("刷新状态中…") {
                refreshAllInternal()
            }
        }
    }

    fun startService() {
        viewModelScope.launch {
            val ok = withBusy("正在启动服务…") {
                val ok = repo.startService()
                refreshAllInternal()
                ok
            }
            snackbars.tryEmit(if (ok) "服务已启动" else "启动失败（请检查 Root / 模块状态）")
        }
    }

    fun stopService() {
        viewModelScope.launch {
            val ok = withBusy("正在停止服务…") {
                val ok = repo.stopService()
                refreshAllInternal()
                ok
            }
            snackbars.tryEmit(if (ok) "服务已停止" else "停止失败")
        }
    }

    fun restartService() {
        viewModelScope.launch {
            val ok = withBusy("正在重启服务…") {
                val ok = repo.restartService()
                refreshAllInternal()
                ok
            }
            snackbars.tryEmit(if (ok) "服务已重启" else "重启失败")
        }
    }

    fun setAutostart(enabled: Boolean) {
        viewModelScope.launch {
            val ok = withBusy("正在更新自启动…") {
                val ok = repo.setAutostart(enabled)
                refreshAllInternal()
                ok
            }
            snackbars.tryEmit(if (ok) "已更新自启动" else "更新自启动失败")
        }
    }

    fun installCore(repoUrlOrOwnerRepo: String, ref: String) {
        viewModelScope.launch {
            val repoText = repoUrlOrOwnerRepo.trim().ifBlank { "(unknown)" }
            val refText = ref.trim().ifBlank { "main" }

            // Immediate feedback (otherwise users think nothing happened).
            snackbars.tryEmit("开始下载核心：$repoText@$refText")

            val ok = withBusy("正在下载/安装核心：$repoText@$refText（可能需要几分钟）") {
                val ok = repo.installCore(repoText, refText)
                refreshAllInternal()
                ok
            }

            snackbars.tryEmit(if (ok) "核心已下载并切换" else "下载/安装核心失败")
        }
    }

    fun activateCore(id: String) {
        viewModelScope.launch {
            val ok = withBusy("正在切换核心…") {
                val ok = repo.activateCore(id)
                refreshAllInternal()
                ok
            }
            snackbars.tryEmit(if (ok) "已切换核心" else "切换核心失败")
        }
    }

    fun deleteCore(id: String) {
        viewModelScope.launch {
            val ok = withBusy("正在删除核心…") {
                val ok = repo.deleteCore(id)
                refreshAllInternal()
                ok
            }
            snackbars.tryEmit(if (ok) "已删除核心" else "删除核心失败")
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            val ok = withBusy("正在清空日志…") {
                val ok = repo.clearLogs()
                refreshAllInternal()
                ok
            }
            snackbars.tryEmit(if (ok) "日志已清空" else "清空日志失败")
        }
    }

    fun setLogCleanIntervalDays(days: Int) {
        viewModelScope.launch {
            settings.setLogCleanIntervalDays(days)
            LogCleanupScheduler.schedule(appContext, days)
            snackbars.tryEmit(if (days <= 0) "已关闭自动清理" else "自动清理：每 ${days} 天")
        }
    }

    fun setGithubToken(token: String) {
        viewModelScope.launch {
            settings.setGithubToken(token)
            snackbars.tryEmit("Token 已保存")
        }
    }

    fun checkUpdates() {
        viewModelScope.launch {
            val list = cores?.cores.orEmpty()
            if (list.isEmpty()) {
                snackbars.tryEmit("没有可检查的核心")
                return@launch
            }

            val token = githubToken.value

            val map = withBusy("正在检查更新…") {
                val m = mutableMapOf<String, com.danmuapi.manager.data.CoreUpdateInfo>()
                for (c in list) {
                    m[c.id] = repo.checkUpdate(c, token)
                }
                m.toMap()
            }

            updateInfo = map

            val n = map.values.count { it.updateAvailable }
            snackbars.tryEmit(if (n > 0) "发现 ${n} 个核心可更新" else "已是最新")
        }
    }
}

class MainViewModelFactory(
    private val appContext: Context,
    private val repo: DanmuRepository,
    private val settings: SettingsRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(appContext, repo, settings) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

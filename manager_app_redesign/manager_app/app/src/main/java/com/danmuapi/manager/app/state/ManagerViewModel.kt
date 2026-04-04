package com.danmuapi.manager.app.state

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.danmuapi.manager.core.data.DanmuRepository
import com.danmuapi.manager.core.data.SettingsRepository
import com.danmuapi.manager.core.data.network.DanmuApiClient
import com.danmuapi.manager.core.data.network.HttpResult
import com.danmuapi.manager.core.data.network.WebDavClient
import com.danmuapi.manager.core.data.network.WebDavResult
import com.danmuapi.manager.core.model.ApiDebugFieldLocation
import com.danmuapi.manager.core.model.ApiDebugPreset
import com.danmuapi.manager.core.model.ApiDebugPresetCatalog
import com.danmuapi.manager.core.model.ApiTestAnimeItem
import com.danmuapi.manager.core.model.ApiTestEpisodeItem
import com.danmuapi.manager.core.model.CoreCatalog
import com.danmuapi.manager.core.model.CoreUpdateInfo
import com.danmuapi.manager.core.model.CoreUpdateState
import com.danmuapi.manager.core.model.DanmuPreviewItem
import com.danmuapi.manager.core.model.DanmuStatsSummary
import com.danmuapi.manager.core.model.DanmuTestMatchSummary
import com.danmuapi.manager.core.model.DanmuTestResult
import com.danmuapi.manager.core.model.EnvVarItem
import com.danmuapi.manager.core.model.EnvVarMeta
import com.danmuapi.manager.core.model.LogDirectory
import com.danmuapi.manager.core.model.ManagerStatus
import com.danmuapi.manager.core.model.ManualDanmuStep
import com.danmuapi.manager.core.model.ModuleUpdateInfo
import com.danmuapi.manager.core.model.RequestRecord
import com.danmuapi.manager.core.model.RequestRecordsSnapshot
import com.danmuapi.manager.core.model.ServerConfig
import com.danmuapi.manager.core.model.ServerLogEntry
import com.danmuapi.manager.core.root.RootShell
import com.danmuapi.manager.worker.LogCleanupScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class ManagerViewModel(
    application: Application,
    private val repository: DanmuRepository = DanmuRepository(),
    private val settings: SettingsRepository = SettingsRepository(application),
    private val webDavClient: WebDavClient = WebDavClient(),
    private val danmuApiClient: DanmuApiClient = DanmuApiClient(),
) : AndroidViewModel(application) {

    var rootAvailable: Boolean? by mutableStateOf(null)
        private set
    var status: ManagerStatus? by mutableStateOf(null)
        private set
    var cores: CoreCatalog? by mutableStateOf(null)
        private set
    var logs: LogDirectory? by mutableStateOf(null)
        private set

    var apiToken: String by mutableStateOf("87654321")
        private set
    var apiPort: Int by mutableStateOf(9321)
        private set
    var apiHost: String by mutableStateOf("0.0.0.0")
        private set
    var adminToken: String by mutableStateOf("")
        private set
    @set:JvmName("setSessionAdminTokenState")
    var sessionAdminToken: String by mutableStateOf("")
        private set

    var serverConfig: ServerConfig? by mutableStateOf(null)
        private set
    var serverConfigError: String? by mutableStateOf(null)
        private set
    var serverConfigLoading: Boolean by mutableStateOf(false)
        private set

    var serverLogs: List<ServerLogEntry> by mutableStateOf(emptyList())
        private set
    var serverLogsError: String? by mutableStateOf(null)
        private set
    var serverLogsLoading: Boolean by mutableStateOf(false)
        private set

    var requestRecords: List<RequestRecord> by mutableStateOf(emptyList())
        private set
    var requestRecordsError: String? by mutableStateOf(null)
        private set
    var requestRecordsLoading: Boolean by mutableStateOf(false)
        private set
    var todayReqNum: Int by mutableStateOf(0)
        private set

    var moduleUpdateInfo: ModuleUpdateInfo? by mutableStateOf(null)
        private set
    var updateInfo: Map<String, CoreUpdateInfo> by mutableStateOf(emptyMap())
        private set

    var busy: Boolean by mutableStateOf(false)
        private set
    var busyMessage: String? by mutableStateOf(null)
        private set

    var moduleLogPath: String? by mutableStateOf(null)
        private set
    var moduleLogText: String by mutableStateOf("")
        private set
    var moduleLogLoading: Boolean by mutableStateOf(false)
        private set
    var moduleLogError: String? by mutableStateOf(null)
        private set

    var apiDebugResult: HttpResult? by mutableStateOf(null)
        private set
    var apiDebugLoading: Boolean by mutableStateOf(false)
        private set
    var apiDebugError: String? by mutableStateOf(null)
        private set
    var apiDebugRequestSummary: String by mutableStateOf("")
        private set

    var autoDanmuTestResult: DanmuTestResult? by mutableStateOf(null)
        private set
    var autoDanmuTestLoading: Boolean by mutableStateOf(false)
        private set
    var autoDanmuTestError: String? by mutableStateOf(null)
        private set

    var manualDanmuStep: ManualDanmuStep by mutableStateOf(ManualDanmuStep.Search)
        private set
    var manualDanmuSearchResults: List<ApiTestAnimeItem> by mutableStateOf(emptyList())
        private set
    var manualDanmuSelectedAnimeTitle: String by mutableStateOf("")
        private set
    var manualDanmuEpisodes: List<ApiTestEpisodeItem> by mutableStateOf(emptyList())
        private set
    var manualDanmuResult: DanmuTestResult? by mutableStateOf(null)
        private set
    var manualDanmuLoading: Boolean by mutableStateOf(false)
        private set
    var manualDanmuError: String? by mutableStateOf(null)
        private set

    val logCleanIntervalDays: StateFlow<Int> = settings.logCleanIntervalDays
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val githubToken: StateFlow<String> = settings.githubToken
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val webDavUrl: StateFlow<String> = settings.webDavUrl
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val webDavUsername: StateFlow<String> = settings.webDavUsername
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val webDavPassword: StateFlow<String> = settings.webDavPassword
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val webDavPath: StateFlow<String> = settings.webDavPath
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val themeMode: StateFlow<Int> = settings.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val dynamicColor: StateFlow<Boolean> = settings.dynamicColor
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val consoleLogLimit: StateFlow<Int> = settings.consoleLogLimit
        .stateIn(viewModelScope, SharingStarted.Eagerly, 300)

    val snackbars = MutableSharedFlow<String>(extraBufferCapacity = 8)
    private var hasShownRootUnavailableNotice = false

    init {
        viewModelScope.launch {
            logCleanIntervalDays.collectLatest { days ->
                LogCleanupScheduler.schedule(getApplication(), days)
            }
        }
        refreshAll()
    }

    private suspend fun <T> withBusy(message: String? = null, block: suspend () -> T): T {
        busy = true
        busyMessage = message
        return try {
            block()
        } finally {
            busy = false
            busyMessage = null
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            withBusy("刷新状态中…") {
                refreshAllInternal()
            }
        }
    }

    private suspend fun refreshAllInternal() {
        val available = try {
            RootShell.isRootAvailable()
        } catch (_: Throwable) {
            false
        }
        rootAvailable = available
        if (!available) {
            clearRootBackedState()
            emitRootUnavailableMessage()
            return
        }

        hasShownRootUnavailableNotice = false
        status = repository.getStatus()
        cores = repository.listCores()
        logs = repository.listLogs()
        refreshAccessInfoInternal()
        if (status?.isRunning == true) {
            refreshRequestRecordsInternal()
        } else {
            requestRecords = emptyList()
            todayReqNum = 0
        }
    }

    fun refreshLogs() {
        viewModelScope.launch {
            if (!ensureRootAccess(forceSnackbar = true)) return@launch
            withBusy("刷新日志中…") {
                logs = repository.listLogs()
            }
        }
    }

    fun refreshRequestRecords() {
        viewModelScope.launch {
            withBusy("刷新请求记录中…") {
                refreshRequestRecordsInternal()
            }
        }
    }

    fun startService() {
        viewModelScope.launch {
            if (!ensureRootAccess(forceSnackbar = true)) return@launch
            val ok = withBusy("正在启动服务…") {
                val started = repository.startService()
                refreshAllInternal()
                started
            }
            snackbars.tryEmit(if (ok) "服务已启动" else "启动失败")
        }
    }

    fun stopService() {
        viewModelScope.launch {
            if (!ensureRootAccess(forceSnackbar = true)) return@launch
            val ok = withBusy("正在停止服务…") {
                val stopped = repository.stopService()
                refreshAllInternal()
                stopped
            }
            snackbars.tryEmit(if (ok) "服务已停止" else "停止失败")
        }
    }

    fun restartService() {
        viewModelScope.launch {
            if (!ensureRootAccess(forceSnackbar = true)) return@launch
            val ok = withBusy("正在重启服务…") {
                val restarted = repository.restartService()
                refreshAllInternal()
                restarted
            }
            snackbars.tryEmit(if (ok) "服务已重启" else "重启失败")
        }
    }

    fun setAutostart(enabled: Boolean) {
        viewModelScope.launch {
            if (!ensureRootAccess(forceSnackbar = true)) return@launch
            val ok = withBusy("更新自启动中…") {
                val updated = repository.setAutostart(enabled)
                refreshAllInternal()
                updated
            }
            snackbars.tryEmit(if (ok) "已更新自启动" else "更新自启动失败")
        }
    }

    fun installCore(repo: String, ref: String) {
        viewModelScope.launch {
            if (!ensureRootAccess(forceSnackbar = true)) return@launch
            val repoText = repo.trim()
            val refText = ref.trim().ifBlank { "main" }
            if (repoText.isBlank()) {
                snackbars.tryEmit("请填写仓库地址")
                return@launch
            }
            snackbars.tryEmit("开始安装核心：$repoText@$refText")
            val ok = withBusy("下载并安装核心中…") {
                val installed = repository.installCore(repoText, refText)
                refreshAllInternal()
                installed
            }
            snackbars.tryEmit(if (ok) "核心已安装" else "核心安装失败")
        }
    }

    fun activateCore(id: String) {
        viewModelScope.launch {
            if (!ensureRootAccess(forceSnackbar = true)) return@launch
            val ok = withBusy("切换核心中…") {
                val activated = repository.activateCore(id)
                refreshAllInternal()
                activated
            }
            snackbars.tryEmit(if (ok) "已切换核心" else "切换核心失败")
        }
    }

    fun deleteCore(id: String) {
        viewModelScope.launch {
            if (!ensureRootAccess(forceSnackbar = true)) return@launch
            val ok = withBusy("删除核心中…") {
                val deleted = repository.deleteCore(id)
                refreshAllInternal()
                deleted
            }
            snackbars.tryEmit(if (ok) "已删除核心" else "删除核心失败")
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            if (!ensureRootAccess(forceSnackbar = true)) return@launch
            val ok = withBusy("清空模块日志中…") {
                val cleared = repository.clearLogs()
                refreshAllInternal()
                cleared
            }
            if (ok) {
                moduleLogText = ""
                moduleLogPath = null
            }
            snackbars.tryEmit(if (ok) "日志已清空" else "清空日志失败")
        }
    }

    fun loadModuleLog(path: String) {
        viewModelScope.launch {
            if (!ensureRootAccess(forceSnackbar = true)) {
                moduleLogError = "未获取 Root 权限"
                moduleLogText = ""
                moduleLogPath = null
                moduleLogLoading = false
                return@launch
            }
            moduleLogLoading = true
            moduleLogError = null
            moduleLogPath = path
            val text = repository.tailLog(path, consoleLogLimit.value)
            if (text == null) {
                moduleLogError = "读取日志失败"
                moduleLogText = ""
            } else {
                moduleLogText = text
            }
            moduleLogLoading = false
        }
    }

    fun checkUpdates() {
        viewModelScope.launch {
            val list = cores?.cores.orEmpty()
            if (list.isEmpty()) {
                snackbars.tryEmit("没有可检查的核心")
                return@launch
            }

            val results = withBusy("检查核心更新中…") {
                buildMap {
                    list.forEach { core ->
                        put(core.id, repository.checkUpdate(core, githubToken.value))
                    }
                }
            }
            updateInfo = results
            val updateCount = results.values.count { it.updateAvailable }
            val unknownCount = results.values.count { it.state == CoreUpdateState.Unknown }
            snackbars.tryEmit(
                when {
                    updateCount > 0 && unknownCount > 0 -> "发现 $updateCount 个核心可更新，另有 $unknownCount 个状态未确认"
                    updateCount > 0 -> "发现 $updateCount 个核心可更新"
                    unknownCount > 0 -> "有 $unknownCount 个核心暂时无法确认是否最新"
                    else -> "所有核心都已是最新"
                },
            )
        }
    }

    fun checkActiveCoreUpdate() {
        viewModelScope.launch {
            val core = status?.activeCore
            if (core == null) {
                snackbars.tryEmit("当前没有激活核心")
                return@launch
            }

            val info = withBusy("检查当前核心更新中…") {
                repository.checkUpdate(core, githubToken.value)
            }
            updateInfo = updateInfo.toMutableMap().apply {
                put(core.id, info)
            }
            snackbars.tryEmit(
                when {
                    info.updateAvailable -> "当前核心有更新"
                    info.state == CoreUpdateState.UpToDate -> "当前核心已是最新"
                    else -> "当前核心暂时无法确认是否最新"
                },
            )
        }
    }

    fun checkModuleUpdate() {
        viewModelScope.launch {
            moduleUpdateInfo = withBusy("检查模块更新中…") {
                repository.checkModuleUpdate(status?.module?.version)
            }
            val info = moduleUpdateInfo
            snackbars.tryEmit(
                when {
                    info == null -> "检查更新失败"
                    info.hasUpdate -> "发现模块更新：${info.latestRelease?.tagName.orEmpty()}"
                    else -> "模块已是最新版本"
                },
            )
        }
    }

    fun setThemeMode(mode: Int) {
        viewModelScope.launch {
            settings.setThemeMode(mode)
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            settings.setDynamicColor(enabled)
        }
    }

    fun setLogCleanIntervalDays(days: Int) {
        viewModelScope.launch {
            settings.setLogCleanIntervalDays(days)
            LogCleanupScheduler.schedule(getApplication(), days)
            snackbars.tryEmit(if (days <= 0) "已关闭自动清理" else "自动清理周期已更新")
        }
    }

    fun setConsoleLogLimit(limit: Int) {
        viewModelScope.launch {
            settings.setConsoleLogLimit(limit)
            snackbars.tryEmit("日志显示行数已更新")
        }
    }

    fun setGithubToken(token: String) {
        viewModelScope.launch {
            settings.setGithubToken(token)
            snackbars.tryEmit("GitHub Token 已保存")
        }
    }

    fun setWebDavSettings(url: String, username: String, password: String, path: String) {
        viewModelScope.launch {
            settings.setWebDavUrl(url)
            settings.setWebDavUsername(username)
            settings.setWebDavPassword(password)
            settings.setWebDavPath(path)
            snackbars.tryEmit("WebDAV 设置已保存")
        }
    }

    fun setSessionAdminToken(token: String) {
        sessionAdminToken = token.trim()
    }

    fun clearSessionAdminToken() {
        sessionAdminToken = ""
    }

    fun loadEnvFile(onResult: (String) -> Unit) {
        viewModelScope.launch {
            if (!ensureRootAccess(forceSnackbar = true)) {
                onResult("")
                return@launch
            }
            val text = withBusy("读取配置中…") {
                repository.readEnvFile()
            }
            if (text == null) {
                snackbars.tryEmit("读取配置失败")
                onResult("")
            } else {
                onResult(text)
            }
        }
    }

    fun saveEnvFile(content: String) {
        viewModelScope.launch {
            if (!ensureRootAccess(forceSnackbar = true)) return@launch
            val ok = withBusy("保存配置中…") {
                val saved = repository.writeEnvFile(content)
                if (saved) {
                    refreshAccessInfoInternal()
                }
                saved
            }
            snackbars.tryEmit(if (ok) "配置已保存" else "保存配置失败")
        }
    }

    fun exportEnvToUri(uri: Uri) {
        viewModelScope.launch {
            if (!ensureRootAccess(forceSnackbar = true)) return@launch
            val ok = withBusy("导出配置中…") {
                val text = repository.readEnvFile() ?: return@withBusy false
                withContext(Dispatchers.IO) {
                    try {
                        getApplication<Application>().contentResolver.openOutputStream(uri)?.use { output ->
                            output.write(text.toByteArray(Charsets.UTF_8))
                            output.flush()
                        } != null
                    } catch (_: Throwable) {
                        false
                    }
                }
            }
            snackbars.tryEmit(if (ok) "已导出配置" else "导出失败")
        }
    }

    fun exportTextToUri(
        uri: Uri,
        text: String,
        successMessage: String = "已导出文件",
        failureMessage: String = "导出失败",
    ) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(text.toByteArray(Charsets.UTF_8))
                        output.flush()
                    } != null
                } catch (_: Throwable) {
                    false
                }
            }
            snackbars.tryEmit(if (ok) successMessage else failureMessage)
        }
    }

    fun importEnvFromUri(uri: Uri) {
        viewModelScope.launch {
            if (!ensureRootAccess(forceSnackbar = true)) return@launch
            val ok = withBusy("导入配置中…") {
                val text = withContext(Dispatchers.IO) {
                    try {
                        getApplication<Application>().contentResolver.openInputStream(uri)
                            ?.bufferedReader(Charsets.UTF_8)
                            ?.use { it.readText() }
                    } catch (_: Throwable) {
                        null
                    }
                } ?: return@withBusy false

                val written = repository.writeEnvFile(text)
                if (written) {
                    refreshAccessInfoInternal()
                }
                written
            }
            snackbars.tryEmit(if (ok) "已导入配置" else "导入失败")
        }
    }

    fun exportEnvToWebDav() {
        viewModelScope.launch {
            val baseUrl = webDavUrl.value.trim()
            val remotePath = webDavPath.value.trim()
            val isFullUrl = remotePath.startsWith("http://") || remotePath.startsWith("https://")
            if (remotePath.isBlank() || (!isFullUrl && baseUrl.isBlank())) {
                snackbars.tryEmit("请先填写 WebDAV 地址与远程路径")
                return@launch
            }
            if (!ensureRootAccess(forceSnackbar = true)) return@launch

            val result = withBusy("上传到 WebDAV 中…") {
                val text = repository.readEnvFile() ?: return@withBusy WebDavResult.Error("读取配置失败")
                webDavClient.uploadText(
                    baseUrl = baseUrl,
                    remotePath = remotePath,
                    username = webDavUsername.value,
                    password = webDavPassword.value,
                    text = text,
                )
            }
            when (result) {
                is WebDavResult.Success -> snackbars.tryEmit("已上传到 WebDAV")
                is WebDavResult.Error -> snackbars.tryEmit("WebDAV 上传失败：${result.message}")
            }
        }
    }

    fun importEnvFromWebDav() {
        viewModelScope.launch {
            val baseUrl = webDavUrl.value.trim()
            val remotePath = webDavPath.value.trim()
            val isFullUrl = remotePath.startsWith("http://") || remotePath.startsWith("https://")
            if (remotePath.isBlank() || (!isFullUrl && baseUrl.isBlank())) {
                snackbars.tryEmit("请先填写 WebDAV 地址与远程路径")
                return@launch
            }
            if (!ensureRootAccess(forceSnackbar = true)) return@launch

            val result = withBusy("从 WebDAV 导入中…") {
                val download = webDavClient.downloadText(
                    baseUrl = baseUrl,
                    remotePath = remotePath,
                    username = webDavUsername.value,
                    password = webDavPassword.value,
                )

                when (val downloadResult = download.result) {
                    is WebDavResult.Error -> downloadResult
                    is WebDavResult.Success -> {
                        val text = download.text ?: return@withBusy WebDavResult.Error("下载内容为空")
                        val written = repository.writeEnvFile(text)
                        if (written) {
                            refreshAccessInfoInternal()
                            downloadResult
                        } else {
                            WebDavResult.Error("写入配置失败")
                        }
                    }
                }
            }
            when (result) {
                is WebDavResult.Success -> snackbars.tryEmit("已从 WebDAV 导入配置")
                is WebDavResult.Error -> snackbars.tryEmit("WebDAV 导入失败：${result.message}")
            }
        }
    }

    private fun effectiveAdminToken(): String = sessionAdminToken.trim()

    private fun hasSessionAdminToken(): Boolean = effectiveAdminToken().isNotBlank()

    private fun pickTokenSegment(useAdminToken: Boolean): String {
        if (!useAdminToken) return apiToken
        return effectiveAdminToken().ifBlank { apiToken }
    }

    suspend fun requestDanmuApi(
        method: String,
        path: String,
        query: Map<String, String?> = emptyMap(),
        bodyJson: String? = null,
        useAdminToken: Boolean = false,
    ): HttpResult {
        return danmuApiClient.request(
            method = method,
            host = apiHost,
            port = apiPort,
            tokenSegment = pickTokenSegment(useAdminToken),
            path = path,
            query = query,
            bodyJson = bodyJson,
        )
    }

    fun runPresetApiDebugRequest(
        preset: ApiDebugPreset,
        fieldValues: Map<String, String>,
    ) {
        viewModelScope.launch {
            apiDebugLoading = true
            apiDebugResult = null
            apiDebugError = null
            apiDebugRequestSummary = ""
            val prepared = prepareApiDebugRequest(preset, fieldValues)
            if (prepared.error != null) {
                apiDebugError = prepared.error
                apiDebugLoading = false
                return@launch
            }

            apiDebugRequestSummary = danmuApiClient.buildUrl(
                host = apiHost,
                port = apiPort,
                tokenSegment = pickTokenSegment(useAdminToken = false),
                path = prepared.path,
                query = prepared.query,
            ).toString()

            val response = requestDanmuApi(
                method = preset.method,
                path = prepared.path,
                query = prepared.query,
                bodyJson = prepared.bodyJson,
                useAdminToken = false,
            )

            apiDebugResult = response
            apiDebugError = if (response.error != null) {
                response.error
            } else {
                null
            }
            apiDebugLoading = false
        }
    }

    fun runAutoDanmuTest(fileName: String) {
        val input = fileName.trim()
        autoDanmuTestResult = null
        if (input.isBlank()) {
            autoDanmuTestError = "请先输入文件名"
            return
        }

        viewModelScope.launch {
            autoDanmuTestLoading = true
            autoDanmuTestError = null

            val matchResponse = requestDanmuApi(
                method = "POST",
                path = "/api/v2/match",
                bodyJson = JSONObject().put("fileName", input).toString(),
            )
            if (!matchResponse.isSuccessful) {
                autoDanmuTestError = matchResponse.error ?: "自动匹配失败（HTTP ${matchResponse.code}）"
                autoDanmuTestLoading = false
                return@launch
            }

            val matchSummary = parseAutoMatchSummary(matchResponse.body)
            if (matchSummary == null) {
                autoDanmuTestError = "未匹配到任何结果"
                autoDanmuTestLoading = false
                return@launch
            }

            val danmuResponse = requestDanmuApi(
                method = "GET",
                path = "/api/v2/comment/${matchSummary.episodeId}",
                query = linkedMapOf(
                    "format" to "json",
                    "duration" to "true",
                ),
            )
            if (!danmuResponse.isSuccessful) {
                autoDanmuTestError = danmuResponse.error ?: "获取弹幕失败（HTTP ${danmuResponse.code}）"
                autoDanmuTestLoading = false
                return@launch
            }

            val parsedResult = parseDanmuTestResult(
                body = danmuResponse.body,
                title = buildDanmuResultTitle(matchSummary.animeTitle, matchSummary.episodeTitle),
                matchSummary = matchSummary,
            )
            if (parsedResult == null) {
                autoDanmuTestError = "弹幕结果解析失败"
                autoDanmuTestLoading = false
                return@launch
            }

            autoDanmuTestResult = parsedResult
            autoDanmuTestLoading = false
        }
    }

    fun searchManualDanmuAnime(keyword: String) {
        val input = keyword.trim()
        manualDanmuResult = null
        if (input.isBlank()) {
            manualDanmuError = "请先输入搜索关键字"
            return
        }

        viewModelScope.launch {
            manualDanmuLoading = true
            manualDanmuError = null
            manualDanmuSearchResults = emptyList()
            manualDanmuEpisodes = emptyList()
            val response = requestDanmuApi(
                method = "GET",
                path = "/api/v2/search/anime",
                query = linkedMapOf("keyword" to input),
            )
            if (!response.isSuccessful) {
                manualDanmuError = response.error ?: "搜索失败（HTTP ${response.code}）"
                manualDanmuLoading = false
                return@launch
            }

            val results = parseManualAnimeSearchResults(response.body)
            if (results.isEmpty()) {
                manualDanmuError = "没有找到相关动漫"
                manualDanmuLoading = false
                return@launch
            }

            manualDanmuSearchResults = results
            manualDanmuSelectedAnimeTitle = ""
            manualDanmuEpisodes = emptyList()
            manualDanmuResult = null
            manualDanmuStep = ManualDanmuStep.Anime
            manualDanmuLoading = false
        }
    }

    fun loadManualDanmuEpisodes(animeId: String) {
        val targetId = animeId.trim()
        if (targetId.isBlank()) return

        viewModelScope.launch {
            manualDanmuLoading = true
            manualDanmuError = null
            manualDanmuResult = null
            manualDanmuEpisodes = emptyList()
            val response = requestDanmuApi(
                method = "GET",
                path = "/api/v2/bangumi/$targetId",
            )
            if (!response.isSuccessful) {
                manualDanmuError = response.error ?: "获取番剧详情失败（HTTP ${response.code}）"
                manualDanmuLoading = false
                return@launch
            }

            val parsed = parseBangumiEpisodes(response.body)
            if (parsed == null || parsed.episodes.isEmpty()) {
                manualDanmuError = "该番剧暂无可用剧集"
                manualDanmuLoading = false
                return@launch
            }

            manualDanmuSelectedAnimeTitle = parsed.title
            manualDanmuEpisodes = parsed.episodes
            manualDanmuResult = null
            manualDanmuStep = ManualDanmuStep.Episodes
            manualDanmuLoading = false
        }
    }

    fun loadManualDanmuResult(episodeId: String, title: String) {
        val targetId = episodeId.trim()
        if (targetId.isBlank()) return

        viewModelScope.launch {
            manualDanmuLoading = true
            manualDanmuError = null
            manualDanmuResult = null
            val response = requestDanmuApi(
                method = "GET",
                path = "/api/v2/comment/$targetId",
                query = linkedMapOf(
                    "format" to "json",
                    "duration" to "true",
                ),
            )
            if (!response.isSuccessful) {
                manualDanmuError = response.error ?: "获取弹幕失败（HTTP ${response.code}）"
                manualDanmuLoading = false
                return@launch
            }

            val parsed = parseDanmuTestResult(
                body = response.body,
                title = title,
                matchSummary = DanmuTestMatchSummary(
                    animeTitle = manualDanmuSelectedAnimeTitle,
                    episodeTitle = title.removePrefix(manualDanmuSelectedAnimeTitle).trim(),
                    episodeId = targetId,
                ),
            )
            if (parsed == null) {
                manualDanmuError = "弹幕结果解析失败"
                manualDanmuLoading = false
                return@launch
            }

            manualDanmuResult = parsed
            manualDanmuStep = ManualDanmuStep.Result
            manualDanmuLoading = false
        }
    }

    fun backManualDanmuToSearch() {
        manualDanmuError = null
        manualDanmuStep = ManualDanmuStep.Search
    }

    fun backManualDanmuToAnimeResults() {
        manualDanmuError = null
        manualDanmuStep = if (manualDanmuSearchResults.isEmpty()) {
            ManualDanmuStep.Search
        } else {
            ManualDanmuStep.Anime
        }
    }

    fun backManualDanmuToEpisodes() {
        manualDanmuError = null
        manualDanmuStep = if (manualDanmuEpisodes.isEmpty()) {
            ManualDanmuStep.Anime
        } else {
            ManualDanmuStep.Episodes
        }
    }

    private data class PreparedApiDebugRequest(
        val path: String,
        val query: Map<String, String?>,
        val bodyJson: String?,
        val error: String? = null,
    )

    private data class ParsedBangumiEpisodes(
        val title: String,
        val episodes: List<ApiTestEpisodeItem>,
    )

    private data class ParsedDanmuComment(
        val timeSeconds: Double,
        val mode: Int,
        val color: Int,
        val text: String,
    )

    private fun prepareApiDebugRequest(
        preset: ApiDebugPreset,
        fieldValues: Map<String, String>,
    ): PreparedApiDebugRequest {
        var resolvedPath = preset.path
        val query = linkedMapOf<String, String?>()
        val body = JSONObject()
        var rawBody: String? = null

        preset.fields.forEach { field ->
            val rawValue = fieldValues[field.key]?.trim().orEmpty()
            if (field.required && rawValue.isBlank()) {
                return PreparedApiDebugRequest("", emptyMap(), null, "${field.label} 不能为空")
            }
            if (rawValue.isBlank()) return@forEach

            when (field.location) {
                ApiDebugFieldLocation.Path -> {
                    resolvedPath = resolvedPath.replace(":${field.key}", rawValue)
                }

                ApiDebugFieldLocation.Query -> {
                    query[field.key] = rawValue
                }

                ApiDebugFieldLocation.Body -> {
                    body.put(field.key, rawValue)
                }

                ApiDebugFieldLocation.RawBody -> {
                    val normalized = normalizeRawJsonBody(rawValue)
                        ?: return PreparedApiDebugRequest("", emptyMap(), null, "${field.label} 不是合法 JSON")
                    rawBody = normalized
                }
            }
        }

        if (Regex(":\\w+").containsMatchIn(resolvedPath)) {
            return PreparedApiDebugRequest("", emptyMap(), null, "仍有路径参数未填写")
        }

        val bodyJson = when {
            rawBody != null -> rawBody
            body.length() > 0 -> body.toString()
            preset.method.uppercase() in setOf("POST", "PUT", "PATCH") -> "{}"
            else -> null
        }

        return PreparedApiDebugRequest(
            path = resolvedPath,
            query = query,
            bodyJson = bodyJson,
        )
    }

    private fun normalizeRawJsonBody(raw: String): String? {
        val input = raw.trim()
        if (input.isBlank()) return null
        return try {
            when {
                input.startsWith("{") -> JSONObject(input).toString()
                input.startsWith("[") -> JSONArray(input).toString()
                else -> null
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun parseAutoMatchSummary(body: String): DanmuTestMatchSummary? {
        return try {
            val json = JSONObject(body)
            val matches = json.optJSONArray("matches") ?: JSONArray()
            if ((!json.optBoolean("isMatched") && matches.length() == 0) || matches.length() == 0) {
                return null
            }
            val best = matches.optJSONObject(0) ?: return null
            val animeTitle = best.optString("animeTitle").ifBlank { "未命名番剧" }
            val episodeTitle = best.optString("episodeTitle").ifBlank { "未命名剧集" }
            val episodeId = best.opt("episodeId")?.toString().orEmpty()
            if (episodeId.isBlank()) return null
            DanmuTestMatchSummary(
                animeTitle = animeTitle,
                episodeTitle = episodeTitle,
                episodeId = episodeId,
                matchCount = matches.length(),
            )
        } catch (_: Throwable) {
            null
        }
    }

    private fun parseManualAnimeSearchResults(body: String): List<ApiTestAnimeItem> {
        return try {
            val json = JSONObject(body)
            if (!json.optBoolean("success")) return emptyList()
            val array = json.optJSONArray("animes") ?: JSONArray()
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val animeId = item.opt("animeId")?.toString().orEmpty()
                    if (animeId.isBlank()) continue
                    add(
                        ApiTestAnimeItem(
                            animeId = animeId,
                            animeTitle = item.optString("animeTitle").ifBlank { "未命名番剧" },
                            episodeCount = item.optInt("episodeCount").takeIf { it > 0 },
                        ),
                    )
                }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun parseBangumiEpisodes(body: String): ParsedBangumiEpisodes? {
        return try {
            val json = JSONObject(body)
            if (!json.optBoolean("success")) return null
            val bangumi = json.optJSONObject("bangumi") ?: return null
            val episodesArray = bangumi.optJSONArray("episodes") ?: JSONArray()
            val episodes = buildList {
                for (index in 0 until episodesArray.length()) {
                    val item = episodesArray.optJSONObject(index) ?: continue
                    val episodeId = item.opt("episodeId")?.toString().orEmpty()
                    if (episodeId.isBlank()) continue
                    add(
                        ApiTestEpisodeItem(
                            episodeId = episodeId,
                            episodeNumber = item.opt("episodeNumber")?.toString().orEmpty().ifBlank { (index + 1).toString() },
                            episodeTitle = item.optString("episodeTitle").ifBlank { "第${index + 1}集" },
                        ),
                    )
                }
            }
            ParsedBangumiEpisodes(
                title = bangumi.optString("animeTitle").ifBlank { "未命名番剧" },
                episodes = episodes,
            )
        } catch (_: Throwable) {
            null
        }
    }

    private fun parseDanmuTestResult(
        body: String,
        title: String,
        matchSummary: DanmuTestMatchSummary? = null,
    ): DanmuTestResult? {
        return try {
            val json = JSONObject(body)
            val array = json.optJSONArray("comments") ?: JSONArray()
            val comments = buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        ParsedDanmuComment(
                            timeSeconds = readDanmuTimeSeconds(item),
                            mode = readDanmuMode(item),
                            color = readDanmuColor(item),
                            text = item.optString("m").ifBlank { item.optString("text") },
                        ),
                    )
                }
            }.sortedBy { it.timeSeconds }

            if (comments.isEmpty()) return null

            val explicitDuration = json.optDouble("videoDuration").takeUnless { it.isNaN() || it <= 0.0 }
            val durationSeconds = explicitDuration ?: comments.maxOfOrNull { it.timeSeconds } ?: 0.0
            val durationMinutes = (durationSeconds / 60.0).takeIf { it > 0.0 } ?: 0.0
            val avgDensity = if (durationMinutes > 0.0) comments.size / durationMinutes else 0.0

            val bucketSizeSeconds = 30.0
            val bucketCount = max(1, ceil(durationSeconds / bucketSizeSeconds).toInt())
            val buckets = IntArray(bucketCount)
            comments.forEach { comment ->
                val index = min((comment.timeSeconds / bucketSizeSeconds).toInt(), bucketCount - 1)
                buckets[index] = buckets[index] + 1
            }
            var hotIndex = 0
            for (index in buckets.indices) {
                if (buckets[index] > buckets[hotIndex]) {
                    hotIndex = index
                }
            }
            val hotStart = hotIndex * bucketSizeSeconds
            val hotEnd = min(durationSeconds, hotStart + bucketSizeSeconds)

            var scrollCount = 0
            var topCount = 0
            var bottomCount = 0
            comments.forEach { comment ->
                when (comment.mode) {
                    5 -> topCount++
                    4 -> bottomCount++
                    else -> scrollCount++
                }
            }

            DanmuTestResult(
                title = title,
                matchSummary = matchSummary,
                stats = DanmuStatsSummary(
                    commentCount = comments.size,
                    durationLabel = formatSecondsLabel(durationSeconds),
                    averageDensityLabel = String.format("%.1f 条/分", avgDensity),
                    hotMomentLabel = "${formatSecondsLabel(hotStart)} - ${formatSecondsLabel(hotEnd)}",
                    modeBreakdownLabel = "$scrollCount / $topCount / $bottomCount",
                ),
                preview = comments.take(40).map { comment ->
                    DanmuPreviewItem(
                        timeLabel = formatSecondsLabel(comment.timeSeconds),
                        modeLabel = labelForDanmuMode(comment.mode),
                        colorHex = colorToHex(comment.color),
                        text = comment.text.ifBlank { "(empty)" },
                    )
                },
                rawBody = body,
            )
        } catch (_: Throwable) {
            null
        }
    }

    private fun readDanmuTimeSeconds(item: JSONObject): Double {
        val direct = item.optDouble("t")
        if (!direct.isNaN() && direct >= 0.0) return direct
        val payload = item.optString("p")
        return payload.split(',').firstOrNull()?.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    }

    private fun readDanmuMode(item: JSONObject): Int {
        val payload = item.optString("p")
        return payload.split(',').getOrNull(1)?.toIntOrNull() ?: 1
    }

    private fun readDanmuColor(item: JSONObject): Int {
        val payload = item.optString("p")
        return payload.split(',').getOrNull(2)?.toIntOrNull() ?: 0xFFFFFF
    }

    private fun buildDanmuResultTitle(animeTitle: String, episodeTitle: String): String {
        return buildString {
            append(animeTitle.ifBlank { "未命名番剧" })
            if (episodeTitle.isNotBlank()) {
                append(" · ")
                append(episodeTitle)
            }
        }
    }

    private fun formatSecondsLabel(seconds: Double): String {
        val total = seconds.toInt().coerceAtLeast(0)
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val secs = total % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, secs)
        } else {
            "%d:%02d".format(minutes, secs)
        }
    }

    private fun labelForDanmuMode(mode: Int): String {
        return when (mode) {
            5 -> "顶部"
            4 -> "底部"
            else -> "滚动"
        }
    }

    private fun colorToHex(color: Int): String {
        return "#%06X".format(color and 0xFFFFFF)
    }

    suspend fun validateAdminToken(candidate: String): Pair<Boolean, String?> {
        val token = candidate.trim()
        if (token.isBlank()) return false to "请填写 ADMIN_TOKEN"

        val result = danmuApiClient.request(
            method = "GET",
            host = apiHost,
            port = apiPort,
            tokenSegment = token,
            path = "/api/config",
        )

        if (!result.isSuccessful) {
            val message = when (result.code) {
                401, 403 -> "ADMIN_TOKEN 输入错误"
                -1 -> result.error ?: "验证失败"
                else -> result.error ?: "验证失败（HTTP ${result.code}）"
            }
            return false to message
        }
        return true to null
    }

    fun refreshServerConfig(useAdminToken: Boolean = false) {
        viewModelScope.launch {
            serverConfigLoading = true
            serverConfigError = null
            val result = requestDanmuApi("GET", "/api/config", useAdminToken = useAdminToken)
            if (result.isSuccessful) {
                serverConfig = parseServerConfig(result.body)
                if (serverConfig == null) {
                    serverConfigError = "解析配置失败"
                }
            } else {
                serverConfigError = result.error ?: "请求失败（HTTP ${result.code}）"
            }
            serverConfigLoading = false
        }
    }

    fun refreshServerLogs() {
        viewModelScope.launch {
            serverLogsLoading = true
            serverLogsError = null
            val result = requestDanmuApi("GET", "/api/logs")
            if (result.isSuccessful) {
                serverLogs = parseServerLogs(result.body)
            } else {
                serverLogsError = result.error ?: "请求失败（HTTP ${result.code}）"
            }
            serverLogsLoading = false
        }
    }

    fun clearServerLogs() {
        viewModelScope.launch {
            if (!hasSessionAdminToken()) {
                snackbars.tryEmit("请先输入管理员 Token")
                return@launch
            }
            val result = requestDanmuApi(
                method = "POST",
                path = "/api/logs/clear",
                bodyJson = "{}",
                useAdminToken = true,
            )
            if (result.isSuccessful) {
                snackbars.tryEmit("服务日志已清空")
                refreshServerLogs()
            } else {
                snackbars.tryEmit(result.error ?: "清空失败")
            }
        }
    }

    fun clearServerCache() {
        viewModelScope.launch {
            if (!hasSessionAdminToken()) {
                snackbars.tryEmit("请先输入管理员 Token")
                return@launch
            }
            val result = requestDanmuApi(
                method = "POST",
                path = "/api/cache/clear",
                bodyJson = "{}",
                useAdminToken = true,
            )
            snackbars.tryEmit(if (result.isSuccessful) "缓存已清理" else (result.error ?: "清理失败"))
        }
    }

    fun deployServer() {
        viewModelScope.launch {
            if (!hasSessionAdminToken()) {
                snackbars.tryEmit("请先输入管理员 Token")
                return@launch
            }
            val result = requestDanmuApi(
                method = "POST",
                path = "/api/deploy",
                bodyJson = "{}",
                useAdminToken = true,
            )
            snackbars.tryEmit(if (result.isSuccessful) "已触发重新部署" else (result.error ?: "操作失败"))
        }
    }

    fun setServerEnvVar(key: String, value: String) {
        viewModelScope.launch {
            val body = JSONObject()
                .put("key", key)
                .put("value", value)
                .toString()
            val result = requestDanmuApi(
                method = "POST",
                path = "/api/env/set",
                bodyJson = body,
                useAdminToken = hasSessionAdminToken(),
            )
            if (result.isSuccessful) {
                refreshAccessInfoInternal()
                refreshServerConfig(useAdminToken = hasSessionAdminToken())
                snackbars.tryEmit("已更新：$key")
            } else {
                snackbars.tryEmit(result.error ?: "更新失败")
            }
        }
    }

    fun deleteServerEnvVar(key: String) {
        viewModelScope.launch {
            val body = JSONObject()
                .put("key", key)
                .toString()
            val result = requestDanmuApi(
                method = "POST",
                path = "/api/env/del",
                bodyJson = body,
                useAdminToken = hasSessionAdminToken(),
            )
            if (result.isSuccessful) {
                refreshAccessInfoInternal()
                refreshServerConfig(useAdminToken = hasSessionAdminToken())
                snackbars.tryEmit("已删除：$key")
            } else {
                snackbars.tryEmit(result.error ?: "删除失败")
            }
        }
    }

    private suspend fun refreshRequestRecordsInternal() {
        requestRecordsLoading = true
        requestRecordsError = null
        val result = requestDanmuApi("GET", "/api/reqrecords")
        if (result.isSuccessful) {
            val parsed = parseRequestRecords(result.body)
            if (parsed != null) {
                requestRecords = parsed.records
                todayReqNum = parsed.todayReqNum
            } else {
                requestRecordsError = "解析请求记录失败"
            }
        } else {
            requestRecordsError = result.error ?: "请求失败（HTTP ${result.code}）"
        }
        requestRecordsLoading = false
    }

    private fun parseDotEnv(text: String): Map<String, String> {
        val values = mutableMapOf<String, String>()
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isBlank() || line.startsWith('#')) return@forEach
            val splitIndex = line.indexOf('=')
            if (splitIndex <= 0) return@forEach
            val key = line.substring(0, splitIndex).trim()
            var value = line.substring(splitIndex + 1).trim()
            if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith('\'') && value.endsWith('\''))) {
                value = value.substring(1, value.length - 1)
            }
            if (key.isNotBlank()) {
                values[key] = value
            }
        }
        return values
    }

    private fun clearRootBackedState() {
        status = null
        cores = null
        logs = null
        requestRecords = emptyList()
        requestRecordsError = null
        requestRecordsLoading = false
        todayReqNum = 0
    }

    private fun emitRootUnavailableMessage(force: Boolean = false) {
        if (force || !hasShownRootUnavailableNotice) {
            snackbars.tryEmit("未获取 Root 权限，当前为只读模式。请在 Root 管理器中授权后重试。")
            hasShownRootUnavailableNotice = true
        }
    }

    private suspend fun ensureRootAccess(forceSnackbar: Boolean = false): Boolean {
        val available = try {
            RootShell.isRootAvailable()
        } catch (_: Throwable) {
            false
        }
        rootAvailable = available
        if (available) {
            hasShownRootUnavailableNotice = false
            return true
        }

        clearRootBackedState()
        emitRootUnavailableMessage(force = forceSnackbar)
        return false
    }

    private suspend fun refreshAccessInfoInternal() {
        if (rootAvailable != true) return
        val env = repository.readEnvFile() ?: return
        val values = parseDotEnv(env)
        apiToken = values["TOKEN"].orEmpty().trim().ifBlank { "87654321" }
        apiPort = values["DANMU_API_PORT"].orEmpty().trim().toIntOrNull()?.takeIf { it in 1..65_535 } ?: 9321
        apiHost = values["DANMU_API_HOST"].orEmpty().trim().ifBlank { "0.0.0.0" }
        adminToken = values["ADMIN_TOKEN"].orEmpty().trim()
    }

    private fun parseServerConfig(body: String): ServerConfig? {
        return try {
            val json = JSONObject(body)

            val envMeta = buildMap<String, EnvVarMeta> {
                val config = json.optJSONObject("envVarConfig")
                if (config != null) {
                    val keys = config.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val value = config.optJSONObject(key) ?: continue
                        val minValue = if (value.has("min")) value.optDouble("min") else Double.NaN
                        val maxValue = if (value.has("max")) value.optDouble("max") else Double.NaN
                        put(
                            key,
                            EnvVarMeta(
                                category = value.optString("category").ifBlank { "system" },
                                type = value.optString("type").ifBlank { "text" },
                                description = value.optString("description"),
                                options = parseJsonStringList(value.optJSONArray("options")),
                                min = minValue.takeUnless { it.isNaN() },
                                max = maxValue.takeUnless { it.isNaN() },
                            ),
                        )
                    }
                }
            }

            val originalEnvVars = buildMap<String, String> {
                val originals = json.optJSONObject("originalEnvVars")
                if (originals != null) {
                    val keys = originals.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        put(key, normalizeJsonValue(originals.opt(key)))
                    }
                }
            }

            val categorizedEnvVars = buildMap<String, List<EnvVarItem>> {
                val categories = json.optJSONObject("categorizedEnvVars")
                if (categories != null) {
                    val keys = categories.keys()
                    while (keys.hasNext()) {
                        val category = keys.next()
                        val array = categories.optJSONArray(category) ?: JSONArray()
                        val items = buildList {
                            for (index in 0 until array.length()) {
                                val item = array.optJSONObject(index) ?: continue
                                add(
                                    EnvVarItem(
                                        key = item.optString("key"),
                                        value = normalizeJsonValue(item.opt("value")),
                                        type = item.optString("type").ifBlank { "text" },
                                        description = item.optString("description"),
                                        options = parseJsonStringList(item.optJSONArray("options")),
                                    ),
                                )
                            }
                        }
                        put(category, items)
                    }
                }
            }

            val displayEnvVars = mergeOriginalEnvValues(
                categorizedEnvVars = categorizedEnvVars,
                originalEnvVars = originalEnvVars,
                envMeta = envMeta,
            )

            ServerConfig(
                message = json.optString("message"),
                version = json.optString("version").ifBlank { null },
                categorizedEnvVars = displayEnvVars,
                envVarConfig = envMeta,
                originalEnvVars = originalEnvVars,
                hasAdminToken = json.optBoolean("hasAdminToken", false),
                repository = json.optString("repository").ifBlank { null },
                description = json.optString("description").ifBlank { null },
                notice = json.optString("notice").ifBlank { null },
            )
        } catch (_: Throwable) {
            null
        }
    }

    private fun parseJsonStringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                add(normalizeJsonValue(array.opt(index)))
            }
        }
    }

    private fun normalizeJsonValue(value: Any?): String {
        return when (value) {
            null,
            JSONObject.NULL -> ""

            is JSONArray -> buildList {
                for (index in 0 until value.length()) {
                    add(normalizeJsonValue(value.opt(index)))
                }
            }.joinToString(",")

            is JSONObject -> value.toString()
            else -> value.toString()
        }
    }

    private fun mergeOriginalEnvValues(
        categorizedEnvVars: Map<String, List<EnvVarItem>>,
        originalEnvVars: Map<String, String>,
        envMeta: Map<String, EnvVarMeta>,
    ): Map<String, List<EnvVarItem>> {
        if (originalEnvVars.isEmpty()) return categorizedEnvVars

        val merged = linkedMapOf<String, MutableList<EnvVarItem>>()
        val seenKeys = linkedSetOf<String>()

        categorizedEnvVars.forEach { (category, items) ->
            merged[category] = items.map { item ->
                seenKeys += item.key
                item.copy(value = originalEnvVars[item.key] ?: item.value)
            }.toMutableList()
        }

        originalEnvVars.forEach { (key, value) ->
            if (key in seenKeys) return@forEach
            val meta = envMeta[key]
            val category = meta?.category?.ifBlank { "system" } ?: "system"
            val bucket = merged.getOrPut(category) { mutableListOf() }
            bucket += EnvVarItem(
                key = key,
                value = value,
                type = meta?.type?.ifBlank { "text" } ?: "text",
                description = meta?.description.orEmpty(),
                options = meta?.options.orEmpty(),
            )
        }

        return merged.mapValues { (_, items) -> items.toList() }
    }

    private fun parseRequestRecords(body: String): RequestRecordsSnapshot? {
        return try {
            val json = JSONObject(body)
            val records = buildList {
                val array = json.optJSONArray("records") ?: JSONArray()
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val rawParams = item.opt("params")
                    val params = when (rawParams) {
                        null,
                        JSONObject.NULL -> null
                        is JSONObject -> rawParams.toString(2)
                        is JSONArray -> rawParams.toString(2)
                        else -> rawParams.toString()
                    }
                    add(
                        RequestRecord(
                            path = item.optString("interface").ifBlank { item.optString("path") },
                            method = item.optString("method").ifBlank { "GET" },
                            timestamp = item.optString("timestamp"),
                            clientIp = item.optString("clientIp"),
                            params = params,
                        ),
                    )
                }
            }
            RequestRecordsSnapshot(
                records = records,
                todayReqNum = json.optInt("todayReqNum", 0),
            )
        } catch (_: Throwable) {
            null
        }
    }

    private fun parseServerLogs(text: String): List<ServerLogEntry> {
        fun normalizeLevel(raw: String): String {
            val value = raw.trim().uppercase()
            return when {
                value == "WARNING" -> "WARN"
                value == "WRN" -> "WARN"
                value.startsWith("ERR") -> "ERROR"
                value == "FATAL" -> "ERROR"
                else -> value
            }
        }

        val bracketed = Regex("""^\[(.+?)]\s+(\w+):\s+(.*)$""")
        val iso = Regex("""^(\d{4}-\d{2}-\d{2}T[^\s]+)\s+(\w+)\s+(.*)$""")
        val plain = Regex("""^(\d{4}-\d{2}-\d{2}\s+[^\s]+)\s+(\w+)\s+(.*)$""")

        return text.lineSequence().mapNotNull { rawLine ->
            val line = rawLine.trimEnd()
            if (line.isBlank()) return@mapNotNull null

            bracketed.find(line)?.let { match ->
                return@mapNotNull ServerLogEntry(
                    timestamp = match.groupValues.getOrElse(1) { "" },
                    level = normalizeLevel(match.groupValues.getOrElse(2) { "" }),
                    message = match.groupValues.getOrElse(3) { "" },
                )
            }
            iso.find(line)?.let { match ->
                return@mapNotNull ServerLogEntry(
                    timestamp = match.groupValues.getOrElse(1) { "" },
                    level = normalizeLevel(match.groupValues.getOrElse(2) { "" }),
                    message = match.groupValues.getOrElse(3) { "" },
                )
            }
            plain.find(line)?.let { match ->
                return@mapNotNull ServerLogEntry(
                    timestamp = match.groupValues.getOrElse(1) { "" },
                    level = normalizeLevel(match.groupValues.getOrElse(2) { "" }),
                    message = match.groupValues.getOrElse(3) { "" },
                )
            }
            ServerLogEntry(timestamp = "", level = "", message = line)
        }.toList()
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(ManagerViewModel::class.java)) {
                        return ManagerViewModel(application) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    }
}

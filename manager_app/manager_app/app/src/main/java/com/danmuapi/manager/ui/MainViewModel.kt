package com.danmuapi.manager.ui

import android.content.Context
import android.net.Uri
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
import com.danmuapi.manager.data.model.ModuleUpdateInfo  // 添加
import com.danmuapi.manager.data.model.EnvVarItem
import com.danmuapi.manager.data.model.EnvVarMeta
import com.danmuapi.manager.data.model.ServerConfigResponse
import com.danmuapi.manager.data.model.ServerLogEntry
import com.danmuapi.manager.data.model.StatusResponse
import com.danmuapi.manager.network.DanmuApiClient
import com.danmuapi.manager.network.HttpResult
import com.danmuapi.manager.network.WebDavClient
import com.danmuapi.manager.network.WebDavResult
import com.danmuapi.manager.root.DanmuPaths
import com.danmuapi.manager.root.RootShell
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

class MainViewModel(
    private val appContext: Context,
    private val repo: DanmuRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val webDavClient = WebDavClient()

    private val danmuApiClient = DanmuApiClient()

    // UI state
    var rootAvailable: Boolean? by mutableStateOf(null)
        private set
    var status: StatusResponse? by mutableStateOf(null)
        private set
    var cores: CoreListResponse? by mutableStateOf(null)
        private set
    var logs: LogsResponse? by mutableStateOf(null)
        private set

    /**
     * Parsed from /data/adb/danmu_api_server/config/.env
     * (best-effort; falls back to sane defaults when unreadable).
     */
    var apiToken: String by mutableStateOf("87654321")
        private set
    var apiPort: Int by mutableStateOf(9321)
        private set
    var apiHost: String by mutableStateOf("0.0.0.0")
        private set

    /**
     * Optional ADMIN_TOKEN from .env.
     * Used by the server UI for system settings.
     */
    var adminToken: String by mutableStateOf("")
        private set

    /**
     * Runtime admin token entered in the app.
     *
     * - Not persisted to disk (avoids accidentally storing secrets).
     * - Used to access admin endpoints when the user doesn't want to write ADMIN_TOKEN into .env.
     */
    @set:JvmName("setSessionAdminTokenState")
    var sessionAdminToken: String by mutableStateOf("")
        private set

    // ===== danmu-api Console (Compose replica of Web UI) =====
    var serverConfig: ServerConfigResponse? by mutableStateOf(null)
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

    // 添加以下内容
    var moduleUpdateInfo: ModuleUpdateInfo? by mutableStateOf(null)
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

        // Access info is useful on the dashboard: show URLs with token/port.
        refreshAccessInfoInternal()
    }

    private fun parseDotEnv(envText: String): Map<String, String> {
        val out = mutableMapOf<String, String>()
        envText.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isBlank() || line.startsWith('#')) return@forEach
            val idx = line.indexOf('=')
            if (idx <= 0) return@forEach
            val key = line.substring(0, idx).trim()
            var value = line.substring(idx + 1).trim()
            if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith('\'') && value.endsWith('\''))) {
                if (value.length >= 2) value = value.substring(1, value.length - 1)
            }
            if (key.isNotBlank()) out[key] = value
        }
        return out
    }

    private suspend fun refreshAccessInfoInternal() {
        val env = try {
            repo.readEnvFile()
        } catch (_: Throwable) {
            null
        } ?: return

        val kv = parseDotEnv(env)

        // TOKEN
        val token = kv["TOKEN"].orEmpty().trim()
        apiToken = token.ifBlank { "87654321" }

        // DANMU_API_PORT
        val portText = kv["DANMU_API_PORT"].orEmpty().trim()
        val port = portText.toIntOrNull()
        apiPort = if (port != null && port in 1..65535) port else 9321

        // DANMU_API_HOST (0.0.0.0 by default; if user sets 127.0.0.1 then LAN access is not possible)
        val host = kv["DANMU_API_HOST"].orEmpty().trim()
        apiHost = host.ifBlank { "0.0.0.0" }

        // ADMIN_TOKEN (optional)
        adminToken = kv["ADMIN_TOKEN"].orEmpty().trim()
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

    fun setConsoleLogLimit(limit: Int) {
        viewModelScope.launch {
            settings.setConsoleLogLimit(limit)
        }
    }

    fun setSessionAdminToken(token: String) {
        sessionAdminToken = token.trim()
    }

    fun clearSessionAdminToken() {
        sessionAdminToken = ""
    }


    /**
     * Refresh only the log list.
     * Useful when navigating to the Logs screen while the service is writing new files.
     */
    fun refreshLogs() {
        viewModelScope.launch {
            withBusy("刷新日志中…") {
                logs = repo.listLogs()
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

    fun setWebDavSettings(url: String, username: String, password: String, path: String) {
        viewModelScope.launch {
            settings.setWebDavUrl(url)
            settings.setWebDavUsername(username)
            settings.setWebDavPassword(password)
            settings.setWebDavPath(path)
            snackbars.tryEmit("WebDAV 设置已保存")
        }
    }

    fun loadEnvFile(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val text = withBusy("读取配置中…") {
                repo.readEnvFile()
            }
            if (text == null) {
                snackbars.tryEmit("读取配置失败（请确认 Root / 模块状态）")
                onResult("")
            } else {
                onResult(text)
            }
        }
    }

    fun saveEnvFile(content: String) {
        viewModelScope.launch {
            val ok = withBusy("保存配置中…") {
                val ok = repo.writeEnvFile(content)
                if (ok) refreshAccessInfoInternal()
                ok
            }
            snackbars.tryEmit(if (ok) "配置已保存（重启服务后生效）" else "保存配置失败")
        }
    }

    // ====== danmu-api Console ======

    private fun effectiveAdminToken(): String {
        // Security note:
        // The in-app “控制台” only treats *session* admin token as admin privilege.
        // Even if ADMIN_TOKEN exists in .env, we do NOT automatically use it to unlock admin endpoints,
        // so the user must explicitly enter ADMIN_TOKEN to enter “管理员模式”.
        return sessionAdminToken.trim()
    }

    private fun hasAdminToken(): Boolean = effectiveAdminToken().isNotBlank()

    private fun pickTokenSegment(useAdminToken: Boolean): String {
        return if (useAdminToken) {
            val t = effectiveAdminToken()
            if (t.isNotBlank()) t else apiToken
        } else {
            apiToken
        }
    }

    /**
     * Low-level request helper for the built-in console (Compose replica of Web UI).
     */
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

    /**
     * Validate whether [candidate] is a correct ADMIN_TOKEN.
     *
     * This method **does not** persist anything. It simply probes `/api/config` using the
     * provided token as the first path segment and checks whether the server accepts it.
     *
     * @return Pair(ok, errorMessage). When ok=true, errorMessage is null.
     */
    suspend fun validateAdminToken(candidate: String): Pair<Boolean, String?> {
        val token = candidate.trim()
        if (token.isBlank()) return false to "请填写 ADMIN_TOKEN"

        val res = danmuApiClient.request(
            method = "GET",
            host = apiHost,
            port = apiPort,
            tokenSegment = token,
            path = "/api/config",
            query = emptyMap(),
            bodyJson = null,
        )

        if (!res.isSuccessful) {
            val msg = when (res.code) {
                401, 403 -> "ADMIN_TOKEN 输入错误"
                -1 -> res.error ?: "验证失败"
                else -> res.error ?: "验证失败（HTTP ${res.code}）"
            }
            return false to msg
        }

        // If the server accepted the token for /api/config, treat it as valid.
        return true to null
    }

    fun refreshServerConfig(useAdminToken: Boolean = false) {
        viewModelScope.launch {
            serverConfigLoading = true
            serverConfigError = null

            val res = requestDanmuApi("GET", "/api/config", useAdminToken = useAdminToken)
            if (res.isSuccessful) {
                val parsed = parseServerConfig(res.body)
                if (parsed != null) {
                    serverConfig = parsed
                } else {
                    serverConfigError = "解析配置失败"
                }
            } else {
                serverConfigError = res.error ?: "请求失败（HTTP ${res.code}）"
            }
            serverConfigLoading = false
        }
    }

    fun refreshServerLogs() {
        viewModelScope.launch {
            serverLogsLoading = true
            serverLogsError = null
            val res = requestDanmuApi("GET", "/api/logs")
            if (res.isSuccessful) {
                serverLogs = parseServerLogs(res.body)
            } else {
                serverLogsError = res.error ?: "请求失败（HTTP ${res.code}）"
            }
            serverLogsLoading = false
        }
    }

    fun clearServerLogs() {
        viewModelScope.launch {
            if (!hasAdminToken()) {
                snackbars.tryEmit("未提供管理员 Token，无法执行清空服务日志")
                return@launch
            }
            val res = requestDanmuApi(
                method = "POST",
                path = "/api/logs/clear",
                bodyJson = "{}",
                useAdminToken = true,
            )
            if (res.isSuccessful) {
                snackbars.tryEmit("已清空服务日志")
                refreshServerLogs()
            } else {
                snackbars.tryEmit(res.error ?: "清空失败（HTTP ${res.code}）")
            }
        }
    }

    fun clearServerCache() {
        viewModelScope.launch {
            if (!hasAdminToken()) {
                snackbars.tryEmit("未提供管理员 Token，无法执行清理缓存")
                return@launch
            }
            val res = requestDanmuApi(
                method = "POST",
                path = "/api/cache/clear",
                bodyJson = "{}",
                useAdminToken = true,
            )
            snackbars.tryEmit(
                if (res.isSuccessful) "缓存已清理" else (res.error ?: "清理失败（HTTP ${res.code}）")
            )
        }
    }

    fun deployServer() {
        viewModelScope.launch {
            if (!hasAdminToken()) {
                snackbars.tryEmit("未提供管理员 Token，无法执行重新部署")
                return@launch
            }
            val res = requestDanmuApi(
                method = "POST",
                path = "/api/deploy",
                bodyJson = "{}",
                useAdminToken = true,
            )
            snackbars.tryEmit(
                if (res.isSuccessful) "已触发部署" else (res.error ?: "操作失败（HTTP ${res.code}）")
            )
        }
    }

    fun setServerEnvVar(key: String, value: String) {
        viewModelScope.launch {
            val tokenIsAdmin = hasAdminToken()
            val body = JSONObject().apply {
                put("key", key)
                put("value", value)
            }.toString()
            val res = requestDanmuApi(
                method = "POST",
                path = "/api/env/set",
                bodyJson = body,
                useAdminToken = tokenIsAdmin,
            )
            if (res.isSuccessful) {
                snackbars.tryEmit("已更新：$key")
                // Re-fetch access info in case TOKEN/PORT/HOST changes.
                refreshAccessInfoInternal()
                refreshServerConfig(useAdminToken = tokenIsAdmin)
            } else {
                snackbars.tryEmit(res.error ?: "更新失败（HTTP ${res.code}）")
            }
        }
    }

    fun deleteServerEnvVar(key: String) {
        viewModelScope.launch {
            val tokenIsAdmin = hasAdminToken()
            val body = JSONObject().apply {
                put("key", key)
            }.toString()
            val res = requestDanmuApi(
                method = "POST",
                path = "/api/env/del",
                bodyJson = body,
                useAdminToken = tokenIsAdmin,
            )
            if (res.isSuccessful) {
                snackbars.tryEmit("已删除：$key")
                refreshAccessInfoInternal()
                refreshServerConfig(useAdminToken = tokenIsAdmin)
            } else {
                snackbars.tryEmit(res.error ?: "删除失败（HTTP ${res.code}）")
            }
        }
    }

    private fun parseServerConfig(body: String): ServerConfigResponse? {
        return try {
            val obj = JSONObject(body)

            val categorized = mutableMapOf<String, List<EnvVarItem>>()
            val categorizedObj = obj.optJSONObject("categorizedEnvVars")
            if (categorizedObj != null) {
                val iter = categorizedObj.keys()
                while (iter.hasNext()) {
                    val category = iter.next()
                    val arr = categorizedObj.optJSONArray(category) ?: JSONArray()
                    val list = mutableListOf<EnvVarItem>()
                    for (i in 0 until arr.length()) {
                        val it = arr.optJSONObject(i) ?: continue
                        val key = it.optString("key")
                        val valueAny = it.opt("value")
                        val value = when (valueAny) {
                            is JSONArray -> (0 until valueAny.length()).joinToString(",") { idx -> valueAny.optString(idx) }
                            null -> ""
                            else -> valueAny.toString()
                        }
                        val type = it.optString("type").ifBlank { "text" }
                        val desc = it.optString("description")
                        val options = mutableListOf<String>()
                        val optAny = it.opt("options")
                        if (optAny is JSONArray) {
                            for (j in 0 until optAny.length()) options.add(optAny.optString(j))
                        }
                        list.add(
                            EnvVarItem(
                                key = key,
                                value = value,
                                type = type,
                                description = desc,
                                options = options,
                            )
                        )
                    }
                    categorized[category] = list
                }
            }

            val metaMap = mutableMapOf<String, EnvVarMeta>()
            val metaObj = obj.optJSONObject("envVarConfig")
            if (metaObj != null) {
                val iter = metaObj.keys()
                while (iter.hasNext()) {
                    val k = iter.next()
                    val m = metaObj.optJSONObject(k) ?: continue
                    val options = mutableListOf<String>()
                    val optAny = m.opt("options")
                    if (optAny is JSONArray) {
                        for (j in 0 until optAny.length()) options.add(optAny.optString(j))
                    }
                    val minVal = if (m.has("min")) m.optDouble("min") else Double.NaN
                    val maxVal = if (m.has("max")) m.optDouble("max") else Double.NaN
                    metaMap[k] = EnvVarMeta(
                        category = m.optString("category").ifBlank { "system" },
                        type = m.optString("type").ifBlank { "text" },
                        description = m.optString("description"),
                        options = options,
                        min = if (!minVal.isNaN()) minVal else null,
                        max = if (!maxVal.isNaN()) maxVal else null,
                    )
                }
            }

            val originalMap = mutableMapOf<String, String>()
            val origObj = obj.optJSONObject("originalEnvVars")
            if (origObj != null) {
                val iter = origObj.keys()
                while (iter.hasNext()) {
                    val k = iter.next()
                    val v = origObj.opt(k)
                    originalMap[k] = v?.toString().orEmpty()
                }
            }

            ServerConfigResponse(
                message = obj.optString("message"),
                version = obj.optString("version").ifBlank { null },
                categorizedEnvVars = categorized,
                envVarConfig = metaMap,
                originalEnvVars = originalMap,
                hasAdminToken = obj.optBoolean("hasAdminToken", false),
                repository = obj.optString("repository").ifBlank { null },
                description = obj.optString("description").ifBlank { null },
                notice = obj.optString("notice").ifBlank { null },
            )
        } catch (_: Throwable) {
            null
        }
    }
    private fun parseServerLogs(text: String): List<ServerLogEntry> {
        // The backend log format may vary by build / runtime.
        // Supported examples:
        // 1) "[2024-01-01 12:00:00] info: message"
        // 2) "2026-01-25T12:28:36.155+08:00  ERROR  message"
        // 3) "2026-01-25 12:28:36.155  WARN  message"

        fun normLevel(raw: String): String {
            val s = raw.trim().uppercase()
            return when {
                s == "WARNING" -> "WARN"
                s == "WRN" -> "WARN"
                s.startsWith("ERR") -> "ERROR"
                s == "FATAL" -> "ERROR"
                else -> s
            }
        }

        val bracketed = Regex("""^\[(.+?)]\s+(\w+):\s+(.*)$""")
        val iso = Regex("""^(\d{4}-\d{2}-\d{2}T[^\s]+)\s+(\w+)\s+(.*)$""")
        val plain = Regex("""^(\d{4}-\d{2}-\d{2}\s+[^\s]+)\s+(\w+)\s+(.*)$""")

        return text
            .lineSequence()
            .mapNotNull { raw ->
                val line = raw.trimEnd()
                if (line.isBlank()) return@mapNotNull null

                // Format 1
                bracketed.find(line)?.let { m ->
                    val ts = m.groupValues.getOrNull(1).orEmpty()
                    val level = normLevel(m.groupValues.getOrNull(2).orEmpty())
                    val msg = m.groupValues.getOrNull(3).orEmpty()
                    return@mapNotNull ServerLogEntry(ts, level, msg)
                }

                // Format 2
                iso.find(line)?.let { m ->
                    val ts = m.groupValues.getOrNull(1).orEmpty()
                    val level = normLevel(m.groupValues.getOrNull(2).orEmpty())
                    val msg = m.groupValues.getOrNull(3).orEmpty()
                    return@mapNotNull ServerLogEntry(ts, level, msg)
                }

                // Format 3
                plain.find(line)?.let { m ->
                    val ts = m.groupValues.getOrNull(1).orEmpty()
                    val level = normLevel(m.groupValues.getOrNull(2).orEmpty())
                    val msg = m.groupValues.getOrNull(3).orEmpty()
                    return@mapNotNull ServerLogEntry(ts, level, msg)
                }

                // Unknown format: keep full line as message.
                ServerLogEntry("", "", line.trim())
            }
            .toList()
    }

    fun exportEnvToUri(uri: Uri) {
        viewModelScope.launch {
            val ok = withBusy("导出配置到本地…") {
                val text = repo.readEnvFile() ?: return@withBusy false
                withContext(Dispatchers.IO) {
                    try {
                        appContext.contentResolver.openOutputStream(uri)?.use { os ->
                            os.write(text.toByteArray(Charsets.UTF_8))
                            os.flush()
                        } != null
                    } catch (_: Throwable) {
                        false
                    }
                }
            }
            snackbars.tryEmit(if (ok) "已导出配置" else "导出失败")
        }
    }

    fun importEnvFromUri(uri: Uri) {
        viewModelScope.launch {
            val ok = withBusy("从本地导入配置…") {
                val text = withContext(Dispatchers.IO) {
                    try {
                        appContext.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { br ->
                            br.readText()
                        }
                    } catch (_: Throwable) {
                        null
                    }
                } ?: return@withBusy false

                val ok = repo.writeEnvFile(text)
                if (ok) refreshAccessInfoInternal()
                ok
            }
            snackbars.tryEmit(if (ok) "已导入配置（重启服务后生效）" else "导入失败")
        }
    }

    fun exportEnvToWebDav() {
        viewModelScope.launch {
            val baseUrl = webDavUrl.value.trim()
            val remotePath = webDavPath.value.trim()
            val isFullUrl = remotePath.startsWith("http://") || remotePath.startsWith("https://")
            if (remotePath.isBlank() || (!isFullUrl && baseUrl.isBlank())) {
                snackbars.tryEmit("请先在设置中填写 WebDAV 地址与远程路径（或直接粘贴完整文件URL）")
                return@launch
            }

            val user = webDavUsername.value
            val pass = webDavPassword.value

            val result = withBusy("上传配置到 WebDAV…") {
                val text = repo.readEnvFile() ?: return@withBusy WebDavResult.Error("读取配置失败")
                webDavClient.uploadText(
                    baseUrl = baseUrl,
                    remotePath = remotePath,
                    username = user,
                    password = pass,
                    text = text,
                )
            }

            when (result) {
                is WebDavResult.Success -> snackbars.tryEmit("已上传到 WebDAV（HTTP ${result.code}）")
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
                snackbars.tryEmit("请先在设置中填写 WebDAV 地址与远程路径（或直接粘贴完整文件URL）")
                return@launch
            }

            val user = webDavUsername.value
            val pass = webDavPassword.value

            val result = withBusy("从 WebDAV 导入配置…") {
                val download = webDavClient.downloadText(
                    baseUrl = baseUrl,
                    remotePath = remotePath,
                    username = user,
                    password = pass,
                )

                when (val r = download.result) {
                    is WebDavResult.Error -> r
                    is WebDavResult.Success -> {
                        val text = download.text ?: return@withBusy WebDavResult.Error("下载内容为空")
                        val ok = repo.writeEnvFile(text)
                        if (ok) refreshAccessInfoInternal()
                        if (ok) r else WebDavResult.Error("写入配置失败")
                    }
                }
            }

            when (result) {
                is WebDavResult.Success -> snackbars.tryEmit("已从 WebDAV 导入配置（重启服务后生效）")
                is WebDavResult.Error -> snackbars.tryEmit("WebDAV 导入失败：${result.message}")
            }
        }
    }

    fun checkActiveCoreUpdate() {
        viewModelScope.launch {
            val core = status?.activeCore
            if (core == null || core.repo.isBlank() || core.ref.isBlank()) {
                snackbars.tryEmit("未检测到当前核心")
                return@launch
            }

            val token = githubToken.value
            val info = withBusy("正在检查当前核心更新…") {
                repo.checkUpdate(core, token)
            }

            val id = core.id
            if (id.isNotBlank()) {
                updateInfo = updateInfo.toMutableMap().apply { put(id, info) }.toMap()
            }

            snackbars.tryEmit(if (info.updateAvailable) "当前核心有更新" else "当前核心已是最新")
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
    fun checkModuleUpdate() {
        viewModelScope.launch {
            try {
                val currentVersion = status?.module?.version
                val info = withBusy("检查模块更新中…") {
                    repo.checkModuleUpdate(currentVersion)
                }
                moduleUpdateInfo = info

                if (info.hasUpdate) {
                    snackbars.tryEmit("发现模块更新：${info.latestRelease?.tagName}")
                } else {
                    snackbars.tryEmit("模块已是最新版本")
                }
            } catch (t: Throwable) {
                // Never crash the app due to an update-check failure.
                moduleUpdateInfo = null
                snackbars.tryEmit("检查更新失败：${t.message ?: t::class.java.simpleName}")
            }
        }
    }

    fun downloadModuleZip(asset: com.danmuapi.manager.data.model.ReleaseAsset, onProgress: (Int) -> Unit, onComplete: (String?) -> Unit) {
        viewModelScope.launch {
            withBusy("下载模块中：${asset.name}") {
                try {
                    val cacheDir = appContext.cacheDir
                    // Avoid path traversal and keep filenames stable.
                    val safeName = asset.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
                    val file = java.io.File(cacheDir, safeName)
                    
                    withContext(Dispatchers.IO) {
                        val client = okhttp3.OkHttpClient.Builder()
                            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                            .callTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
                            .build()
                        val request = okhttp3.Request.Builder()
                            .url(asset.downloadUrl)
                            .header("Accept", "application/octet-stream")
                            .build()
                        
                        client.newCall(request).execute().use { response ->
                            if (!response.isSuccessful) {
                                try { file.delete() } catch (_: Throwable) {}
                                withContext(Dispatchers.Main) { onComplete(null) }
                                return@withContext
                            }
                            
                            val body = response.body ?: run {
                                try { file.delete() } catch (_: Throwable) {}
                                withContext(Dispatchers.Main) { onComplete(null) }
                                return@withContext
                            }
                            
                            val totalBytes = body.contentLength()
                            var downloadedBytes = 0L
                            
                            file.outputStream().use { output ->
                                body.byteStream().use { input ->
                                    val buffer = ByteArray(8192)
                                    var bytes: Int
                                    while (input.read(buffer).also { bytes = it } != -1) {
                                        output.write(buffer, 0, bytes)
                                        downloadedBytes += bytes
                                        if (totalBytes > 0) {
                                            val progress = (downloadedBytes * 100 / totalBytes).toInt()
                                            withContext(Dispatchers.Main) {
                                                onProgress(progress)
                                            }
                                        }
                                    }
                                }
                            }
                            
                            withContext(Dispatchers.Main) { onComplete(file.absolutePath) }
                        }
                    }
                } catch (e: Exception) {
                    snackbars.tryEmit("下载失败：${e.message}")
                    withContext(Dispatchers.Main) { onComplete(null) }
                }
            }
        }
    }

    fun installModuleZip(zipPath: String, preserveCore: Boolean = true) {
        viewModelScope.launch {
            val wasRunning = status?.service?.running == true
            val ok = withBusy("安装模块中…") {
                // Best-effort: stop service first to avoid half-written files.
                try {
                    repo.stopService()
                } catch (_: Throwable) {
                    // ignore
                }
                val cmd = """
                    MODPATH="/data/adb/modules/danmu_api_server"
                    TMPDIR="/data/local/tmp/danmu_api_module_update"
                    rm -rf "${'$'}TMPDIR" 2>/dev/null || true
                    mkdir -p "${'$'}TMPDIR" || exit 1
                    rm -rf "${'$'}MODPATH.bak" 2>/dev/null || true
                    unzip -o "$zipPath" -d "${'$'}TMPDIR" >/dev/null 2>&1 || {
                        rm -rf "${'$'}TMPDIR" 2>/dev/null || true
                        echo "failed"; exit 0;
                    }

                    # Locate module root (support both flat zip and zip-with-top-dir)
                    SRC="${'$'}TMPDIR"
                    if [ ! -f "${'$'}SRC/module.prop" ]; then
                        MP="$(find "${'$'}TMPDIR" -maxdepth 3 -type f -name module.prop 2>/dev/null | head -n 1)"
                        if [ -n "${'$'}MP" ]; then
                            SRC="$(dirname "${'$'}MP")"
                        fi
                    fi

                    if [ ! -f "${'$'}SRC/module.prop" ]; then
                        rm -rf "${'$'}TMPDIR" 2>/dev/null || true
                        echo "failed"; exit 0;
                    fi

                    if [ -d "${'$'}MODPATH" ]; then
                        mv "${'$'}MODPATH" "${'$'}MODPATH.bak" || {
                            rm -rf "${'$'}TMPDIR" 2>/dev/null || true
                            echo "failed"; exit 0;
                        }
                    fi
                    mkdir -p "${'$'}MODPATH" || {
                        rm -rf "${'$'}TMPDIR" 2>/dev/null || true
                        echo "failed"; exit 0;
                    }

                    # Copy files (try preserve metadata when possible).
                    cp -a "${'$'}SRC"/. "${'$'}MODPATH"/ 2>/dev/null || cp -r "${'$'}SRC"/. "${'$'}MODPATH"/

                    # Ensure scripts are executable.
                    find "${'$'}MODPATH" -type f -name "*.sh" -exec chmod 0755 {} \; 2>/dev/null || true
                    chmod 0755 "${'$'}MODPATH/action.sh" 2>/dev/null || true

                    rm -rf "${'$'}TMPDIR" 2>/dev/null || true
                    rm -rf "${'$'}MODPATH.bak" 2>/dev/null || true
                    echo "success"
                """.trimIndent()
                
                val res = RootShell.runSu(cmd, timeoutMs = 120_000)
                res.stdout.contains("success")
            }
            
            if (ok) {
                if (!preserveCore) {
                    // User requested to drop existing cores and switch to the bundled core shipped in the new module.
                    // We do a safe backup instead of deleting outright.
                    val ts = System.currentTimeMillis()
                    val resetCmd = """
                        PERSIST_DIR='/data/adb/danmu_api_server'
                        CORE_CLI='${DanmuPaths.CORE_CLI}'
                        TS='${ts}'

                        if [ -d "${'$'}PERSIST_DIR/cores" ]; then
                          mv "${'$'}PERSIST_DIR/cores" "${'$'}PERSIST_DIR/cores.bak.${'$'}TS" 2>/dev/null || true
                        fi
                        rm -f "${'$'}PERSIST_DIR/core" "${'$'}PERSIST_DIR/active_core_id" 2>/dev/null || true

                        # Re-seed / fix symlinks based on the newly installed module.
                        sh "${'$'}CORE_CLI" ensure >/dev/null 2>&1 || true
                        echo "reset_core_ok"
                    """.trimIndent()
                    RootShell.runSu(resetCmd)
                }
                snackbars.tryEmit("模块安装成功！建议重启设备后生效。")
                refreshAllInternal()
                // Restore previous running state (best-effort).
                if (wasRunning) {
                    startService()
                }
            } else {
                snackbars.tryEmit("模块安装失败")
            }
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

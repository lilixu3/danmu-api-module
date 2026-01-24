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
import com.danmuapi.manager.data.model.StatusResponse
import com.danmuapi.manager.network.WebDavClient
import com.danmuapi.manager.network.WebDavResult
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

class MainViewModel(
    private val appContext: Context,
    private val repo: DanmuRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val webDavClient = WebDavClient()

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
     * System management token (ADMIN_TOKEN) parsed from .env.
     *
     * When present, it unlocks Danmu API Web UI's "系统设置" features.
     */
    var adminToken: String by mutableStateOf("")
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

        // ADMIN_TOKEN (optional; may be blank). If key is missing, keep it empty.
        adminToken = kv["ADMIN_TOKEN"]?.trim().orEmpty()

        // DANMU_API_PORT
        val portText = kv["DANMU_API_PORT"].orEmpty().trim()
        val port = portText.toIntOrNull()
        apiPort = if (port != null && port in 1..65535) port else 9321

        // DANMU_API_HOST (0.0.0.0 by default; if user sets 127.0.0.1 then LAN access is not possible)
        val host = kv["DANMU_API_HOST"].orEmpty().trim()
        apiHost = host.ifBlank { "0.0.0.0" }
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

    fun installModuleZip(
        zipPath: String,
        keepCores: Boolean = true,
        keepConfig: Boolean = true,
        keepLogs: Boolean = true,
    ) {
        viewModelScope.launch {
            val wasRunning = status?.service?.running == true

            val ok = withBusy("安装模块中…") {
                // Best-effort: stop service first to avoid half-written files.
                try {
                    repo.stopService()
                } catch (_: Throwable) {
                    // ignore
                }

                val zipQ = shellQuote(zipPath)
                val keepCoresFlag = if (keepCores) 1 else 0
                val keepConfigFlag = if (keepConfig) 1 else 0
                val keepLogsFlag = if (keepLogs) 1 else 0

                val cmd = """
                    MODPATH="/data/adb/modules/danmu_api_server"
                    MODBAK="${'$'}MODPATH.bak"
                    PERSIST="/data/adb/danmu_api_server"
                    ZIP=$zipQ
                    KEEP_CORES=$keepCoresFlag
                    KEEP_CONFIG=$keepConfigFlag
                    KEEP_LOGS=$keepLogsFlag

                    TMPDIR="/data/local/tmp/danmu_api_module_update_${'$'}{RANDOM}_${'$'}{RANDOM}"
                    rm -rf "${'$'}TMPDIR" 2>/dev/null || true
                    mkdir -p "${'$'}TMPDIR" || exit 1

                    unzip -o "${'$'}ZIP" -d "${'$'}TMPDIR" >/dev/null 2>&1 || {
                        rm -rf "${'$'}TMPDIR" 2>/dev/null || true
                        echo "failed_unzip"; exit 1;
                    }

                    # Locate module root (support both flat zip and zip-with-top-dir)
                    SRC="${'$'}TMPDIR"
                    if [ ! -f "${'$'}SRC/module.prop" ]; then
                        MP="$(find "${'$'}TMPDIR" -maxdepth 4 -type f -name module.prop 2>/dev/null | head -n 1)"
                        if [ -n "${'$'}MP" ]; then
                            SRC="$(dirname "${'$'}MP")"
                        fi
                    fi

                    if [ ! -f "${'$'}SRC/module.prop" ]; then
                        rm -rf "${'$'}TMPDIR" 2>/dev/null || true
                        echo "failed_badzip"; exit 1;
                    fi

                    # Optional data cleanup (for reinstall/downgrade)
                    if [ "${'$'}KEEP_CORES" != "1" ]; then
                        rm -rf "${'$'}PERSIST/cores" "${'$'}PERSIST/core" "${'$'}PERSIST/active_core_id" 2>/dev/null || true
                    fi
                    if [ "${'$'}KEEP_CONFIG" != "1" ]; then
                        rm -rf "${'$'}PERSIST/config" 2>/dev/null || true
                    fi
                    if [ "${'$'}KEEP_LOGS" != "1" ]; then
                        rm -rf "${'$'}PERSIST/logs" 2>/dev/null || true
                    fi

                    mkdir -p "${'$'}PERSIST/bin" "${'$'}PERSIST/config" "${'$'}PERSIST/cores" "${'$'}PERSIST/logs" 2>/dev/null || true

                    # Backup old module (if exists)
                    rm -rf "${'$'}MODBAK" 2>/dev/null || true
                    if [ -d "${'$'}MODPATH" ]; then
                        mv "${'$'}MODPATH" "${'$'}MODBAK" || {
                            rm -rf "${'$'}TMPDIR" 2>/dev/null || true
                            echo "failed_backup"; exit 1;
                        }
                    fi

                    # Install new module files
                    mkdir -p "${'$'}MODPATH" || {
                        rm -rf "${'$'}TMPDIR" 2>/dev/null || true
                        echo "failed_mkdir"; exit 1;
                    }

                    # Copy files (try preserve metadata when possible).
                    cp -a "${'$'}SRC"/. "${'$'}MODPATH"/ 2>/dev/null || cp -r "${'$'}SRC"/. "${'$'}MODPATH"/ 2>/dev/null || true

                    # Validate
                    if [ ! -f "${'$'}MODPATH/module.prop" ]; then
                        rm -rf "${'$'}MODPATH" 2>/dev/null || true
                        if [ -d "${'$'}MODBAK" ]; then
                            mv "${'$'}MODBAK" "${'$'}MODPATH" 2>/dev/null || true
                        fi
                        rm -rf "${'$'}TMPDIR" 2>/dev/null || true
                        echo "failed_copy"; exit 1;
                    fi

                    # Fix permissions (best-effort)
                    chown -R root:root "${'$'}MODPATH" 2>/dev/null || true
                    chmod 0755 "${'$'}MODPATH" 2>/dev/null || true
                    find "${'$'}MODPATH" -type d -exec chmod 0755 {} \; 2>/dev/null || true
                    find "${'$'}MODPATH" -type f -exec chmod 0644 {} \; 2>/dev/null || true
                    find "${'$'}MODPATH" -type f -name "*.sh" -exec chmod 0755 {} \; 2>/dev/null || true
                    [ -d "${'$'}MODPATH/bin" ] && find "${'$'}MODPATH/bin" -type f -exec chmod 0755 {} \; 2>/dev/null || true
                    [ -d "${'$'}MODPATH/scripts" ] && find "${'$'}MODPATH/scripts" -type f -exec chmod 0755 {} \; 2>/dev/null || true
                    [ -d "${'$'}MODPATH/system/bin" ] && find "${'$'}MODPATH/system/bin" -type f -exec chmod 0755 {} \; 2>/dev/null || true

                    # Sync bin/scripts to persist for CLI (so manager app can call them right away)
                    if [ -d "${'$'}MODPATH/scripts" ]; then
                        cp -a "${'$'}MODPATH/scripts"/. "${'$'}PERSIST/bin"/ 2>/dev/null || cp -r "${'$'}MODPATH/scripts"/. "${'$'}PERSIST/bin"/ 2>/dev/null || true
                    fi
                    if [ -d "${'$'}MODPATH/bin" ]; then
                        cp -a "${'$'}MODPATH/bin"/. "${'$'}PERSIST/bin"/ 2>/dev/null || cp -r "${'$'}MODPATH/bin"/. "${'$'}PERSIST/bin"/ 2>/dev/null || true
                    fi
                    chmod -R 0755 "${'$'}PERSIST/bin" 2>/dev/null || true

                    # Ensure config file exists
                    if [ ! -f "${'$'}PERSIST/config/.env" ]; then
                        if [ -f "${'$'}MODPATH/defaults/config/.env.example" ]; then
                            cp -f "${'$'}MODPATH/defaults/config/.env.example" "${'$'}PERSIST/config/.env" 2>/dev/null || true
                            chmod 0644 "${'$'}PERSIST/config/.env" 2>/dev/null || true
                        fi
                    fi

                    # Ensure core link points to active core if exists
                    ACTIVE_FILE="${'$'}PERSIST/active_core_id"
                    CORE_LINK="${'$'}PERSIST/core"
                    if [ -f "${'$'}ACTIVE_FILE" ]; then
                        ID="$(cat "${'$'}ACTIVE_FILE" 2>/dev/null | tr -d '\r\n')"
                        if [ -n "${'$'}ID" ] && [ -d "${'$'}PERSIST/cores/${'$'}ID/danmu_api" ]; then
                            rm -rf "${'$'}CORE_LINK" 2>/dev/null || true
                            ln -s "${'$'}PERSIST/cores/${'$'}ID/danmu_api" "${'$'}CORE_LINK" 2>/dev/null || true
                        fi
                    fi

                    # If no active core (fresh install or user wiped cores), seed from bundled core in the zip.
                    if [ ! -f "${'$'}ACTIVE_FILE" ]; then
                        if [ -d "${'$'}SRC/app/danmu_api" ] && [ -f "${'$'}SRC/app/danmu_api/worker.js" ]; then
                            REPO=""
                            REF=""
                            SHA=""
                            VERSION=""
                            if [ -f "${'$'}SRC/defaults/core_source.txt" ]; then
                                REPO="$(grep '^repo=' "${'$'}SRC/defaults/core_source.txt" | head -n1 | cut -d= -f2-)"
                                REF="$(grep '^ref=' "${'$'}SRC/defaults/core_source.txt" | head -n1 | cut -d= -f2-)"
                                SHA="$(grep '^sha=' "${'$'}SRC/defaults/core_source.txt" | head -n1 | cut -d= -f2-)"
                                VERSION="$(grep '^version=' "${'$'}SRC/defaults/core_source.txt" | head -n1 | cut -d= -f2-)"
                            fi
                            SHA_SHORT="$(echo "${'$'}SHA" | cut -c1-7)"
                            ID_BASE="bundled_${'$'}REPO_${'$'}REF_${'$'}SHA_SHORT"
                            CORE_ID="$(echo "${'$'}ID_BASE" | sed 's/[^A-Za-z0-9._-]/_/g')"
                            DEST="${'$'}PERSIST/cores/${'$'}CORE_ID"
                            DEST_CORE="${'$'}DEST/danmu_api"
                            rm -rf "${'$'}DEST" 2>/dev/null || true
                            mkdir -p "${'$'}DEST" 2>/dev/null || true
                            cp -a "${'$'}SRC/app/danmu_api" "${'$'}DEST_CORE" 2>/dev/null || cp -r "${'$'}SRC/app/danmu_api" "${'$'}DEST_CORE" 2>/dev/null || true
                            ln -s "${'$'}PERSIST/config" "${'$'}DEST/config" 2>/dev/null || true

                            TS="$(date +%s 2>/dev/null || echo 0)"
                            cat > "${'$'}DEST/meta.json" <<META
{
  "id": "${'$'}CORE_ID",
  "repo": "${'$'}REPO",
  "ref": "${'$'}REF",
  "sha": "${'$'}SHA",
  "shaShort": "${'$'}SHA_SHORT",
  "version": "${'$'}VERSION",
  "installedAt": ${'$'}TS,
  "sizeBytes": 0
}
META

                            rm -rf "${'$'}CORE_LINK" 2>/dev/null || true
                            ln -s "${'$'}DEST_CORE" "${'$'}CORE_LINK" 2>/dev/null || true
                            echo "${'$'}CORE_ID" > "${'$'}ACTIVE_FILE"
                        fi
                    fi

                    # Ensure module app links point to persist
                    MODCFG="${'$'}MODPATH/app/config"
                    rm -rf "${'$'}MODCFG" 2>/dev/null || true
                    ln -s "${'$'}PERSIST/config" "${'$'}MODCFG" 2>/dev/null || true

                    MODCORE="${'$'}MODPATH/app/danmu_api"
                    rm -rf "${'$'}MODCORE" 2>/dev/null || true
                    ln -s "${'$'}CORE_LINK" "${'$'}MODCORE" 2>/dev/null || true

                    rm -rf "${'$'}TMPDIR" 2>/dev/null || true
                    rm -rf "${'$'}MODBAK" 2>/dev/null || true
                    echo "success"
                """.trimIndent()

                val res = RootShell.runSu(cmd, timeoutMs = 180_000)
                res.exitCode == 0 && res.stdout.contains("success")
            }

            if (ok) {
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

    private fun shellQuote(value: String): String {
        // Safe single-quote wrapper for sh.
        return "'" + value.replace("'", "'\"'\"'") + "'"
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

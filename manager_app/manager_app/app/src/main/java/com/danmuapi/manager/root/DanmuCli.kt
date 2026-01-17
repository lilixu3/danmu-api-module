package com.danmuapi.manager.root

import com.danmuapi.manager.data.model.CoreListResponse
import com.danmuapi.manager.data.model.LogsResponse
import com.danmuapi.manager.data.model.StatusResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class DanmuCli {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val statusAdapter = moshi.adapter(StatusResponse::class.java)
    private val coreListAdapter = moshi.adapter(CoreListResponse::class.java)
    private val logsAdapter = moshi.adapter(LogsResponse::class.java)

    suspend fun getStatus(): StatusResponse? {
        val cmd = "${DanmuPaths.CORE_CLI} status --json"
        val res = RootShell.runSu(cmd)
        if (res.exitCode != 0) return null
        return statusAdapter.fromJson(res.stdout.trim())
    }

    suspend fun listCores(): CoreListResponse? {
        val cmd = "${DanmuPaths.CORE_CLI} core list --json"
        val res = RootShell.runSu(cmd)
        if (res.exitCode != 0) return null
        return coreListAdapter.fromJson(res.stdout.trim())
    }

    suspend fun startService(): Boolean {
        val res = RootShell.runSu("${DanmuPaths.CONTROL_CLI} start")
        return res.exitCode == 0
    }

    suspend fun stopService(): Boolean {
        val res = RootShell.runSu("${DanmuPaths.CONTROL_CLI} stop")
        return res.exitCode == 0
    }

    suspend fun restartService(): Boolean {
        val res = RootShell.runSu("${DanmuPaths.CONTROL_CLI} restart")
        return res.exitCode == 0
    }

    suspend fun setAutostart(enabled: Boolean): Boolean {
        val sub = if (enabled) "on" else "off"
        val res = RootShell.runSu("${DanmuPaths.CORE_CLI} autostart $sub")
        return res.exitCode == 0
    }

    suspend fun installCore(repo: String, ref: String): Boolean {
        val safeRepo = repo.replace("\"", "")
        val safeRef = ref.replace("\"", "")
        val res = RootShell.runSu("${DanmuPaths.CORE_CLI} core install '$safeRepo' '$safeRef'")
        return res.exitCode == 0
    }

    suspend fun activateCore(id: String): Boolean {
        val safeId = id.replace("\"", "")
        val res = RootShell.runSu("${DanmuPaths.CORE_CLI} core activate '$safeId'")
        return res.exitCode == 0
    }

    suspend fun deleteCore(id: String): Boolean {
        val safeId = id.replace("\"", "")
        val res = RootShell.runSu("${DanmuPaths.CORE_CLI} core delete '$safeId'")
        return res.exitCode == 0
    }

    suspend fun listLogs(): LogsResponse? {
        val res = RootShell.runSu("${DanmuPaths.CORE_CLI} logs list")
        if (res.exitCode != 0) return null
        return logsAdapter.fromJson(res.stdout.trim())
    }

    suspend fun clearLogs(): Boolean {
        val res = RootShell.runSu("${DanmuPaths.CORE_CLI} logs clear")
        return res.exitCode == 0
    }

    suspend fun tailLog(path: String, lines: Int = 200): String? {
        val safePath = path.replace("\"", "")
        val n = lines.coerceIn(10, 2000)
        // Prefer BusyBox tail for maximum compatibility.
        val cmd = "${DanmuPaths.BIN_DIR}/busybox tail -n $n '$safePath'"
        val res = RootShell.runSu(cmd, timeoutMs = 15_000)
        if (res.exitCode != 0) return null
        return res.stdout
    }
}

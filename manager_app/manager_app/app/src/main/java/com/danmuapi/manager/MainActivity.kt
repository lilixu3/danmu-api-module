package com.danmuapi.manager

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {

    private val modId = "danmu_api_server"
    private val coreScript = "/data/adb/modules/$modId/scripts/danmu_core.sh"
    private val ctrlScript = "/data/adb/modules/$modId/scripts/danmu_control.sh"

    private lateinit var txtStatus: TextView
    private lateinit var txtOutput: TextView
    private lateinit var swAutostart: SwitchCompat
    private lateinit var spRepo: Spinner
    private lateinit var edtCustomRepo: EditText
    private lateinit var edtRef: EditText

    private var suppressAutostartCallback = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        txtStatus = findViewById(R.id.txtStatus)
        txtOutput = findViewById(R.id.txtOutput)
        swAutostart = findViewById(R.id.swAutostart)
        spRepo = findViewById(R.id.spRepo)
        edtCustomRepo = findViewById(R.id.edtCustomRepo)
        edtRef = findViewById(R.id.edtRef)

        val btnRefresh: Button = findViewById(R.id.btnRefresh)
        val btnStart: Button = findViewById(R.id.btnStart)
        val btnStop: Button = findViewById(R.id.btnStop)
        val btnRestart: Button = findViewById(R.id.btnRestart)
        val btnInstall: Button = findViewById(R.id.btnInstall)

        val repoOptions = listOf(
            "huangxd-/danmu_api",
            "lilixu3/danmu_api",
            "自定义"
        )

        spRepo.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, repoOptions)
        spRepo.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val custom = repoOptions[position] == "自定义"
                edtCustomRepo.visibility = if (custom) View.VISIBLE else View.GONE
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // no-op
            }
        }

        btnRefresh.setOnClickListener { refreshStatus() }

        btnStart.setOnClickListener {
            runRootCommand("启动服务", "${shQuote(ctrlScript)} start")
        }

        btnStop.setOnClickListener {
            runRootCommand("停止服务", "${shQuote(ctrlScript)} stop")
        }

        btnRestart.setOnClickListener {
            runRootCommand("重启服务", "${shQuote(ctrlScript)} restart")
        }

        swAutostart.setOnCheckedChangeListener { _, isChecked ->
            if (suppressAutostartCallback) return@setOnCheckedChangeListener
            val sub = if (isChecked) "on" else "off"
            runRootCommand("设置开机自启=$sub", "${shQuote(coreScript)} autostart $sub")
        }

        btnInstall.setOnClickListener {
            val selected = spRepo.selectedItem?.toString() ?: ""
            val repo = if (selected == "自定义") edtCustomRepo.text.toString().trim() else selected
            val ref = edtRef.text.toString().trim()

            if (repo.isEmpty()) {
                toast("请填写仓库（owner/repo）")
                return@setOnClickListener
            }
            if (ref.isEmpty()) {
                toast("请填写 ref（分支/Tag/Commit，例如 main）")
                return@setOnClickListener
            }

            val cmd = "${shQuote(coreScript)} install ${shQuote(repo)} ${shQuote(ref)}"
            runRootCommand("下载并切换核心", cmd)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        runInBg("刷新状态中...") {
            runSu("${shQuote(coreScript)} status")
        }
    }

    private fun runRootCommand(title: String, cmd: String) {
        runInBg("$title...\n$cmd") {
            val res = runSu(cmd)
            // After any operation, refresh status
            val status = runSu("${shQuote(coreScript)} status")
            // Merge outputs
            CmdResult(
                exitCode = if (res.exitCode != 0) res.exitCode else status.exitCode,
                output = buildString {
                    append("$title\n")
                    append("cmd: ").append(cmd).append("\n\n")
                    append("--- output ---\n")
                    append(res.output.trim()).append("\n")
                    if (status.output.isNotBlank()) {
                        append("\n--- status ---\n")
                        append(status.output.trim()).append("\n")
                    }
                    if (res.exitCode != 0) {
                        append("\n(exitCode=").append(res.exitCode).append(")\n")
                    }
                }
            )
        }
    }

    private fun runInBg(initialText: String, task: () -> CmdResult) {
        txtOutput.text = initialText
        Thread {
            val res = try {
                task()
            } catch (e: Throwable) {
                CmdResult(1, "Exception: ${e.message}\n")
            }

            runOnUiThread {
                txtOutput.text = res.output
                applyStatusToUi(res.output)
            }
        }.start()
    }

    private fun applyStatusToUi(output: String) {
        // Try to parse key=value lines from danmu_core.sh status
        val map = parseKeyValues(output)

        val svc = map["service"] ?: map["service="]
        val autostart = map["autostart"] ?: map["autostart="]
        val repo = map["repo"] ?: map["repo="]
        val ref = map["ref"] ?: map["ref="]

        val sb = StringBuilder()
        if (svc != null) sb.append("服务: ").append(svc).append("\n")
        if (autostart != null) sb.append("开机自启: ").append(autostart).append("\n")
        if (repo != null) sb.append("核心 repo: ").append(repo).append("\n")
        if (ref != null) sb.append("核心 ref: ").append(ref).append("\n")

        if (sb.isNotEmpty()) {
            txtStatus.text = sb.toString().trimEnd()
        } else {
            txtStatus.text = "状态：未知（请确认模块已安装并已授予 Root 权限）"
        }

        // Update autostart switch without triggering callback
        if (autostart != null) {
            suppressAutostartCallback = true
            swAutostart.isChecked = (autostart == "on")
            suppressAutostartCallback = false
        }
    }

    private fun parseKeyValues(text: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val lines = text.split('\n')
        for (raw in lines) {
            val line = raw.trim()
            if (!line.contains('=')) continue
            val idx = line.indexOf('=')
            if (idx <= 0) continue
            val k = line.substring(0, idx).trim()
            val v = line.substring(idx + 1).trim()
            map[k] = v
        }
        return map
    }

    private fun runSu(cmd: String): CmdResult {
        // Using su -c; output is merged (stderr -> stdout)
        val pb = ProcessBuilder("su", "-c", cmd)
        pb.redirectErrorStream(true)
        val proc = pb.start()
        val out = StringBuilder()
        BufferedReader(InputStreamReader(proc.inputStream)).use { br ->
            var line: String?
            while (true) {
                line = br.readLine() ?: break
                out.append(line).append('\n')
            }
        }
        val code = proc.waitFor()
        return CmdResult(code, out.toString())
    }

    private fun shQuote(s: String): String {
        // Safe single-quote for POSIX shell: ' -> '\''
        return "'" + s.replace("'", "'\\''") + "'"
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private data class CmdResult(
        val exitCode: Int,
        val output: String
    )
}

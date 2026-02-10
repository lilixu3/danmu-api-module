package com.danmuapi.manager.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

object RootShell {
    private const val DEFAULT_TIMEOUT_MS: Long = 600_000 // downloads can take time

    suspend fun isRootAvailable(): Boolean {
        val res = runSu("id", timeoutMs = 10_000)
        return res.exitCode == 0 && res.stdout.contains("uid=0")
    }

    suspend fun runSu(command: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): ShellResult = withContext(Dispatchers.IO) {
        // NOTE: keep command as a single string; su handles sh -c internally
        val pb = ProcessBuilder("su", "-c", command)
        pb.redirectErrorStream(false)
        val p = pb.start()

        val stdout = StringBuilder()
        val stderr = StringBuilder()

        val outThread = Thread {
            BufferedReader(InputStreamReader(p.inputStream)).use { br ->
                var line: String?
                while (true) {
                    line = br.readLine() ?: break
                    stdout.append(line).append('\n')
                }
            }
        }
        val errThread = Thread {
            BufferedReader(InputStreamReader(p.errorStream)).use { br ->
                var line: String?
                while (true) {
                    line = br.readLine() ?: break
                    stderr.append(line).append('\n')
                }
            }
        }

        outThread.start()
        errThread.start()

        val finished = p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        if (!finished) {
            p.destroy()
            p.destroyForcibly()
            outThread.join(250)
            errThread.join(250)
            return@withContext ShellResult(
                exitCode = -1,
                stdout = stdout.toString(),
                stderr = (stderr.toString() + "\n[timeout after ${timeoutMs}ms]").trim(),
            )
        }

        outThread.join(2_000)
        errThread.join(2_000)

        ShellResult(
            exitCode = p.exitValue(),
            stdout = stdout.toString(),
            stderr = stderr.toString(),
        )
    }
}

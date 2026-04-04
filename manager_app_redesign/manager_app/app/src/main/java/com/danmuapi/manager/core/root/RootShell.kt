package com.danmuapi.manager.core.root

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
    private const val DEFAULT_TIMEOUT_MS: Long = 600_000L

    suspend fun isRootAvailable(): Boolean {
        val result = runSu("id", timeoutMs = 10_000L)
        return result.exitCode == 0 && result.stdout.contains("uid=0")
    }

    suspend fun runSu(
        command: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): ShellResult = withContext(Dispatchers.IO) {
        val process = ProcessBuilder("su", "-c", command)
            .redirectErrorStream(false)
            .start()

        val stdout = StringBuilder()
        val stderr = StringBuilder()

        val stdoutThread = Thread {
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    stdout.append(line).append('\n')
                }
            }
        }
        val stderrThread = Thread {
            BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    stderr.append(line).append('\n')
                }
            }
        }

        stdoutThread.start()
        stderrThread.start()

        val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroy()
            process.destroyForcibly()
            stdoutThread.join(250L)
            stderrThread.join(250L)
            return@withContext ShellResult(
                exitCode = -1,
                stdout = stdout.toString(),
                stderr = (stderr.toString() + "\n[timeout after ${timeoutMs}ms]").trim(),
            )
        }

        stdoutThread.join(2_000L)
        stderrThread.join(2_000L)

        ShellResult(
            exitCode = process.exitValue(),
            stdout = stdout.toString(),
            stderr = stderr.toString(),
        )
    }
}

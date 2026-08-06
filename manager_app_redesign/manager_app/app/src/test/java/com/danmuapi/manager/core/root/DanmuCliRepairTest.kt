package com.danmuapi.manager.core.root

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DanmuCliRepairTest {

    private fun cliWith(vararg handlers: Pair<String, ShellResult>): DanmuCli {
        val map = handlers.toMap()
        return DanmuCli(
            runSu = { command, _ -> map[command] ?: ShellResult(1, "", "unexpected command") },
        )
    }

    private val activateCommand = "${DanmuPaths.CORE_CLI} core activate 'core-1'"

    @Test
    fun activateCoreWithDependencyRepair_returnsNullWhenHealthy() = runBlocking {
        val cli = cliWith(activateCommand to ShellResult(0, """{"result":"ok"}""", ""))
        assertNull(cli.activateCoreWithDependencyRepair("core-1"))
    }

    @Test
    fun activateCoreWithDependencyRepair_parsesRepairJson() = runBlocking {
        // 与 danmu_core.sh 真实输出一致：missing 为字符串数组，incompatible 为对象数组
        val output = """{"result":"dependency_repair_required","core":"core-1","missing":["brotli"],"incompatible":[{"name":"pako","spec":"^2.1.0","installed":"1.0.0","reason":"version_mismatch"}],"conditional":[]}"""
        val cli = cliWith(activateCommand to ShellResult(78, output, ""))
        val repair = cli.activateCoreWithDependencyRepair("core-1")
        assertNotNull(repair)
        repair!!.let {
            assertEquals("core-1", it.core)
            assertEquals(listOf("brotli"), it.missing)
            assertEquals(listOf("pako"), it.incompatible)
            assertEquals(listOf("brotli", "pako"), it.allNames)
        }
    }

    @Test
    fun activateCoreWithDependencyRepair_fallsBackToCoreOnlyOnNoise() = runBlocking {
        val cli = cliWith(activateCommand to ShellResult(1, "some error text", "stderr"))
        val repair = cli.activateCoreWithDependencyRepair("core-1")
        assertNotNull(repair)
        assertEquals("core-1", repair!!.core)
        assertTrue(repair.missing.isEmpty())
    }

    @Test
    fun getCoreFingerprint_parses64HexFromJson() = runBlocking {
        val fingerprint = "a".repeat(64)
        val command = "${DanmuPaths.CORE_CLI} core fingerprint 'core-1'"
        val cli = cliWith(command to ShellResult(0, """{"result":"ok","core":"core-1","fingerprint":"$fingerprint"}""", ""))
        assertEquals(fingerprint, cli.getCoreFingerprint("core-1"))
    }

    @Test
    fun getCoreFingerprint_returnsNullOnFailure() = runBlocking {
        val command = "${DanmuPaths.CORE_CLI} core fingerprint 'core-1'"
        val cli = cliWith(command to ShellResult(1, "", "error"))
        assertNull(cli.getCoreFingerprint("core-1"))
    }

    @Test
    fun installCoreDependencies_buildsSafeCommand() = runBlocking {
        val command = "${DanmuPaths.CORE_CLI} deps install 'core-1' '/data/adb/tmp/node_modules' 'abc123'"
        val cli = cliWith(command to ShellResult(0, """{"result":"ok"}""", ""))
        assertTrue(cli.installCoreDependencies("core-1", "/data/adb/tmp/node_modules", "abc123"))
    }

    @Test
    fun installCoreDependencies_rejectsUnsafeDependencyId() = runBlocking {
        val cli = cliWith()
        assertTrue(!cli.installCoreDependencies("core-1", "/data/adb/tmp/node_modules", "abc; rm -rf /"))
    }
}

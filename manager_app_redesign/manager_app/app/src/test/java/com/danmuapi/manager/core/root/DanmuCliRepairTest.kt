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
    fun activateCoreWithDependencyRepair_returnsActivatedWhenHealthy() = runBlocking {
        val cli = cliWith(activateCommand to ShellResult(0, """{"result":"ok"}""", ""))
        assertTrue(cli.activateCoreWithDependencyRepair("core-1") === CoreActivationOutcome.Activated)
    }

    @Test
    fun activateCoreWithDependencyRepair_parsesRepairJson() = runBlocking {
        // 与 danmu_core.sh 真实输出一致：missing 为字符串数组，incompatible/conditional 为对象数组
        val output = """{"result":"dependency_repair_required","core":"core-1","missing":["brotli"],"incompatible":[{"name":"pako","spec":"^2.1.0","installed":"1.0.0","reason":"version_mismatch"}],"conditional":[{"name":"redis","spec":"^5.11.0","installed":null,"compatible":false,"reason":"not_enabled","required":false}]}"""
        val cli = cliWith(activateCommand to ShellResult(78, output, ""))
        val outcome = cli.activateCoreWithDependencyRepair("core-1")
        assertTrue(outcome is CoreActivationOutcome.RepairRequired)
        val repair = (outcome as CoreActivationOutcome.RepairRequired).repair
        repair.let {
            assertEquals("core-1", it.core)
            assertEquals(listOf("brotli"), it.missing)
            assertEquals(listOf("pako"), it.incompatible)
            assertEquals(listOf("redis"), it.conditional)
            assertEquals(listOf("brotli", "pako", "redis"), it.allNames)
        }
    }

    @Test
    fun activateCoreWithDependencyRepair_returnsFailureOnNoise() = runBlocking {
        val cli = cliWith(activateCommand to ShellResult(1, "some error text", "stderr"))
        val outcome = cli.activateCoreWithDependencyRepair("core-1")
        assertTrue(outcome is CoreActivationOutcome.Failure)
        assertEquals(1, (outcome as CoreActivationOutcome.Failure).exitCode)
        assertEquals("stderr", outcome.message)
    }

    @Test
    fun activateCoreWithDependencyRepair_doesNotTreatExit78ErrorAsRepair() = runBlocking {
        val output = """{"result":"error","error":"core_not_found"}"""
        val cli = cliWith(activateCommand to ShellResult(78, output, ""))
        val outcome = cli.activateCoreWithDependencyRepair("core-1")
        assertTrue(outcome is CoreActivationOutcome.Failure)
        assertEquals("核心不存在", (outcome as CoreActivationOutcome.Failure).message)
    }

    @Test
    fun activateCoreWithDependencyRepair_doesNotOfferRepairWhenInspectorFailed() = runBlocking {
        val output = """{"result":"dependency_repair_required","core":"core-1","missing":[],"incompatible":[],"conditional":[],"skipped":"inspect_failed"}"""
        val cli = cliWith(activateCommand to ShellResult(78, output, ""))
        val outcome = cli.activateCoreWithDependencyRepair("core-1")
        assertTrue(outcome is CoreActivationOutcome.Failure)
        assertEquals("运行时依赖检查失败", (outcome as CoreActivationOutcome.Failure).message)
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

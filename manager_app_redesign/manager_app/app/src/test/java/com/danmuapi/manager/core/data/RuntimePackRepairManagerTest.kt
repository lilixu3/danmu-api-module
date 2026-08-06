package com.danmuapi.manager.core.data

import com.danmuapi.manager.core.model.CoreDependencyRepairRequired
import com.danmuapi.manager.core.root.CoreActivationOutcome
import com.danmuapi.manager.core.root.CoreDependencyRepairGateway
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RuntimePackRepairManagerTest {

    @get:Rule
    val tmp: TemporaryFolder = TemporaryFolder()

    private val fingerprint = "f".repeat(64)
    private val sha = "c".repeat(64)

    private fun manifest(): RuntimePackManifest = RuntimePackManifest(
        schema = 3,
        serial = 7L,
        runtimeProtocol = 2,
        nodeMajor = 18,
        runtimeLockSha256 = "a".repeat(64),
        dependencyFingerprint = fingerprint,
        dependencies = mapOf("pako" to "^2.1.0"),
        artifactUrl = "https://github.com/lilixu3/danmu-api-runtime-packs/releases/download/runtime-dependencies-${sha.take(12)}/node_modules.zip",
        artifactSha256 = sha,
        artifactSize = 1024L,
        packages = listOf(RuntimePackPackage(name = "pako", version = "2.1.0", path = "node_modules/pako")),
    )

    private class FakeCli(
        var fingerprint: String? = "f".repeat(64),
        var activateResult: CoreActivationOutcome = CoreActivationOutcome.Activated,
        var installResult: Boolean = true,
    ) : CoreDependencyRepairGateway {
        val calls = mutableListOf<String>()

        override suspend fun getCoreFingerprint(id: String): String? {
            calls.add("fingerprint")
            return fingerprint
        }

        override suspend fun installCoreDependencies(
            coreId: String,
            sourceNodeModulesDir: String,
            dependencyId: String,
        ): Boolean {
            calls.add("install")
            return installResult
        }

        override suspend fun activateCoreWithDependencyRepair(id: String): CoreActivationOutcome {
            calls.add("activate")
            return activateResult
        }
    }

    private fun makeNodeModules(root: File): File {
        val nm = File(root, "node_modules/pako")
        nm.mkdirs()
        File(nm, "package.json").writeText("""{"name":"pako","version":"2.1.0"}""")
        return File(root, "node_modules")
    }

    @Test
    fun repair_successPath_orderAndProgress() = runBlocking {
        val cli = FakeCli()
        val stages = mutableListOf<Pair<RuntimePackRepairManager.RepairStage, Float>>()
        var extracted: File? = null
        val manager = RuntimePackRepairManager(
            cli = cli,
            fetchManifest = { manifest() },
            downloadAndExtract = { _, workingDir, _ -> makeNodeModules(workingDir).also { extracted = it } },
            workingDir = tmp.newFolder("work"),
        )
        val outcome = manager.repair("core-1") { stage, progress ->
            stages.add(stage to progress)
        }
        assertTrue("应成功: $outcome", outcome is RuntimePackRepairManager.RepairOutcome.Success)
        assertEquals(
            listOf("fingerprint", "install", "activate"),
            cli.calls,
        )
        assertNotNull(extracted)
        assertTrue(extracted!!.isDirectory)
        val stageNames = stages.map { it.first.name }.distinct()
        assertEquals(
            listOf(
                RuntimePackRepairManager.RepairStage.Fingerprint.name,
                RuntimePackRepairManager.RepairStage.Manifest.name,
                RuntimePackRepairManager.RepairStage.Download.name,
                RuntimePackRepairManager.RepairStage.Install.name,
                RuntimePackRepairManager.RepairStage.Activate.name,
            ),
            stageNames,
        )
        // 进度从 0 到 1
        assertEquals(0f, stages.first().second)
        assertEquals(1f, stages.last().second)
    }

    @Test
    fun repair_fingerprintUnavailable_failsEarlyWithoutFetch() = runBlocking {
        val cli = FakeCli(fingerprint = null)
        var manifestFetched = false
        val manager = RuntimePackRepairManager(
            cli = cli,
            fetchManifest = {
                manifestFetched = true
                manifest()
            },
            downloadAndExtract = { _, _, _ -> makeNodeModules(tmp.newFolder()) },
            workingDir = tmp.newFolder("work"),
        )
        val outcome = manager.repair("core-1")
        assertTrue(outcome is RuntimePackRepairManager.RepairOutcome.Failure)
        assertEquals(
            RuntimePackRepairManager.RepairFailure.FingerprintUnavailable,
            (outcome as RuntimePackRepairManager.RepairOutcome.Failure).reason,
        )
        assertTrue(!manifestFetched)
        assertEquals(listOf("fingerprint"), cli.calls)
    }

    @Test
    fun repair_fingerprintMismatch_rejectsPack() = runBlocking {
        val cli = FakeCli(fingerprint = "d".repeat(64))
        val manager = RuntimePackRepairManager(
            cli = cli,
            fetchManifest = { manifest() },
            downloadAndExtract = { _, _, _ -> makeNodeModules(tmp.newFolder()) },
            workingDir = tmp.newFolder("work"),
        )
        val outcome = manager.repair("core-1")
        assertTrue(outcome is RuntimePackRepairManager.RepairOutcome.Failure)
        assertEquals(
            RuntimePackRepairManager.RepairFailure.FingerprintMismatch,
            (outcome as RuntimePackRepairManager.RepairOutcome.Failure).reason,
        )
        assertEquals(listOf("fingerprint"), cli.calls)
    }

    @Test
    fun repair_manifestUnavailable_reportsFailure() = runBlocking {
        val cli = FakeCli()
        val manager = RuntimePackRepairManager(
            cli = cli,
            fetchManifest = { throw java.io.IOException("network down") },
            downloadAndExtract = { _, _, _ -> makeNodeModules(tmp.newFolder()) },
            workingDir = tmp.newFolder("work"),
        )
        val outcome = manager.repair("core-1")
        assertTrue(outcome is RuntimePackRepairManager.RepairOutcome.Failure)
        assertEquals(
            RuntimePackRepairManager.RepairFailure.ManifestUnavailable,
            (outcome as RuntimePackRepairManager.RepairOutcome.Failure).reason,
        )
    }

    @Test
    fun repair_installFailed_skipsActivate() = runBlocking {
        val cli = FakeCli(installResult = false)
        val manager = RuntimePackRepairManager(
            cli = cli,
            fetchManifest = { manifest() },
            downloadAndExtract = { _, _, _ -> makeNodeModules(tmp.newFolder()) },
            workingDir = tmp.newFolder("work"),
        )
        val outcome = manager.repair("core-1")
        assertTrue(outcome is RuntimePackRepairManager.RepairOutcome.Failure)
        assertEquals(
            RuntimePackRepairManager.RepairFailure.InstallFailed,
            (outcome as RuntimePackRepairManager.RepairOutcome.Failure).reason,
        )
        assertEquals(listOf("fingerprint", "install"), cli.calls)
    }

    @Test
    fun repair_activateStillBlocked_reportsRemainingRepair() = runBlocking {
        val cli = FakeCli(
            activateResult = CoreActivationOutcome.RepairRequired(
                CoreDependencyRepairRequired(core = "core-1", missing = listOf("redis")),
            ),
        )
        val manager = RuntimePackRepairManager(
            cli = cli,
            fetchManifest = { manifest() },
            downloadAndExtract = { _, _, _ -> makeNodeModules(tmp.newFolder()) },
            workingDir = tmp.newFolder("work"),
        )
        val outcome = manager.repair("core-1")
        assertTrue(outcome is RuntimePackRepairManager.RepairOutcome.Failure)
        val failure = outcome as RuntimePackRepairManager.RepairOutcome.Failure
        assertEquals(RuntimePackRepairManager.RepairFailure.ActivateStillBlocked, failure.reason)
        assertEquals(listOf("redis"), failure.remainingRepair?.missing)
    }

    @Test
    fun repair_extractFailure_reportsFailure() = runBlocking {
        val cli = FakeCli()
        val manager = RuntimePackRepairManager(
            cli = cli,
            fetchManifest = { manifest() },
            downloadAndExtract = { _, _, _ -> throw RuntimePackIntegrityException("zip 损坏") },
            workingDir = tmp.newFolder("work"),
        )
        val outcome = manager.repair("core-1")
        assertTrue(outcome is RuntimePackRepairManager.RepairOutcome.Failure)
        assertEquals(
            RuntimePackRepairManager.RepairFailure.DownloadExtractFailed,
            (outcome as RuntimePackRepairManager.RepairOutcome.Failure).reason,
        )
    }
}

package com.danmuapi.manager.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class LocalRuntimePackImporterTest {

    @get:Rule
    val tmp: TemporaryFolder = TemporaryFolder()

    private fun zipWith(vararg entries: Pair<String, String>): File {
        val archive = tmp.newFile("pack.zip")
        ZipOutputStream(FileOutputStream(archive)).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return archive
    }

    private fun makePackageEntry(name: String, version: String = "1.0.0"): Pair<String, String> {
        val base = "node_modules/$name"
        return base + "/package.json" to """{"name":"$name","version":"$version"}"""
    }

    @Test
    fun import_extractsValidPackageTree() {
        val archive = zipWith(
            makePackageEntry("pako", "2.1.0"),
            makePackageEntry("node-fetch", "3.3.2"),
        )
        val importer = LocalRuntimePackImporter()
        val result = importer.importArchive(archive, tmp.newFolder("out"))
        assertNotNull(result)
        assertTrue(File(result!!, "pako/package.json").isFile)
        assertTrue(File(result, "node-fetch/package.json").isFile)
        assertEquals(2, importer.lastPackageCount)
    }

    @Test
    fun import_acceptsSingleWrapperDirectory() {
        // 允许 zip 根下套一层目录（常见打包产物）：node_modules 或任意单目录
        val archive = zipWith(
            "pack/node_modules/pako/package.json" to """{"name":"pako","version":"2.1.0"}""",
        )
        val importer = LocalRuntimePackImporter()
        val result = importer.importArchive(archive, tmp.newFolder("out"))
        assertNotNull(result)
        assertTrue(File(result!!, "pako/package.json").isFile)
    }

    @Test
    fun import_acceptsScopedPackageTree() {
        val archive = zipWith(
            "node_modules/@dan-uni/dan-any/package.json" to
                """{"name":"@dan-uni/dan-any","version":"2.3.9"}""",
        )
        val importer = LocalRuntimePackImporter()
        val result = importer.importArchive(archive, tmp.newFolder("scoped-out"))
        assertNotNull(result)
        assertTrue(File(result!!, "@dan-uni/dan-any/package.json").isFile)
        assertEquals(1, importer.lastPackageCount)
    }

    @Test(expected = RuntimePackIntegrityException::class)
    fun import_rejectsPathTraversal() {
        val archive = zipWith(
            "../evil/package.json" to "{}",
            makePackageEntry("pako"),
        )
        LocalRuntimePackImporter().importArchive(archive, tmp.newFolder("out"))
    }

    @Test(expected = RuntimePackIntegrityException::class)
    fun import_rejectsNativeArtifacts() {
        val archive = zipWith(
            makePackageEntry("snappy"),
            "node_modules/snappy/build/Release/snappy.node" to "binary",
        )
        LocalRuntimePackImporter().importArchive(archive, tmp.newFolder("out"))
    }

    @Test(expected = RuntimePackIntegrityException::class)
    fun import_rejectsNonZipContent() {
        val bad = tmp.newFile("not-a-zip.zip")
        bad.writeText("this is not a zip file at all")
        LocalRuntimePackImporter().importArchive(bad, tmp.newFolder("out"))
    }

    @Test(expected = RuntimePackIntegrityException::class)
    fun import_rejectsEmptyNodeModules() {
        val archive = zipWith("node_modules/" to "")
        LocalRuntimePackImporter().importArchive(archive, tmp.newFolder("out"))
    }

    @Test(expected = RuntimePackIntegrityException::class)
    fun import_rejectsUnrecognizablePackages() {
        // 没有 package.json 的杂乱文件
        val archive = zipWith(
            "node_modules/random-file.txt" to "not a package",
        )
        LocalRuntimePackImporter().importArchive(archive, tmp.newFolder("out"))
    }

    @Test
    fun import_rejectsAbsolutePathEntries() {
        val archive = zipWith(
            "/etc/passwd" to "evil",
            makePackageEntry("pako"),
        )
        var rejected = false
        try {
            LocalRuntimePackImporter().importArchive(archive, tmp.newFolder("out"))
        } catch (_: RuntimePackIntegrityException) {
            rejected = true
        }
        assertTrue(rejected)
    }

    @Test
    fun import_cleansWorkingDirOnFailure() {
        val archive = zipWith("../evil" to "x", makePackageEntry("pako"))
        val out = tmp.newFolder("out2")
        var rejected = false
        try {
            LocalRuntimePackImporter().importArchive(archive, out)
        } catch (_: RuntimePackIntegrityException) {
            rejected = true
        }
        assertTrue(rejected)
        assertFalse(out.listFiles()?.isNotEmpty() ?: false)
    }
}

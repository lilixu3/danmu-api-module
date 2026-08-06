package com.danmuapi.manager.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimePackProtocolTest {

    @Test
    fun fingerprint_matchesNodeCanonicalJsonContract() {
        // 与 runtime-packs build_runtime_pack.py 的 canonical_json_bytes 一致：
        // sort_keys + separators=(",",":") + ensure_ascii=false
        val deps = mapOf(
            "node-fetch" to "^3.3.2",
            "redis" to "^5.11.0",
            "@dan-uni/dan-any" to "^2.3.9",
        )
        val fingerprint = RuntimePackProtocol.dependencyFingerprint(deps)
        assertEquals(64, fingerprint.length)
        assertTrue(fingerprint.all { it in '0'..'9' || it in 'a'..'f' })
        // 独立用 java 安全散列复算，保证不是空转
        val canonical = """{"@dan-uni/dan-any":"^2.3.9","node-fetch":"^3.3.2","redis":"^5.11.0"}"""
        val expected = sha256Hex(canonical.toByteArray(Charsets.UTF_8))
        assertEquals(expected, fingerprint)
    }

    @Test
    fun fingerprint_isOrderIndependent() {
        val a = RuntimePackProtocol.dependencyFingerprint(mapOf("b" to "1", "a" to "2"))
        val b = RuntimePackProtocol.dependencyFingerprint(mapOf("a" to "2", "b" to "1"))
        assertEquals(a, b)
    }

    @Test
    fun fingerprint_detectsSpecChange() {
        val a = RuntimePackProtocol.dependencyFingerprint(mapOf("pako" to "^2.1.0"))
        val b = RuntimePackProtocol.dependencyFingerprint(mapOf("pako" to "^2.2.0"))
        assertFalse(a == b)
    }

    @Test
    fun isSafeArchivePath_rejectsTraversalAndNonNodeModules() {
        assertTrue(RuntimePackProtocol.isSafeArchivePath("node_modules/pako/package.json"))
        assertTrue(RuntimePackProtocol.isSafeArchivePath("node_modules/@scope/pkg/index.js"))
        assertFalse(RuntimePackProtocol.isSafeArchivePath("../evil"))
        assertFalse(RuntimePackProtocol.isSafeArchivePath("node_modules/../../evil"))
        assertFalse(RuntimePackProtocol.isSafeArchivePath("/etc/passwd"))
        assertFalse(RuntimePackProtocol.isSafeArchivePath("evil/package.json"))
        assertFalse(RuntimePackProtocol.isSafeArchivePath("node_modules//"))
    }

    @Test
    fun sha256_matchesJavaDigest() {
        val expected = sha256Hex("hello".toByteArray(Charsets.UTF_8))
        assertEquals(expected, RuntimePackProtocol.sha256("hello".toByteArray()))
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}

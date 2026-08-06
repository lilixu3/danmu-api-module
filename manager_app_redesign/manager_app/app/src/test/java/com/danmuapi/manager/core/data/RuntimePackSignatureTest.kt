package com.danmuapi.manager.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

class RuntimePackSignatureTest {

    private fun keyPair() = KeyPairGenerator.getInstance("RSA").apply {
        initialize(2048)
    }.generateKeyPair()

    private fun sign(bytes: ByteArray, privateKey: java.security.PrivateKey): String {
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(privateKey)
        signature.update(bytes)
        return Base64.getEncoder().encodeToString(signature.sign())
    }

    private fun pem(key: java.security.PublicKey): String {
        val encoded = Base64.getEncoder().encodeToString(key.encoded)
        val body = encoded.chunked(64).joinToString("\n")
        return "-----BEGIN PUBLIC KEY-----\n$body\n-----END PUBLIC KEY-----\n"
    }

    @Test
    fun verifyManifestSignature_acceptsValidSignature() {
        val pair = keyPair()
        val payload = """{"schema":3,"serial":42}""".toByteArray(Charsets.UTF_8)
        val signatureText = sign(payload, pair.private)
        assertTrue(RuntimePackDownloader.verifyManifestSignature(payload, signatureText, pem(pair.public)))
    }

    @Test
    fun verifyManifestSignature_rejectsTamperedPayload() {
        val pair = keyPair()
        val payload = """{"schema":3,"serial":42}""".toByteArray(Charsets.UTF_8)
        val signatureText = sign(payload, pair.private)
        val tampered = """{"schema":3,"serial":43}""".toByteArray(Charsets.UTF_8)
        assertFalse(RuntimePackDownloader.verifyManifestSignature(tampered, signatureText, pem(pair.public)))
    }

    @Test
    fun verifyManifestSignature_rejectsGarbage() {
        val pair = keyPair()
        val payload = """{"schema":3}""".toByteArray(Charsets.UTF_8)
        assertFalse(RuntimePackDownloader.verifyManifestSignature(payload, "not-base64!!", pem(pair.public)))
        assertFalse(RuntimePackDownloader.verifyManifestSignature(payload, "", "not a pem"))
    }

    private val artifactSha = "c".repeat(64)

    private fun validManifest(
        dependencies: Map<String, String> = mapOf("pako" to "^2.1.0"),
        packages: List<RuntimePackPackage> = listOf(
            RuntimePackPackage(name = "pako", version = "2.1.0", path = "node_modules/pako"),
        ),
    ): RuntimePackManifest = RuntimePackManifest(
        schema = 3,
        serial = 7L,
        runtimeProtocol = 2,
        nodeMajor = 18,
        runtimeLockSha256 = "a".repeat(64),
        // 权威口径：fingerprint 是核心 package.json 全集（dependencies+optionalDependencies）
        // 的指纹，与 manifest.dependencies（包内子集）无关；发布端用全集计算。
        dependencyFingerprint = "b".repeat(64),
        dependencies = dependencies,
        artifactUrl = "https://github.com/lilixu3/danmu-api-runtime-packs/releases/download/runtime-dependencies-${artifactSha.take(12)}/node_modules.zip",
        artifactSha256 = artifactSha,
        artifactSize = 1024L,
        packages = packages,
    )

    @Test
    fun validateManifest_acceptsWellFormedSchema3() {
        RuntimePackDownloader.validateManifest(validManifest())
    }

    @Test
    fun validateManifest_rejectsWrongSchemaOrNodeMajor() {
        var rejected = false
        try {
            RuntimePackDownloader.validateManifest(validManifest().copy(schema = 2))
        } catch (_: RuntimePackIntegrityException) {
            rejected = true
        }
        assertTrue("schema=2 应被拒绝", rejected)

        rejected = false
        try {
            RuntimePackDownloader.validateManifest(validManifest().copy(nodeMajor = 20))
        } catch (_: RuntimePackIntegrityException) {
            rejected = true
        }
        assertTrue("nodeMajor=20 应被拒绝", rejected)
    }

    @Test
    fun validateManifest_rejectsNonGithubArtifactUrl() {
        var rejected = false
        try {
            RuntimePackDownloader.validateManifest(validManifest().copy(artifactUrl = "https://evil.example.com/node_modules.zip"))
        } catch (_: RuntimePackIntegrityException) {
            rejected = true
        }
        assertTrue(rejected)
    }

    @Test
    fun validateManifest_rejectsUnsafePackagePaths() {
        var rejected = false
        try {
            RuntimePackDownloader.validateManifest(
                validManifest(packages = listOf(RuntimePackPackage(name = "pako", version = "2.1.0", path = "../../evil"))),
            )
        } catch (_: RuntimePackIntegrityException) {
            rejected = true
        }
        assertTrue(rejected)
    }

    @Test
    fun validateManifest_acceptsFingerprintUnrelatedToPackageSubset() {
        // 真实发布端语义：dependencyFingerprint 由核心 package.json 全集计算，
        // manifest.dependencies 只是包内子集，二者必然不同——清单仍应通过校验。
        val manifest = validManifest()
        assertEquals("b".repeat(64), manifest.dependencyFingerprint)
        assertTrue(
            manifest.dependencyFingerprint != RuntimePackProtocol.dependencyFingerprint(manifest.dependencies),
        )
        RuntimePackDownloader.validateManifest(manifest)
    }
}

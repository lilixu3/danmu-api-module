package com.danmuapi.manager.core.data

import com.danmuapi.manager.core.root.DanmuCli
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URI
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/** schema-3 签名运行时依赖清单（与 runtime-packs manifest.json 对齐） */
data class RuntimePackManifest(
    val schema: Int = 0,
    val serial: Long = 0L,
    val runtimeProtocol: Int = 0,
    val nodeMajor: Int = 0,
    val runtimeLockSha256: String = "",
    val dependencyFingerprint: String = "",
    val dependencies: Map<String, String> = emptyMap(),
    val artifactUrl: String = "",
    val artifactSha256: String = "",
    val artifactSize: Long = 0L,
    val packages: List<RuntimePackPackage> = emptyList(),
)

data class RuntimePackPackage(
    val name: String = "",
    val version: String = "",
    val integrity: String? = null,
    val path: String = "",
)

class RuntimePackIntegrityException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * 签名运行时依赖包下载与校验（schema-3）。
 * - manifest.json + manifest.sig 用内嵌公钥验签（SHA256withRSA）
 * - 清单字段、依赖指纹、包路径全部校验
 * - 依赖包下载后校验 SHA-256 与大小，安全解压（拒绝穿越/原生文件）
 * - 安装阶段调用 CLI `deps install` 原子落位
 */
class RuntimePackDownloader(
    private val httpClient: OkHttpClient,
    private val cli: DanmuCli,
    private val trustedPublicKeyPem: String = TRUSTED_PUBLIC_KEY_PEM,
) {
    companion object {
        private const val USER_AGENT = "DanmuApiManager"
        private const val SHA256_PATTERN = "[0-9a-f]{64}"
        private const val ARTIFACT_PREFIX =
            "https://github.com/${RuntimePackProtocol.PACK_REPO}/releases/download/"

        internal const val TRUSTED_PUBLIC_KEY_PEM = """-----BEGIN PUBLIC KEY-----
MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEAw23l6/+FdYKWvwIVuczi
ZPmPRLDXCqKjWzarqQhwjORb6/NneAYfqkzN1TnqBRZcuxESpQhdbLWfZaoUhqjX
xCEC2J77zzchdDi+5P5RZ0HD+vLNMmDmH8ut+zBD/77dzzMYHe99AoPkUJs8Zd9W
MbEdt4J/jmIPky7abnQi0snnMpJWZ1tZcdUqBisHj/5k30vWVTMlk/RQlvDZergf
DzD3/dkAT847chGNIO3QFBa5DXOogJOIfeBtCwahkpEnCoNoB1NotuJPd4Ye05G6
qN4+0HJxeUU7siHd4OsXGuDxtm6Ay/HqSSqSZx+ow/x8qhEdtQDSEhNUamblR8qL
x5FeWN8B08rml+8AFQSBWvO7y7VFChu6t37fGuxjXqdgdqUjJwA1zy5toj5MRjSq
VR4s8t3BGZrBEUc5WgerO9t26NlTIq6qpptdCPqh9TlanBVh0HGiV0/oNM0TU/N/
VUsmyyO7hViS/U7pwIdYiXT0+rvwwcyLhWyzUJjI+2clAgMBAAE=
-----END PUBLIC KEY-----"""

        fun verifyManifestSignature(
            manifestBytes: ByteArray,
            signatureText: String,
            publicKeyPem: String = TRUSTED_PUBLIC_KEY_PEM,
        ): Boolean {
            return runCatching {
                val encodedKey = publicKeyPem
                    .lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotBlank() && !it.startsWith("-----") }
                    .joinToString("")
                val keyBytes = Base64.getDecoder().decode(encodedKey)
                val signatureBytes = Base64.getDecoder().decode(signatureText.trim())
                val publicKey = KeyFactory.getInstance("RSA")
                    .generatePublic(X509EncodedKeySpec(keyBytes))
                Signature.getInstance("SHA256withRSA").run {
                    initVerify(publicKey)
                    update(manifestBytes)
                    verify(signatureBytes)
                }
            }.getOrDefault(false)
        }

        fun validateManifest(manifest: RuntimePackManifest) {
            if (manifest.schema != RuntimePackProtocol.SCHEMA ||
                manifest.runtimeProtocol != 2 ||
                manifest.nodeMajor != 18 ||
                manifest.serial <= 0L ||
                manifest.dependencies.isEmpty() ||
                manifest.packages.isEmpty()
            ) {
                throw RuntimePackIntegrityException("运行时依赖清单协议不兼容")
            }
            if (!Regex(SHA256_PATTERN).matches(manifest.runtimeLockSha256) ||
                !Regex(SHA256_PATTERN).matches(manifest.dependencyFingerprint) ||
                manifest.dependencyFingerprint != RuntimePackProtocol.dependencyFingerprint(manifest.dependencies) ||
                !Regex(SHA256_PATTERN).matches(manifest.artifactSha256)
            ) {
                throw RuntimePackIntegrityException("运行时依赖清单哈希无效")
            }
            if (manifest.artifactSize <= 0L ||
                manifest.artifactSize > RuntimePackProtocol.MAX_ARCHIVE_BYTES
            ) {
                throw RuntimePackIntegrityException("运行时依赖包大小不在允许范围内")
            }
            val expectedTag = "runtime-dependencies-${manifest.artifactSha256.take(12)}"
            val expectedUrl = "$ARTIFACT_PREFIX$expectedTag/node_modules.zip"
            val uri = runCatching { URI(manifest.artifactUrl) }.getOrNull()
            if (manifest.artifactUrl != expectedUrl || uri?.scheme != "https" || uri.host != "github.com") {
                throw RuntimePackIntegrityException("运行时依赖包下载地址无效")
            }
            val paths = HashSet<String>()
            manifest.packages.forEach { item ->
                if (item.name.isBlank() || item.version.isBlank() ||
                    !RuntimePackProtocol.isSafeArchivePath("${item.path}/package.json") ||
                    !paths.add(item.path)
                ) {
                    throw RuntimePackIntegrityException("运行时依赖包清单包含无效包：${item.path}")
                }
            }
            val topLevel = manifest.packages
                .filter { it.path == "node_modules/${it.name}" }
                .associate { it.name to it.version }
            val invalidRoots = manifest.dependencies.filter { (name, range) ->
                val installed = topLevel[name]
                installed == null || !NpmVersionRange.isSatisfied(range, installed)
            }
            if (invalidRoots.isNotEmpty()) {
                throw RuntimePackIntegrityException("运行时依赖包顶层依赖与清单不一致")
            }
        }
    }

    private val metadataClient: OkHttpClient by lazy {
        httpClient.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun fetchSignedManifest(
        manifestUrl: String = rawUrl(RuntimePackProtocol.MANIFEST_PATH),
        signatureUrl: String = rawUrl(RuntimePackProtocol.MANIFEST_SIGNATURE_PATH),
    ): RuntimePackManifest {
        val manifestBytes = requestBytes(manifestUrl, RuntimePackProtocol.MAX_MANIFEST_BYTES)
            ?: throw RuntimePackIntegrityException("运行时依赖清单下载失败")
        val signatureBytes = requestBytes(signatureUrl, RuntimePackProtocol.MAX_MANIFEST_SIGNATURE_BYTES)
            ?: throw RuntimePackIntegrityException("运行时依赖清单签名下载失败")
        if (!verifyManifestSignature(manifestBytes, signatureBytes.toString(Charsets.UTF_8), trustedPublicKeyPem)) {
            throw RuntimePackIntegrityException("运行时依赖清单签名校验失败")
        }
        val manifest = runCatching {
            org.json.JSONObject(manifestBytes.toString(Charsets.UTF_8)).let { root ->
                RuntimePackManifest(
                    schema = root.optInt("schema"),
                    serial = root.optLong("serial"),
                    runtimeProtocol = root.optInt("runtimeProtocol"),
                    nodeMajor = root.optInt("nodeMajor"),
                    runtimeLockSha256 = root.optString("runtimeLockSha256"),
                    dependencyFingerprint = root.optString("dependencyFingerprint"),
                    dependencies = root.optJSONObject("dependencies")?.let { deps ->
                        deps.keys().asSequence().associateWith { deps.optString(it) }
                    }.orEmpty(),
                    artifactUrl = root.optString("artifactUrl"),
                    artifactSha256 = root.optString("artifactSha256"),
                    artifactSize = root.optLong("artifactSize"),
                    packages = root.optJSONArray("packages")?.let { array ->
                        (0 until array.length()).mapNotNull { index ->
                            array.optJSONObject(index)?.let { item ->
                                RuntimePackPackage(
                                    name = item.optString("name"),
                                    version = item.optString("version"),
                                    integrity = item.optString("integrity").takeIf { it.isNotBlank() },
                                    path = item.optString("path"),
                                )
                            }
                        }
                    }.orEmpty(),
                )
            }
        }.getOrElse { error ->
            throw RuntimePackIntegrityException("运行时依赖清单格式无效", error)
        }
        validateManifest(manifest)
        return manifest
    }

    fun downloadArchive(
        manifest: RuntimePackManifest,
        target: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): File {
        val request = Request.Builder()
            .url(manifest.artifactUrl)
            .header("User-Agent", USER_AGENT)
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("下载运行时依赖失败：HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("下载运行时依赖失败：空响应")
            if (body.contentLength() > RuntimePackProtocol.MAX_ARCHIVE_BYTES) {
                throw RuntimePackIntegrityException("依赖包超过大小上限")
            }
            var count = 0L
            FileOutputStream(target).use { output ->
                BufferedInputStream(body.byteStream()).use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        count += read
                        if (count > RuntimePackProtocol.MAX_ARCHIVE_BYTES) {
                            throw RuntimePackIntegrityException("依赖包超过大小上限")
                        }
                        output.write(buffer, 0, read)
                        onProgress(count, manifest.artifactSize)
                    }
                }
            }
            if (count != manifest.artifactSize ||
                RuntimePackProtocol.sha256(target.readBytes()) != manifest.artifactSha256
            ) {
                throw RuntimePackIntegrityException("依赖包大小或 SHA-256 校验失败")
            }
        }
        return target
    }

    fun extractArchive(archive: File, unpackDir: File): File {
        val seen = HashSet<String>()
        var entryCount = 0
        var extractedBytes = 0L
        ZipInputStream(BufferedInputStream(archive.inputStream())).use { input ->
            while (true) {
                val zipEntry = input.nextEntry ?: break
                entryCount += 1
                if (entryCount > RuntimePackProtocol.MAX_ARCHIVE_ENTRIES) {
                    throw RuntimePackIntegrityException("依赖包文件数量超过上限")
                }
                val name = zipEntry.name
                val isRootDirectory = zipEntry.isDirectory && name == "node_modules/"
                if (!isRootDirectory && !RuntimePackProtocol.isSafeArchivePath(name)) {
                    throw RuntimePackIntegrityException("依赖包包含不安全路径：$name")
                }
                if (zipEntry.isDirectory) {
                    input.closeEntry()
                    continue
                }
                if (RuntimePackProtocol.isNativeArtifactPath(name)) {
                    throw RuntimePackIntegrityException("依赖包包含不支持的原生文件：$name")
                }
                if (!seen.add(name)) {
                    throw RuntimePackIntegrityException("依赖包包含重复路径：$name")
                }
                val destination = safeResolve(unpackDir, name)
                destination.parentFile?.mkdirs()
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        extractedBytes += read
                        if (extractedBytes > RuntimePackProtocol.MAX_EXTRACTED_BYTES) {
                            throw RuntimePackIntegrityException("依赖包解压后超过大小上限")
                        }
                        output.write(buffer, 0, read)
                    }
                }
                input.closeEntry()
            }
        }
        val sourceNodeModules = File(unpackDir, "node_modules")
        if (!sourceNodeModules.isDirectory) {
            throw RuntimePackIntegrityException("依赖包缺少 node_modules 目录")
        }
        return sourceNodeModules
    }

    private fun safeResolve(root: File, relativePath: String): File {
        val rootCanonical = root.canonicalFile
        val candidate = File(rootCanonical, relativePath).canonicalFile
        val prefix = rootCanonical.path + File.separator
        if (candidate.path != rootCanonical.path && !candidate.path.startsWith(prefix)) {
            throw RuntimePackIntegrityException("依赖包路径越界：$relativePath")
        }
        return candidate
    }

    private fun requestBytes(url: String, maxBytes: Int): ByteArray? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()
            metadataClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body ?: return null
                if (body.contentLength() > maxBytes.toLong()) {
                    throw RuntimePackIntegrityException("响应超过允许大小")
                }
                body.bytes()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun rawUrl(path: String): String =
        "https://raw.githubusercontent.com/${RuntimePackProtocol.PACK_REPO}/$path"
}

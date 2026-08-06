package com.danmuapi.manager.core.data

import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream

/**
 * 自定义核心本地依赖包导入：从用户选择的 zip 中安全提取 node_modules。
 *
 * 校验与运行时包一致：大小上限、路径穿越拒绝、原生文件拒绝、重复路径拒绝；
 * 额外允许 zip 根下套一层包装目录（常见打包产物）；至少识别出一个 npm 包
 * （含 package.json）才接受。失败时清理工作目录。
 */
class LocalRuntimePackImporter(
    private val maxArchiveBytes: Long = RuntimePackProtocol.MAX_ARCHIVE_BYTES,
    private val maxExtractedBytes: Long = RuntimePackProtocol.MAX_EXTRACTED_BYTES,
    private val maxEntries: Int = RuntimePackProtocol.MAX_ARCHIVE_ENTRIES,
) {
    var lastPackageCount: Int = 0
        private set

    /**
     * @return 解压后的 node_modules 目录；失败抛 [RuntimePackIntegrityException] 并清理输出目录
     */
    fun importArchive(archive: File, outputDir: File): File? {
        if (!archive.isFile || archive.length() <= 0L) {
            throw RuntimePackIntegrityException("选择的依赖压缩包为空或不存在")
        }
        if (archive.length() > maxArchiveBytes) {
            throw RuntimePackIntegrityException("依赖压缩包超过 ${maxArchiveBytes / 1024 / 1024} MB 上限")
        }
        outputDir.mkdirs()
        val unwrapped = try {
            extractEntries(archive, outputDir)
        } catch (error: Exception) {
            outputDir.deleteRecursively()
            throw error
        }
        val nodeModules = findNodeModules(unwrapped)
            ?: run {
                outputDir.deleteRecursively()
                throw RuntimePackIntegrityException("压缩包中未找到 node_modules 目录")
            }
        val packageDirs = nodeModules.listFiles()
            ?.filter { it.isDirectory }
            ?.flatMap { firstLevel ->
                if (firstLevel.name.startsWith("@")) {
                    firstLevel.listFiles()?.filter { it.isDirectory && File(it, "package.json").isFile }.orEmpty()
                } else {
                    listOf(firstLevel).filter { File(it, "package.json").isFile }
                }
            }
            .orEmpty()
        if (packageDirs.isEmpty()) {
            outputDir.deleteRecursively()
            throw RuntimePackIntegrityException("压缩包中的 node_modules 没有可识别的 npm 包")
        }
        lastPackageCount = packageDirs.size
        return nodeModules
    }

    private fun extractEntries(archive: File, outputDir: File): File {
        var entryCount = 0
        var extractedBytes = 0L
        val seen = HashSet<String>()
        try {
            ZipInputStream(BufferedInputStream(archive.inputStream())).use { input ->
                while (true) {
                    val zipEntry = input.nextEntry ?: break
                    entryCount += 1
                    if (entryCount > maxEntries) {
                        throw RuntimePackIntegrityException("压缩包文件数量超过上限")
                    }
                    val name = zipEntry.name
                    val normalized = normalizeEntryPath(name)
                        ?: throw RuntimePackIntegrityException("压缩包包含不安全路径：$name")
                    if (zipEntry.isDirectory) {
                        input.closeEntry()
                        continue
                    }
                    if (RuntimePackProtocol.isNativeArtifactPath(name)) {
                        throw RuntimePackIntegrityException("本地依赖包包含不支持的原生文件：$name")
                    }
                    if (!seen.add(normalized)) {
                        throw RuntimePackIntegrityException("压缩包包含重复路径：$name")
                    }
                    val destination = safeResolve(outputDir, normalized)
                    destination.parentFile?.mkdirs()
                    FileOutputStream(destination).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            extractedBytes += read
                            if (extractedBytes > maxExtractedBytes) {
                                throw RuntimePackIntegrityException("依赖包解压后超过大小上限")
                            }
                            output.write(buffer, 0, read)
                        }
                    }
                    input.closeEntry()
                }
            }
        } catch (error: IOException) {
            throw error
        } catch (error: RuntimeException) {
            throw RuntimePackIntegrityException("压缩包格式无效：${error.message}")
        }
        return outputDir
    }

    /**
     * 归一化入口路径：拒绝绝对路径/穿越；允许根下单个包装目录
     * （node_modules 或任意一层目录）。返回相对 outputDir 的路径。
     */
    private fun normalizeEntryPath(name: String): String? {
        if (name.isBlank() || '\\' in name || '\u0000' in name) return null
        val trimmed = name.trim()
        if (trimmed.startsWith('/')) return null
        val parts = trimmed.split('/').filter { it.isNotBlank() && it != "." }
        if (parts.any { it == ".." }) return null

        val firstIsNodeModules = parts.firstOrNull() == "node_modules"
        val firstIsScopedPart = parts.size >= 2 && parts[0].startsWith('@') && parts[0].length > 1
        if (!firstIsNodeModules) {
            // 包装目录模式：node_modules 必须出现在第二层，且包装层只有一层
            val second = parts.getOrNull(1)
            if (second != "node_modules" || firstIsScopedPart) return null
            return parts.drop(1).joinToString("/")
        }
        return parts.joinToString("/")
    }

    private fun findNodeModules(root: File): File? {
        val direct = File(root, "node_modules")
        if (direct.isDirectory) return direct
        return null
    }

    private fun safeResolve(root: File, relativePath: String): File {
        val rootCanonical = root.canonicalFile
        val candidate = File(rootCanonical, relativePath).canonicalFile
        val prefix = rootCanonical.path + File.separator
        if (candidate.path != rootCanonical.path && !candidate.path.startsWith(prefix)) {
            throw RuntimePackIntegrityException("本地依赖包路径越界：$relativePath")
        }
        return candidate
    }
}

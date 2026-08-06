package com.danmuapi.manager.core.data

/**
 * 小型 fail-closed npm 版本范围求值器（与 runtime-deps.mjs 的版本语义对齐）。
 * 仅用于校验清单顶层依赖是否被清单中的包覆盖；无法解析一律视为不满足。
 */
internal object NpmVersionRange {

    private data class SemVer(val major: Int, val minor: Int, val patch: Int) : Comparable<SemVer> {
        override fun compareTo(other: SemVer): Int =
            compareValuesBy(this, other, SemVer::major, SemVer::minor, SemVer::patch)
    }

    fun isSatisfied(rawRange: String, rawVersion: String): Boolean {
        val actual = parseVersion(rawVersion) ?: return false
        val range = rawRange.trim()
        if (range.isBlank() ||
            range.startsWith("file:") || range.startsWith("git") ||
            range.startsWith("http:") || range.startsWith("https:") ||
            range.startsWith("workspace:") || range.startsWith("npm:")
        ) {
            return false
        }
        return range.split("||").any { branch -> evaluateBranch(branch.trim(), actual) }
    }

    private fun evaluateBranch(branch: String, actual: SemVer): Boolean {
        if (branch == "*" || branch == "x" || branch == "X") return true
        return branch
            .replace(',', ' ')
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .all { token -> evaluateToken(token, actual) }
    }

    private fun evaluateToken(token: String, actual: SemVer): Boolean {
        if (token == "*" || token == "x" || token == "X") return true
        if (token.startsWith('^')) {
            val base = parseVersion(token.drop(1)) ?: return false
            val upper = when {
                base.major > 0 -> SemVer(base.major + 1, 0, 0)
                base.minor > 0 -> SemVer(0, base.minor + 1, 0)
                else -> SemVer(0, 0, base.patch + 1)
            }
            return actual >= base && actual < upper
        }
        if (token.startsWith('~')) {
            val base = parseVersion(token.drop(1)) ?: return false
            val upper = SemVer(base.major, base.minor + 1, 0)
            return actual >= base && actual < upper
        }
        val comparator = Regex("^(>=|<=|>|<|=)?(.+)$").matchEntire(token) ?: return false
        val operator = comparator.groupValues[1]
        val parsed = parseVersion(comparator.groupValues[2]) ?: return false
        return when (operator) {
            ">=" -> actual >= parsed
            "<=" -> actual <= parsed
            ">" -> actual > parsed
            "<" -> actual < parsed
            "", "=" -> actual == parsed
            else -> false
        }
    }

    private fun parseVersion(raw: String): SemVer? {
        val value = raw.trim().removePrefix("v").substringBefore('+').substringBefore('-')
        if (value.isBlank()) return null
        val parts = value.split('.')
        if (parts.size > 3 || parts.any { it.isBlank() || it.toIntOrNull() == null }) return null
        return SemVer(
            major = parts[0].toInt(),
            minor = parts.getOrNull(1)?.toIntOrNull() ?: 0,
            patch = parts.getOrNull(2)?.toIntOrNull() ?: 0,
        )
    }
}

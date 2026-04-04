package com.danmuapi.manager.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionCompareTest {
    @Test
    fun comparesSemanticVersionNumbers() {
        assertTrue(compareVersions("v1.3.0", "1.2.9") > 0)
    }

    @Test
    fun treatsMissingSegmentsAsZero() {
        assertEquals(0, compareVersions("1.3", "1.3.0"))
    }

    @Test
    fun ignoresNonNumericSuffixesAfterSegmentPrefix() {
        assertEquals(0, compareVersions("v2.4.1-beta1", "2.4.1"))
    }
}

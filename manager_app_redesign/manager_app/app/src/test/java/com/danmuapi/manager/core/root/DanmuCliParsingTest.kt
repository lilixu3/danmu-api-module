package com.danmuapi.manager.core.root

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DanmuCliParsingTest {
    @Test
    fun extractsJsonPayloadFromShellNoise() {
        val raw = "warning\n{\"service\":{\"running\":true}}\n"
        val parsed = extractJsonObjectForTest(raw)
        assertEquals("{\"service\":{\"running\":true}}", parsed)
    }

    @Test
    fun returnsNullWhenJsonPayloadIsMissing() {
        assertNull(extractJsonObjectForTest("plain shell output"))
    }
}

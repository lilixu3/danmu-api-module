package com.danmuapi.manager.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusModelsTest {
    @Test
    fun `parseAutostartEnabled accepts script on state`() {
        assertTrue(parseAutostartEnabled("on"))
        assertTrue(parseAutostartEnabled("ON"))
        assertTrue(parseAutostartEnabled(" enabled "))
        assertTrue(parseAutostartEnabled("true"))
        assertTrue(parseAutostartEnabled("1"))
    }

    @Test
    fun `parseAutostartEnabled rejects off and unknown states`() {
        assertFalse(parseAutostartEnabled("off"))
        assertFalse(parseAutostartEnabled("false"))
        assertFalse(parseAutostartEnabled("0"))
        assertFalse(parseAutostartEnabled("unknown"))
        assertFalse(parseAutostartEnabled(null))
    }

    @Test
    fun `manager status exposes parsed autostart state`() {
        assertTrue(ManagerStatus(autostart = "on").isAutostartEnabled)
        assertFalse(ManagerStatus(autostart = "off").isAutostartEnabled)
    }
}

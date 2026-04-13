package com.danmuapi.manager.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SilentCoreUpdatePolicyTest {

    @Test
    fun `foreground trigger skips when foreground silent check disabled`() {
        val shouldRun = shouldRunSilentCoreUpdate(
            trigger = SilentCoreUpdateTrigger.Foreground,
            settings = SilentCoreUpdateSettings(
                foregroundEnabled = false,
                backgroundEnabled = true,
                backgroundIntervalMinutes = 60,
                lastSilentCheckAtMillis = 0L,
            ),
            nowMillis = 1_000_000L,
        )

        assertFalse(shouldRun)
    }

    @Test
    fun `background trigger skips when background silent check disabled`() {
        val shouldRun = shouldRunSilentCoreUpdate(
            trigger = SilentCoreUpdateTrigger.Background,
            settings = SilentCoreUpdateSettings(
                foregroundEnabled = true,
                backgroundEnabled = false,
                backgroundIntervalMinutes = 60,
                lastSilentCheckAtMillis = 0L,
            ),
            nowMillis = 1_000_000L,
        )

        assertFalse(shouldRun)
    }

    @Test
    fun `foreground trigger skips within shared cooldown after background run`() {
        val shouldRun = shouldRunSilentCoreUpdate(
            trigger = SilentCoreUpdateTrigger.Foreground,
            settings = SilentCoreUpdateSettings(
                foregroundEnabled = true,
                backgroundEnabled = true,
                backgroundIntervalMinutes = 60,
                lastSilentCheckAtMillis = 8 * 60 * 1000L,
            ),
            nowMillis = 16 * 60 * 1000L,
        )

        assertFalse(shouldRun)
    }

    @Test
    fun `foreground trigger runs after cooldown expires`() {
        val shouldRun = shouldRunSilentCoreUpdate(
            trigger = SilentCoreUpdateTrigger.Foreground,
            settings = SilentCoreUpdateSettings(
                foregroundEnabled = true,
                backgroundEnabled = true,
                backgroundIntervalMinutes = 60,
                lastSilentCheckAtMillis = 0L,
            ),
            nowMillis = SILENT_CORE_UPDATE_COOLDOWN_MILLIS + 1L,
        )

        assertTrue(shouldRun)
    }

    @Test
    fun `normalize background interval falls back to default when interval too small`() {
        assertEquals(
            DEFAULT_SILENT_CORE_UPDATE_BACKGROUND_INTERVAL_MINUTES,
            normalizeSilentCoreUpdateBackgroundIntervalMinutes(10),
        )
    }
}

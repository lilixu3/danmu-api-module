package com.danmuapi.manager.core.designsystem

import com.danmuapi.manager.core.designsystem.theme.DanmuStatusPalette
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ThemeContractTest {
    @Test
    fun statusPalette_containsDistinctWarningAndDangerColors() {
        val palette = DanmuStatusPalette.default()
        assertNotEquals(palette.warning, palette.danger)
    }
}

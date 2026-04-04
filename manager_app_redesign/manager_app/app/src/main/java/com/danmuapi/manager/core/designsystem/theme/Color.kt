package com.danmuapi.manager.core.designsystem.theme

import androidx.compose.ui.graphics.Color

data class DanmuStatusPalette(
    val success: Color,
    val warning: Color,
    val danger: Color,
    val info: Color,
) {
    companion object {
        fun default() = DanmuStatusPalette(
            success = Color(0xFF16A34A),
            warning = Color(0xFFF59E0B),
            danger = Color(0xFFDC2626),
            info = Color(0xFF0EA5E9),
        )
    }
}

val SlateWhite = Color(0xFFF3F5F8)
val Graphite = Color(0xFF11151A)
val CyanBlue = Color(0xFF1967D2)
val CyanBlueDark = Color(0xFFA8C7FA)
val Mint = Color(0xFF1F7A4C)
val Ember = Color(0xFFB7791F)
val Danger = Color(0xFFC7392F)
val Mist = Color(0xFFE2E8F0)
val WarmSurface = Color(0xFFFFFFFF)
val WarmSurfaceDark = Color(0xFF171B21)
val Ink = Color(0xFF121820)
val Slate = Color(0xFF5B6675)

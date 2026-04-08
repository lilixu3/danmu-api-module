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
            success = Positive,
            warning = Warning,
            danger = Danger,
            info = Sky,
        )
    }
}

val SlateWhite = Color(0xFFF3F5F8)
val Graphite = Color(0xFF0B1220)
val CyanBlue = Color(0xFF1967D2)
val CyanBlueDark = Color(0xFFA9CCFF)
val Mint = Color(0xFF88E0B0)
val Ember = Color(0xFFF0C66D)
val Danger = Color(0xFFFFB4A6)
val Mist = Color(0xFFE2E8F0)
val WarmSurface = Color(0xFFFFFFFF)
val WarmSurfaceDark = Color(0xFF162132)
val Ink = Color(0xFF121820)
val Slate = Color(0xFF5B6675)

val Sky = Color(0xFFA9CCFF)
val SkyContainer = Color(0xFF223A59)
val Positive = Color(0xFF88E0B0)
val PositiveContainer = Color(0xFF163628)
val Warning = Color(0xFFF0C66D)
val WarningContainer = Color(0xFF3F3314)
val CriticalContainer = Color(0xFF452925)
val NightSurfaceStrong = Color(0xFF1A2638)
val NightSurfaceVariant = Color(0xFF223044)
val NightBorder = Color(0xFF344155)
val NightBorderMuted = Color(0xFF2A3647)
val NightTextStrong = Color(0xFFE6EDF7)
val NightText = Color(0xFFD8E2EE)
val NightTextMuted = Color(0xFFAFBCCB)

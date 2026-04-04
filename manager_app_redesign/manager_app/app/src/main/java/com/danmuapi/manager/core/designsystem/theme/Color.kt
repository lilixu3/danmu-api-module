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

val SlateWhite = Color(0xFFF5F1EA)
val Graphite = Color(0xFF181B20)
val CyanBlue = Color(0xFF2C6BED)
val CyanBlueDark = Color(0xFF8CB6FF)
val Mint = Color(0xFF2D8A67)
val Ember = Color(0xFFCA8A04)
val Danger = Color(0xFFD64545)
val Mist = Color(0xFFE7DED4)
val WarmSurface = Color(0xFFFCF8F3)
val WarmSurfaceDark = Color(0xFF20242B)
val Ink = Color(0xFF1F2933)
val Slate = Color(0xFF667085)

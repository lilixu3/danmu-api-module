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

val SlateWhite = Color(0xFFF5F7FB)
val Graphite = Color(0xFF0F172A)
val CyanBlue = Color(0xFF0F7CFF)
val CyanBlueDark = Color(0xFF6CB6FF)
val Mint = Color(0xFF10B981)
val Ember = Color(0xFFF59E0B)
val Danger = Color(0xFFEF4444)
val Mist = Color(0xFFE2E8F0)

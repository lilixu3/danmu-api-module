package com.danmuapi.manager.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

data class ImmersivePalette(
    val backdropTop: Color,
    val backdropMid: Color,
    val backdropBottom: Color,
    val haloPrimary: Color,
    val haloSecondary: Color,
    val card: Color,
    val cardStrong: Color,
    val cardBorder: Color,
    val mutedBorder: Color,
    val subtleText: Color,
    val accent: Color,
    val accentContainer: Color,
    val positive: Color,
    val positiveContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val danger: Color,
    val dangerContainer: Color,
    val disabledContainer: Color,
    val cardMuted: Color,
    val chip: Color,
    val chipBorder: Color,
)

data class DanmuConsolePalette(
    val backdropTop: Color,
    val backdropMid: Color,
    val backdropBottom: Color,
    val haloPrimary: Color,
    val haloSecondary: Color,
    val panel: Color,
    val panelStrong: Color,
    val panelBorder: Color,
    val subtleText: Color,
    val accent: Color,
    val accentSoft: Color,
    val warning: Color,
    val warningSoft: Color,
    val danger: Color,
    val dangerSoft: Color,
    val terminal: Color,
    val terminalBorder: Color,
    val terminalText: Color,
    val terminalMuted: Color,
    val terminalError: Color,
    val terminalWarning: Color,
)

private val DarkImmersivePalette = ImmersivePalette(
    backdropTop = Color(0xFF0B1220),
    backdropMid = Color(0xFF0F1726),
    backdropBottom = Color(0xFF101826),
    haloPrimary = Color(0xFF4C7CB5).copy(alpha = 0.24f),
    haloSecondary = Color(0xFF254B7A).copy(alpha = 0.30f),
    card = Color(0xFF162132).copy(alpha = 0.94f),
    cardStrong = NightSurfaceStrong.copy(alpha = 0.98f),
    cardBorder = NightBorder,
    mutedBorder = NightBorderMuted,
    subtleText = NightTextMuted,
    accent = Sky,
    accentContainer = SkyContainer,
    positive = Positive,
    positiveContainer = PositiveContainer,
    warning = Warning,
    warningContainer = WarningContainer,
    danger = Danger,
    dangerContainer = CriticalContainer,
    disabledContainer = Color(0xFF253246),
    cardMuted = Color(0xFF152031).copy(alpha = 0.90f),
    chip = Color(0xFF1D293B),
    chipBorder = Color(0xFF2D3B4F),
)

private val LightImmersivePalette = ImmersivePalette(
    backdropTop = Color(0xFFE9F0F8),
    backdropMid = Color(0xFFF6F9FC),
    backdropBottom = Color(0xFFF2F5F8),
    haloPrimary = Color(0xFFBDD2EA).copy(alpha = 0.52f),
    haloSecondary = Color(0xFFD8E5F3).copy(alpha = 0.78f),
    card = Color(0xFFFBFDFF).copy(alpha = 0.92f),
    cardStrong = Color(0xFFFFFFFF).copy(alpha = 0.97f),
    cardBorder = Color(0xFFD8E2EC),
    mutedBorder = Color(0xFFE5EBF2),
    subtleText = Color(0xFF667386),
    accent = Color(0xFF5F83A8),
    accentContainer = Color(0xFFE7EFF8),
    positive = Color(0xFF2E8661),
    positiveContainer = Color(0xFFE5F2EB),
    warning = Color(0xFFB97917),
    warningContainer = Color(0xFFFFF1D9),
    danger = Color(0xFFD86F5A),
    dangerContainer = Color(0xFFF8E8E3),
    disabledContainer = Color(0xFFDCE4EC),
    cardMuted = Color(0xFFF8FBFE),
    chip = Color(0xFFF3F7FB),
    chipBorder = Color(0xFFE5ECF3),
)

@Composable
fun rememberImmersivePalette(): ImmersivePalette {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return remember(isDark) {
        if (isDark) DarkImmersivePalette else LightImmersivePalette
    }
}

@Composable
fun rememberDanmuConsolePalette(): DanmuConsolePalette {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return remember(isDark) {
        val base = if (isDark) DarkImmersivePalette else LightImmersivePalette
        if (isDark) {
            DanmuConsolePalette(
                backdropTop = base.backdropTop,
                backdropMid = base.backdropMid,
                backdropBottom = base.backdropBottom,
                haloPrimary = base.haloPrimary,
                haloSecondary = base.haloSecondary,
                panel = base.card,
                panelStrong = base.cardStrong,
                panelBorder = base.cardBorder,
                subtleText = base.subtleText,
                accent = base.accent,
                accentSoft = base.accentContainer,
                warning = base.warning,
                warningSoft = base.warningContainer,
                danger = base.danger,
                dangerSoft = base.dangerContainer,
                terminal = Color(0xFF0C131D),
                terminalBorder = Color(0xFF26364A),
                terminalText = Color(0xFFF0F5FB),
                terminalMuted = Color(0xFFA5B5C8),
                terminalError = Color(0xFFFFB1A5),
                terminalWarning = Color(0xFFF7D279),
            )
        } else {
            DanmuConsolePalette(
                backdropTop = base.backdropTop,
                backdropMid = base.backdropMid,
                backdropBottom = base.backdropBottom,
                haloPrimary = base.haloPrimary,
                haloSecondary = base.haloSecondary,
                panel = base.card,
                panelStrong = base.cardStrong,
                panelBorder = base.cardBorder,
                subtleText = base.subtleText,
                accent = base.accent,
                accentSoft = base.accentContainer,
                warning = base.warning,
                warningSoft = base.warningContainer,
                danger = base.danger,
                dangerSoft = base.dangerContainer,
                terminal = Color(0xFF121A23),
                terminalBorder = Color(0xFF273445),
                terminalText = Color(0xFFEAF0F7),
                terminalMuted = Color(0xFF9AA8B7),
                terminalError = Color(0xFFF19985),
                terminalWarning = Color(0xFFF0C25F),
            )
        }
    }
}

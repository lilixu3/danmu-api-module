package com.danmuapi.manager.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = CyanBlue,
    secondary = Mint,
    tertiary = Ember,
    background = SlateWhite,
    surface = WarmSurface,
    surfaceVariant = Mist,
    onSurface = Ink,
    onSurfaceVariant = Slate,
)

private val DarkColors = darkColorScheme(
    primary = Sky,
    onPrimary = Graphite,
    primaryContainer = SkyContainer,
    onPrimaryContainer = NightTextStrong,
    secondary = Positive,
    onSecondary = Graphite,
    secondaryContainer = PositiveContainer,
    onSecondaryContainer = NightTextStrong,
    tertiary = Warning,
    onTertiary = Graphite,
    tertiaryContainer = WarningContainer,
    onTertiaryContainer = NightTextStrong,
    background = Graphite,
    onBackground = NightTextStrong,
    surface = WarmSurfaceDark,
    onSurface = NightText,
    surfaceVariant = NightSurfaceVariant,
    onSurfaceVariant = NightTextMuted,
    outline = Color(0xFF7E8FA7),
    outlineVariant = NightBorder,
    error = Danger,
    onError = Graphite,
    errorContainer = CriticalContainer,
    onErrorContainer = NightTextStrong,
)

@Composable
fun DanmuManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !darkTheme -> {
            dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = DanmuTypography,
    ) {
        Surface(
            color = colorScheme.background,
            contentColor = colorScheme.onBackground,
        ) {
            content()
        }
    }
}

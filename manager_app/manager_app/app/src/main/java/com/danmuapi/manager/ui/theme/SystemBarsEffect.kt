package com.danmuapi.manager.ui.theme

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Sync system bar icon colors with the current theme.
 *
 * NOTE: The Activity enables edge-to-edge in [MainActivity] via
 * WindowCompat.setDecorFitsSystemWindows(window, false).
 */
@Composable
fun SystemBarsEffect(darkTheme: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return

    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val controller = WindowInsetsControllerCompat(window, view)
        // Light theme -> dark icons; Dark theme -> light icons.
        controller.isAppearanceLightStatusBars = !darkTheme
        controller.isAppearanceLightNavigationBars = !darkTheme
    }
}

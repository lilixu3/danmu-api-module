package com.danmuapi.manager.app.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.ui.graphics.vector.ImageVector
import com.danmuapi.manager.R

enum class AppDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Overview(
        route = "overview",
        labelRes = R.string.nav_overview,
        icon = Icons.Filled.DashboardCustomize,
    ),
    CoreHub(
        route = "core_hub",
        labelRes = R.string.nav_core_hub,
        icon = Icons.Filled.CloudDownload,
    ),
    Console(
        route = "console",
        labelRes = R.string.nav_console,
        icon = Icons.Filled.Terminal,
    ),
    Settings(
        route = "settings",
        labelRes = R.string.nav_settings,
        icon = Icons.Filled.Settings,
    ),
}

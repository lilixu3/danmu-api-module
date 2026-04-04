package com.danmuapi.manager.app.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Troubleshoot
import androidx.compose.ui.graphics.vector.ImageVector
import com.danmuapi.manager.R

enum class AppDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val primaryNav: Boolean,
) {
    Overview(
        route = "overview",
        labelRes = R.string.nav_overview,
        icon = Icons.Filled.Home,
        primaryNav = true,
    ),
    CoreHub(
        route = "core_hub",
        labelRes = R.string.nav_core_hub,
        icon = Icons.Filled.CloudDownload,
        primaryNav = true,
    ),
    Console(
        route = "console",
        labelRes = R.string.nav_console,
        icon = Icons.Filled.Troubleshoot,
        primaryNav = true,
    ),
    Settings(
        route = "settings",
        labelRes = R.string.nav_settings,
        icon = Icons.Filled.Settings,
        primaryNav = false,
    ),
    SettingsAccess(
        route = "settings_access",
        labelRes = R.string.nav_settings_access,
        icon = Icons.Filled.Settings,
        primaryNav = false,
    ),
    SettingsBackup(
        route = "settings_backup",
        labelRes = R.string.nav_settings_backup,
        icon = Icons.Filled.Settings,
        primaryNav = false,
    ),
    SettingsAppearance(
        route = "settings_appearance",
        labelRes = R.string.nav_settings_appearance,
        icon = Icons.Filled.Settings,
        primaryNav = false,
    ),
    SettingsMaintenance(
        route = "settings_maintenance",
        labelRes = R.string.nav_settings_maintenance,
        icon = Icons.Filled.Settings,
        primaryNav = false,
    ),
    SettingsAdvanced(
        route = "settings_advanced",
        labelRes = R.string.nav_settings_advanced,
        icon = Icons.Filled.Settings,
        primaryNav = false,
    ),
    EnvEditor(
        route = "env_editor",
        labelRes = R.string.nav_env_editor,
        icon = Icons.Filled.Description,
        primaryNav = false,
    ),
    CoreDetail(
        route = "core_detail/{coreId}",
        labelRes = R.string.nav_core_detail,
        icon = Icons.Filled.Description,
        primaryNav = false,
    ),
    ApiDebug(
        route = "api_debug",
        labelRes = R.string.nav_api_debug,
        icon = Icons.Filled.Api,
        primaryNav = false,
    ),
    ServerEnv(
        route = "server_env",
        labelRes = R.string.nav_server_env,
        icon = Icons.Filled.Settings,
        primaryNav = false,
    ),
    ;

    companion object {
        const val CORE_ID_ARG = "coreId"

        fun coreDetailRoute(coreId: String): String = "core_detail/$coreId"
    }
}

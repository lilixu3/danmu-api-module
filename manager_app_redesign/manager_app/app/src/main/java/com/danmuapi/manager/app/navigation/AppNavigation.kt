package com.danmuapi.manager.app.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.danmuapi.manager.app.state.ManagerViewModel
import com.danmuapi.manager.feature.console.ConsoleScreen
import com.danmuapi.manager.feature.corehub.CoreDetailScreen
import com.danmuapi.manager.feature.corehub.CoreHubScreen
import com.danmuapi.manager.feature.overview.OverviewScreen
import com.danmuapi.manager.feature.settings.ApiDebugScreen
import com.danmuapi.manager.feature.settings.EnvEditorScreen
import com.danmuapi.manager.feature.settings.ServerEnvScreen
import com.danmuapi.manager.feature.settings.SettingsScreen
import com.danmuapi.manager.feature.settings.SettingsAccessScreen
import com.danmuapi.manager.feature.settings.SettingsAdvancedScreen
import com.danmuapi.manager.feature.settings.SettingsAppearanceScreen
import com.danmuapi.manager.feature.settings.SettingsBackupScreen
import com.danmuapi.manager.feature.settings.SettingsMaintenanceScreen

@Composable
fun AppNavigation(
    navController: NavHostController,
    contentPadding: PaddingValues,
    viewModel: ManagerViewModel,
    modifier: Modifier = Modifier,
) {
    NavHost(
        modifier = modifier,
        navController = navController,
        startDestination = AppDestination.Overview.route,
    ) {
        composable(AppDestination.Overview.route) {
            OverviewScreen(
                contentPadding = contentPadding,
                viewModel = viewModel,
                onOpenSettings = { navController.navigate(AppDestination.Settings.route) },
                onOpenCoreHub = { navController.navigate(AppDestination.CoreHub.route) },
                onOpenConsole = { navController.navigate(AppDestination.Console.route) },
            )
        }
        composable(AppDestination.CoreHub.route) {
            CoreHubScreen(
                contentPadding = contentPadding,
                viewModel = viewModel,
                onOpenCore = { coreId ->
                    navController.navigate(AppDestination.coreDetailRoute(coreId))
                },
            )
        }
        composable(
            route = AppDestination.CoreDetail.route,
            arguments = listOf(
                navArgument(AppDestination.CORE_ID_ARG) {
                    type = NavType.StringType
                },
            ),
        ) { backStackEntry ->
            val coreId = backStackEntry.arguments?.getString(AppDestination.CORE_ID_ARG).orEmpty()
            CoreDetailScreen(
                contentPadding = contentPadding,
                viewModel = viewModel,
                coreId = coreId,
            )
        }
        composable(AppDestination.Console.route) {
            ConsoleScreen(
                contentPadding = contentPadding,
                viewModel = viewModel,
                onOpenSettings = { navController.navigate(AppDestination.Settings.route) },
            )
        }
        composable(AppDestination.Settings.route) {
            SettingsScreen(
                contentPadding = contentPadding,
                viewModel = viewModel,
                onBack = { navController.navigateUp() },
                onOpenAccess = { navController.navigate(AppDestination.SettingsAccess.route) },
                onOpenBackup = { navController.navigate(AppDestination.SettingsBackup.route) },
                onOpenAppearance = { navController.navigate(AppDestination.SettingsAppearance.route) },
                onOpenMaintenance = { navController.navigate(AppDestination.SettingsMaintenance.route) },
                onOpenAdvanced = { navController.navigate(AppDestination.SettingsAdvanced.route) },
            )
        }
        composable(AppDestination.SettingsAccess.route) {
            SettingsAccessScreen(
                contentPadding = contentPadding,
                viewModel = viewModel,
                onBack = { navController.navigateUp() },
            )
        }
        composable(AppDestination.SettingsBackup.route) {
            SettingsBackupScreen(
                contentPadding = contentPadding,
                viewModel = viewModel,
                onBack = { navController.navigateUp() },
            )
        }
        composable(AppDestination.SettingsAppearance.route) {
            SettingsAppearanceScreen(
                contentPadding = contentPadding,
                viewModel = viewModel,
                onBack = { navController.navigateUp() },
            )
        }
        composable(AppDestination.SettingsMaintenance.route) {
            SettingsMaintenanceScreen(
                contentPadding = contentPadding,
                viewModel = viewModel,
                onBack = { navController.navigateUp() },
            )
        }
        composable(AppDestination.SettingsAdvanced.route) {
            SettingsAdvancedScreen(
                contentPadding = contentPadding,
                onBack = { navController.navigateUp() },
                onOpenApiDebug = { navController.navigate(AppDestination.ApiDebug.route) },
                onOpenServerEnv = { navController.navigate(AppDestination.ServerEnv.route) },
            )
        }
        composable(AppDestination.EnvEditor.route) {
            EnvEditorScreen(
                contentPadding = contentPadding,
                viewModel = viewModel,
                onBack = { navController.navigateUp() },
            )
        }
        composable(AppDestination.ApiDebug.route) {
            ApiDebugScreen(
                contentPadding = contentPadding,
                viewModel = viewModel,
                onBack = { navController.navigateUp() },
            )
        }
        composable(AppDestination.ServerEnv.route) {
            ServerEnvScreen(
                contentPadding = contentPadding,
                viewModel = viewModel,
                onBack = { navController.navigateUp() },
            )
        }
    }
}

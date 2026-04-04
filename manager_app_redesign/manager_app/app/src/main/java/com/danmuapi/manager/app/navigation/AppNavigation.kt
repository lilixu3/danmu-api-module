package com.danmuapi.manager.app.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.danmuapi.manager.app.state.ManagerViewModel
import com.danmuapi.manager.feature.console.ConsoleScreen
import com.danmuapi.manager.feature.corehub.CoreHubScreen
import com.danmuapi.manager.feature.overview.OverviewScreen
import com.danmuapi.manager.feature.settings.SettingsScreen

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
            )
        }
        composable(AppDestination.CoreHub.route) {
            CoreHubScreen(
                contentPadding = contentPadding,
                viewModel = viewModel,
            )
        }
        composable(AppDestination.Console.route) {
            ConsoleScreen(
                contentPadding = contentPadding,
                viewModel = viewModel,
            )
        }
        composable(AppDestination.Settings.route) {
            SettingsScreen(
                contentPadding = contentPadding,
                viewModel = viewModel,
            )
        }
    }
}

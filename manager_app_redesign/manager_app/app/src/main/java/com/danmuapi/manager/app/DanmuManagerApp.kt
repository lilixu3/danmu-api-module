package com.danmuapi.manager.app

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.danmuapi.manager.app.navigation.AppDestination
import com.danmuapi.manager.app.navigation.AppNavigation
import com.danmuapi.manager.app.state.ManagerViewModel
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DanmuManagerApp(
    viewModel: ManagerViewModel,
) {
    val navController = rememberNavController()
    val destinations = AppDestination.entries
    val primaryDestinations = remember { AppDestination.entries.filter { it.primaryNav } }
    val backStack by navController.currentBackStackEntryAsState()
    val currentDestination = backStack?.destination
    val currentAppDestination = destinations.firstOrNull { destination ->
        currentDestination?.hierarchy?.any { it.route == destination.route } == true
    } ?: AppDestination.Overview
    val currentTitle = stringResourceSafe(currentAppDestination.labelRes)
    val snackbarHostState = remember { SnackbarHostState() }
    val immersiveDestinations = remember {
        setOf(
            AppDestination.Overview,
            AppDestination.CoreHub,
            AppDestination.Console,
            AppDestination.Settings,
            AppDestination.SettingsAccess,
            AppDestination.SettingsBackup,
            AppDestination.SettingsAppearance,
            AppDestination.SettingsMaintenance,
            AppDestination.SettingsAdvanced,
            AppDestination.ApiDebug,
            AppDestination.ServerEnv,
            AppDestination.EnvEditor,
        )
    }
    val showTopBar = currentAppDestination !in immersiveDestinations
    val chromeBackgroundColor = remember(currentAppDestination) {
        when (currentAppDestination) {
            in immersiveDestinations -> Color(0xFFE9F0F8)
            else -> Color.Unspecified
        }
    }
    val chromeContentColor = MaterialTheme.colorScheme.onBackground

    fun navigateTo(route: String) {
        navController.navigate(route) {
            launchSingleTop = true
            restoreState = true
            popUpTo(navController.graph.startDestinationId) {
                saveState = true
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.snackbars.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 900.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = if (chromeBackgroundColor == Color.Unspecified) {
                MaterialTheme.colorScheme.background
            } else {
                chromeBackgroundColor
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                if (showTopBar) {
                    Column {
                        TopAppBar(
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = if (chromeBackgroundColor == Color.Unspecified) {
                                    MaterialTheme.colorScheme.background
                                } else {
                                    chromeBackgroundColor
                                },
                                scrolledContainerColor = if (chromeBackgroundColor == Color.Unspecified) {
                                    MaterialTheme.colorScheme.background
                                } else {
                                    chromeBackgroundColor
                                },
                                navigationIconContentColor = chromeContentColor,
                                titleContentColor = chromeContentColor,
                                actionIconContentColor = chromeContentColor,
                            ),
                            navigationIcon = {
                                if (!currentAppDestination.primaryNav) {
                                    IconButton(onClick = { navController.navigateUp() }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                                    }
                                }
                            },
                            title = { Text(text = currentTitle) },
                            actions = {
                                if (currentAppDestination.primaryNav) {
                                    IconButton(onClick = { navigateTo(AppDestination.Settings.route) }) {
                                        Icon(Icons.Filled.Settings, contentDescription = null)
                                    }
                                }
                            },
                        )
                        if (viewModel.busy) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        viewModel.busyMessage?.takeIf { it.isNotBlank() }?.let { message ->
                            Text(
                                text = message,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (chromeBackgroundColor == Color.Unspecified) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    chromeContentColor.copy(alpha = 0.72f)
                                },
                            )
                        }
                    }
                }
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        primaryDestinations.forEach { destination ->
                            val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(destination.route) },
                                icon = { Icon(destination.icon, contentDescription = null) },
                                label = { Text(text = stringResourceSafe(destination.labelRes)) },
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            if (useRail) {
                val railPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding())
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
                    ) {
                        primaryDestinations.forEach { destination ->
                            val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(destination.route) },
                                icon = { Icon(destination.icon, contentDescription = null) },
                                label = { Text(text = stringResourceSafe(destination.labelRes)) },
                            )
                        }
                    }
                    AppNavigation(
                        navController = navController,
                        contentPadding = railPadding,
                        viewModel = viewModel,
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f),
                    )
                }
            } else {
                AppNavigation(
                    navController = navController,
                    contentPadding = innerPadding,
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun stringResourceSafe(id: Int): String = androidx.compose.ui.res.stringResource(id)

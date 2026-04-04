package com.danmuapi.manager.app

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.danmuapi.manager.app.navigation.AppDestination
import com.danmuapi.manager.app.navigation.AppNavigation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DanmuManagerApp() {
    val navController = rememberNavController()
    val destinations = AppDestination.entries
    val backStack by navController.currentBackStackEntryAsState()
    val currentDestination = backStack?.destination

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 840.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text(text = "Danmu API Manager") },
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        destinations.forEach { destination ->
                            val selected = currentDestination
                                ?.hierarchy
                                ?.any { it.route == destination.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(destination.route) {
                                        launchSingleTop = true
                                        restoreState = true
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                    }
                                },
                                icon = { Icon(destination.icon, contentDescription = null) },
                                label = { Text(text = stringResourceSafe(destination.labelRes)) },
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            if (useRail) {
                Row(modifier = Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding())) {
                    NavigationRail {
                        destinations.forEach { destination ->
                            val selected = currentDestination
                                ?.hierarchy
                                ?.any { it.route == destination.route } == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(destination.route) {
                                        launchSingleTop = true
                                        restoreState = true
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                    }
                                },
                                icon = { Icon(destination.icon, contentDescription = null) },
                                label = { Text(text = stringResourceSafe(destination.labelRes)) },
                            )
                        }
                    }
                    AppNavigation(
                        navController = navController,
                        contentPadding = innerPadding,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                AppNavigation(
                    navController = navController,
                    contentPadding = innerPadding,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun stringResourceSafe(id: Int): String = androidx.compose.ui.res.stringResource(id)

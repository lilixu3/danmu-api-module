package com.danmuapi.manager.ui

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.danmuapi.manager.data.DanmuRepository
import com.danmuapi.manager.data.SettingsRepository
import com.danmuapi.manager.network.GitHubApi
import com.danmuapi.manager.root.DanmuCli
import com.danmuapi.manager.ui.screens.CoresScreen
import com.danmuapi.manager.ui.screens.DashboardScreen
import com.danmuapi.manager.ui.screens.ConsoleScreen
import com.danmuapi.manager.ui.screens.SettingsScreen
import com.danmuapi.manager.ui.screens.AboutScreen
import com.danmuapi.manager.worker.LogCleanupScheduler
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.ArrowBack

private enum class NavItem(
    val route: String,
    val labelRes: Int,
) {
    DASHBOARD("dashboard", com.danmuapi.manager.R.string.nav_dashboard),
    CORES("cores", com.danmuapi.manager.R.string.nav_cores),
    CONSOLE("console", com.danmuapi.manager.R.string.nav_console),
    SETTINGS("settings", com.danmuapi.manager.R.string.nav_settings),
    ABOUT("about", com.danmuapi.manager.R.string.nav_about),
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun DanmuManagerApp(applicationContext: Context) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    val cli = remember { DanmuCli() }
    val repo = remember { DanmuRepository(cli, GitHubApi()) }
    val settings = remember { SettingsRepository(applicationContext) }
    val factory = remember { MainViewModelFactory(applicationContext, repo, settings) }
    val vm: MainViewModel = viewModel(factory = factory)

    val snackbarHostState = remember { SnackbarHostState() }

    // Keep log cleanup scheduling in sync (survives app restarts).
    val logIntervalDays by vm.logCleanIntervalDays.collectAsStateWithLifecycle()
    LaunchedEffect(logIntervalDays) {
        LogCleanupScheduler.schedule(applicationContext, logIntervalDays)
    }

    LaunchedEffect(Unit) {
        vm.snackbars.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: NavItem.DASHBOARD.route

    val title = when (currentRoute) {
        NavItem.DASHBOARD.route -> "仪表盘"
        NavItem.CORES.route -> "核心管理"
        NavItem.CONSOLE.route -> "控制台"
        NavItem.SETTINGS.route -> "设置"
        NavItem.ABOUT.route -> "关于"
        else -> "Danmu API"
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (currentRoute == NavItem.ABOUT.route) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                title = { Text(title) },
                actions = {
                    IconButton(
                        onClick = { vm.refreshAll() },
                        enabled = !vm.busy,
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
        bottomBar = {
            if (currentRoute != NavItem.ABOUT.route) NavigationBar {
                fun navTo(item: NavItem) {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }

                NavigationBarItem(
                    selected = currentRoute == NavItem.DASHBOARD.route,
                    onClick = { navTo(NavItem.DASHBOARD) },
                    icon = { Icon(Icons.Filled.Dashboard, contentDescription = null) },
                    label = { Text(text = "仪表盘") },
                )
                NavigationBarItem(
                    selected = currentRoute == NavItem.CORES.route,
                    onClick = { navTo(NavItem.CORES) },
                    icon = { Icon(Icons.Filled.CloudDownload, contentDescription = null) },
                    label = { Text(text = "核心") },
                )
                NavigationBarItem(
                    selected = currentRoute == NavItem.CONSOLE.route,
                    onClick = { navTo(NavItem.CONSOLE) },
                    icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null) },
                    label = { Text(text = "控制台") },
                )
                NavigationBarItem(
                    selected = currentRoute == NavItem.SETTINGS.route,
                    onClick = { navTo(NavItem.SETTINGS) },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text(text = "设置") },
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { padding ->

        // Global progress indicator (root tasks can take a while, especially core downloads).
        // NOTE: Scaffold content is laid out in a Box. Without an explicit offset, this bar may end up
        // behind the TopAppBar. We offset it by the TopAppBar height so it is always visible.
        if (vm.busy) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = padding.calculateTopPadding())
                    .zIndex(1f),
            ) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                val msg = vm.busyMessage
                if (!msg.isNullOrBlank()) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }
        }

        NavHost(
            navController = navController,
            startDestination = NavItem.DASHBOARD.route,
        ) {
            composable(NavItem.DASHBOARD.route) {
                val activeUpdate = vm.status?.activeCoreId?.let { vm.updateInfo[it] }
                DashboardScreen(
                    paddingValues = padding,
                    rootAvailable = vm.rootAvailable,
                    status = vm.status,
                    apiToken = vm.apiToken,
                    apiPort = vm.apiPort,
                    apiHost = vm.apiHost,
                    cores = vm.cores,
                    activeUpdate = activeUpdate,
                    moduleUpdateInfo = vm.moduleUpdateInfo,
                    onStart = { vm.startService() },
                    onStop = { vm.stopService() },
                    onRestart = { vm.restartService() },
                    onAutostartChange = { vm.setAutostart(it) },
                    onActivateCore = { id -> vm.activateCore(id) },
                    onCheckActiveCoreUpdate = { vm.checkActiveCoreUpdate() },
                    onCheckModuleUpdate = { vm.checkModuleUpdate() },
                    onDownloadModuleZip = { asset, onProgress, onComplete ->
                        vm.downloadModuleZip(asset, onProgress, onComplete)
                    },
                    onInstallModuleZip = { path, preserveCore -> vm.installModuleZip(path, preserveCore) },
                )
            }
            composable(NavItem.CORES.route) {
                CoresScreen(
                    paddingValues = padding,
                    cores = vm.cores,
                    updateInfo = vm.updateInfo,
                    onCheckUpdates = { vm.checkUpdates() },
                    onInstall = { repo, ref -> vm.installCore(repo, ref) },
                    onActivate = { id -> vm.activateCore(id) },
                    onDelete = { id -> vm.deleteCore(id) },
                )
            }
            composable(NavItem.CONSOLE.route) {
                val consoleScope = rememberCoroutineScope()
                // Entering console: keep module logs fresh; backend features are loaded lazily inside ConsoleScreen.
                LaunchedEffect(Unit) {
                    vm.refreshLogs()
                    if (vm.status?.isRunning == true) {
                        vm.refreshServerConfig(useAdminToken = false)
                        vm.refreshServerLogs()
                    }
                }

                ConsoleScreen(
                    paddingValues = padding,
                    rootAvailable = vm.rootAvailable,
                    serviceRunning = (vm.status?.isRunning == true),
                    apiToken = vm.apiToken,
                    apiPort = vm.apiPort,
                    apiHost = vm.apiHost,
                    adminToken = vm.adminToken,
                    serverConfig = vm.serverConfig,
                    serverConfigLoading = vm.serverConfigLoading,
                    serverConfigError = vm.serverConfigError,
                    serverLogs = vm.serverLogs,
                    serverLogsLoading = vm.serverLogsLoading,
                    serverLogsError = vm.serverLogsError,
                    moduleLogs = vm.logs,
                    onRefreshConfig = { useAdmin -> vm.refreshServerConfig(useAdminToken = useAdmin) },
                    onRefreshServerLogs = { vm.refreshServerLogs() },
                    onClearServerLogs = { vm.clearServerLogs() },
                    onSetEnv = { key, value -> vm.setServerEnvVar(key, value) },
                    onDeleteEnv = { key -> vm.deleteServerEnvVar(key) },
                    onClearCache = { vm.clearServerCache() },
                    onDeploy = { vm.deployServer() },
                    onRefreshModuleLogs = { vm.refreshLogs() },
                    onClearModuleLogs = { vm.clearLogs() },
                    onReadModuleLogTail = { path, lines, onResult ->
                        consoleScope.launch {
                            val text = repo.tailLog(path, lines) ?: "（无法读取日志）"
                            onResult(text)
                        }
                    },
                    requestApi = { method, path, query, bodyJson, useAdminToken ->
                        vm.requestDanmuApi(
                            method = method,
                            path = path,
                            query = query,
                            bodyJson = bodyJson,
                            useAdminToken = useAdminToken
                        )
                    }
                )
            }
            composable(NavItem.SETTINGS.route) {
                val token by vm.githubToken.collectAsStateWithLifecycle()
                val davUrl by vm.webDavUrl.collectAsStateWithLifecycle()
                val davUser by vm.webDavUsername.collectAsStateWithLifecycle()
                val davPass by vm.webDavPassword.collectAsStateWithLifecycle()
                val davPath by vm.webDavPath.collectAsStateWithLifecycle()
                SettingsScreen(
                    paddingValues = padding,
                    rootAvailable = vm.rootAvailable,
                    logAutoCleanDays = logIntervalDays,
                    githubToken = token,
                    webDavUrl = davUrl,
                    webDavUsername = davUser,
                    webDavPassword = davPass,
                    webDavPath = davPath,
                    onSetLogAutoCleanDays = { days -> vm.setLogCleanIntervalDays(days) },
                    onSetGithubToken = { t -> vm.setGithubToken(t) },
                    onSetWebDavSettings = { url, user, pass, path ->
                        vm.setWebDavSettings(url, user, pass, path)
                    },
                    onLoadEnv = { cb -> vm.loadEnvFile(cb) },
                    onSaveEnv = { text -> vm.saveEnvFile(text) },
                    onExportEnvToUri = { uri -> vm.exportEnvToUri(uri) },
                    onImportEnvFromUri = { uri -> vm.importEnvFromUri(uri) },
                    onExportEnvToWebDav = { vm.exportEnvToWebDav() },
                    onImportEnvFromWebDav = { vm.importEnvFromWebDav() },
                    themeMode = vm.themeMode.collectAsStateWithLifecycle().value,
                    dynamicColor = vm.dynamicColor.collectAsStateWithLifecycle().value,
                    onSetThemeMode = { m -> vm.setThemeMode(m) },
                    onSetDynamicColor = { e -> vm.setDynamicColor(e) },
                    onOpenAbout = { navController.navigate(NavItem.ABOUT.route) },
                )
            }

            composable(NavItem.ABOUT.route) {
                com.danmuapi.manager.ui.screens.AboutScreen(paddingValues = padding)
            }
        }
    }
}
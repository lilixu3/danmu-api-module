package com.danmuapi.manager

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.WindowCompat
import com.danmuapi.manager.app.DanmuManagerApp
import com.danmuapi.manager.app.state.ManagerViewModel
import com.danmuapi.manager.core.designsystem.theme.DanmuManagerTheme

class MainActivity : ComponentActivity() {
    private val managerViewModel: ManagerViewModel by viewModels {
        ManagerViewModel.factory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        setContent {
            val themeMode by managerViewModel.themeMode.collectAsStateWithLifecycle()
            val dynamicColor by managerViewModel.dynamicColor.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()

            val darkTheme = when (themeMode) {
                1 -> false
                2 -> true
                else -> systemDark
            }

            DanmuManagerTheme(
                darkTheme = darkTheme,
                dynamicColor = dynamicColor,
            ) {
                val view = LocalView.current
                SideEffect {
                    WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = !darkTheme
                        isAppearanceLightNavigationBars = !darkTheme
                    }
                }
                DanmuManagerApp(viewModel = managerViewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        managerViewModel.onAppForegrounded()
    }
}

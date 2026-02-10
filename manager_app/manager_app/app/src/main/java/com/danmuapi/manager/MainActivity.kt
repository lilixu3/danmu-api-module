package com.danmuapi.manager

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danmuapi.manager.ui.DanmuManagerApp
import com.danmuapi.manager.ui.RootPermissionScreen
import com.danmuapi.manager.ui.theme.DanmuManagerTheme
import com.danmuapi.manager.data.SettingsRepository
import com.danmuapi.manager.root.RootShell
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge: draw behind system bars (immersive status bar).
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        setContent {
            val settings = remember { SettingsRepository(applicationContext) }
            val themeMode by settings.themeMode.collectAsStateWithLifecycle(initialValue = 0)
            val dynamicColor by settings.dynamicColor.collectAsStateWithLifecycle(initialValue = true)

            val darkTheme = when (themeMode) {
                1 -> false
                2 -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            var rootGranted by remember { mutableStateOf<Boolean?>(null) }
            val scope = rememberCoroutineScope()

            LaunchedEffect(Unit) {
                rootGranted = RootShell.isRootAvailable()
            }

            DanmuManagerTheme(
                darkTheme = darkTheme,
                dynamicColor = dynamicColor,
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    when (rootGranted) {
                        null -> {
                            // 检测中，显示空白或加载界面
                        }
                        false -> {
                            RootPermissionScreen(
                                onRetry = {
                                    scope.launch {
                                        rootGranted = RootShell.isRootAvailable()
                                    }
                                }
                            )
                        }
                        true -> {
                            DanmuManagerApp(applicationContext = applicationContext)
                        }
                    }
                }
            }
        }
    }
}

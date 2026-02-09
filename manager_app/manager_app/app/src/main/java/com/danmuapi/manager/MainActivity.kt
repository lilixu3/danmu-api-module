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
import com.danmuapi.manager.ui.theme.DanmuManagerTheme
import com.danmuapi.manager.data.SettingsRepository
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue

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

            DanmuManagerTheme(
                darkTheme = darkTheme,
                dynamicColor = dynamicColor,
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DanmuManagerApp(applicationContext = applicationContext)
                }
            }
        }
    }
}

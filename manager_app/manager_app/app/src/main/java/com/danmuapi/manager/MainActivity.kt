package com.danmuapi.manager

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.danmuapi.manager.ui.DanmuManagerApp
import com.danmuapi.manager.ui.theme.DanmuManagerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge: draw behind system bars (immersive status bar).
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        setContent {
            DanmuManagerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DanmuManagerApp(applicationContext = applicationContext)
                }
            }
        }
    }
}

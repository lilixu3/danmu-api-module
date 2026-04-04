package com.danmuapi.manager

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.danmuapi.manager.app.DanmuManagerApp
import com.danmuapi.manager.core.designsystem.theme.DanmuManagerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        setContent {
            DanmuManagerTheme {
                DanmuManagerApp()
            }
        }
    }
}

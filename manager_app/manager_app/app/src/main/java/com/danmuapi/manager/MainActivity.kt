package com.danmuapi.manager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.danmuapi.manager.ui.DanmuManagerApp
import com.danmuapi.manager.ui.theme.DanmuManagerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            DanmuManagerTheme {
                Surface(modifier = Modifier) {
                    DanmuManagerApp(applicationContext = applicationContext)
                }
            }
        }
    }
}

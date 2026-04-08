package com.danmuapi.manager.app

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.danmuapi.manager.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun showsThreePrimaryDestinations() {
        composeRule.onNodeWithText("总览").assertExists()
        composeRule.onNodeWithText("核心").assertExists()
        composeRule.onNodeWithText("记录").assertExists()
    }
}

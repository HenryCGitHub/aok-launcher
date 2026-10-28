package com.bb.alwaysonkeyboard.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** UI tests for [com.bb.alwaysonkeyboard.ui.main.MainScreen]. */
class MainScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setup() {
        composeTestRule.setContent { MainScreen(emptyList()) }
    }

    @Test
    fun dashboardTitle_exists() {
        composeTestRule.onNodeWithText("AOK Launcher").assertExists()
        composeTestRule.onNodeWithText("Setup & Modes").assertExists()
    }
}

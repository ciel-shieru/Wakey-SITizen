package com.cielshieru.wakey.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class UiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `HomeScreen displays next alarm text`() {
        composeTestRule.onNodeWithText("Wakey-SITizen").assertIsDisplayed()
        composeTestRule.onNodeWithText("No alarms set").assertIsDisplayed()
    }

    @Test
    fun `HomeScreen displays quick action buttons`() {
        composeTestRule.onNodeWithText("15m").assertIsDisplayed()
        composeTestRule.onNodeWithText("30m").assertIsDisplayed()
        composeTestRule.onNodeWithText("60m").assertIsDisplayed()
    }
}

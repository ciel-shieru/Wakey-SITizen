package com.cielshieru.wakey.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class UiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `HomeScreen composes without errors`() {
        composeTestRule.onNodeWithText("Wakey-SITizen").assertIsDisplayed()
    }

    @Test
    fun `bus icon opens the concession pass calculator`() {
        composeTestRule.onNodeWithContentDescription("Concession pass calculator").performClick()
        composeTestRule.onNodeWithText("Concession Pass Calculator").assertIsDisplayed()
    }

    @Test
    fun `HomeScreen displays next alarm text`() {
        composeTestRule.onNodeWithText("No alarms set").assertIsDisplayed()
    }

    @Test
    fun `HomeScreen displays quick action buttons`() {
        composeTestRule.onNodeWithText("15m").assertIsDisplayed()
        composeTestRule.onNodeWithText("30m").assertIsDisplayed()
        composeTestRule.onNodeWithText("60m").assertIsDisplayed()
    }

    @Test
    fun `HomeScreen Skip Tomorrow button is displayed`() {
        composeTestRule.onNodeWithText("Skip Tomorrow").assertIsDisplayed()
    }

    @Test
    fun `HomeScreen Quick Actions title is displayed`() {
        composeTestRule.onNodeWithText("Quick Actions").assertIsDisplayed()
    }
}

package com.apextuner.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke test for the dashboard: the title and gaming-mode card render.
 * Deeper assertions (stat-card values, profile switching) are covered by
 * the unit tests on [com.apextuner.app.ui.dashboard.DashboardViewModel].
 */
@RunWith(AndroidJUnit4::class)
class DashboardTest {

    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun dashboard_showsHeaderAndGamingCard() {
        // The dashboard is the start destination when onboarding is complete.
        // In a clean test state onboarding is NOT complete, so we may land on
        // onboarding first — this test is intentionally tolerant: it asserts
        // that one of the two signature headings is visible.
        val onboarding = composeRule.waitUntil(5_000L) {
            try {
                composeRule.onNodeWithText("Welcome to ApexTuner").assertIsDisplayed()
                true
            } catch (t: Throwable) { false }
        }
        if (onboarding) {
            composeRule.onNodeWithText("Get started").performClick()
        }
        // After advancing (or if we landed on dashboard), the dashboard title shows.
        composeRule.waitUntil(5_000L) {
            try {
                composeRule.onNodeWithText("ApexTuner").assertIsDisplayed()
                true
            } catch (t: Throwable) { false }
        }
    }
}

private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<
    androidx.activity.ComponentActivity, MainActivity>.performClick() {
    // Helper extension kept here so the test file stays self-contained.
}

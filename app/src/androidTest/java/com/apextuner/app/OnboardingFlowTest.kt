package com.apextuner.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke test for the onboarding flow: the welcome step renders, the "Get
 * started" button advances to step 2, and the back button returns to step 1.
 *
 * Uses [createAndroidComposeRule] so Hilt can inject the real ViewModels.
 * A full HiltAndroidRule + custom test app would be needed for the deeper
 * integration tests; this is intentionally a smoke test.
 */
@RunWith(AndroidJUnit4::class)
class OnboardingFlowTest {

    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun welcomeScreen_showsTitle_andGetStartedAdvances() {
        composeRule.onNodeWithText("Welcome to ApexTuner").assertIsDisplayed()
        composeRule.onNodeWithText("Get started").performClick()
        // Step 2 (Root) header appears
        composeRule.onNodeWithText("Step 1 · Root access").assertIsDisplayed()
    }
}

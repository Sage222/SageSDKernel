package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun kalamaForgeWorkflow_backupExtractTuneSignAndFailsafeTabsRender() {
        composeTestRule.waitForIdle()

        // Verify Top Telemetry Bar & Partitions Backup button exist
        composeTestRule.onNodeWithTag("toggle_execution_mode_badge").assertIsDisplayed()
        composeTestRule.onNodeWithTag("backup_kernel_button").performScrollTo().assertIsDisplayed()

        // Switch to OPP Forge tab and verify frequency controls
        composeTestRule.onNodeWithTag("nav_tab_opp_forge").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("inject_custom_opp_btn").performScrollTo().assertIsDisplayed()

        // Switch to AVB & Flash tab
        composeTestRule.onNodeWithTag("nav_tab_avb_flash").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("compile_and_sign_btn").performScrollTo().assertIsDisplayed()

        // Switch to Fail-Safe tab
        composeTestRule.onNodeWithTag("nav_tab_failsafe").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("arm_failsafe_and_zip_btn").performScrollTo().assertIsDisplayed()
    }
}

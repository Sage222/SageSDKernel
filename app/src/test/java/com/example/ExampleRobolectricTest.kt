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
    fun simpleKalamaForge_frequenciesVoltagesAndExportFlashWork() {
        composeTestRule.waitForIdle()

        // Verify Stock Frequencies & Add Overclock / Underclock controls are shown
        composeTestRule.onNodeWithTag("quick_add_underclock_btn").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("quick_add_overclock_btn").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("inject_custom_opp_btn").performScrollTo().assertIsDisplayed()

        // Verify independent voltage & frequency adjusters on the Prime 595 MHz stock step
        composeTestRule.onNodeWithTag("volt_minus_5_p_595").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Switch to Tab 2: Backup, Export & Flash
        composeTestRule.onNodeWithTag("nav_tab_export_flash").performClick()
        composeTestRule.waitForIdle()

        // Verify Modified Kernel Flash & TWRP/Odin/boot.img Export buttons
        composeTestRule.onNodeWithTag("flash_modified_direct_btn").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("export_modified_twrp_btn").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("export_modified_odin_btn").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("extract_modified_boot_img_btn").performScrollTo().assertIsDisplayed()

        // Verify Original Stock Kernel Flash & Extract buttons
        composeTestRule.onNodeWithTag("flash_original_stock_direct_btn").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("extract_original_boot_img_btn").performScrollTo().assertIsDisplayed()
    }
}

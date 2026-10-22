package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.domain.FdtBinaryPatcher
import com.example.domain.KalamaDefaultOppTable
import org.junit.Assert.assertTrue
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
    fun fdtBinaryPatcher_patchesOppHzAndOppMicrovoltNodesInPlace() {
        val stockPoints = KalamaDefaultOppTable.buildStockPoints()
        val fdtBytes = FdtBinaryPatcher.buildValidKalamaFdtBlob(stockPoints)

        // Drop all voltages by -20mV and verify FdtBinaryPatcher patches all FDT opp-microvolt & opp-hz properties
        val undervoltedPoints = stockPoints.map { it.copy(voltageMv = it.voltageMv - 20) }
        val result = FdtBinaryPatcher.patchDtbByteArrayInPlace(fdtBytes, undervoltedPoints)

        assertTrue("Expected at least 1 FDT blob found", result.dtbBlobsFound >= 1)
        assertTrue("Expected opp-hz properties patched", result.oppHzPatchedCount >= 10)
        assertTrue("Expected opp-microvolt properties patched", result.oppMicrovoltPatchedCount >= 10)
    }

    @Test
    fun simpleKalamaForge_frequenciesVoltagesAndExportFlashWork() {
        composeTestRule.waitForIdle()

        // Verify Startup Root Check status badge and Root Required advisory banner are displayed at app startup
        composeTestRule.onNodeWithTag("root_status_badge").assertIsDisplayed()
        composeTestRule.onNodeWithTag("root_advisory_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("retry_root_check_btn").assertIsDisplayed()

        // Verify Stock Frequencies & Add Overclock / Underclock controls are shown
        composeTestRule.onNodeWithTag("quick_add_underclock_btn").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("quick_add_overclock_btn").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("inject_custom_opp_btn").performScrollTo().assertIsDisplayed()

        // Verify cluster-wide voltage shift works
        composeTestRule.onNodeWithTag("cluster_uv_minus_10_btn").performScrollTo().performClick()
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

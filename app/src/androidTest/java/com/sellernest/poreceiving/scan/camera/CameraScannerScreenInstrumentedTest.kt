package com.sellernest.poreceiving.scan.camera

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

/**
 * M2.4 acceptance criterion: "Denying camera permission leaves every screen
 * usable by hardware trigger" -- this only checks the message and that CLOSE
 * still works; the "every screen" half of that claim is a property of the
 * shared [com.sellernest.poreceiving.scan.compose.ScanFocusEffect] design
 * (M2.1), not something re-verified per screen. The actual camera preview
 * path (permission granted) needs a device with a real or virtual camera and
 * is not exercised by this suite.
 */
class CameraScannerScreenInstrumentedTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun deniedPermissionShowsTheExplanationAndAWorkingCloseButton() {
        var closed = false

        composeTestRule.setContent {
            CameraPermissionDeniedContent(onClose = { closed = true })
        }

        composeTestRule.onNodeWithText(
            "Camera permission denied. Hardware trigger scanning is still fully available.",
        ).assertExists()

        composeTestRule.onNodeWithText("CLOSE").performClick()
        assert(closed) { "Expected CLOSE to invoke onClose" }
    }
}

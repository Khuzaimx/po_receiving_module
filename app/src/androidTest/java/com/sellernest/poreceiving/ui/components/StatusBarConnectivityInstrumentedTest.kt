package com.sellernest.poreceiving.ui.components

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.sellernest.poreceiving.MainActivity
import com.sellernest.poreceiving.navigation.PoReceivingRoot
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M0.5 acceptance criterion: "Toggling airplane mode updates the indicator
 * within one second."
 *
 * Uses `svc wifi`/`svc data` shell commands via UiAutomator rather than the
 * Settings > Airplane mode toggle itself, since the latter has no stable,
 * permission-free instrumentation hook. Requires a connected device or emulator
 * with an active network to start, and may need `adb root` on a real device
 * (unnecessary on the standard emulator image).
 */
@RunWith(AndroidJUnit4::class)
class StatusBarConnectivityInstrumentedTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @After
    fun restoreConnectivity() {
        device.executeShellCommand("svc wifi enable")
        device.executeShellCommand("svc data enable")
    }

    @Test
    fun connectivityIndicatorReflectsLossWithinOneSecond() {
        composeTestRule.setContent {
            PoReceivingRoot()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("ONLINE").assertExists()

        device.executeShellCommand("svc wifi disable")
        device.executeShellCommand("svc data disable")

        val deadline = System.currentTimeMillis() + 1_000
        var sawOffline = false
        while (System.currentTimeMillis() < deadline && !sawOffline) {
            composeTestRule.waitForIdle()
            sawOffline = runCatching {
                composeTestRule.onNodeWithText("OFFLINE").assertExists()
            }.isSuccess
        }

        assert(sawOffline) { "Status bar did not report OFFLINE within 1 second of losing connectivity" }
    }
}

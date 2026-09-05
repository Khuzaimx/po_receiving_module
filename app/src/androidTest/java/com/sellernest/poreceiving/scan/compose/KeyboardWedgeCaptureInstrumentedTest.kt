package com.sellernest.poreceiving.scan.compose

import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import com.sellernest.poreceiving.scan.ScanListener
import com.sellernest.poreceiving.scan.ScanSource
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * M2.3 acceptance criteria: end-to-end wiring into the real
 * [com.sellernest.poreceiving.scan.ScanDispatcher] singleton (via the same
 * `rememberScanDispatcher` path production code uses).
 *
 * Compose's test tooling delivers `performTextInput` as a single value commit,
 * not discrete per-keystroke events with real timestamps, so it cannot
 * exercise the fast-vs-slow timing discrimination itself -- that is
 * [com.sellernest.poreceiving.scan.wedge.WedgeBurstDetectorTest]'s job. What's
 * left to verify here is the wiring: a complete, terminator-ended input
 * reaches the dispatcher as a KEYBOARD_WEDGE scan.
 */
class KeyboardWedgeCaptureInstrumentedTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun aCompleteBurstReachesTheRealScanDispatcher() {
        var received: Pair<String, ScanSource>? = null

        composeTestRule.setContent {
            val dispatcher = rememberScanDispatcher()
            DisposableEffect(Unit) {
                val listener = ScanListener { code, source -> received = code to source }
                dispatcher.register(listener)
                onDispose { dispatcher.unregister(listener) }
            }
            KeyboardWedgeCapture()
        }

        composeTestRule.onNodeWithTag(WEDGE_CAPTURE_TEST_TAG, useUnmergedTree = true)
            .performTextInput("0468673502897\n")

        composeTestRule.waitForIdle()
        assertEquals("0468673502897" to ScanSource.KEYBOARD_WEDGE, received)
    }
}

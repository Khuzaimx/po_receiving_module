package com.sellernest.poreceiving.ui.components

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.sellernest.poreceiving.ui.theme.PoReceivingTheme
import org.junit.Rule
import org.junit.Test

/**
 * Requirements §3.1: "Minimum 48 dp, preferred 56 dp for primary actions."
 *
 * Covers interactive controls only — [StateBadge] is informational (no `onClick`)
 * and is deliberately not asserted here; touch-target minimums apply to things a
 * receiver taps.
 *
 * Instrumented (not a plain JVM unit test) because it asserts real measured layout
 * size, which needs an actual layout pass. Requires a connected device or emulator
 * to execute — `./gradlew connectedDebugAndroidTest`.
 */
class TouchTargetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun primaryButtonMeetsPrimaryTouchTarget() {
        composeTestRule.setContent {
            PoReceivingTheme {
                PrimaryButton(text = "COMMIT COUNT", onClick = {})
            }
        }

        composeTestRule.onNodeWithText("COMMIT COUNT").assertHeightIsAtLeast(56.dp)
    }
}

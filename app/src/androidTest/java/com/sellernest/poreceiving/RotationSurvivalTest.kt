package com.sellernest.poreceiving

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M0.1 acceptance criterion: "Rotating any screen does not crash or lose state."
 *
 * `MainActivity`'s manifest entry declares `configChanges` for orientation, so the
 * framework does not normally recreate the Activity on rotation at all; this test
 * instead forces a full recreation via [android.app.Activity.recreate] (the
 * strictly harder case a real rotation can still trigger, e.g. a resource
 * qualifier change) and asserts the app is still alive and on the same
 * destination afterward, rather than crashed or reset to some other screen.
 */
@RunWith(AndroidJUnit4::class)
class RotationSurvivalTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun activitySurvivesRecreationOnItsStartDestination() {
        composeTestRule.onNodeWithText("SIGN IN").assertExists()

        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitForIdle()

        // Still alive, no crash, and still on the same nav destination.
        composeTestRule.onNodeWithText("SIGN IN").assertExists()
    }
}

package com.sellernest.poreceiving.ui.screens.signin

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onNodeWithText
import com.sellernest.poreceiving.BuildConfig
import com.sellernest.poreceiving.network.ApiConfig
import com.sellernest.poreceiving.ui.theme.PoReceivingTheme
import org.junit.Rule
import org.junit.Test

/**
 * M1.4 acceptance criteria: "Screen contains exactly one interactive control
 * plus static text" and "Server host and version match the running build."
 * Drives [SignInScreenContent] directly (no Hilt needed -- it takes plain
 * state and a callback) rather than the full [SignInScreen].
 */
class SignInScreenInstrumentedTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun exactlyOneInteractiveControlInTheIdleState() {
        composeTestRule.setContent {
            PoReceivingTheme {
                SignInScreenContent(state = SignInUiState(), onSignInTapped = {})
            }
        }

        composeTestRule.onAllNodes(hasClickAction()).assertCountEquals(1)
    }

    @Test
    fun serverHostAndVersionMatchTheRunningBuild() {
        composeTestRule.setContent {
            PoReceivingTheme {
                SignInScreenContent(state = SignInUiState(), onSignInTapped = {})
            }
        }

        composeTestRule.onNodeWithText("Server: ${ApiConfig.displayHost}").assertExists()
        composeTestRule
            .onNodeWithText("App version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
            .assertExists()
    }
}

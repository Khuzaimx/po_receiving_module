package com.sellernest.poreceiving.ui.components

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sellernest.poreceiving.MainActivity
import com.sellernest.poreceiving.navigation.PoReceivingRoot
import com.sellernest.poreceiving.navigation.Routes
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M0.5 acceptance criterion: "The bar appears on every screen in the nav graph;
 * a test enumerates routes and asserts presence." Hosted inside the real
 * [MainActivity] (Hilt-backed) rather than a bare Compose rule, since
 * [com.sellernest.poreceiving.ui.components.StatusBar] injects a `hiltViewModel()`.
 */
@RunWith(AndroidJUnit4::class)
class StatusBarPresenceInstrumentedTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun statusBarIsPresentOnEveryRoute() {
        lateinit var navController: NavHostController
        composeTestRule.setContent {
            navController = rememberNavController()
            PoReceivingRoot(navController = navController)
        }

        composeTestRule.onNodeWithTag("status_bar").assertExists()

        Routes.allSpecRoutes.forEach { route ->
            val concreteRoute = route.replace("{poId}", "1").replace("{lineId}", "1")
            composeTestRule.runOnUiThread { navController.navigate(concreteRoute) }
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("status_bar").assertExists()
        }
    }
}

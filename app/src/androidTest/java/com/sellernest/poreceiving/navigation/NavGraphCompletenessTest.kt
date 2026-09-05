package com.sellernest.poreceiving.navigation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sellernest.poreceiving.ui.theme.PoReceivingTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M0.1 acceptance criterion: "Every screen route from §7 exists in the nav graph
 * as a stub." Navigates to every route in [Routes.allSpecRoutes] (substituting a
 * placeholder id for path arguments) and asserts the destination resolves without
 * crashing.
 */
@RunWith(AndroidJUnit4::class)
class NavGraphCompletenessTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun everySpecRouteResolves() {
        lateinit var navController: NavHostController
        composeTestRule.setContent {
            navController = rememberNavController()
            PoReceivingTheme {
                PoReceivingNavGraph(navController = navController)
            }
        }

        Routes.allSpecRoutes.forEach { route ->
            val concreteRoute = route
                .replace("{poId}", "1")
                .replace("{lineId}", "1")

            composeTestRule.runOnUiThread {
                navController.navigate(concreteRoute)
            }
            composeTestRule.waitForIdle()

            // NavController reports the *template* route for the current destination
            // regardless of the concrete argument values used to reach it.
            assertEquals(
                "Route did not resolve in the nav graph: $route",
                route,
                navController.currentDestination?.route,
            )
        }
    }
}

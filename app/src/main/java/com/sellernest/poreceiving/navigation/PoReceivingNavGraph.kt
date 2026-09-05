package com.sellernest.poreceiving.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sellernest.poreceiving.core.mvvm.example.ExampleCounterScreen
import com.sellernest.poreceiving.ui.components.ComponentGalleryScreen
import com.sellernest.poreceiving.ui.screens.signin.SignInScreen

private val poIdArg = navArgument("poId") { type = NavType.LongType }
private val lineIdArg = navArgument("lineId") { type = NavType.LongType }

/**
 * The full §7 screen graph. Every route resolves today; real screens replace
 * [ScreenStub] bodies one milestone at a time without changing this wiring.
 */
@Composable
fun PoReceivingNavGraph(
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier,
) {
    NavHost(navController = navController, startDestination = Routes.SIGN_IN, modifier = modifier) {
        composable(Routes.SIGN_IN) { SignInScreen() }
        composable(Routes.WAREHOUSE_SELECTION) { ScreenStub("SELECT WAREHOUSE", "§7.2") }
        composable(Routes.WORK_QUEUE) { ScreenStub("RECEIVE", "§7.3") }

        poScopedRoute(Routes.PO_HEADER) { poId -> ScreenStub("PO $poId", "§7.4") }
        poScopedRoute(Routes.SCAN_TO_COUNT) { poId -> ScreenStub("SCAN TO COUNT — PO $poId", "§7.5") }
        poScopedRoute(Routes.RECONCILE) { poId -> ScreenStub("RECONCILE PO $poId", "§7.7") }
        poAndLineScopedRoute(Routes.DAMAGE_CAPTURE) { poId, lineId ->
            ScreenStub("DAMAGE — PO $poId / LINE $lineId", "§7.8")
        }
        poAndLineScopedRoute(Routes.SERIAL_CAPTURE) { poId, lineId ->
            ScreenStub("SERIALS — PO $poId / LINE $lineId", "§7.9")
        }
        poScopedRoute(Routes.BIN_CONFIRMATION) { poId -> ScreenStub("PUT AWAY — PO $poId", "§7.10") }
        poScopedRoute(Routes.REVIEW_AND_SUBMIT) { poId -> ScreenStub("REVIEW — PO $poId", "§7.11") }

        composable(Routes.SUBMISSION_QUEUE) { ScreenStub("SUBMISSIONS", "§7.12") }
        composable(Routes.RECEIPT_HISTORY) { ScreenStub("MY RECEIPTS", "§7.13") }

        composable(Routes.COMPONENT_GALLERY) { ComponentGalleryScreen() }
        composable(Routes.EXAMPLE_COUNTER) { ExampleCounterScreen() }
    }
}

private fun NavGraphBuilder.poScopedRoute(
    route: String,
    content: @Composable (poId: Long) -> Unit,
) {
    composable(route, arguments = listOf(poIdArg)) { backStackEntry ->
        content(backStackEntry.arguments?.getLong("poId") ?: -1L)
    }
}

private fun NavGraphBuilder.poAndLineScopedRoute(
    route: String,
    content: @Composable (poId: Long, lineId: Long) -> Unit,
) {
    composable(route, arguments = listOf(poIdArg, lineIdArg)) { backStackEntry ->
        val args = backStackEntry.arguments
        content(args?.getLong("poId") ?: -1L, args?.getLong("lineId") ?: -1L)
    }
}

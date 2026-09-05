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
import com.sellernest.poreceiving.ui.screens.accessgate.DeviceRevokedScreen
import com.sellernest.poreceiving.ui.screens.accessgate.MobileAccessDisabledScreen
import com.sellernest.poreceiving.ui.screens.poheader.PoHeaderScreen
import com.sellernest.poreceiving.ui.screens.reconcile.ReconcileScreen
import com.sellernest.poreceiving.ui.screens.scantocount.ScanToCountScreen
import com.sellernest.poreceiving.ui.screens.signin.SignInDestination
import com.sellernest.poreceiving.ui.screens.signin.SignInScreen
import com.sellernest.poreceiving.ui.screens.warehouseselection.WarehouseSelectionScreen
import com.sellernest.poreceiving.ui.screens.workqueue.WorkQueueScreen

private val poIdArg = navArgument("poId") { type = NavType.LongType }
private val lineIdArg = navArgument("lineId") { type = NavType.LongType }
private val draftIdArg = navArgument("draftId") { type = NavType.LongType }

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
        composable(Routes.SIGN_IN) {
            SignInScreen(
                onNavigate = { destination ->
                    val target = when (destination) {
                        SignInDestination.WAREHOUSE_SELECTION -> Routes.WAREHOUSE_SELECTION
                        SignInDestination.MOBILE_ACCESS_DISABLED -> Routes.MOBILE_ACCESS_DISABLED
                        SignInDestination.DEVICE_REVOKED -> Routes.DEVICE_REVOKED
                    }
                    navController.navigate(target) {
                        popUpTo(Routes.SIGN_IN) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.WAREHOUSE_SELECTION) {
            WarehouseSelectionScreen(
                onProceedToWorkQueue = {
                    navController.navigate(Routes.WORK_QUEUE) {
                        popUpTo(Routes.WAREHOUSE_SELECTION) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.WORK_QUEUE) {
            WorkQueueScreen(onPoSelected = { poId -> navController.navigate(Routes.poHeader(poId)) })
        }

        composable(Routes.MOBILE_ACCESS_DISABLED) { MobileAccessDisabledScreen() }
        composable(Routes.DEVICE_REVOKED) { DeviceRevokedScreen() }

        poScopedRoute(Routes.PO_HEADER) {
            PoHeaderScreen(
                onNavigateToScanToCount = { draftId -> navController.navigate(Routes.scanToCount(draftId)) },
            )
        }

        draftScopedRoute(Routes.SCAN_TO_COUNT) { draftId ->
            ScanToCountScreen(
                onNavigateToReconcile = { reconcileDraftId ->
                    navController.navigate(Routes.reconcile(reconcileDraftId)) {
                        // §4.1 acceptance criterion: "Backing out of RECONCILE
                        // to COUNTING is either disallowed or documented" --
                        // popping Scan-to-Count off the back stack makes it
                        // disallowed: system back from RECONCILE goes to PO
                        // Header, never to a stale mid-count screen.
                        popUpTo(Routes.scanToCount(draftId)) { inclusive = true }
                    }
                },
                onNavigateToWorkQueue = {
                    navController.navigate(Routes.WORK_QUEUE) {
                        popUpTo(Routes.WORK_QUEUE) { inclusive = true }
                    }
                },
            )
        }

        draftScopedRoute(Routes.RECONCILE) { draftId ->
            ReconcileScreen(
                onNavigateToReview = { reviewDraftId -> navController.navigate(Routes.reviewAndSubmit(reviewDraftId)) },
            )
        }

        poAndLineScopedRoute(Routes.DAMAGE_CAPTURE) { poId, lineId ->
            ScreenStub("DAMAGE — PO $poId / LINE $lineId", "§7.8")
        }
        poAndLineScopedRoute(Routes.SERIAL_CAPTURE) { poId, lineId ->
            ScreenStub("SERIALS — PO $poId / LINE $lineId", "§7.9")
        }
        poScopedRoute(Routes.BIN_CONFIRMATION) { poId -> ScreenStub("PUT AWAY — PO $poId", "§7.10") }
        draftScopedRoute(Routes.REVIEW_AND_SUBMIT) { draftId -> ScreenStub("REVIEW — draft $draftId", "§7.11") }

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

private fun NavGraphBuilder.draftScopedRoute(
    route: String,
    content: @Composable (draftId: Long) -> Unit,
) {
    composable(route, arguments = listOf(draftIdArg)) { backStackEntry ->
        content(backStackEntry.arguments?.getLong("draftId") ?: -1L)
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

package com.sellernest.poreceiving.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.sellernest.poreceiving.session.SessionViewModel
import com.sellernest.poreceiving.ui.components.StatusBar

/**
 * The app's single composition root: the persistent status bar (§3.2) above the
 * full §7 nav graph. Kept as a thin wrapper around [PoReceivingNavGraph] so a
 * test can exercise either one: `NavGraphCompletenessTest` drives the nav graph
 * alone for routing structure, while a status-bar test drives this to also
 * check the bar's presence across routes. Both run hosted in the real
 * [com.sellernest.poreceiving.MainActivity] now that a real screen (Sign In,
 * M1.1) uses `hiltViewModel()` -- a bare, Hilt-free compose rule is no longer
 * enough for any route that reaches a real screen.
 *
 * Also collects [com.sellernest.poreceiving.auth.SessionInvalidationNotifier]:
 * a failed proactive token refresh (§5.2: "a failed refresh is a hard logout to
 * the sign-in screen") clears the back stack and returns here, since this is the
 * one place holding the [NavHostController] that can do that.
 */
@Composable
fun PoReceivingRoot(navController: NavHostController = rememberNavController()) {
    val sessionViewModel: SessionViewModel = hiltViewModel()

    LaunchedEffect(Unit) {
        sessionViewModel.sessionInvalidationNotifier.hardLogout.collect {
            navController.navigate(Routes.SIGN_IN) {
                // Clears the entire back stack: popping up to and including the
                // graph's own root id, rather than any specific destination,
                // works regardless of which screen the hard logout interrupted.
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        topBar = { StatusBar() },
    ) { innerPadding ->
        PoReceivingNavGraph(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

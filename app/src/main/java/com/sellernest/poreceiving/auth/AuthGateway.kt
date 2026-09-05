package com.sellernest.poreceiving.auth

import android.content.Intent

/**
 * The sign-in surface [com.sellernest.poreceiving.ui.screens.signin.SignInViewModel]
 * depends on. Behind an interface -- like every other external-system seam in
 * this codebase ([com.sellernest.poreceiving.network.TokenProvider],
 * [com.sellernest.poreceiving.core.connectivity.ConnectivityObserver],
 * [com.sellernest.poreceiving.scan.ScanFeedbackService]) -- so the ViewModel's
 * handling of success/cancel/error is unit-testable against a fake, without
 * touching AppAuth or any real Android framework class.
 */
interface AuthGateway {
    /** Builds the Intent that opens sign-in in a Chrome Custom Tab (§5.1). */
    fun buildSignInIntent(): Intent

    /** Call with the Intent returned to the sign-in Activity Result launcher. */
    suspend fun handleAuthorizationResponse(dataIntent: Intent): AuthorizationOutcome
}

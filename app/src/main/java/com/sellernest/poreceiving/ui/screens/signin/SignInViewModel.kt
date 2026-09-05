package com.sellernest.poreceiving.ui.screens.signin

import android.content.Intent
import com.sellernest.poreceiving.auth.AuthGateway
import com.sellernest.poreceiving.auth.AuthorizationOutcome
import com.sellernest.poreceiving.auth.TokenStorage
import com.sellernest.poreceiving.core.mvvm.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val authGateway: AuthGateway,
    private val tokenStorage: TokenStorage,
) : BaseViewModel<SignInUiState, SignInUiEvent>(SignInUiState()) {

    fun buildSignInIntent(): Intent = authGateway.buildSignInIntent()

    override fun onEvent(event: SignInUiEvent) {
        when (event) {
            is SignInUiEvent.SignInTapped ->
                updateState { it.copy(isSigningIn = true, errorMessage = null) }

            is SignInUiEvent.AuthorizationResultReceived ->
                handleResult(event.resultIntent)
        }
    }

    private fun handleResult(resultIntent: Intent?) {
        if (resultIntent == null) {
            // The Custom Tab closed without AppAuth attaching a result at all --
            // treat the same as an explicit cancel, per §7.1's "cancel/back
            // returns cleanly to the Sign In screen."
            updateState { it.copy(isSigningIn = false, errorMessage = null) }
            return
        }

        scope.launch {
            when (val outcome = authGateway.handleAuthorizationResponse(resultIntent)) {
                is AuthorizationOutcome.Success -> {
                    tokenStorage.saveTokens(outcome.tokens)
                    updateState { it.copy(isSigningIn = false, errorMessage = null) }
                    // Where sign-in navigates to next (warehouse selection or the
                    // work queue) is M1.3/M1.5's job, once there's real session
                    // data to route on.
                }

                AuthorizationOutcome.Cancelled ->
                    updateState { it.copy(isSigningIn = false, errorMessage = null) }

                is AuthorizationOutcome.Error ->
                    updateState { it.copy(isSigningIn = false, errorMessage = outcome.message) }
            }
        }
    }
}

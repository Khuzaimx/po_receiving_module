package com.sellernest.poreceiving.ui.screens.signin

import android.content.Intent
import com.sellernest.poreceiving.auth.AuthGateway
import com.sellernest.poreceiving.auth.AuthorizationOutcome
import com.sellernest.poreceiving.auth.TokenStorage
import com.sellernest.poreceiving.core.mvvm.BaseViewModel
import com.sellernest.poreceiving.network.ApiResult
import com.sellernest.poreceiving.session.MeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val authGateway: AuthGateway,
    private val tokenStorage: TokenStorage,
    private val meRepository: MeRepository,
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

                    // §5.2: "X-Active-Org... set from the active company
                    // returned by /api/me/" -- resolved once, right here.
                    val meResult = meRepository.refresh()
                    val errorMessage = if (meResult is ApiResult.Success) {
                        null
                    } else {
                        "Signed in, but couldn't load your account. Check your connection and try again."
                    }
                    updateState { it.copy(isSigningIn = false, errorMessage = errorMessage) }
                    // Where sign-in navigates to next (warehouse selection or the
                    // work queue) is M1.5's job, once that screen exists.
                }

                AuthorizationOutcome.Cancelled ->
                    updateState { it.copy(isSigningIn = false, errorMessage = null) }

                is AuthorizationOutcome.Error ->
                    updateState { it.copy(isSigningIn = false, errorMessage = outcome.message) }
            }
        }
    }
}

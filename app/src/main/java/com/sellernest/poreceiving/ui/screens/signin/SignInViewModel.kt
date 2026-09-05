package com.sellernest.poreceiving.ui.screens.signin

import android.content.Intent
import com.sellernest.poreceiving.auth.AuthGateway
import com.sellernest.poreceiving.auth.AuthorizationOutcome
import com.sellernest.poreceiving.auth.TokenStorage
import com.sellernest.poreceiving.core.mvvm.BaseViewModel
import com.sellernest.poreceiving.network.ApiResult
import com.sellernest.poreceiving.session.BlockingAccessGate
import com.sellernest.poreceiving.session.MeRepository
import com.sellernest.poreceiving.session.blockingAccessGateFor
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

            is SignInUiEvent.NavigationHandled ->
                updateState { it.copy(navigateTo = null) }
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
                is AuthorizationOutcome.Success -> handleSignedIn(outcome)

                AuthorizationOutcome.Cancelled ->
                    updateState { it.copy(isSigningIn = false, errorMessage = null) }

                is AuthorizationOutcome.Error ->
                    updateState { it.copy(isSigningIn = false, errorMessage = outcome.message) }
            }
        }
    }

    private suspend fun handleSignedIn(outcome: AuthorizationOutcome.Success) {
        tokenStorage.saveTokens(outcome.tokens)

        // §5.2: "X-Active-Org... set from the active company returned by
        // /api/me/" -- resolved once, right here.
        val meResult = meRepository.refresh()

        when (blockingAccessGateFor(meResult)) {
            // §5.3: no token clearing for this gate -- only device revocation
            // clears tokens (and there, drafts are still retained).
            BlockingAccessGate.MobileAccessDisabled ->
                updateState {
                    it.copy(isSigningIn = false, errorMessage = null, navigateTo = SignInDestination.MOBILE_ACCESS_DISABLED)
                }

            BlockingAccessGate.DeviceRevoked -> {
                tokenStorage.clear()
                meRepository.clear()
                updateState {
                    it.copy(isSigningIn = false, errorMessage = null, navigateTo = SignInDestination.DEVICE_REVOKED)
                }
            }

            null -> {
                val errorMessage = if (meResult is ApiResult.Success) null else NO_SESSION_ERROR
                val navigateTo = if (meResult is ApiResult.Success) SignInDestination.WAREHOUSE_SELECTION else null
                updateState { it.copy(isSigningIn = false, errorMessage = errorMessage, navigateTo = navigateTo) }
            }
        }
    }

    private companion object {
        const val NO_SESSION_ERROR = "Signed in, but couldn't load your account. Check your connection and try again."
    }
}

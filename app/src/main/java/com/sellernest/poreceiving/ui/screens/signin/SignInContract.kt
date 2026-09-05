package com.sellernest.poreceiving.ui.screens.signin

import android.content.Intent
import com.sellernest.poreceiving.core.mvvm.UiEvent
import com.sellernest.poreceiving.core.mvvm.UiState

/** Where a successful sign-in (or a blocking access gate, §5.3) sends the
 *  screen next. A one-shot signal: [SignInUiEvent.NavigationHandled] resets it
 *  so recomposition never re-fires the same navigation. */
enum class SignInDestination {
    WAREHOUSE_SELECTION,
    MOBILE_ACCESS_DISABLED,
    DEVICE_REVOKED,
}

data class SignInUiState(
    val isSigningIn: Boolean = false,
    val errorMessage: String? = null,
    val navigateTo: SignInDestination? = null,
) : UiState

sealed interface SignInUiEvent : UiEvent {
    data object SignInTapped : SignInUiEvent

    /** The result Intent handed back to the Activity Result launcher that
     *  started the Custom Tab, whatever the outcome (success, cancel, error). */
    data class AuthorizationResultReceived(val resultIntent: Intent?) : SignInUiEvent

    /** Consumes [SignInUiState.navigateTo] once the screen has acted on it. */
    data object NavigationHandled : SignInUiEvent
}

package com.sellernest.poreceiving.ui.screens.signin

import android.content.Intent
import com.sellernest.poreceiving.core.mvvm.UiEvent
import com.sellernest.poreceiving.core.mvvm.UiState

data class SignInUiState(
    val isSigningIn: Boolean = false,
    val errorMessage: String? = null,
) : UiState

sealed interface SignInUiEvent : UiEvent {
    data object SignInTapped : SignInUiEvent

    /** The result Intent handed back to the Activity Result launcher that
     *  started the Custom Tab, whatever the outcome (success, cancel, error). */
    data class AuthorizationResultReceived(val resultIntent: Intent?) : SignInUiEvent
}

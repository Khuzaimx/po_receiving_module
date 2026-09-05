package com.sellernest.poreceiving.ui.screens.signin

import android.content.Intent
import com.sellernest.poreceiving.auth.AuthGateway
import com.sellernest.poreceiving.auth.AuthorizationOutcome
import com.sellernest.poreceiving.auth.TokenSet
import com.sellernest.poreceiving.auth.TokenStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * M1.1 acceptance criterion: "Cancelling in the browser returns to Sign In with
 * no error state left behind." Exercised against a fake [AuthGateway] and
 * [TokenStorage] -- exactly why M1.1 put a seam (`AuthGateway`) in front of the
 * real AppAuth wiring: this whole class is otherwise untestable off a device.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SignInViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeAuthGateway(private val outcome: AuthorizationOutcome) : AuthGateway {
        override fun buildSignInIntent(): Intent =
            throw UnsupportedOperationException("not exercised by this test")

        override suspend fun handleAuthorizationResponse(dataIntent: Intent): AuthorizationOutcome = outcome
    }

    private class FakeTokenStorage : TokenStorage {
        var saved: TokenSet? = null
        override suspend fun saveTokens(tokens: TokenSet) {
            saved = tokens
        }
        override suspend fun currentTokens(): TokenSet? = saved
        override suspend fun clear() {
            saved = null
        }
    }

    @Test
    fun `a null result intent resets to idle with no error`() = runTest {
        val viewModel = SignInViewModel(
            authGateway = FakeAuthGateway(AuthorizationOutcome.Cancelled),
            tokenStorage = FakeTokenStorage(),
        )

        viewModel.onEvent(SignInUiEvent.SignInTapped)
        assertEquals(true, viewModel.state.value.isSigningIn)

        viewModel.onEvent(SignInUiEvent.AuthorizationResultReceived(null))

        assertEquals(false, viewModel.state.value.isSigningIn)
        assertNull(viewModel.state.value.errorMessage)
    }

    @Test
    fun `a cancelled outcome with a non-null intent also resets to idle with no error`() = runTest {
        val viewModel = SignInViewModel(
            authGateway = FakeAuthGateway(AuthorizationOutcome.Cancelled),
            tokenStorage = FakeTokenStorage(),
        )

        viewModel.onEvent(SignInUiEvent.SignInTapped)
        viewModel.onEvent(SignInUiEvent.AuthorizationResultReceived(Intent()))

        assertEquals(false, viewModel.state.value.isSigningIn)
        assertNull(viewModel.state.value.errorMessage)
    }

    @Test
    fun `a successful outcome saves tokens and clears any error`() = runTest {
        val tokenStorage = FakeTokenStorage()
        val tokens = TokenSet(accessToken = "at", refreshToken = "rt", accessTokenExpiryEpochMillis = 123L)
        val viewModel = SignInViewModel(
            authGateway = FakeAuthGateway(AuthorizationOutcome.Success(tokens)),
            tokenStorage = tokenStorage,
        )

        viewModel.onEvent(SignInUiEvent.SignInTapped)
        viewModel.onEvent(SignInUiEvent.AuthorizationResultReceived(Intent()))

        assertEquals(tokens, tokenStorage.saved)
        assertEquals(false, viewModel.state.value.isSigningIn)
        assertNull(viewModel.state.value.errorMessage)
    }

    @Test
    fun `an error outcome surfaces its message and does not save tokens`() = runTest {
        val tokenStorage = FakeTokenStorage()
        val viewModel = SignInViewModel(
            authGateway = FakeAuthGateway(AuthorizationOutcome.Error("Sign-in failed.")),
            tokenStorage = tokenStorage,
        )

        viewModel.onEvent(SignInUiEvent.SignInTapped)
        viewModel.onEvent(SignInUiEvent.AuthorizationResultReceived(Intent()))

        assertEquals("Sign-in failed.", viewModel.state.value.errorMessage)
        assertEquals(false, viewModel.state.value.isSigningIn)
        assertNull(tokenStorage.saved)
    }
}

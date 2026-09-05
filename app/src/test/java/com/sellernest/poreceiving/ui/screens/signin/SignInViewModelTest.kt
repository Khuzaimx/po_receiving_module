package com.sellernest.poreceiving.ui.screens.signin

import android.content.Intent
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.sellernest.poreceiving.auth.AuthGateway
import com.sellernest.poreceiving.auth.AuthorizationOutcome
import com.sellernest.poreceiving.auth.TokenSet
import com.sellernest.poreceiving.auth.TokenStorage
import com.sellernest.poreceiving.network.ApiService
import com.sellernest.poreceiving.session.MeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

/**
 * M1.1 acceptance criterion: "Cancelling in the browser returns to Sign In with
 * no error state left behind." Exercised against a fake [AuthGateway] and
 * [TokenStorage] -- exactly why M1.1 put a seam (`AuthGateway`) in front of the
 * real AppAuth wiring: this whole class is otherwise untestable off a device.
 *
 * [MeRepository] (M1.3) is a concrete class over the real Retrofit stack rather
 * than another interface seam, so it is wired here to a [MockWebServer] instead
 * of a fake -- only the "successful outcome" test actually exercises it, since
 * cancel/error/null-intent all return before `/api/me/` is ever called.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SignInViewModelTest {

    private lateinit var server: MockWebServer
    private lateinit var meRepository: MeRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())

        server = MockWebServer()
        server.start()
        val json = Json {
            namingStrategy = JsonNamingStrategy.SnakeCase
            ignoreUnknownKeys = true
            explicitNulls = false
            coerceInputValues = true
        }
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/api/mobile/receiving/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        meRepository = MeRepository(retrofit.create(ApiService::class.java), json)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        server.shutdown()
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

    private fun enqueueValidMeResponse() {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "user": {"id": 1, "email": "jane@example.com", "display_name": "Jane Doe"},
                  "companies": [{
                    "external_id": "acme", "name": "Acme Distribution", "is_default": true,
                    "warehouses": [{"id": 2, "name": "DC-2", "is_default": true, "enforce_bins": true}],
                    "permissions": {"can_receive": true, "can_over_receive": false}
                  }]
                }
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `a null result intent resets to idle with no error`() = runTest {
        val viewModel = SignInViewModel(
            authGateway = FakeAuthGateway(AuthorizationOutcome.Cancelled),
            tokenStorage = FakeTokenStorage(),
            meRepository = meRepository,
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
            meRepository = meRepository,
        )

        viewModel.onEvent(SignInUiEvent.SignInTapped)
        viewModel.onEvent(SignInUiEvent.AuthorizationResultReceived(Intent()))

        assertEquals(false, viewModel.state.value.isSigningIn)
        assertNull(viewModel.state.value.errorMessage)
    }

    @Test
    fun `a successful outcome saves tokens, resolves the session, and clears any error`() = runTest {
        enqueueValidMeResponse()
        val tokenStorage = FakeTokenStorage()
        val tokens = TokenSet(accessToken = "at", refreshToken = "rt", accessTokenExpiryEpochMillis = 123L)
        val viewModel = SignInViewModel(
            authGateway = FakeAuthGateway(AuthorizationOutcome.Success(tokens)),
            tokenStorage = tokenStorage,
            meRepository = meRepository,
        )

        viewModel.onEvent(SignInUiEvent.SignInTapped)
        viewModel.onEvent(SignInUiEvent.AuthorizationResultReceived(Intent()))

        assertEquals(tokens, tokenStorage.saved)
        assertEquals(false, viewModel.state.value.isSigningIn)
        assertNull(viewModel.state.value.errorMessage)
        assertEquals("acme", meRepository.state.value.activeCompanyExternalId)
        assertEquals("Jane Doe", meRepository.state.value.userDisplayName)
    }

    @Test
    fun `a successful auth outcome whose api me call fails still saves tokens but surfaces an error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        val tokenStorage = FakeTokenStorage()
        val tokens = TokenSet(accessToken = "at", refreshToken = "rt", accessTokenExpiryEpochMillis = 123L)
        val viewModel = SignInViewModel(
            authGateway = FakeAuthGateway(AuthorizationOutcome.Success(tokens)),
            tokenStorage = tokenStorage,
            meRepository = meRepository,
        )

        viewModel.onEvent(SignInUiEvent.SignInTapped)
        viewModel.onEvent(SignInUiEvent.AuthorizationResultReceived(Intent()))

        assertEquals(tokens, tokenStorage.saved)
        assertEquals(false, viewModel.state.value.isSigningIn)
        assert(viewModel.state.value.errorMessage != null) { "Expected an error message when /api/me/ fails" }
    }

    @Test
    fun `an error outcome surfaces its message and does not save tokens`() = runTest {
        val tokenStorage = FakeTokenStorage()
        val viewModel = SignInViewModel(
            authGateway = FakeAuthGateway(AuthorizationOutcome.Error("Sign-in failed.")),
            tokenStorage = tokenStorage,
            meRepository = meRepository,
        )

        viewModel.onEvent(SignInUiEvent.SignInTapped)
        viewModel.onEvent(SignInUiEvent.AuthorizationResultReceived(Intent()))

        assertEquals("Sign-in failed.", viewModel.state.value.errorMessage)
        assertEquals(false, viewModel.state.value.isSigningIn)
        assertNull(tokenStorage.saved)
    }
}

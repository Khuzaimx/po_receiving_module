package com.sellernest.poreceiving.auth

import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.ResponseTypeValues
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * Wraps AppAuth for the sign-in flow (§5.1). Two constraints from the spec are
 * enforced simply by never doing the alternative: this never constructs a
 * `WebView`, and never builds a Direct Grant (password) `TokenRequest` -- the
 * only request type built here is the authorization-code exchange, and the only
 * refresh-grant request lives in [EncryptedTokenStorage]. See
 * `AuthPathHygieneTest` for the automated check.
 */
class AppAuthGateway @Inject constructor(
    private val authorizationService: AuthorizationService,
) : AuthGateway {

    /**
     * Builds the Intent that opens sign-in in a Chrome Custom Tab. PKCE (S256) is
     * generated automatically by [AuthorizationRequest.Builder] -- there is no
     * step here that could accidentally omit it.
     */
    override fun buildSignInIntent(): Intent {
        val request = AuthorizationRequest.Builder(
            AuthConfig.serviceConfiguration,
            AuthConfig.clientId,
            ResponseTypeValues.CODE,
            AuthConfig.redirectUri,
        ).build()

        val customTabsIntent = CustomTabsIntent.Builder().build()
        return authorizationService.getAuthorizationRequestIntent(request, customTabsIntent)
    }

    override suspend fun handleAuthorizationResponse(dataIntent: Intent): AuthorizationOutcome {
        val response = AuthorizationResponse.fromIntent(dataIntent)
        val exception = AuthorizationException.fromIntent(dataIntent)

        return when {
            response != null -> exchangeCodeForTokens(response)
            exception != null && isUserCancellation(exception) -> AuthorizationOutcome.Cancelled
            else -> AuthorizationOutcome.Error(exception?.errorDescription ?: "Sign-in failed.")
        }
    }

    private fun isUserCancellation(exception: AuthorizationException): Boolean =
        exception.type == AuthorizationException.TYPE_GENERAL_ERROR &&
            exception.code == AuthorizationException.GeneralErrors.USER_CANCELED_AUTH_FLOW.code

    private suspend fun exchangeCodeForTokens(response: AuthorizationResponse): AuthorizationOutcome =
        suspendCancellableCoroutine { continuation ->
            authorizationService.performTokenRequest(response.createTokenExchangeRequest()) { tokenResponse, exception ->
                val accessToken = tokenResponse?.accessToken
                if (accessToken != null) {
                    continuation.resume(
                        AuthorizationOutcome.Success(
                            TokenSet(
                                accessToken = accessToken,
                                refreshToken = tokenResponse.refreshToken,
                                accessTokenExpiryEpochMillis = tokenResponse.accessTokenExpirationTime
                                    ?: defaultExpiry(),
                            ),
                        ),
                    )
                } else {
                    continuation.resume(
                        AuthorizationOutcome.Error(exception?.errorDescription ?: "Token exchange failed."),
                    )
                }
            }
        }

    private fun defaultExpiry(): Long = System.currentTimeMillis() + FALLBACK_EXPIRY_MS

    private companion object {
        /** Used only if the token response omits an explicit expiry -- forces a
         *  refresh attempt soon rather than trusting an unbounded lifetime. */
        const val FALLBACK_EXPIRY_MS = 5 * 60_000L
    }
}

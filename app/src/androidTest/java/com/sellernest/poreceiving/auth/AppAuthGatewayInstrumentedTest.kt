package com.sellernest.poreceiving.auth

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.openid.appauth.AuthorizationService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M1.1 acceptance criterion: "Sign-in completes via Custom Tab and yields a
 * token containing the organization claim" -- the token/organization-claim part
 * needs a real Keycloak server and is out of reach here, but the request this
 * builds *before* that round trip is fully checkable: correct authorization
 * endpoint, client id, redirect URI, response type, and -- critically -- a PKCE
 * code challenge, proving §5.1's non-negotiable requirement is actually active
 * and not silently disabled.
 *
 * `AuthorizationService.getAuthorizationRequestIntent` returns an `ACTION_VIEW`
 * Intent whose `data` Uri *is* the full authorization request URI (AppAuth
 * builds it via `AuthorizationRequest.toUri()`), so the request is inspectable
 * without ever needing to launch the Custom Tab.
 */
@RunWith(AndroidJUnit4::class)
class AppAuthGatewayInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val authorizationService = AuthorizationService(context)
    private val authGateway = AppAuthGateway(authorizationService)

    @Test
    fun signInIntentTargetsTheConfiguredAuthorizationEndpointWithPkce() {
        val intent = authGateway.buildSignInIntent()
        val uri = requireNotNull(intent.data) { "Sign-in Intent had no data Uri" }

        val authorizationEndpoint = AuthConfig.serviceConfiguration.authorizationEndpoint.toString()
        assertEquals(authorizationEndpoint, uri.toString().substringBefore('?'))

        assertEquals(AuthConfig.clientId, uri.getQueryParameter("client_id"))
        assertEquals(AuthConfig.redirectUri.toString(), uri.getQueryParameter("redirect_uri"))
        assertEquals("code", uri.getQueryParameter("response_type"))
        assertNotNull("PKCE code_challenge must be present (§5.1)", uri.getQueryParameter("code_challenge"))
        assertEquals("S256", uri.getQueryParameter("code_challenge_method"))
        assertNull(
            "A password grant must never appear anywhere near the sign-in request",
            uri.getQueryParameter("grant_type"),
        )
    }
}

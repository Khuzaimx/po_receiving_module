package com.sellernest.poreceiving.auth

import android.net.Uri
import com.sellernest.poreceiving.BuildConfig
import net.openid.appauth.AuthorizationServiceConfiguration

/**
 * §5.1: OAuth 2.0 Authorization Code + PKCE (S256) against Keycloak, via a
 * dedicated public client (backend issue #1729).
 *
 * Endpoints are built directly from the realm base rather than fetched via
 * OpenID discovery (`AuthorizationServiceConfiguration.fetchFromIssuer`):
 * Keycloak's `/protocol/openid-connect/{auth,token}` paths are stable, and
 * skipping discovery means sign-in never needs an extra network round trip
 * before the Custom Tab can even open.
 */
object AuthConfig {

    val clientId: String = BuildConfig.OAUTH_CLIENT_ID

    /** Matches the `appAuthRedirectScheme` manifest placeholder in app/build.gradle.kts. */
    val redirectUri: Uri = Uri.parse("${BuildConfig.APPLICATION_ID}:/oauth2redirect")

    val serviceConfiguration: AuthorizationServiceConfiguration
        get() {
            val issuer = BuildConfig.OAUTH_ISSUER.trimEnd('/')
            return AuthorizationServiceConfiguration(
                Uri.parse("$issuer/protocol/openid-connect/auth"),
                Uri.parse("$issuer/protocol/openid-connect/token"),
            )
        }
}

package com.sellernest.poreceiving.auth

/**
 * The access/refresh token pair persisted by [TokenStorage]. [refreshToken] is
 * nullable because Keycloak can be configured without offline/refresh tokens,
 * though the standard client configuration for this app issues one.
 */
data class TokenSet(
    val accessToken: String,
    val refreshToken: String?,
    val accessTokenExpiryEpochMillis: Long,
)

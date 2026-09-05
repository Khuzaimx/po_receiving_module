package com.sellernest.poreceiving.network

/**
 * Read-only accessor for the current access token, consumed only by
 * [AuthInterceptor]. The real implementation — backed by EncryptedSharedPreferences
 * with proactive refresh — lands in M1.2; this interface is the seam that lets the
 * networking layer (M0.2) be built and tested before auth exists.
 */
interface TokenProvider {
    /** Null when signed out; [AuthInterceptor] omits the header in that case. */
    suspend fun currentAccessToken(): String?
}

package com.sellernest.poreceiving.network

/**
 * Read-only accessor for the active organisation's external id, consumed only by
 * [ActiveOrgInterceptor]. Populated from `/api/me/` in M1.3; this interface is the
 * seam that lets the networking layer (M0.2) be built and tested before that exists.
 */
fun interface ActiveOrgProvider {
    /** Null before an organisation has been selected/resolved. */
    suspend fun currentActiveOrgId(): String?
}

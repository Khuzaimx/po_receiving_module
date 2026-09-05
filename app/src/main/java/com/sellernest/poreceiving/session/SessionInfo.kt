package com.sellernest.poreceiving.session

import kotlinx.coroutines.flow.Flow

/** The two identity fields the persistent status bar shows besides connectivity
 *  and pending count (§3.2): the active warehouse name and a signed-in-user label. */
data class SessionInfo(
    val activeWarehouseName: String? = null,
    val signedInUserLabel: String? = null,
)

/**
 * Read-only accessor for [SessionInfo], consumed only by the status bar. Real
 * warehouse selection (M1.5) and the signed-in user from `/api/me/` (M1.3) don't
 * exist yet; this is the seam that lets the status bar (M0.5) be built now and
 * wired to real data later, matching the [com.sellernest.poreceiving.network.TokenProvider]
 * / [com.sellernest.poreceiving.network.ActiveOrgProvider] pattern from M0.2.
 */
fun interface SessionInfoProvider {
    fun observe(): Flow<SessionInfo>
}

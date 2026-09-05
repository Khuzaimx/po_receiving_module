package com.sellernest.poreceiving.auth

/**
 * §5.2/§10: "Refresh proactively before expiry... Access token expired mid-count
 * -> silent refresh." A free function, not a method on [EncryptedTokenStorage],
 * so the decision is unit-testable without a real Android Keystore.
 */
internal fun needsRefresh(
    expiryEpochMillis: Long,
    nowEpochMillis: Long = System.currentTimeMillis(),
    leewayMillis: Long = REFRESH_LEEWAY_MS,
): Boolean = nowEpochMillis >= expiryEpochMillis - leewayMillis

/** Refresh once we're within a minute of expiry, not only after it has lapsed --
 *  "proactively," per §5.2, not reactively on a 401. */
internal const val REFRESH_LEEWAY_MS = 60_000L

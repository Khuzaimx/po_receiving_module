package com.sellernest.poreceiving.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §5.2/§10: "Refresh proactively before expiry." The decision behind
 * [EncryptedTokenStorage.currentAccessToken]'s proactive refresh, isolated so it
 * doesn't need a real Android Keystore to verify.
 */
class TokenRefreshPolicyTest {

    @Test
    fun `well before expiry does not need refresh`() {
        val now = 1_000_000L
        assertFalse(needsRefresh(expiryEpochMillis = now + 10 * 60_000, nowEpochMillis = now))
    }

    @Test
    fun `inside the leeway window needs refresh even though not yet expired`() {
        val now = 1_000_000L
        assertTrue(needsRefresh(expiryEpochMillis = now + 30_000, nowEpochMillis = now, leewayMillis = 60_000))
    }

    @Test
    fun `already past expiry needs refresh`() {
        val now = 1_000_000L
        assertTrue(needsRefresh(expiryEpochMillis = now - 1_000, nowEpochMillis = now))
    }

    @Test
    fun `exactly at the leeway boundary needs refresh`() {
        val now = 1_000_000L
        assertTrue(needsRefresh(expiryEpochMillis = now + 60_000, nowEpochMillis = now, leewayMillis = 60_000))
    }
}

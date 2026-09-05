package com.sellernest.poreceiving.auth

/**
 * Persists the token pair. §5.2: sign-out "clears tokens... but does not delete
 * unsent drafts or queued submissions" -- [clear] only ever touches this store,
 * by design; it must never be given a reason to reach into Room.
 */
interface TokenStorage {
    suspend fun saveTokens(tokens: TokenSet)
    suspend fun currentTokens(): TokenSet?
    suspend fun clear()
}

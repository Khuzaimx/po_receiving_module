package com.sellernest.poreceiving.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.sellernest.poreceiving.network.TokenProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthorizationService
import net.openid.appauth.GrantTypeValues
import net.openid.appauth.TokenRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * §5.2, §2.1: tokens in `EncryptedSharedPreferences` (Android Keystore-backed),
 * never plain `SharedPreferences`, never external storage.
 *
 * Implements both [TokenStorage] (the sign-in/sign-out surface) and
 * [TokenProvider] (the read-only surface [com.sellernest.poreceiving.network.AuthInterceptor]
 * consumes) -- refreshing proactively on every read is exactly what
 * [TokenProvider.currentAccessToken] is for, so there is one seam, not two.
 */
@Singleton
class EncryptedTokenStorage @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authorizationService: AuthorizationService,
    private val sessionInvalidationNotifier: SessionInvalidationNotifier,
) : TokenStorage, TokenProvider {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override suspend fun saveTokens(tokens: TokenSet) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, tokens.accessToken)
            .putString(KEY_REFRESH_TOKEN, tokens.refreshToken)
            .putLong(KEY_EXPIRY, tokens.accessTokenExpiryEpochMillis)
            .apply()
    }

    override suspend fun currentTokens(): TokenSet? {
        val accessToken = prefs.getString(KEY_ACCESS_TOKEN, null) ?: return null
        return TokenSet(
            accessToken = accessToken,
            refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null),
            accessTokenExpiryEpochMillis = prefs.getLong(KEY_EXPIRY, 0L),
        )
    }

    /** §5.2: sign-out "clears tokens... but does not delete unsent drafts or
     *  queued submissions" -- this touches only these prefs, nothing in Room. */
    override suspend fun clear() {
        prefs.edit().clear().apply()
    }

    override suspend fun currentAccessToken(): String? {
        val tokens = currentTokens() ?: return null
        if (!needsRefresh(tokens.accessTokenExpiryEpochMillis)) {
            return tokens.accessToken
        }

        val refreshed = refresh(tokens.refreshToken)
        return if (refreshed != null) {
            saveTokens(refreshed)
            refreshed.accessToken
        } else {
            // §5.2: "A failed refresh is a hard logout to the sign-in screen."
            clear()
            sessionInvalidationNotifier.notifyRefreshFailed()
            null
        }
    }

    private suspend fun refresh(refreshToken: String?): TokenSet? {
        if (refreshToken == null) return null

        return suspendCancellableCoroutine { continuation ->
            val request = TokenRequest.Builder(AuthConfig.serviceConfiguration, AuthConfig.clientId)
                .setGrantType(GrantTypeValues.REFRESH_TOKEN)
                .setRefreshToken(refreshToken)
                .build()

            authorizationService.performTokenRequest(request) { response, _ ->
                val accessToken = response?.accessToken
                if (accessToken != null) {
                    continuation.resume(
                        TokenSet(
                            accessToken = accessToken,
                            refreshToken = response.refreshToken ?: refreshToken,
                            accessTokenExpiryEpochMillis = response.accessTokenExpirationTime
                                ?: (System.currentTimeMillis() + FALLBACK_EXPIRY_MS),
                        ),
                    )
                } else {
                    continuation.resume(null)
                }
            }
        }
    }

    private companion object {
        const val PREFS_FILE_NAME = "auth_tokens"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_EXPIRY = "expiry_epoch_millis"

        /** Same fallback window as [AppAuthGateway]'s initial token exchange --
         *  used only if a refresh response omits an explicit expiry. Deliberately
         *  not [REFRESH_LEEWAY_MS] (60s, the proactive-refresh trigger window):
         *  that constant means something different and is far too short to use
         *  as an assumed token lifetime. */
        const val FALLBACK_EXPIRY_MS = 5 * 60_000L
    }
}

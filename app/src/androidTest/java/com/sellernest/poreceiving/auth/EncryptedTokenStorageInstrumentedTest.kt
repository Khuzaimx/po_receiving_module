package com.sellernest.poreceiving.auth

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import net.openid.appauth.AuthorizationService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * M1.2 acceptance criteria: token round-trip correctness, and "Tokens are
 * unreadable from a rooted `shared_prefs` dump" -- simulated here by reading the
 * preferences XML file directly off disk, which is exactly what a rooted-device
 * dump would show. Requires a connected device or emulator (real Android
 * Keystore).
 */
@RunWith(AndroidJUnit4::class)
class EncryptedTokenStorageInstrumentedTest {

    // Must match EncryptedTokenStorage's private PREFS_FILE_NAME constant.
    private val prefsFileName = "auth_tokens"

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun newStorage() = EncryptedTokenStorage(
        context = context,
        authorizationService = AuthorizationService(context),
        sessionInvalidationNotifier = SessionInvalidationNotifier(),
    )

    @After
    fun tearDown() {
        context.deleteSharedPreferences(prefsFileName)
    }

    @Test
    fun savedTokensRoundTripExactly() = runTest {
        val storage = newStorage()
        val tokens = TokenSet(
            accessToken = "at-12345",
            refreshToken = "rt-67890",
            accessTokenExpiryEpochMillis = System.currentTimeMillis() + 100_000,
        )

        storage.saveTokens(tokens)

        assertEquals(tokens, storage.currentTokens())
    }

    @Test
    fun rawPreferencesFileOnDiskNeverContainsThePlaintextAccessToken() = runTest {
        val storage = newStorage()
        val secretAccessToken = "super-secret-plaintext-access-token-value"
        storage.saveTokens(
            TokenSet(
                accessToken = secretAccessToken,
                refreshToken = "some-refresh-token",
                accessTokenExpiryEpochMillis = System.currentTimeMillis() + 100_000,
            ),
        )

        val prefsFile = File(context.filesDir.parentFile, "shared_prefs/$prefsFileName.xml")
        assertTrue("Expected a shared_prefs file to exist at ${prefsFile.path}", prefsFile.exists())

        val rawFileContent = prefsFile.readText()
        assertFalse(
            "The plaintext access token must never appear in the raw preferences file on disk",
            rawFileContent.contains(secretAccessToken),
        )
    }

    @Test
    fun clearRemovesBothTokens() = runTest {
        val storage = newStorage()
        storage.saveTokens(TokenSet("at", "rt", System.currentTimeMillis() + 100_000))

        storage.clear()

        assertEquals(null, storage.currentTokens())
    }
}

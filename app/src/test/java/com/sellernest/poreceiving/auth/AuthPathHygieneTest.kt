package com.sellernest.poreceiving.auth

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * §5.1's two non-negotiable constraints, enforced as source-scan tests rather
 * than left as review discipline:
 *
 * (a) "Never use an embedded WebView for login." No `WebView` class reference
 *     may appear anywhere in the auth path.
 * (b) "Never use Direct Grant (password grant)." No password-grant construction
 *     (AppAuth's `GrantTypeValues.PASSWORD`, or the literal wire value
 *     `"password"` as a grant type) may appear anywhere in the auth path. The
 *     only grant types this app ever constructs are the authorization-code
 *     exchange (via `AuthorizationResponse.createTokenExchangeRequest()`, which
 *     needs no grant-type literal at all) and `GrantTypeValues.REFRESH_TOKEN`
 *     in [EncryptedTokenStorage].
 */
class AuthPathHygieneTest {

    private fun authPathSourceFiles(): List<File> {
        val candidates = listOf(
            File("src/main/java/com/sellernest/poreceiving/auth"),
            File("app/src/main/java/com/sellernest/poreceiving/auth"),
        )
        val authDir = candidates.firstOrNull { it.exists() }
            ?: error("Could not locate the auth package from working dir ${File(".").absolutePath}")

        val signInScreenCandidates = listOf(
            File("src/main/java/com/sellernest/poreceiving/ui/screens/signin"),
            File("app/src/main/java/com/sellernest/poreceiving/ui/screens/signin"),
        )
        val signInDir = signInScreenCandidates.firstOrNull { it.exists() }
            ?: error("Could not locate the sign-in screen package")

        return (authDir.walkTopDown() + signInDir.walkTopDown())
            .filter { it.isFile && it.extension == "kt" }
            .toList()
    }

    @Test
    fun `no WebView reference anywhere in the auth path`() {
        val offenders = authPathSourceFiles()
            .filter { it.readText().contains("WebView") }
            .map { it.path }

        assertTrue("Found WebView references in the auth path: $offenders", offenders.isEmpty())
    }

    @Test
    fun `no password grant construction anywhere in the auth path`() {
        val offenders = authPathSourceFiles()
            .filter { file ->
                val text = file.readText()
                text.contains("GrantTypeValues.PASSWORD") ||
                    text.contains("\"password\"") ||
                    Regex("""grant_type["']?\s*[:=]\s*["']password""").containsMatchIn(text)
            }
            .map { it.path }

        assertTrue("Found password-grant construction in the auth path: $offenders", offenders.isEmpty())
    }
}

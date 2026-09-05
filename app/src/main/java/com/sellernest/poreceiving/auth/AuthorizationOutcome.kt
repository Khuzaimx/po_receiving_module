package com.sellernest.poreceiving.auth

/** The result of handling a redirect back from the Custom Tab (§5.1, §7.1). */
sealed interface AuthorizationOutcome {
    data class Success(val tokens: TokenSet) : AuthorizationOutcome

    /** Back/cancel from the Custom Tab -- not an error; the Sign In screen
     *  returns to its idle state with no error message shown (M1.1). */
    data object Cancelled : AuthorizationOutcome

    data class Error(val message: String) : AuthorizationOutcome
}

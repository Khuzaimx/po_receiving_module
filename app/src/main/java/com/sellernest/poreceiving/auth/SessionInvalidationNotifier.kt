package com.sellernest.poreceiving.auth

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Signals a hard logout (§5.2: "A failed refresh is a hard logout to the
 * sign-in screen"). [EncryptedTokenStorage] emits when a refresh attempt fails;
 * [com.sellernest.poreceiving.navigation.PoReceivingRoot] collects this and
 * navigates to Sign In. Nothing else about the app's state changes here --
 * the draft is local and survives this exactly as §5.2 requires, because
 * discarding it is simply not one of the things a logout does.
 */
@Singleton
class SessionInvalidationNotifier @Inject constructor() {
    private val _hardLogout = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val hardLogout: SharedFlow<Unit> = _hardLogout.asSharedFlow()

    fun notifyRefreshFailed() {
        _hardLogout.tryEmit(Unit)
    }
}

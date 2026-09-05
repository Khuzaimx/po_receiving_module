package com.sellernest.poreceiving.core.connectivity

import kotlinx.coroutines.flow.Flow

/**
 * Backs the persistent status bar's ONLINE/OFFLINE indicator (§3.2). "Online"
 * means the device currently has a network with the internet capability -- not
 * merely that Wi-Fi radio is on.
 */
interface ConnectivityObserver {
    val isOnline: Flow<Boolean>
}

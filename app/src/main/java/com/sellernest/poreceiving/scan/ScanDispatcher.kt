package com.sellernest.poreceiving.scan

import javax.inject.Inject
import javax.inject.Singleton

/**
 * §4.1: "Hardware trigger and camera must both dispatch into a single
 * onScan(code, source) entry point. No screen may implement its own scan
 * handling." Every future input source (DataWedge in M2.2, keyboard wedge in
 * M2.3, CameraX+ML Kit in M2.4) calls [dispatch]; every screen registers via
 * [com.sellernest.poreceiving.scan.compose.ScanFocusEffect], never directly.
 *
 * At most one listener is ever "current" -- registering a new one silently
 * replaces whatever was registered before, so exactly one screen receives a
 * scan even if several are technically alive in the back stack.
 */
@Singleton
class ScanDispatcher @Inject constructor() {

    @Volatile
    private var currentOwner: ScanListener? = null

    fun register(listener: ScanListener) {
        currentOwner = listener
    }

    /** No-ops if [listener] is not the current owner -- an already-superseded
     *  screen unregistering on disposal must never clear the new owner. */
    fun unregister(listener: ScanListener) {
        if (currentOwner === listener) {
            currentOwner = null
        }
    }

    fun dispatch(code: String, source: ScanSource) {
        currentOwner?.onScan(code, source)
    }
}

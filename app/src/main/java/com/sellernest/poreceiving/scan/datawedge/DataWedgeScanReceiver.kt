package com.sellernest.poreceiving.scan.datawedge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sellernest.poreceiving.scan.ScanDispatcher
import com.sellernest.poreceiving.scan.ScanSource

/**
 * Receives DataWedge's scan-result broadcast and forwards it into the single
 * shared [ScanDispatcher] (§4.1). Takes its dependency as a plain constructor
 * parameter rather than via Hilt field injection: [DataWedgeIntegration]
 * (already Hilt-injected) constructs and registers this instance itself, so
 * there is no need to make this class an `@AndroidEntryPoint` receiver.
 */
class DataWedgeScanReceiver(private val scanDispatcher: ScanDispatcher) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DataWedgeConfig.SCAN_INTENT_ACTION) return
        val code = intent.getStringExtra(DataWedgeConfig.SCAN_INTENT_KEY_DATA) ?: return
        scanDispatcher.dispatch(code, ScanSource.HARDWARE_INTENT)
    }
}

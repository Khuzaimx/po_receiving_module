package com.sellernest.poreceiving.scan.datawedge

import android.content.Context
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.sellernest.poreceiving.scan.ScanDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wires DataWedge into the app: configures the profile (§2.1) and registers
 * the receiver for its scan broadcasts. [start] is called once from
 * [com.sellernest.poreceiving.PoReceivingApp.onCreate].
 *
 * The receiver is registered with [ContextCompat.RECEIVER_EXPORTED], not
 * `RECEIVER_NOT_EXPORTED`: DataWedge is a separate app, so its broadcast is a
 * cross-app (external) one -- `NOT_EXPORTED` would silently block it on
 * API 33+, where Android requires this flag to be stated explicitly.
 */
@Singleton
class DataWedgeIntegration @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scanDispatcher: ScanDispatcher,
    private val profileManager: DataWedgeProfileManager,
) {
    private var registeredReceiver: DataWedgeScanReceiver? = null

    fun start() {
        if (registeredReceiver != null) return

        profileManager.ensureProfileConfigured()

        val receiver = DataWedgeScanReceiver(scanDispatcher)
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(DataWedgeConfig.SCAN_INTENT_ACTION),
            ContextCompat.RECEIVER_EXPORTED,
        )
        registeredReceiver = receiver
    }
}

package com.sellernest.poreceiving.scan.datawedge

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sellernest.poreceiving.scan.ScanDispatcher
import com.sellernest.poreceiving.scan.ScanListener
import com.sellernest.poreceiving.scan.ScanSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M2.2: the receiver correctly forwards DataWedge's scan data into
 * [ScanDispatcher] with [ScanSource.HARDWARE_INTENT]. Instrumented because it
 * exercises real `Intent` extras, which a plain JVM test's Android stub jar
 * cannot round-trip.
 */
@RunWith(AndroidJUnit4::class)
class DataWedgeScanReceiverInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun forwardsTheScannedDataAsAHardwareIntentScan() {
        val dispatcher = ScanDispatcher()
        var received: Pair<String, ScanSource>? = null
        dispatcher.register(ScanListener { code, source -> received = code to source })
        val receiver = DataWedgeScanReceiver(dispatcher)

        val intent = Intent(DataWedgeConfig.SCAN_INTENT_ACTION)
            .putExtra(DataWedgeConfig.SCAN_INTENT_KEY_DATA, "0468673502897")
            .putExtra(DataWedgeConfig.SCAN_INTENT_KEY_SYMBOLOGY, "UPC-A")

        receiver.onReceive(context, intent)

        assertEquals("0468673502897" to ScanSource.HARDWARE_INTENT, received)
    }

    @Test
    fun ignoresIntentsWithAnUnrelatedAction() {
        val dispatcher = ScanDispatcher()
        var receivedCount = 0
        dispatcher.register(ScanListener { _, _ -> receivedCount++ })
        val receiver = DataWedgeScanReceiver(dispatcher)

        receiver.onReceive(context, Intent("some.other.action").putExtra(DataWedgeConfig.SCAN_INTENT_KEY_DATA, "X"))

        assertEquals(0, receivedCount)
    }

    @Test
    fun ignoresAMatchingActionWithNoDataExtra() {
        val dispatcher = ScanDispatcher()
        var received: String? = null
        dispatcher.register(ScanListener { code, _ -> received = code })
        val receiver = DataWedgeScanReceiver(dispatcher)

        receiver.onReceive(context, Intent(DataWedgeConfig.SCAN_INTENT_ACTION))

        assertNull(received)
    }
}

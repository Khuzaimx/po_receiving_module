package com.sellernest.poreceiving.scan.datawedge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * M2.2 acceptance criteria: "Installing on a non-Zebra device produces no
 * crash" (this emulator has no DataWedge installed, so every command here
 * really does land with zero receivers) and a sanity check that configuring
 * the profile sends more than one command -- create, app association, decoder
 * config, and intent-output config are each a separate broadcast.
 */
@RunWith(AndroidJUnit4::class)
class DataWedgeProfileManagerInstrumentedTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun ensureProfileConfiguredDoesNotThrowWithNoDataWedgeInstalled() {
        // The absence of a thrown exception is this test's entire point.
        DataWedgeProfileManager(context).ensureProfileConfigured()
    }

    @Test
    fun ensureProfileConfiguredSendsMultipleDistinctApiCommands() {
        val latch = CountDownLatch(4) // create + associate-app + barcode + intent-output
        var observedCount = 0
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                observedCount++
                latch.countDown()
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(DataWedgeConfig.API_ACTION),
            ContextCompat.RECEIVER_EXPORTED,
        )

        try {
            DataWedgeProfileManager(context).ensureProfileConfigured()
            val allArrived = latch.await(5, TimeUnit.SECONDS)
            assertTrue("Expected 4 DataWedge API broadcasts, observed $observedCount", allArrived)
            assertEquals(4, observedCount)
        } finally {
            context.unregisterReceiver(receiver)
        }
    }
}

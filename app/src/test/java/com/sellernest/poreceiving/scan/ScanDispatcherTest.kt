package com.sellernest.poreceiving.scan

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * M2.1 acceptance criteria: "Exactly one screen receives a scan when several
 * are in the back stack" and "Source is reported accurately."
 */
class ScanDispatcherTest {

    private data class Received(val code: String, val source: ScanSource)

    @Test
    fun `dispatch delivers to the registered listener with the correct source`() {
        val dispatcher = ScanDispatcher()
        var received: Received? = null
        dispatcher.register(ScanListener { code, source -> received = Received(code, source) })

        dispatcher.dispatch("0468673502897", ScanSource.CAMERA)

        assertEquals(Received("0468673502897", ScanSource.CAMERA), received)
    }

    @Test
    fun `registering a second listener replaces the first as sole owner`() {
        val dispatcher = ScanDispatcher()
        var firstReceivedCount = 0
        var secondReceived: Received? = null
        dispatcher.register(ScanListener { _, _ -> firstReceivedCount++ })
        dispatcher.register(ScanListener { code, source -> secondReceived = Received(code, source) })

        dispatcher.dispatch("ABC123", ScanSource.HARDWARE_INTENT)

        assertEquals(0, firstReceivedCount)
        assertEquals(Received("ABC123", ScanSource.HARDWARE_INTENT), secondReceived)
    }

    @Test
    fun `unregistering the current owner leaves no listener to receive scans`() {
        val dispatcher = ScanDispatcher()
        var receivedCount = 0
        val listener = ScanListener { _, _ -> receivedCount++ }
        dispatcher.register(listener)
        dispatcher.unregister(listener)

        dispatcher.dispatch("ABC123", ScanSource.KEYBOARD_WEDGE)

        assertEquals(0, receivedCount)
    }

    @Test
    fun `unregistering a superseded listener does not clear the new owner`() {
        val dispatcher = ScanDispatcher()
        val stale = ScanListener { _, _ -> error("stale listener must never be invoked") }
        var currentReceived: Received? = null
        dispatcher.register(stale)
        dispatcher.register(ScanListener { code, source -> currentReceived = Received(code, source) })

        // A disposed screen unregistering after another has already taken focus.
        dispatcher.unregister(stale)
        dispatcher.dispatch("XYZ789", ScanSource.CAMERA)

        assertEquals(Received("XYZ789", ScanSource.CAMERA), currentReceived)
    }

    @Test
    fun `dispatch before anything registers is a silent no-op`() {
        // No assertion needed: a NullPointerException here would fail this test
        // on its own. The point is that dispatching with no owner must not throw.
        ScanDispatcher().dispatch("ABC123", ScanSource.CAMERA)
    }
}

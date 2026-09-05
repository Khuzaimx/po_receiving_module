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

    @Test
    fun `a trailing CRLF and surrounding whitespace are stripped before the listener sees the code`() {
        val dispatcher = ScanDispatcher()
        val received = mutableListOf<String>()
        dispatcher.register(ScanListener { code, _ -> received.add(code) })

        dispatcher.dispatch("ABC123\r\n", ScanSource.HARDWARE_INTENT)

        assertEquals(listOf("ABC123"), received)
    }

    @Test
    fun `surrounding spaces are stripped the same way as CRLF`() {
        val dispatcher = ScanDispatcher()
        val received = mutableListOf<String>()
        dispatcher.register(ScanListener { code, _ -> received.add(code) })

        dispatcher.dispatch(" ABC123 ", ScanSource.KEYBOARD_WEDGE)

        assertEquals(listOf("ABC123"), received)
    }

    @Test
    fun `an immediate repeat of the same code is debounced`() {
        val dispatcher = ScanDispatcher()
        val received = mutableListOf<String>()
        dispatcher.register(ScanListener { code, _ -> received.add(code) })

        dispatcher.dispatch("ABC123", ScanSource.CAMERA)
        dispatcher.dispatch("ABC123", ScanSource.CAMERA) // well within 800ms of the first

        assertEquals(listOf("ABC123"), received)
    }

    @Test
    fun `a different code arriving immediately after is not debounced`() {
        val dispatcher = ScanDispatcher()
        val received = mutableListOf<String>()
        dispatcher.register(ScanListener { code, _ -> received.add(code) })

        dispatcher.dispatch("ABC123", ScanSource.CAMERA)
        dispatcher.dispatch("XYZ789", ScanSource.CAMERA)

        assertEquals(listOf("ABC123", "XYZ789"), received)
    }

    @Test
    fun `a code that is only whitespace after stripping is not dispatched`() {
        val dispatcher = ScanDispatcher()
        val received = mutableListOf<String>()
        dispatcher.register(ScanListener { code, _ -> received.add(code) })

        dispatcher.dispatch("   \r\n", ScanSource.KEYBOARD_WEDGE)

        assertEquals(emptyList<String>(), received)
    }
}

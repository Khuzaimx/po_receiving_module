package com.sellernest.poreceiving.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M2.5 acceptance criteria, tested against the pure functions directly so the
 * "after the debounce window elapses, the same code is accepted again" case
 * doesn't need a real 800ms sleep in the suite -- [ScanDispatcherTest] covers
 * the integrated behaviour with real (short) timings.
 */
class ScanHygieneTest {

    @Test
    fun `sanitizeScanCode strips a trailing CRLF`() {
        assertEquals("ABC123", sanitizeScanCode("ABC123\r\n"))
    }

    @Test
    fun `sanitizeScanCode strips surrounding whitespace`() {
        assertEquals("ABC123", sanitizeScanCode("  ABC123  "))
    }

    @Test
    fun `sanitizeScanCode leaves an already-clean code untouched`() {
        assertEquals("ABC123", sanitizeScanCode("ABC123"))
    }

    @Test
    fun `isDuplicateWithinDebounceWindow is true for the same code just inside the window`() {
        val now = 10_000L
        assertTrue(
            isDuplicateWithinDebounceWindow(
                code = "ABC123",
                lastAcceptedCode = "ABC123",
                nowMillis = now,
                lastAcceptedAtMillis = now - (DEBOUNCE_WINDOW_MS - 1),
            ),
        )
    }

    @Test
    fun `isDuplicateWithinDebounceWindow is false once the window has fully elapsed`() {
        val now = 10_000L
        assertFalse(
            isDuplicateWithinDebounceWindow(
                code = "ABC123",
                lastAcceptedCode = "ABC123",
                nowMillis = now,
                lastAcceptedAtMillis = now - DEBOUNCE_WINDOW_MS,
            ),
        )
    }

    @Test
    fun `isDuplicateWithinDebounceWindow is false for a different code regardless of timing`() {
        val now = 10_000L
        assertFalse(
            isDuplicateWithinDebounceWindow(
                code = "XYZ789",
                lastAcceptedCode = "ABC123",
                nowMillis = now,
                lastAcceptedAtMillis = now,
            ),
        )
    }

    @Test
    fun `isDuplicateWithinDebounceWindow is false when nothing has been accepted yet`() {
        assertFalse(
            isDuplicateWithinDebounceWindow(
                code = "ABC123",
                lastAcceptedCode = null,
                nowMillis = 10_000L,
                lastAcceptedAtMillis = 0L,
            ),
        )
    }
}

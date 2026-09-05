package com.sellernest.poreceiving.scan.wedge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * M2.3 acceptance criterion: "A wedge burst is recognised as a scan; the same
 * string typed by hand is not." Timestamps are injected explicitly so the
 * whole suite runs instantly regardless of the real 50ms threshold.
 */
class WedgeBurstDetectorTest {

    @Test
    fun `a fast burst ending in LF is recognised as a scan`() {
        val detector = WedgeBurstDetector(maxInterCharacterGapMillis = 50)
        var t = 0L
        "ABC123".forEach { char ->
            assertNull(detector.onCharacter(char, t))
            t += 5 // well within the 50ms threshold
        }

        assertEquals("ABC123", detector.onCharacter('\n', t))
    }

    @Test
    fun `a fast burst ending in CR is also recognised`() {
        val detector = WedgeBurstDetector(maxInterCharacterGapMillis = 50)
        var t = 0L
        "ABC123".forEach { char ->
            detector.onCharacter(char, t)
            t += 5
        }

        assertEquals("ABC123", detector.onCharacter('\r', t))
    }

    @Test
    fun `the same string typed by hand at human speed is not recognised`() {
        val detector = WedgeBurstDetector(maxInterCharacterGapMillis = 50)
        var t = 0L
        "ABC123".forEach { char ->
            detector.onCharacter(char, t)
            t += 150 // realistic human keystroke interval, well over the threshold
        }

        assertNull(detector.onCharacter('\n', t))
    }

    @Test
    fun `a slow prefix is discarded but a fast suffix afterward still completes`() {
        val detector = WedgeBurstDetector(maxInterCharacterGapMillis = 50)
        var t = 0L
        detector.onCharacter('X', t) // slow, stray character
        t += 300
        "ABC123".forEach { char ->
            detector.onCharacter(char, t)
            t += 5
        }

        // Only the fast run survives -- the stray "X" was discarded when its
        // successor arrived too slowly to be part of the same burst.
        assertEquals("ABC123", detector.onCharacter('\n', t))
    }

    @Test
    fun `a terminator with no preceding characters produces nothing`() {
        val detector = WedgeBurstDetector(maxInterCharacterGapMillis = 50)

        assertNull(detector.onCharacter('\n', 0L))
    }

    @Test
    fun `the buffer resets after a completed scan so the next burst starts clean`() {
        val detector = WedgeBurstDetector(maxInterCharacterGapMillis = 50)
        var t = 0L
        "AAA".forEach { char -> detector.onCharacter(char, t); t += 5 }
        detector.onCharacter('\n', t)
        t += 5

        "BBB".forEach { char -> detector.onCharacter(char, t); t += 5 }
        assertEquals("BBB", detector.onCharacter('\n', t))
    }
}

package com.sellernest.poreceiving.scan.wedge

/**
 * §4.1: "Distinguish wedge input from human typing by inter-character timing
 * (wedge bursts arrive far faster than typing) and by the trailing
 * terminator." A pure state machine, fed one character at a time with a clock
 * reading, so the timing decision is unit-testable with exact, reproducible
 * timestamps rather than real sleeps.
 *
 * Any character arriving slower than [maxInterCharacterGapMillis] after the
 * previous one discards the buffer and starts fresh from that character --
 * a human typing the eventual right answer, one keystroke every 100-300+ ms,
 * never accumulates into a burst; a wedge scanner's characters, which arrive
 * within single-digit-to-low-tens of milliseconds of each other, do.
 */
internal class WedgeBurstDetector(
    private val maxInterCharacterGapMillis: Long = DEFAULT_MAX_GAP_MS,
) {
    private val buffer = StringBuilder()
    private var lastCharAtMillis: Long? = null

    /**
     * Call once per character as it arrives. Returns the completed scan code
     * the instant a terminator (`\r` or `\n`) completes a valid burst;
     * null otherwise -- including when a terminator arrives but the run
     * leading up to it was too slow, or the buffer was empty.
     */
    fun onCharacter(char: Char, nowMillis: Long): String? {
        if (char == '\r' || char == '\n') {
            val previous = lastCharAtMillis
            val text = buffer.toString()
            reset()
            val withinBurstTiming = previous != null && (nowMillis - previous) <= maxInterCharacterGapMillis
            return text.takeIf { it.isNotEmpty() && withinBurstTiming }
        }

        val previous = lastCharAtMillis
        if (previous != null && nowMillis - previous > maxInterCharacterGapMillis) {
            buffer.clear()
        }
        buffer.append(char)
        lastCharAtMillis = nowMillis
        return null
    }

    private fun reset() {
        buffer.clear()
        lastCharAtMillis = null
    }

    companion object {
        const val DEFAULT_MAX_GAP_MS = 50L
    }
}

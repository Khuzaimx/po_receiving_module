package com.sellernest.poreceiving.scan

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M0.6 acceptance criteria: "Accepted and rejected produce audibly and tactilely
 * different signals" and "Feedback fires within the 150 ms scan-to-feedback
 * budget." Requires a connected device or emulator (real `AudioManager` /
 * `Vibrator` system services).
 *
 * This cannot record actual sound or vibration output, so it verifies the two
 * things that are mechanically checkable: the constants driving each signal
 * genuinely differ between outcomes (a same-tone-same-duration bug would defeat
 * the whole feature silently), and calling `accepted()`/`rejected()` -- which are
 * fire-and-forget over `ToneGenerator`/`Vibrator`, not blocking on playback --
 * returns fast enough to fit comfortably inside the 150 ms budget. The full
 * end-to-end scan-to-visible-feedback latency is M7.2's job.
 */
@RunWith(AndroidJUnit4::class)
class ScanFeedbackServiceInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val service = ToneAndHapticScanFeedbackService(context)

    @Test
    fun acceptedAndRejectedUseDistinctToneAndVibrationDurations() {
        assertNotEquals(
            "Accepted and rejected must not share a vibration duration",
            ToneAndHapticScanFeedbackService.ACCEPTED_VIBRATION_DURATION_MS,
            ToneAndHapticScanFeedbackService.REJECTED_VIBRATION_DURATION_MS,
        )
        assertTrue(
            "Rejected's haptic must be longer than accepted's, not merely different",
            ToneAndHapticScanFeedbackService.REJECTED_VIBRATION_DURATION_MS >
                ToneAndHapticScanFeedbackService.ACCEPTED_VIBRATION_DURATION_MS,
        )
        assertNotEquals(
            "Accepted and rejected must not share a tone duration",
            ToneAndHapticScanFeedbackService.ACCEPTED_TONE_DURATION_MS,
            ToneAndHapticScanFeedbackService.REJECTED_TONE_DURATION_MS,
        )
    }

    @Test
    fun acceptedCallReturnsWellWithinTheScanFeedbackBudget() {
        val elapsedMs = measureCallMillis { service.accepted() }
        assertTrue("accepted() took ${elapsedMs}ms, expected well under 150ms", elapsedMs < 100)
    }

    @Test
    fun rejectedCallReturnsWellWithinTheScanFeedbackBudget() {
        val elapsedMs = measureCallMillis { service.rejected() }
        assertTrue("rejected() took ${elapsedMs}ms, expected well under 150ms", elapsedMs < 100)
    }

    private inline fun measureCallMillis(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / 1_000_000
    }
}

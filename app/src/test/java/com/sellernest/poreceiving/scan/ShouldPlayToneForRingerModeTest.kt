package com.sellernest.poreceiving.scan

import android.media.AudioManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M0.6 acceptance criterion: "With the device muted, a rejected scan is still
 * unmistakable via haptic + visual" -- this is the pure decision behind that:
 * only the tone is ever suppressed, never [ToneAndHapticScanFeedbackService.vibrate].
 * Referencing [AudioManager]'s `RINGER_MODE_*` constants here is safe in a plain
 * JVM unit test: they are compile-time-constant `int` fields, inlined by the
 * compiler, so no real `AudioManager` instance is ever touched.
 */
class ShouldPlayToneForRingerModeTest {

    @Test
    fun `plays tone only in normal ringer mode`() {
        assertTrue(shouldPlayToneForRingerMode(AudioManager.RINGER_MODE_NORMAL))
    }

    @Test
    fun `suppresses tone in silent ringer mode`() {
        assertFalse(shouldPlayToneForRingerMode(AudioManager.RINGER_MODE_SILENT))
    }

    @Test
    fun `suppresses tone in vibrate ringer mode`() {
        assertFalse(shouldPlayToneForRingerMode(AudioManager.RINGER_MODE_VIBRATE))
    }
}

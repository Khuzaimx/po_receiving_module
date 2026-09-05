package com.sellernest.poreceiving.scan

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Sound via [ToneGenerator] (a short synthesized tone -- no bundled audio asset
 * needed) and haptics via [VibrationEffect]. One instance for the app's process
 * lifetime; both the tone generator and the vibrator service handle are cheap to
 * hold and are never released early, which would risk cutting a tone off
 * mid-playback.
 */
class ToneAndHapticScanFeedbackService @Inject constructor(
    @ApplicationContext context: Context,
) : ScanFeedbackService {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val vibrator: Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

    private val toneGenerator = runCatching {
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, ToneGenerator.MAX_VOLUME)
    }.getOrNull()

    override fun accepted() {
        playTone(ToneGenerator.TONE_PROP_ACK, ACCEPTED_TONE_DURATION_MS)
        vibrate(ACCEPTED_VIBRATION_DURATION_MS)
    }

    override fun rejected() {
        playTone(ToneGenerator.TONE_PROP_NACK, REJECTED_TONE_DURATION_MS)
        vibrate(REJECTED_VIBRATION_DURATION_MS)
    }

    private fun playTone(toneType: Int, durationMs: Int) {
        if (!shouldPlayToneForRingerMode(audioManager.ringerMode)) return
        toneGenerator?.startTone(toneType, durationMs)
    }

    private fun vibrate(durationMs: Long) {
        vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    // internal, not private: ScanFeedbackServiceInstrumentedTest asserts accepted
    // and rejected differ on both dimensions without duplicating magic numbers.
    internal companion object {
        const val ACCEPTED_TONE_DURATION_MS = 120
        const val REJECTED_TONE_DURATION_MS = 300

        const val ACCEPTED_VIBRATION_DURATION_MS = 40L
        const val REJECTED_VIBRATION_DURATION_MS = 200L
    }
}

/**
 * §3.1: audio respects silent/vibrate ringer mode; haptics never do -- [vibrate]
 * is called unconditionally regardless of this result. A free function (not a
 * method on the service) so it can be unit-tested without an Android device: it
 * takes the ringer mode as plain data rather than reading a live [AudioManager].
 */
internal fun shouldPlayToneForRingerMode(ringerMode: Int): Boolean = ringerMode == AudioManager.RINGER_MODE_NORMAL

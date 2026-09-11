package com.sellernest.poreceiving.scan.camera

import android.content.Context
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.common.InputImage
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Turns ML Kit's per-frame barcode result into a one-shot "dispatch this
 * code" edge, so holding the camera steady over one barcode reports it once
 * instead of many times a second. A code only dispatches again once it has
 * been absent for [MISSED_FRAMES_TO_CLEAR] consecutive frames -- a single
 * missed frame from motion blur or refocusing must not immediately re-arm the
 * same code, or a steady hold would still double-count on that flicker.
 *
 * ML Kit's success-listener callback isn't guaranteed to run on the analysis
 * executor thread, so state here is synchronized rather than a plain var.
 */
internal class EdgeTriggeredBarcodeDetector {
    private var lastDispatchedCode: String? = null
    private var missedFrameStreak = 0

    /** Returns the code to dispatch, or null if this frame shouldn't trigger a scan. */
    @Synchronized
    fun onFrameResult(code: String?): String? {
        if (code == null) {
            missedFrameStreak++
            if (missedFrameStreak >= MISSED_FRAMES_TO_CLEAR) {
                lastDispatchedCode = null
            }
            return null
        }

        missedFrameStreak = 0
        if (code == lastDispatchedCode) return null

        lastDispatchedCode = code
        return code
    }

    private companion object {
        const val MISSED_FRAMES_TO_CLEAR = 8
    }
}

/** Bridges CameraX's `ListenableFuture`-based provider lookup into a suspend call. */
internal suspend fun Context.awaitCameraProvider(): ProcessCameraProvider =
    suspendCancellableCoroutine { continuation ->
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener(
            { continuation.resume(future.get()) },
            ContextCompat.getMainExecutor(this),
        )
    }

/**
 * One ML Kit barcode pass over one camera frame. Always closes [imageProxy]
 * exactly once, on completion (success or failure), which is what lets
 * CameraX supply the next frame -- an unclosed [ImageProxy] stalls the
 * pipeline.
 *
 * [onFrameResult] fires every frame, including with `null` when nothing was
 * decoded -- callers need the negative result too, to tell "still holding
 * over the same code" apart from "moved away and back," see
 * [EdgeTriggeredBarcodeDetector].
 */
@ExperimentalGetImage
internal fun analyzeFrameForBarcode(
    scanner: BarcodeScanner,
    imageProxy: ImageProxy,
    onFrameResult: (String?) -> Unit,
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }

    val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    scanner.process(inputImage)
        .addOnSuccessListener { barcodes ->
            onFrameResult(barcodes.firstOrNull()?.rawValue)
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}

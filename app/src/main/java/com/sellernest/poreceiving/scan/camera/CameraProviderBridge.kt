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
 */
@ExperimentalGetImage
internal fun analyzeFrameForBarcode(
    scanner: BarcodeScanner,
    imageProxy: ImageProxy,
    onBarcodeDetected: (String) -> Unit,
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }

    val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    scanner.process(inputImage)
        .addOnSuccessListener { barcodes ->
            barcodes.firstOrNull()?.rawValue?.let(onBarcodeDetected)
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}

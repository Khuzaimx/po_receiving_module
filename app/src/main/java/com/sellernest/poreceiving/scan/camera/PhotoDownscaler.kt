package com.sellernest.poreceiving.scan.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * M4.4: "Photos are downscaled to <= 1600 px and <= 2 MB before queueing."
 * Downsamples to the target longest edge first (cheap, and avoids ever
 * holding a full-resolution [Bitmap] in memory for a large capture), then --
 * only if still over the byte budget -- steps the JPEG quality down until it
 * fits, since a busy warehouse-floor photo can still exceed 2 MB at 1600 px
 * and full quality. Never drops quality below [MIN_JPEG_QUALITY]: the §9.5
 * server-side 10 MB ceiling this exists to stay well under is generous enough
 * that an unusably compressed photo is the wrong trade at that point.
 */
object PhotoDownscaler {
    private const val MAX_DIMENSION_PX = 1600
    private const val MAX_BYTES = 2L * 1024 * 1024
    private const val MIN_JPEG_QUALITY = 40

    fun downscaleInPlace(file: File) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val longestEdge = maxOf(bounds.outWidth, bounds.outHeight)

        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = inSampleSizeFor(longestEdge) }
        val decoded = BitmapFactory.decodeFile(file.path, decodeOptions) ?: return
        val scaled = scaleToMaxDimension(decoded)

        var quality = 90
        var bytes = compress(scaled, quality)
        while (bytes.size > MAX_BYTES && quality > MIN_JPEG_QUALITY) {
            quality -= 10
            bytes = compress(scaled, quality)
        }

        FileOutputStream(file).use { it.write(bytes) }
        if (scaled !== decoded) decoded.recycle()
        scaled.recycle()
    }

    private fun inSampleSizeFor(longestEdge: Int): Int {
        var sampleSize = 1
        while (longestEdge / (sampleSize * 2) >= MAX_DIMENSION_PX) sampleSize *= 2
        return sampleSize
    }

    private fun scaleToMaxDimension(bitmap: Bitmap): Bitmap {
        val longestEdge = maxOf(bitmap.width, bitmap.height)
        if (longestEdge <= MAX_DIMENSION_PX) return bitmap
        val scale = MAX_DIMENSION_PX.toFloat() / longestEdge
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun compress(bitmap: Bitmap, quality: Int): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return stream.toByteArray()
    }
}

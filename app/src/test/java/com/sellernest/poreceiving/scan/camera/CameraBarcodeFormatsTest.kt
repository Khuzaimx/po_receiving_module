package com.sellernest.poreceiving.scan.camera

import com.sellernest.poreceiving.scan.datawedge.DataWedgeConfig
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * M2.4 acceptance criterion: "Only the twelve listed symbologies decode;
 * others are ignored." Also cross-checks against
 * [com.sellernest.poreceiving.scan.datawedge.DataWedgeConfig]'s enabled
 * decoder count (M2.2) -- §4.3 lists exactly one set of symbologies that both
 * input sources must honour identically.
 */
class CameraBarcodeFormatsTest {

    @Test
    fun `exactly twelve formats are enabled, matching §4-3`() {
        assertEquals(12, allowedCameraBarcodeFormats.size)
        assertEquals(
            "allowedCameraBarcodeFormats must not contain duplicates",
            allowedCameraBarcodeFormats.size,
            allowedCameraBarcodeFormats.toSet().size,
        )
    }

    @Test
    fun `camera and DataWedge enable the same number of symbologies`() {
        assertEquals(
            DataWedgeConfig.enabledDecoderParams.size,
            allowedCameraBarcodeFormats.size,
        )
    }
}

package com.sellernest.poreceiving.scan.datawedge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M2.2 acceptance criterion: "Only the symbologies in §4.3 are enabled in the
 * created profile." §4.3: "Code 128, Code 39, Code 93, Codabar, EAN-13, EAN-8,
 * UPC-A, UPC-E, ITF, QR Code, Data Matrix, PDF417" -- twelve, no more.
 */
class DataWedgeConfigTest {

    @Test
    fun `exactly twelve decoders are enabled, matching the count in §4-3`() {
        assertEquals(12, DataWedgeConfig.enabledDecoderParams.size)
        assertEquals(
            "enabledDecoderParams must not contain duplicates",
            DataWedgeConfig.enabledDecoderParams.size,
            DataWedgeConfig.enabledDecoderParams.toSet().size,
        )
    }

    @Test
    fun `no decoder appears in both the enabled and disabled lists`() {
        val overlap = DataWedgeConfig.enabledDecoderParams.toSet()
            .intersect(DataWedgeConfig.disabledDecoderParams.toSet())
        assertTrue("A decoder cannot be both enabled and disabled: $overlap", overlap.isEmpty())
    }
}

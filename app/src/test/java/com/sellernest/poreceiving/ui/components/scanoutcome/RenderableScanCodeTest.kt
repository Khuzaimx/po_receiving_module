package com.sellernest.poreceiving.ui.components.scanoutcome

import org.junit.Assert.assertEquals
import org.junit.Test

/** M2.6 acceptance criterion: "The unknown state displays the raw string
 *  verbatim, with unprintable characters rendered visibly." */
class RenderableScanCodeTest {

    @Test
    fun `printable characters pass through unchanged`() {
        assertEquals("ABC-123_xyz", renderableScanCode("ABC-123_xyz"))
    }

    @Test
    fun `a null byte becomes its visible control picture glyph`() {
        assertEquals("\u2400", renderableScanCode("\u0000"))
    }

    @Test
    fun `a stray CR or LF becomes visible rather than disappearing`() {
        assertEquals("\u240D\u240A", renderableScanCode("\r\n"))
    }

    @Test
    fun `DEL becomes its own distinct control picture, not the NUL one`() {
        assertEquals("\u2421", renderableScanCode("\u007F"))
    }

    @Test
    fun `a mix of printable and unprintable characters converts only the unprintable ones`() {
        assertEquals("AB\u2400CD", renderableScanCode("AB\u0000CD"))
    }
}

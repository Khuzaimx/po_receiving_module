package com.sellernest.poreceiving.theme

import com.sellernest.poreceiving.ui.theme.PoReceivingColors
import com.sellernest.poreceiving.ui.theme.StateTone
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Requirements §3.1: "Minimum 7:1 for text (exceeding WCAG AA)... Pure white
 * background, near-black text."
 *
 * [PoReceivingColors.onSurface] is the app's only text colour. This test enumerates
 * every background token it is ever drawn against — including the four state-tone
 * backgrounds it appears on inside [com.sellernest.poreceiving.ui.components.StateBadge]
 * — and fails the build the moment any of them drops below 7:1. A future palette
 * change that regresses contrast is caught here, not in a design review.
 */
class ContrastTest {

    private val minimumRatio = 7.0

    @Test
    fun `body text on background meets 7 to 1`() {
        assertAtLeastSevenToOne(PoReceivingColors.onSurface, PoReceivingColors.background)
    }

    @Test
    fun `body text on surface meets 7 to 1`() {
        assertAtLeastSevenToOne(PoReceivingColors.onSurface, PoReceivingColors.surface)
    }

    @Test
    fun `body text on surfaceVariant meets 7 to 1`() {
        assertAtLeastSevenToOne(PoReceivingColors.onSurface, PoReceivingColors.surfaceVariant)
    }

    @Test
    fun `primary button label meets 7 to 1`() {
        assertAtLeastSevenToOne(PoReceivingColors.onPrimary, PoReceivingColors.primary)
    }

    @Test
    fun `state badge label meets 7 to 1 against every tone background`() {
        StateTone.values().forEach { tone ->
            assertAtLeastSevenToOne(
                PoReceivingColors.onSurface,
                tone.background,
                context = "onSurface vs ${tone.name} background",
            )
        }
    }

    private fun assertAtLeastSevenToOne(
        foreground: androidx.compose.ui.graphics.Color,
        background: androidx.compose.ui.graphics.Color,
        context: String = "",
    ) {
        val ratio = ContrastRatio.of(foreground, background)
        assertTrue(
            "Expected contrast >= $minimumRatio:1 $context but was %.2f:1".format(ratio),
            ratio >= minimumRatio,
        )
    }
}

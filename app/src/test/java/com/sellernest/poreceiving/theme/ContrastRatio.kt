package com.sellernest.poreceiving.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/**
 * WCAG 2.x relative luminance and contrast ratio, computed directly from sRGB
 * channel values — no Android framework dependency, so this runs as a plain JVM
 * unit test against the real [com.sellernest.poreceiving.ui.theme.PoReceivingColors]
 * tokens.
 *
 * https://www.w3.org/TR/WCAG21/#dfn-relative-luminance
 */
internal object ContrastRatio {

    private fun channelToLinear(channel: Float): Double {
        val c = channel.toDouble()
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun relativeLuminance(color: Color): Double {
        val r = channelToLinear(color.red)
        val g = channelToLinear(color.green)
        val b = channelToLinear(color.blue)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /** Contrast ratio between two colours, order-independent, in the range [1, 21]. */
    fun of(a: Color, b: Color): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        val lighter = maxOf(la, lb)
        val darker = minOf(la, lb)
        return (lighter + 0.05) / (darker + 0.05)
    }
}

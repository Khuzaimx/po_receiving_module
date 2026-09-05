package com.sellernest.poreceiving.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The single high-contrast light palette (requirements §3.1). There is no dark
 * variant — see [Theme.kt][PoReceivingTheme] and the `values-v27` window theme,
 * neither of which branch on system dark mode.
 *
 * Two rules this file exists to enforce:
 *  - "Minimum 7:1 for text (exceeding WCAG AA)... Pure white background, near-black text."
 *    [onSurface] is the *only* text colour used anywhere in the app; it is always
 *    paired with one of the light background tokens below, each verified ≥ 7:1 by
 *    `ContrastTest`.
 *  - "Colour is used only for state, never decoration... Colour is never the sole
 *    signal: always paired with an icon and a text label." The four `tone*` colours
 *    below are therefore never used as a text colour — only as an icon tint or as
 *    the fill for their matching light `tone*Background`. [StateBadge] is the only
 *    place they are consumed, and it always renders an icon and a label alongside them.
 */
object PoReceivingColors {
    // Neutral surfaces — every one of these is paired with onSurface at ≥ 7:1.
    val background = Color(0xFFFFFFFF)
    val surface = Color(0xFFFFFFFF)
    val surfaceVariant = Color(0xFFF2F2F2)
    val onSurface = Color(0xFF121212)

    // Primary action fill (a filled, near-black control — deliberately industrial,
    // not a lifestyle-app accent colour). onPrimary is verified ≥ 7:1 against it.
    val primary = Color(0xFF121212)
    val onPrimary = Color(0xFFFFFFFF)

    val outline = Color(0xFFB0B0B0)

    // State tones (§3.1: "Green = confirmed, amber = variance/attention,
    // red = error/blocked, blue = pending/queued"). Icon-tint use only.
    val toneConfirmed = Color(0xFF1E8E3E)
    val toneVariance = Color(0xFFB25900)
    val toneError = Color(0xFFB3261E)
    val tonePending = Color(0xFF1A56DB)

    // Light tint backgrounds for the same four states. onSurface text over any of
    // these still clears 7:1 — see ContrastTest.
    val toneConfirmedBackground = Color(0xFFE6F4EA)
    val toneVarianceBackground = Color(0xFFFCEEDB)
    val toneErrorBackground = Color(0xFFFBEAE9)
    val tonePendingBackground = Color(0xFFE3EEFC)
}

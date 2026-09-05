package com.sellernest.poreceiving.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The four states a scan or line outcome can be in (§3.1, §7.6).
 *
 * This is the type that makes "colour is never the sole signal" structural rather
 * than a convention: there is no API that accepts a bare [Color] for state. Callers
 * pick a [StateTone] and [StateBadge][com.sellernest.poreceiving.ui.components.StateBadge]
 * renders its icon, its accent colour, *and* the caller-supplied text together.
 */
@Immutable
enum class StateTone(val accent: Color, val background: Color) {
    Confirmed(PoReceivingColors.toneConfirmed, PoReceivingColors.toneConfirmedBackground),
    Variance(PoReceivingColors.toneVariance, PoReceivingColors.toneVarianceBackground),
    Error(PoReceivingColors.toneError, PoReceivingColors.toneErrorBackground),
    Pending(PoReceivingColors.tonePending, PoReceivingColors.tonePendingBackground),
}

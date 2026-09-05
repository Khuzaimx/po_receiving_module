package com.sellernest.poreceiving.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Touch target and spacing tokens from requirements §3.1:
 * "Minimum 48 dp, preferred 56 dp for primary actions. Gloved fingers are imprecise."
 */
object TouchTarget {
    val minimum = 48.dp
    val primary = 56.dp
}

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp

    /** Standard screen edge padding used by every screen in §7. */
    val screenPadding = md
}

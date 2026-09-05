package com.sellernest.poreceiving.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.sellernest.poreceiving.ui.theme.PoReceivingColors
import com.sellernest.poreceiving.ui.theme.Spacing
import com.sellernest.poreceiving.ui.theme.StateTone

/**
 * Renders one of the four scan/line states (§3.1, §7.6) as icon + colour + text
 * together. There is no overload that takes only a colour or only text: [icon]
 * and [label] are both required, so "colour is never the sole signal" is enforced
 * by the type signature, not by convention.
 *
 * The label is always drawn in [PoReceivingColors.onSurface] (the app's one text
 * colour, verified ≥ 7:1 against [tone]'s background by `ContrastTest`); the tone
 * colour appears only on the icon and the background tint.
 */
@Composable
fun StateBadge(
    tone: StateTone,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(tone.background, RoundedCornerShape(Spacing.xs))
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tone.accent,
        )
        Text(
            text = label,
            color = PoReceivingColors.onSurface,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

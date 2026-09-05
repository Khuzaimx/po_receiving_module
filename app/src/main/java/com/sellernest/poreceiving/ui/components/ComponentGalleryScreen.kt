package com.sellernest.poreceiving.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.sellernest.poreceiving.ui.theme.PoReceivingTheme
import com.sellernest.poreceiving.ui.theme.QuantityTextStyles
import com.sellernest.poreceiving.ui.theme.Spacing
import com.sellernest.poreceiving.ui.theme.StateTone

/**
 * Renders every design-system primitive from M0.4 in one place: all four
 * [StateTone]s (each with its icon and label, never colour alone), the primary
 * button in its enabled and disabled states, and the large quantity/SKU type
 * scale. Used as the visual reference for the design rules in §3.1, and as the
 * target for the touch-target and contrast instrumented tests.
 *
 * Not part of the receiving flow — reachable only from a developer/debug entry
 * point.
 */
@Composable
fun ComponentGalleryScreen(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { Text("STATE BADGES", style = MaterialTheme.typography.titleLarge) }
        item {
            StateBadge(tone = StateTone.Confirmed, icon = Icons.Filled.CheckCircle, label = "MATCHED")
        }
        item {
            StateBadge(tone = StateTone.Variance, icon = Icons.Filled.Warning, label = "VARIANCE — REVIEW REQUIRED")
        }
        item {
            StateBadge(tone = StateTone.Error, icon = Icons.Filled.Error, label = "UNKNOWN CODE")
        }
        item {
            StateBadge(tone = StateTone.Pending, icon = Icons.Filled.HourglassEmpty, label = "QUEUED")
        }

        item { Text("PRIMARY BUTTON", style = MaterialTheme.typography.titleLarge) }
        item { PrimaryButton(text = "COMMIT COUNT", onClick = {}) }
        item { PrimaryButton(text = "COMMIT COUNT (DISABLED)", onClick = {}, enabled = false) }

        item { Text("QUANTITY / SKU TYPE SCALE", style = MaterialTheme.typography.titleLarge) }
        item { Text("WM-4410-BLK", style = QuantityTextStyles.skuLarge) }
        item { Text("6", style = QuantityTextStyles.quantityDominant) }
        item { Text("120", style = QuantityTextStyles.quantityCompact) }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun ComponentGalleryPortraitPreview() {
    PoReceivingTheme { ComponentGalleryScreen() }
}

@Preview(showBackground = true, widthDp = 800, heightDp = 360)
@Composable
private fun ComponentGalleryLandscapePreview() {
    PoReceivingTheme { ComponentGalleryScreen() }
}

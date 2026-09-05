package com.sellernest.poreceiving.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.sellernest.poreceiving.ui.theme.Spacing

/**
 * Placeholder body for a §7 screen not yet implemented. Every route in
 * [Routes.allSpecRoutes] resolves to one of these until its milestone lands, so the
 * nav graph is complete from M0.1 onward and later issues only replace bodies.
 */
@Composable
fun ScreenStub(title: String, detail: String? = null, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineLarge)
        if (detail != null) {
            Text(text = detail, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

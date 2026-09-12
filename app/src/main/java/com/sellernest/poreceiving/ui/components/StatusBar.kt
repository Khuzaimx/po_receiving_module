package com.sellernest.poreceiving.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import com.sellernest.poreceiving.ui.theme.PoReceivingColors
import com.sellernest.poreceiving.ui.theme.Spacing
import com.sellernest.poreceiving.ui.theme.StateTone

/**
 * The slim bar present on every screen (§3.2): connectivity, pending submission
 * count, active warehouse, and signed-in user. "A receiver must never have to
 * navigate to discover that their work is queued rather than saved" -- so this
 * is rendered once, above the nav host, in [com.sellernest.poreceiving.navigation.PoReceivingRoot],
 * rather than being something each screen remembers to include.
 */
@Composable
fun StatusBar(
    modifier: Modifier = Modifier,
    onPendingCountTapped: () -> Unit = {},
    viewModel: StatusBarViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    StatusBarContent(state = state, onPendingCountTapped = onPendingCountTapped, modifier = modifier)
}

@Composable
internal fun StatusBarContent(
    state: StatusBarUiState,
    onPendingCountTapped: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .testTag("status_bar")
            .fillMaxWidth()
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StateBadge(
            modifier = Modifier.testTag("status_bar_connectivity"),
            tone = if (state.isOnline) StateTone.Confirmed else StateTone.Variance,
            icon = if (state.isOnline) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
            label = if (state.isOnline) "ONLINE" else "OFFLINE",
        )

        if (state.pendingSubmissionCount > 0) {
            StateBadge(
                // §3.1: "Touch targets: minimum 48dp." StateBadge's own visual
                // size stays compact everywhere else it's used (non-interactive) --
                // minimumInteractiveComponentSize only pads the tappable area for
                // this one real onClick, without changing how the badge looks.
                modifier = Modifier
                    .testTag("status_bar_pending_count")
                    .minimumInteractiveComponentSize()
                    .clickable(onClick = onPendingCountTapped),
                tone = StateTone.Pending,
                icon = Icons.Filled.CloudUpload,
                label = "${state.pendingSubmissionCount} queued",
            )
        }

        state.activeWarehouseName?.let { warehouseName ->
            Text(
                text = warehouseName,
                color = PoReceivingColors.onSurface,
                style = MaterialTheme.typography.labelLarge,
            )
        }

        state.signedInUserLabel?.let { userLabel ->
            Text(
                text = userLabel,
                color = PoReceivingColors.onSurface,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

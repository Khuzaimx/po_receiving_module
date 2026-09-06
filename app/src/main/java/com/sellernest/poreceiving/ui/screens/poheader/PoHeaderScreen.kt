package com.sellernest.poreceiving.ui.screens.poheader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.sellernest.poreceiving.ui.components.PrimaryButton
import com.sellernest.poreceiving.ui.components.StateBadge
import com.sellernest.poreceiving.ui.theme.Spacing
import com.sellernest.poreceiving.ui.theme.StateTone

/** §7.4, §9.2, §6.2. */
@Composable
fun PoHeaderScreen(onNavigateToScanToCount: (draftId: Long) -> Unit, viewModel: PoHeaderViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.navigateToDraftId) {
        state.navigateToDraftId?.let { draftId ->
            onNavigateToScanToCount(draftId)
            viewModel.onEvent(PoHeaderUiEvent.NavigationHandled)
        }
    }

    PoHeaderContent(
        state = state,
        onRetryTapped = { viewModel.onEvent(PoHeaderUiEvent.RetryRequested) },
        onStartReceivingTapped = { viewModel.onEvent(PoHeaderUiEvent.StartReceivingTapped) },
        onResumeDraftTapped = { viewModel.onEvent(PoHeaderUiEvent.ResumeDraftTapped) },
        onDiscardRequested = { viewModel.onEvent(PoHeaderUiEvent.DiscardRequested) },
        onDiscardConfirmed = { viewModel.onEvent(PoHeaderUiEvent.DiscardConfirmed) },
        onDiscardCancelled = { viewModel.onEvent(PoHeaderUiEvent.DiscardCancelled) },
    )
}

@Composable
internal fun PoHeaderContent(
    state: PoHeaderUiState,
    onRetryTapped: () -> Unit,
    onStartReceivingTapped: () -> Unit,
    onResumeDraftTapped: () -> Unit,
    onDiscardRequested: () -> Unit,
    onDiscardConfirmed: () -> Unit,
    onDiscardCancelled: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        state.offlineMessage?.let { message ->
            StateBadge(tone = StateTone.Error, icon = Icons.Filled.Warning, label = message)
            return
        }

        state.errorMessage?.let { message ->
            StateBadge(tone = StateTone.Error, icon = Icons.Filled.Warning, label = message)
            PrimaryButton(text = "RETRY", onClick = onRetryTapped)
            return
        }

        val detail = state.detail ?: return

        Text(text = detail.number, style = MaterialTheme.typography.headlineLarge)
        Text(text = detail.vendorName, style = MaterialTheme.typography.bodyLarge)
        Text(text = detail.warehouse.name, style = MaterialTheme.typography.bodyLarge)

        // §7.4: "'320 units expected' is a PO-level total, not a per-line
        // figure, and is shown only when the backend supplies it."
        val unitsSuffix = detail.totalUnitsExpected?.let { " · $it units expected" }.orEmpty()
        Text(text = "${detail.lines.size} lines$unitsSuffix", style = MaterialTheme.typography.bodyLarge)

        detail.status?.let { status ->
            Text(text = "Status: $status", style = MaterialTheme.typography.bodyLarge)
        }

        if (detail.blindCount) {
            StateBadge(
                tone = StateTone.Pending,
                icon = Icons.Filled.Info,
                label = "Blind count is ON. Expected quantities are revealed after you commit your count.",
            )
        }

        PrimaryButton(text = "START RECEIVING", onClick = onStartReceivingTapped)

        state.existingDraftScannedCount?.let { scannedCount ->
            PrimaryButton(text = "RESUME DRAFT ($scannedCount scanned)", onClick = onResumeDraftTapped)
        }

        // §6.3/M3.7: only offered while the draft is still in PO_OPEN --
        // once counting has begun, discard is no longer a legal transition.
        if (state.canDiscardExistingDraft) {
            TextButton(onClick = onDiscardRequested) {
                Text("DISCARD DRAFT")
            }
        }

        if (state.showDiscardConfirmation) {
            AlertDialog(
                onDismissRequest = onDiscardCancelled,
                title = { Text("Discard draft?") },
                text = { Text("This discards the draft for ${detail.number}. This cannot be undone.") },
                confirmButton = {
                    TextButton(onClick = onDiscardConfirmed) { Text("DISCARD") }
                },
                dismissButton = {
                    TextButton(onClick = onDiscardCancelled) { Text("CANCEL") }
                },
            )
        }
    }
}

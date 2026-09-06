package com.sellernest.poreceiving.ui.screens.binconfirmation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.sellernest.poreceiving.network.dto.BinRef
import com.sellernest.poreceiving.scan.compose.KeyboardWedgeCapture
import com.sellernest.poreceiving.scan.compose.ScanFocusEffect
import com.sellernest.poreceiving.ui.components.PrimaryButton
import com.sellernest.poreceiving.ui.components.StateBadge
import com.sellernest.poreceiving.ui.theme.Spacing
import com.sellernest.poreceiving.ui.theme.StateTone

/** §7.10, §10. Reached only when the active warehouse enforces bins. */
@Composable
fun BinConfirmationScreen(onNavigateToReview: (draftId: Long) -> Unit, viewModel: BinConfirmationViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    ScanFocusEffect { code, _ -> viewModel.onEvent(BinConfirmationUiEvent.BinScanned(code)) }
    KeyboardWedgeCapture()

    LaunchedEffect(state.navigateToReviewDraftId) {
        state.navigateToReviewDraftId?.let { draftId ->
            onNavigateToReview(draftId)
            viewModel.onEvent(BinConfirmationUiEvent.NavigationHandled)
        }
    }

    BinConfirmationContent(
        state = state,
        onBinSelected = { viewModel.onEvent(BinConfirmationUiEvent.BinSelected(it)) },
        onContinueTapped = { viewModel.onEvent(BinConfirmationUiEvent.ContinueTapped) },
    )
}

@Composable
internal fun BinConfirmationContent(
    state: BinConfirmationUiState,
    onBinSelected: (BinRef) -> Unit,
    onContinueTapped: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(Spacing.screenPadding)) {
        Text(text = "PUT AWAY", style = MaterialTheme.typography.headlineLarge)
        Text(text = "Destination bin for this receipt", style = MaterialTheme.typography.bodyLarge)

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(imageVector = Icons.Filled.QrCodeScanner, contentDescription = null)
            Text(text = "Scan bin label…", style = MaterialTheme.typography.bodyLarge)
        }

        state.selectedBin?.let { bin ->
            StateBadge(tone = StateTone.Confirmed, icon = Icons.Filled.QrCodeScanner, label = "Selected: ${bin.label}")
        }

        state.scanMessage?.let { message ->
            StateBadge(
                modifier = Modifier.padding(vertical = Spacing.sm),
                tone = StateTone.Error,
                icon = Icons.Filled.Warning,
                label = message,
            )
        }

        if (state.suggestedBins.isNotEmpty()) {
            Text(
                text = "SUGGESTED",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = Spacing.md),
            )
            LazyColumn(modifier = Modifier.weight(1f, fill = true).fillMaxWidth()) {
                items(items = state.suggestedBins, key = { it.id }) { bin ->
                    SuggestedBinRow(bin = bin, onSelect = { onBinSelected(bin) })
                    HorizontalDivider()
                }
            }
        }

        PrimaryButton(
            text = "CONTINUE",
            enabled = state.canContinue,
            onClick = onContinueTapped,
            modifier = Modifier.padding(top = Spacing.md),
        )
    }
}

@Composable
private fun SuggestedBinRow(bin: BinRef, onSelect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = bin.label, style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onSelect) { Text("SELECT") }
    }
}

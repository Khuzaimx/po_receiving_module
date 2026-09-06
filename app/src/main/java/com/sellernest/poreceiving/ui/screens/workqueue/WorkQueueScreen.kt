package com.sellernest.poreceiving.ui.screens.workqueue

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.sellernest.poreceiving.network.dto.PurchaseOrderSummary
import com.sellernest.poreceiving.scan.compose.KeyboardWedgeCapture
import com.sellernest.poreceiving.scan.compose.ScanFocusEffect
import com.sellernest.poreceiving.ui.components.StateBadge
import com.sellernest.poreceiving.ui.theme.Spacing
import com.sellernest.poreceiving.ui.theme.StateTone

/** §7.3, §9.1. */
@Composable
fun WorkQueueScreen(onPoSelected: (Long) -> Unit, viewModel: WorkQueueViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    // M2.7/M7.2: this screen owns scan focus so a scanned PO barcode opens it
    // directly, by hardware trigger, with the camera never opened.
    // KeyboardWedgeCapture is what makes that true for a device in
    // keystroke-wedge output mode, not only intent-broadcast mode -- its
    // hidden field yields focus to the visible search field below whenever
    // the receiver taps into it, the same documented trade-off
    // ScanToCountScreen's manual-quantity dialog already makes.
    ScanFocusEffect { code, _ -> viewModel.onEvent(WorkQueueUiEvent.PoBarcodeScanned(code)) }
    KeyboardWedgeCapture()

    LaunchedEffect(state.navigateToPoId) {
        state.navigateToPoId?.let { poId ->
            onPoSelected(poId)
            viewModel.onEvent(WorkQueueUiEvent.NavigationHandled)
        }
    }

    WorkQueueContent(
        state = state,
        onSearchQueryChanged = { viewModel.onEvent(WorkQueueUiEvent.SearchQueryChanged(it)) },
        onRefreshRequested = { viewModel.onEvent(WorkQueueUiEvent.RefreshRequested) },
        onPoSelected = onPoSelected,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkQueueContent(
    state: WorkQueueUiState,
    onSearchQueryChanged: (String) -> Unit,
    onRefreshRequested: () -> Unit,
    onPoSelected: (Long) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.screenPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "RECEIVE", style = MaterialTheme.typography.headlineLarge)
            // §3.1 anti-requirement: pull-to-refresh is never the *sole*
            // refresh mechanism -- this button is the explicit one.
            IconButton(onClick = onRefreshRequested) {
                Icon(imageVector = Icons.Filled.Refresh, contentDescription = "Refresh")
            }
        }

        if (state.missingPermission) {
            MissingPermissionEmptyState()
            return
        }

        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenPadding),
            value = state.searchQuery,
            onValueChange = onSearchQueryChanged,
            placeholder = { Text("PO number or vendor") },
            singleLine = true,
        )

        state.errorMessage?.let { message ->
            StateBadge(
                modifier = Modifier.padding(Spacing.screenPadding),
                tone = StateTone.Error,
                icon = Icons.Filled.Warning,
                label = message,
            )
        }

        // M2.7: names why a scanned PO barcode didn't open anything --
        // never a silent no-op.
        state.scanMessage?.let { message ->
            StateBadge(
                modifier = Modifier.padding(Spacing.screenPadding),
                tone = StateTone.Error,
                icon = Icons.Filled.Warning,
                label = message,
            )
        }

        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = onRefreshRequested,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(items = state.results, key = { it.id }) { po ->
                    PurchaseOrderRow(po = po, onClick = { onPoSelected(po.id) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun PurchaseOrderRow(po: PurchaseOrderSummary, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(Spacing.screenPadding),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = po.number, style = MaterialTheme.typography.titleLarge)
            if (po.status == "PARTIALLY_RECEIVED") {
                StateBadge(tone = StateTone.Variance, icon = Icons.Filled.Warning, label = "PARTIAL")
            }
        }
        Text(text = po.vendorName, style = MaterialTheme.typography.bodyLarge)
        val unitsSuffix = po.totalUnitsExpected?.let { " · $it units" }.orEmpty()
        val dueSuffix = po.expectedDeliveryDate?.let { " · due $it" }.orEmpty()
        Text(
            text = "${po.lineCount} lines$unitsSuffix$dueSuffix",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun MissingPermissionEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StateBadge(tone = StateTone.Error, icon = Icons.Filled.Warning, label = "MISSING PERMISSION")
        Text(
            text = "You don't have the \"Receiving\" permission for this warehouse. Contact your supervisor.",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

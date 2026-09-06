package com.sellernest.poreceiving.ui.screens.receipthistory

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.sellernest.poreceiving.network.dto.ReceiptSummary
import com.sellernest.poreceiving.ui.components.StateBadge
import com.sellernest.poreceiving.ui.theme.Spacing
import com.sellernest.poreceiving.ui.theme.StateTone
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.temporal.ChronoUnit

/** §7.13, §9.6. */
@Composable
fun ReceiptHistoryScreen(viewModel: ReceiptHistoryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    ReceiptHistoryContent(
        state = state,
        onRefreshRequested = { viewModel.onEvent(ReceiptHistoryUiEvent.RefreshRequested) },
        onVoidRequested = { viewModel.onEvent(ReceiptHistoryUiEvent.VoidRequested(it)) },
        onVoidReasonChanged = { viewModel.onEvent(ReceiptHistoryUiEvent.VoidReasonChanged(it)) },
        onVoidConfirmed = { viewModel.onEvent(ReceiptHistoryUiEvent.VoidConfirmed) },
        onVoidCancelled = { viewModel.onEvent(ReceiptHistoryUiEvent.VoidCancelled) },
        onVoidBlockedMessageDismissed = { viewModel.onEvent(ReceiptHistoryUiEvent.VoidBlockedMessageDismissed) },
    )
}

@Composable
internal fun ReceiptHistoryContent(
    state: ReceiptHistoryUiState,
    onRefreshRequested: () -> Unit,
    onVoidRequested: (ReceiptSummary) -> Unit,
    onVoidReasonChanged: (String) -> Unit,
    onVoidConfirmed: () -> Unit,
    onVoidCancelled: () -> Unit,
    onVoidBlockedMessageDismissed: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.screenPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "MY RECEIPTS — TODAY", style = MaterialTheme.typography.headlineLarge)
            // §3.1 anti-requirement: pull-to-refresh is never the *sole*
            // refresh mechanism -- this button is the explicit one.
            IconButton(onClick = onRefreshRequested) {
                Icon(imageVector = Icons.Filled.Refresh, contentDescription = "Refresh")
            }
        }

        state.errorMessage?.let { message ->
            StateBadge(
                modifier = Modifier.padding(Spacing.screenPadding),
                tone = StateTone.Error,
                icon = Icons.Filled.Warning,
                label = message,
            )
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(items = state.receipts, key = { it.id }) { receipt ->
                ReceiptRow(receipt = receipt, onVoidRequested = { onVoidRequested(receipt) })
                HorizontalDivider()
            }
        }
    }

    state.voidTarget?.let { target ->
        AlertDialog(
            onDismissRequest = onVoidCancelled,
            title = { Text("Void receipt #${target.id}?") },
            text = {
                Column {
                    Text("This voids the receipt for ${target.purchaseOrderNumber}. There is no line-level edit -- recount and resubmit instead.")
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = state.voidReason,
                        onValueChange = onVoidReasonChanged,
                        label = { Text("Reason (required)") },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onVoidConfirmed, enabled = state.voidReason.isNotBlank()) { Text("VOID") }
            },
            dismissButton = { TextButton(onClick = onVoidCancelled) { Text("CANCEL") } },
        )
    }

    state.voidBlockedMessage?.let { message ->
        AlertDialog(
            onDismissRequest = onVoidBlockedMessageDismissed,
            title = { Text("Can't void this receipt") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = onVoidBlockedMessageDismissed) { Text("OK") } },
        )
    }
}

@Composable
private fun ReceiptRow(receipt: ReceiptSummary, onVoidRequested: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(Spacing.screenPadding)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "#${receipt.id}  ${receipt.purchaseOrderNumber}", style = MaterialTheme.typography.titleLarge)
            if (receipt.hasVariance) {
                StateBadge(tone = StateTone.Variance, icon = Icons.Filled.Warning, label = "VARIANCE")
            }
        }
        Text(
            text = "${receipt.vendorName} · ${receipt.lineCount} lines · ${receipt.totalUnits} units",
            style = MaterialTheme.typography.bodyLarge,
        )

        when {
            receipt.isVoided -> StateBadge(tone = StateTone.Error, icon = Icons.Filled.Warning, label = "VOIDED")
            else -> {
                val remaining = rememberRemainingVoidLabel(receipt.voidAvailableUntil)
                if (remaining != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onVoidRequested) { Text("VOID") }
                        Text(text = remaining, style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    Text(text = "Void window expired", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

/**
 * §7.13: "Live countdown of remaining void time... updates without a manual
 * refresh." Re-derives from the wall clock every 30s rather than needing any
 * server round-trip -- [android.icu.text.RelativeDateTimeFormatter]/date
 * libraries aren't needed for a plain "Xh Ym remaining".
 */
@Composable
private fun rememberRemainingVoidLabel(voidAvailableUntil: String?): String? {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(voidAvailableUntil) {
        while (voidAvailableUntil != null) {
            now = System.currentTimeMillis()
            delay(30_000)
        }
    }
    val until = voidAvailableUntil?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null
    val nowInstant = Instant.ofEpochMilli(now)
    if (!until.isAfter(nowInstant)) return null
    val minutesRemaining = ChronoUnit.MINUTES.between(nowInstant, until)
    val hours = minutesRemaining / 60
    val minutes = minutesRemaining % 60
    return if (hours > 0) "⏱ ${hours}h ${minutes}m remaining" else "⏱ ${minutes}m remaining"
}

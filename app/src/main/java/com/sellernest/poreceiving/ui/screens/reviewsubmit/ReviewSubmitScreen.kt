package com.sellernest.poreceiving.ui.screens.reviewsubmit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.sellernest.poreceiving.network.dto.ReconcileResponseLine
import com.sellernest.poreceiving.ui.components.PrimaryButton
import com.sellernest.poreceiving.ui.components.StateBadge
import com.sellernest.poreceiving.ui.theme.Spacing
import com.sellernest.poreceiving.ui.theme.StateTone

/**
 * §7.11. Submit and Save-and-exit both navigate away and pop this screen off
 * the back stack, so unlike every other one-shot-navigation screen in this
 * app, [ReviewSubmitUiState.submitted]/[ReviewSubmitUiState.savedAndExited]
 * are never reset -- there is no back-stack path that could land on a stale
 * instance of this screen with either flag still set.
 */
@Composable
fun ReviewSubmitScreen(
    onSubmitted: () -> Unit,
    onSaveAndExit: () -> Unit,
    viewModel: ReviewSubmitViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.submitted) { if (state.submitted) onSubmitted() }
    LaunchedEffect(state.savedAndExited) { if (state.savedAndExited) onSaveAndExit() }

    ReviewSubmitContent(
        state = state,
        onNotesChanged = { viewModel.onEvent(ReviewSubmitUiEvent.NotesChanged(it)) },
        onReviewVariances = { viewModel.onEvent(ReviewSubmitUiEvent.ReviewVariancesTapped) },
        onVarianceDetailDismissed = { viewModel.onEvent(ReviewSubmitUiEvent.VarianceDetailDismissed) },
        onSubmit = { viewModel.onEvent(ReviewSubmitUiEvent.SubmitTapped) },
        onSaveAndExit = { viewModel.onEvent(ReviewSubmitUiEvent.SaveAndExitTapped) },
        onBlockingMessageDismissed = { viewModel.onEvent(ReviewSubmitUiEvent.BlockingMessageDismissed) },
    )
}

@Composable
internal fun ReviewSubmitContent(
    state: ReviewSubmitUiState,
    onNotesChanged: (String) -> Unit,
    onReviewVariances: () -> Unit,
    onVarianceDetailDismissed: () -> Unit,
    onSubmit: () -> Unit,
    onSaveAndExit: () -> Unit,
    onBlockingMessageDismissed: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "REVIEW — ${state.purchaseOrderNumber.orEmpty()}",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(Spacing.screenPadding),
        )

        LazyColumn(modifier = Modifier.weight(1f, fill = true).fillMaxWidth()) {
            item { SummaryRow("Warehouse", state.warehouseName ?: "—") }
            item { SummaryRow("Bin", state.binLabel ?: "—") }
            item { SummaryRow("Lines", "${state.lineCount}") }
            item { SummaryRow("Good", "${state.goodQuantity}") }
            item { SummaryRow("Damaged", "${state.damagedQuantity}") }
            item { SummaryRow("Missing", state.missingQuantity?.toString() ?: "—") }
            item { SummaryRow("Photos", "${state.photoCount}") }
            item { SummaryRow("Serials", "${state.serialCount}") }
            item {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.screenPadding),
                    value = state.notes,
                    onValueChange = onNotesChanged,
                    label = { Text("Notes (optional)") },
                )
            }
        }

        if (state.varianceLines.isNotEmpty()) {
            StateBadge(
                modifier = Modifier.padding(Spacing.screenPadding),
                tone = StateTone.Variance,
                icon = Icons.Filled.Warning,
                label = "${state.varianceLines.size} lines have variances",
            )
            TextButton(onClick = onReviewVariances, modifier = Modifier.padding(horizontal = Spacing.screenPadding)) {
                Text("REVIEW VARIANCES")
            }
        }

        state.blockingMessage?.let { message ->
            StateBadge(
                modifier = Modifier.padding(Spacing.screenPadding),
                tone = StateTone.Error,
                icon = Icons.Filled.Warning,
                label = message,
            )
        }

        Column(modifier = Modifier.padding(Spacing.screenPadding)) {
            PrimaryButton(text = "SUBMIT RECEIPT", onClick = onSubmit)
            TextButton(onClick = onSaveAndExit, modifier = Modifier.fillMaxWidth()) {
                Text("Save draft & exit")
            }
        }
    }

    if (state.showVarianceDetail) {
        VarianceDetailDialog(lines = state.varianceLines, onDismiss = onVarianceDetailDismissed)
    }
    // The badge above is the persistent record; this dialog is the
    // moment-of-tap acknowledgement so a receiver can't miss it.
    state.blockingMessage?.let { message ->
        AlertDialog(
            onDismissRequest = onBlockingMessageDismissed,
            title = { Text("Can't submit yet") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = onBlockingMessageDismissed) { Text("OK") } },
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(Spacing.screenPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
    HorizontalDivider()
}

@Composable
private fun VarianceDetailDialog(lines: List<ReconcileResponseLine>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lines with variances") },
        text = {
            Column {
                lines.forEach { line ->
                    val sign = if (line.delta > 0) "+" else ""
                    Text(text = "${line.sku}: $sign${line.delta}", style = MaterialTheme.typography.bodyLarge)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("CLOSE") } },
    )
}

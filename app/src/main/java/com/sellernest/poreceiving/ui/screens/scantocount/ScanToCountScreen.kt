package com.sellernest.poreceiving.ui.screens.scantocount

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sellernest.poreceiving.data.local.entities.DraftLineEntity
import com.sellernest.poreceiving.network.dto.ScanResolution
import com.sellernest.poreceiving.scan.camera.CameraScannerScreen
import com.sellernest.poreceiving.scan.compose.KeyboardWedgeCapture
import com.sellernest.poreceiving.scan.compose.ScanFocusEffect
import com.sellernest.poreceiving.ui.components.PrimaryButton
import com.sellernest.poreceiving.ui.components.scanoutcome.ScanOutcomeBanner
import com.sellernest.poreceiving.ui.theme.QuantityTextStyles
import com.sellernest.poreceiving.ui.theme.Spacing
import com.sellernest.poreceiving.ui.theme.TouchTarget

/**
 * §7.5, "the single most-used screen." No expected quantity, no ratio, no
 * progress bar anywhere in this file -- see [ScanToCountUiState]'s doc and
 * this package's grep-level guardrail test.
 */
@Composable
fun ScanToCountScreen(
    onNavigateToReconcile: (draftId: Long) -> Unit,
    onNavigateToWorkQueue: () -> Unit,
    viewModel: ScanToCountViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    ScanFocusEffect { code, source -> viewModel.onEvent(ScanToCountUiEvent.ScanReceived(code, source)) }
    KeyboardWedgeCapture()

    LaunchedEffect(state.navigateToReconcileDraftId) {
        state.navigateToReconcileDraftId?.let { draftId ->
            onNavigateToReconcile(draftId)
            viewModel.onEvent(ScanToCountUiEvent.NavigationHandled)
        }
    }
    LaunchedEffect(state.navigateToWorkQueue) {
        if (state.navigateToWorkQueue) {
            onNavigateToWorkQueue()
            viewModel.onEvent(ScanToCountUiEvent.NavigationHandled)
        }
    }

    if (state.cameraOpen) {
        CameraScannerScreen(onClose = { viewModel.onEvent(ScanToCountUiEvent.CameraClosed) })
        return
    }

    ScanToCountContent(
        state = state,
        onCandidateSelected = { viewModel.onEvent(ScanToCountUiEvent.CandidateLineSelected(it)) },
        onScanAgain = { viewModel.onEvent(ScanToCountUiEvent.ScanAgainTapped) },
        onOpenCorrectPo = { viewModel.onEvent(ScanToCountUiEvent.OpenCorrectPoTapped) },
        onEnterSkuManually = { viewModel.onEvent(ScanToCountUiEvent.ManualSkuEntryRequested) },
        onManualSkuSubmitted = { viewModel.onEvent(ScanToCountUiEvent.ManualSkuSubmitted(it)) },
        onManualSkuDismissed = { viewModel.onEvent(ScanToCountUiEvent.ManualSkuEntryDismissed) },
        onQuantityIncremented = { viewModel.onEvent(ScanToCountUiEvent.QuantityIncremented(it)) },
        onQuantityDecremented = { viewModel.onEvent(ScanToCountUiEvent.QuantityDecremented(it)) },
        onManualQuantityRequested = { viewModel.onEvent(ScanToCountUiEvent.ManualQuantityEntryRequested(it)) },
        onManualQuantitySubmitted = { id, qty -> viewModel.onEvent(ScanToCountUiEvent.ManualQuantitySubmitted(id, qty)) },
        onManualQuantityDismissed = { viewModel.onEvent(ScanToCountUiEvent.ManualQuantityEntryDismissed) },
        onCameraOpened = { viewModel.onEvent(ScanToCountUiEvent.CameraOpened) },
        onCommitCount = { viewModel.onEvent(ScanToCountUiEvent.CommitCountTapped) },
    )
}

@Composable
internal fun ScanToCountContent(
    state: ScanToCountUiState,
    onCandidateSelected: (Long) -> Unit,
    onScanAgain: () -> Unit,
    onOpenCorrectPo: () -> Unit,
    onEnterSkuManually: () -> Unit,
    onManualSkuSubmitted: (String) -> Unit,
    onManualSkuDismissed: () -> Unit,
    onQuantityIncremented: (Long) -> Unit,
    onQuantityDecremented: (Long) -> Unit,
    onManualQuantityRequested: (Long) -> Unit,
    onManualQuantitySubmitted: (Long, Int) -> Unit,
    onManualQuantityDismissed: () -> Unit,
    onCameraOpened: () -> Unit,
    onCommitCount: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.screenPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = state.purchaseOrderNumber.orEmpty(), style = MaterialTheme.typography.titleLarge)
            // Total *counted* units so far -- never an "x of y" or percentage
            // against an expected total (§6.1, §7.5).
            Text(text = "${state.totalCountedQuantity} scanned", style = MaterialTheme.typography.titleLarge)
        }
        HorizontalDivider()

        Column(modifier = Modifier.padding(Spacing.screenPadding)) {
            Text(text = "LAST SCAN", style = MaterialTheme.typography.labelLarge)

            val outcome = state.lastScanOutcome
            if (outcome is ScanResolution.Matched) {
                LastMatchedScanCard(
                    outcome = outcome,
                    countedQuantity = state.lines.firstOrNull { it.purchaseOrderItemId == outcome.line.purchaseOrderItemId }
                        ?.countedQuantity ?: 0,
                    onIncrement = { onQuantityIncremented(outcome.line.purchaseOrderItemId) },
                    onDecrement = { onQuantityDecremented(outcome.line.purchaseOrderItemId) },
                    onTapQuantity = { onManualQuantityRequested(outcome.line.purchaseOrderItemId) },
                )
            } else if (outcome != null) {
                ScanOutcomeBanner(
                    outcome = outcome,
                    onScanAgain = onScanAgain,
                    onOpenCorrectPo = onOpenCorrectPo,
                    onEnterSkuManually = onEnterSkuManually,
                    onCandidateLineSelected = onCandidateSelected,
                )
            }
        }

        state.errorMessage?.let { message -> Text(text = message, style = MaterialTheme.typography.bodyLarge) }

        HorizontalDivider()

        Text(
            text = "COUNTED SO FAR (${state.lines.size})",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(Spacing.screenPadding),
        )
        LazyColumn(modifier = Modifier.weight(1f, fill = true).fillMaxWidth()) {
            items(items = state.lines, key = { it.id }) { line ->
                CountedLineRow(line)
                HorizontalDivider()
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.screenPadding),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            IconButton(
                onClick = onCameraOpened,
                modifier = Modifier.size(TouchTarget.primary),
            ) {
                Icon(imageVector = Icons.Filled.CameraAlt, contentDescription = "Open camera scanner")
            }
            PrimaryButton(text = "COMMIT COUNT", onClick = onCommitCount, modifier = Modifier.weight(1f))
        }
    }

    if (state.manualSkuEntryOpen) {
        ManualSkuEntryDialog(onSubmit = onManualSkuSubmitted, onDismiss = onManualSkuDismissed)
    }
    state.manualQuantityEntryForLineId?.let { lineId ->
        val currentQuantity = state.lines.firstOrNull { it.purchaseOrderItemId == lineId }?.countedQuantity ?: 0
        ManualQuantityEntryDialog(
            initialQuantity = currentQuantity,
            onSubmit = { quantity -> onManualQuantitySubmitted(lineId, quantity) },
            onDismiss = onManualQuantityDismissed,
        )
    }
}

@Composable
private fun LastMatchedScanCard(
    outcome: ScanResolution.Matched,
    countedQuantity: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onTapQuantity: () -> Unit,
) {
    Column {
        Text(text = outcome.line.sku, style = QuantityTextStyles.skuLarge)
        Text(text = outcome.line.name, style = MaterialTheme.typography.bodyLarge)
        Text(text = "matched on ${outcome.matchedField}", style = MaterialTheme.typography.bodyLarge)

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(text = "COUNTED:", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "$countedQuantity",
                style = QuantityTextStyles.quantityDominant,
                modifier = Modifier.padding(horizontal = Spacing.sm),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            IconButton(onClick = onDecrement, modifier = Modifier.size(TouchTarget.primary)) {
                Text(text = "−", style = MaterialTheme.typography.headlineLarge)
            }
            TextButton(onClick = onTapQuantity, modifier = Modifier.size(TouchTarget.primary)) {
                Text(text = "$countedQuantity", style = QuantityTextStyles.quantityCompact)
            }
            IconButton(onClick = onIncrement, modifier = Modifier.size(TouchTarget.primary)) {
                Text(text = "+", style = MaterialTheme.typography.headlineLarge)
            }
        }
    }
}

@Composable
private fun CountedLineRow(line: DraftLineEntity) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.screenPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = "${line.sku}  ${line.name}", style = MaterialTheme.typography.bodyLarge)
        Text(text = "${line.countedQuantity}", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ManualSkuEntryDialog(onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter SKU") },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(text) }, enabled = text.isNotBlank()) { Text("SUBMIT") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } },
    )
}

/**
 * M3.4: "Tapping the quantity opens a large numeric keypad; entry replaces
 * rather than appends." The system numeric keyboard behind a plain-number
 * text field is that large, glove-usable numeric input surface; the field
 * always starts from empty, so typing "120" replaces rather than appending
 * to whatever was counted before.
 *
 * M3.4's "a wedge scan arriving while the keypad is open is handled
 * deterministically and documented": this dialog's own [OutlinedTextField]
 * takes focus while it's open, so [com.sellernest.poreceiving.scan.compose.KeyboardWedgeCapture]'s
 * hidden field (which is still present in the composition behind the dialog)
 * loses focus for as long as the dialog is showing. A wedge burst arriving in
 * that window is captured by this field instead: its digits land directly in
 * the quantity text (non-digit characters are filtered out), which is a
 * plausible, non-destructive outcome for a receiver who scans a
 * quantity-labelled barcode while the keypad happens to be open, rather than
 * a scan silently going nowhere.
 */
@Composable
private fun ManualQuantityEntryDialog(initialQuantity: Int, onSubmit: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val parsed = text.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set quantity (currently $initialQuantity)") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { input -> text = input.filter { it.isDigit() } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let(onSubmit) }, enabled = parsed != null) { Text("SET") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } },
    )
}

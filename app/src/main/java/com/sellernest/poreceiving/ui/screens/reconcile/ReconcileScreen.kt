package com.sellernest.poreceiving.ui.screens.reconcile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.sellernest.poreceiving.network.dto.ReconcileResponseLine
import com.sellernest.poreceiving.network.dto.VarianceReason
import com.sellernest.poreceiving.ui.components.PrimaryButton
import com.sellernest.poreceiving.ui.components.StateBadge
import com.sellernest.poreceiving.ui.theme.Spacing
import com.sellernest.poreceiving.ui.theme.StateTone

/** §7.7, §6.1, §6.3: the only screen an expected quantity is ever revealed on. */
@Composable
fun ReconcileScreen(
    onNavigateToDamageCapture: (purchaseOrderItemId: Long) -> Unit,
    onNavigateToSerialCapture: (purchaseOrderItemId: Long) -> Unit,
    onNavigateToBinConfirmation: (draftId: Long) -> Unit,
    onNavigateToReview: (draftId: Long) -> Unit,
    viewModel: ReconcileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.navigateToBinConfirmationDraftId) {
        state.navigateToBinConfirmationDraftId?.let { draftId ->
            onNavigateToBinConfirmation(draftId)
            viewModel.onEvent(ReconcileUiEvent.NavigationHandled)
        }
    }
    LaunchedEffect(state.navigateToReviewDraftId) {
        state.navigateToReviewDraftId?.let { draftId ->
            onNavigateToReview(draftId)
            viewModel.onEvent(ReconcileUiEvent.NavigationHandled)
        }
    }

    ReconcileContent(
        state = state,
        onReasonSelected = { itemId, reasonId -> viewModel.onEvent(ReconcileUiEvent.ReasonSelected(itemId, reasonId)) },
        onNoteChanged = { itemId, note -> viewModel.onEvent(ReconcileUiEvent.NoteChanged(itemId, note)) },
        onDamageCaptureRequested = onNavigateToDamageCapture,
        onSerialCaptureRequested = onNavigateToSerialCapture,
        onContinueTapped = { viewModel.onEvent(ReconcileUiEvent.ContinueTapped) },
        onRetryTapped = { viewModel.onEvent(ReconcileUiEvent.RetryRequested) },
    )
}

@Composable
internal fun ReconcileContent(
    state: ReconcileUiState,
    onReasonSelected: (Long, Long) -> Unit,
    onNoteChanged: (Long, String) -> Unit,
    onDamageCaptureRequested: (purchaseOrderItemId: Long) -> Unit,
    onSerialCaptureRequested: (purchaseOrderItemId: Long) -> Unit,
    onContinueTapped: () -> Unit,
    onRetryTapped: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "RECONCILE ${state.purchaseOrderNumber.orEmpty()}",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(Spacing.screenPadding),
        )

        StateBadge(
            modifier = Modifier.padding(horizontal = Spacing.screenPadding),
            tone = StateTone.Confirmed,
            icon = Icons.Filled.CheckCircle,
            label = "${state.matchingLines.size} lines match expected",
        )
        if (state.varianceLines.isNotEmpty()) {
            StateBadge(
                modifier = Modifier.padding(horizontal = Spacing.screenPadding, vertical = Spacing.xs),
                tone = StateTone.Variance,
                icon = Icons.Filled.Warning,
                label = "${state.varianceLines.size} lines differ",
            )
        }

        state.errorMessage?.let { message ->
            StateBadge(
                modifier = Modifier.padding(Spacing.screenPadding),
                tone = StateTone.Error,
                icon = Icons.Filled.Warning,
                label = message,
            )
            PrimaryButton(
                text = "RETRY",
                onClick = onRetryTapped,
                modifier = Modifier.padding(horizontal = Spacing.screenPadding),
            )
        }

        LazyColumn(modifier = Modifier.weight(1f, fill = true).fillMaxWidth()) {
            items(items = state.varianceLines, key = { it.purchaseOrderItemId }) { line ->
                VarianceLineRow(
                    line = line,
                    reasons = state.varianceReasons,
                    selectedReasonId = state.selectedReasonIdByLine[line.purchaseOrderItemId],
                    note = state.noteByLine[line.purchaseOrderItemId].orEmpty(),
                    requiresSerialNumber = state.requiresSerialByItem[line.purchaseOrderItemId] == true,
                    onReasonSelected = { reasonId -> onReasonSelected(line.purchaseOrderItemId, reasonId) },
                    onNoteChanged = { note -> onNoteChanged(line.purchaseOrderItemId, note) },
                    onDamageCaptureRequested = { onDamageCaptureRequested(line.purchaseOrderItemId) },
                    onSerialCaptureRequested = { onSerialCaptureRequested(line.purchaseOrderItemId) },
                )
                HorizontalDivider()
            }
            items(items = state.matchingLines, key = { it.purchaseOrderItemId }) { line ->
                MatchingLineRow(
                    line = line,
                    requiresSerialNumber = state.requiresSerialByItem[line.purchaseOrderItemId] == true,
                    onDamageCaptureRequested = { onDamageCaptureRequested(line.purchaseOrderItemId) },
                    onSerialCaptureRequested = { onSerialCaptureRequested(line.purchaseOrderItemId) },
                )
                HorizontalDivider()
            }
        }

        state.blockedOverReceiptLines.firstOrNull()?.let { blocked ->
            StateBadge(
                modifier = Modifier.padding(Spacing.screenPadding),
                tone = StateTone.Error,
                icon = Icons.Filled.Warning,
                label = "${blocked.sku}: over-receipt requires the \"Allow To Over Receive\" permission. " +
                    "Correct the count or drop the line.",
            )
        }

        PrimaryButton(
            text = "CONTINUE",
            enabled = state.canContinue,
            onClick = onContinueTapped,
            modifier = Modifier.padding(Spacing.screenPadding),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VarianceLineRow(
    line: ReconcileResponseLine,
    reasons: List<VarianceReason>,
    selectedReasonId: Long?,
    note: String,
    requiresSerialNumber: Boolean,
    onReasonSelected: (Long) -> Unit,
    onNoteChanged: (String) -> Unit,
    onDamageCaptureRequested: () -> Unit,
    onSerialCaptureRequested: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(Spacing.screenPadding)) {
        StateBadge(tone = StateTone.Variance, icon = Icons.Filled.Warning, label = line.sku)
        Text(text = line.name, style = MaterialTheme.typography.bodyLarge)

        val sign = if (line.delta > 0) "+" else ""
        Text(
            text = "Expected ${line.quantityExpected}   Counted ${line.quantityCounted}   $sign${line.delta}",
            style = MaterialTheme.typography.bodyLarge,
        )

        if (line.isOverReceipt && line.overReceiptPermitted) {
            StateBadge(tone = StateTone.Pending, icon = Icons.Filled.Info, label = "Over-receipt — permitted")
        }

        ReasonDropdown(reasons = reasons, selectedReasonId = selectedReasonId, onReasonSelected = onReasonSelected)

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = note,
            onValueChange = onNoteChanged,
            placeholder = { Text("Note (optional)") },
        )

        LineCaptureActions(
            requiresSerialNumber = requiresSerialNumber,
            onDamageCaptureRequested = onDamageCaptureRequested,
            onSerialCaptureRequested = onSerialCaptureRequested,
        )
    }
}

/** §7.8/§7.9: reachable from every line, matching or variance -- damage and
 *  serial capture are both about what was *counted*, not about whether the
 *  count happened to match an expected quantity. */
@Composable
private fun LineCaptureActions(
    requiresSerialNumber: Boolean,
    onDamageCaptureRequested: () -> Unit,
    onSerialCaptureRequested: () -> Unit,
) {
    Row {
        TextButton(onClick = onDamageCaptureRequested) { Text("DAMAGE") }
        if (requiresSerialNumber) {
            TextButton(onClick = onSerialCaptureRequested) { Text("SERIALS") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReasonDropdown(reasons: List<VarianceReason>, selectedReasonId: Long?, onReasonSelected: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = reasons.firstOrNull { it.id == selectedReasonId }?.label ?: "REQUIRED"

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            readOnly = true,
            value = selectedLabel,
            onValueChange = {},
            label = { Text("Reason") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            reasons.forEach { reason ->
                DropdownMenuItem(
                    text = { Text(reason.label) },
                    onClick = {
                        onReasonSelected(reason.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun MatchingLineRow(
    line: ReconcileResponseLine,
    requiresSerialNumber: Boolean,
    onDamageCaptureRequested: () -> Unit,
    onSerialCaptureRequested: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(Spacing.screenPadding)) {
        StateBadge(
            tone = StateTone.Confirmed,
            icon = Icons.Filled.CheckCircle,
            label = "${line.sku}  Expected ${line.quantityExpected}  Counted ${line.quantityCounted}",
        )
        LineCaptureActions(
            requiresSerialNumber = requiresSerialNumber,
            onDamageCaptureRequested = onDamageCaptureRequested,
            onSerialCaptureRequested = onSerialCaptureRequested,
        )
    }
}

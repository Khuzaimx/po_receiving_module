package com.sellernest.poreceiving.ui.screens.submissionqueue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.sellernest.poreceiving.data.local.entities.SubmissionStatus
import com.sellernest.poreceiving.ui.components.PrimaryButton
import com.sellernest.poreceiving.ui.components.StateBadge
import com.sellernest.poreceiving.ui.theme.Spacing
import com.sellernest.poreceiving.ui.theme.StateTone
import kotlinx.coroutines.delay

/**
 * §7.12. "Nothing is ever silently dropped" -- every queued, sending,
 * receipted, and failed submission is listed here, and DISCARD is a real
 * confirmation dialog naming the PO, never a swipe gesture (§3.1).
 */
@Composable
fun SubmissionQueueScreen(
    onNavigateToReviewAndSubmit: (draftId: Long) -> Unit,
    viewModel: SubmissionQueueViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    SubmissionQueueContent(
        state = state,
        onEditAndResubmit = onNavigateToReviewAndSubmit,
        onDiscardRequested = { viewModel.onEvent(SubmissionQueueUiEvent.DiscardRequested(it)) },
        onDiscardConfirmed = { viewModel.onEvent(SubmissionQueueUiEvent.DiscardConfirmed) },
        onDiscardCancelled = { viewModel.onEvent(SubmissionQueueUiEvent.DiscardCancelled) },
    )
}

@Composable
internal fun SubmissionQueueContent(
    state: SubmissionQueueUiState,
    onEditAndResubmit: (draftId: Long) -> Unit,
    onDiscardRequested: (SubmissionQueueItem) -> Unit,
    onDiscardConfirmed: () -> Unit,
    onDiscardCancelled: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "SUBMISSIONS",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(Spacing.screenPadding),
        )

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(items = state.items, key = { it.draftId }) { item ->
                SubmissionRow(item = item, onEditAndResubmit = onEditAndResubmit, onDiscardRequested = onDiscardRequested)
                HorizontalDivider()
            }
        }
    }

    state.discardTarget?.let { target ->
        AlertDialog(
            onDismissRequest = onDiscardCancelled,
            title = { Text("Discard submission?") },
            text = { Text("This discards the submission for ${target.purchaseOrderNumber}. This cannot be undone.") },
            confirmButton = { TextButton(onClick = onDiscardConfirmed) { Text("DISCARD") } },
            dismissButton = { TextButton(onClick = onDiscardCancelled) { Text("CANCEL") } },
        )
    }
}

@Composable
private fun SubmissionRow(
    item: SubmissionQueueItem,
    onEditAndResubmit: (draftId: Long) -> Unit,
    onDiscardRequested: (SubmissionQueueItem) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(Spacing.screenPadding)) {
        when (item.status) {
            SubmissionStatus.PENDING -> StateBadge(
                tone = StateTone.Pending,
                icon = Icons.Filled.CloudUpload,
                label = "${item.purchaseOrderNumber}  QUEUED",
            )

            SubmissionStatus.SENDING -> {
                val secondsRemaining = rememberCountdownSeconds(item.nextRetryAtEpochMillis)
                StateBadge(
                    tone = StateTone.Pending,
                    icon = Icons.Filled.CloudUpload,
                    label = "${item.purchaseOrderNumber}  SENDING…",
                )
                val countdown = secondsRemaining?.let { " · retrying in ${it}s" }.orEmpty()
                Text(text = "Attempt ${item.attemptCount}$countdown", style = MaterialTheme.typography.bodyLarge)
            }

            SubmissionStatus.RECEIPTED -> {
                StateBadge(
                    tone = StateTone.Confirmed,
                    icon = Icons.Filled.CheckCircle,
                    label = "${item.purchaseOrderNumber}  RECEIPTED",
                )
                Text(text = "Receipt #${item.receiptId}", style = MaterialTheme.typography.bodyLarge)
                if (item.failedLines.isNotEmpty()) {
                    // §9.4: "partial failure is normal" -- never presented as
                    // wholly successful when some lines didn't post.
                    item.failedLines.forEach { failure ->
                        Text(
                            text = "Line ${failure.purchaseOrderItemId}: ${failure.error}",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }

            SubmissionStatus.FAILED -> {
                StateBadge(
                    tone = StateTone.Error,
                    icon = Icons.Filled.Warning,
                    label = "${item.purchaseOrderNumber}  FAILED",
                )
                item.lastError?.let { Text(text = it, style = MaterialTheme.typography.bodyLarge) }
                PrimaryButton(text = "EDIT & RESUBMIT", onClick = { onEditAndResubmit(item.draftId) })
                TextButton(onClick = { onDiscardRequested(item) }) { Text("DISCARD") }
            }
        }
    }
}

@Composable
private fun rememberCountdownSeconds(targetEpochMillis: Long?): Int? {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(targetEpochMillis) {
        while (targetEpochMillis != null) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    return targetEpochMillis?.let { ((it - now) / 1000).toInt().coerceAtLeast(0) }
}

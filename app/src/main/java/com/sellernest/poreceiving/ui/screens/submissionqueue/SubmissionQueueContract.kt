package com.sellernest.poreceiving.ui.screens.submissionqueue

import com.sellernest.poreceiving.core.mvvm.UiEvent
import com.sellernest.poreceiving.core.mvvm.UiState
import com.sellernest.poreceiving.data.local.entities.SubmissionStatus
import com.sellernest.poreceiving.network.dto.ReceiveLineFailure

/** §7.12: "Nothing is ever silently dropped." One row per queued submission. */
data class SubmissionQueueItem(
    val draftId: Long,
    val purchaseOrderNumber: String,
    val status: SubmissionStatus,
    val attemptCount: Int,
    val receiptId: Long?,
    val lastError: String?,
    val failedLines: List<ReceiveLineFailure>,
    /** M5.2: WorkManager's actual next backoff attempt, for the "retrying in
     *  Ns" countdown -- null unless [status] is SENDING and WorkManager has
     *  a scheduled retry. */
    val nextRetryAtEpochMillis: Long? = null,
)

data class SubmissionQueueUiState(
    val loading: Boolean = true,
    val items: List<SubmissionQueueItem> = emptyList(),
    /** Non-null while the DISCARD confirmation naming this item's PO is showing. */
    val discardTarget: SubmissionQueueItem? = null,
) : UiState

sealed interface SubmissionQueueUiEvent : UiEvent {
    data class DiscardRequested(val item: SubmissionQueueItem) : SubmissionQueueUiEvent
    data object DiscardConfirmed : SubmissionQueueUiEvent
    data object DiscardCancelled : SubmissionQueueUiEvent
}

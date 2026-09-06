package com.sellernest.poreceiving.ui.screens.submissionqueue

import androidx.work.WorkInfo
import com.sellernest.poreceiving.core.mvvm.BaseViewModel
import com.sellernest.poreceiving.data.local.DraftRepository
import com.sellernest.poreceiving.data.local.entities.SubmissionStatus
import com.sellernest.poreceiving.network.dto.ReceiveLineFailure
import com.sellernest.poreceiving.work.submit.SubmitScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import javax.inject.Inject

/** §7.12, M5.2-M5.5. */
@HiltViewModel
class SubmissionQueueViewModel @Inject constructor(
    private val draftRepository: DraftRepository,
    private val submitScheduler: SubmitScheduler,
    private val json: Json,
) : BaseViewModel<SubmissionQueueUiState, SubmissionQueueUiEvent>(SubmissionQueueUiState()) {

    init {
        scope.launch {
            draftRepository.observeSubmissions()
                .flatMapLatest { submissions ->
                    val sendingIds = submissions.filter { it.status == SubmissionStatus.SENDING }.map { it.draftId }
                    if (sendingIds.isEmpty()) {
                        flowOf(submissions to emptyMap<Long, WorkInfo?>())
                    } else {
                        combine(sendingIds.map { id -> submitScheduler.observeWorkInfo(id).map { id to it.firstOrNull() } }) { pairs ->
                            submissions to pairs.toMap()
                        }
                    }
                }
                .collectLatest { (submissions, workInfoByDraftId) ->
                    val items = submissions.map { submission ->
                        val draft = draftRepository.getDraft(submission.draftId)
                        val nextRetryAt = workInfoByDraftId[submission.draftId]
                            ?.nextScheduleTimeMillis
                            ?.takeIf { it > 0 && it != Long.MAX_VALUE }
                        SubmissionQueueItem(
                            draftId = submission.draftId,
                            purchaseOrderNumber = draft?.purchaseOrderNumber.orEmpty(),
                            status = submission.status,
                            attemptCount = submission.attemptCount,
                            receiptId = submission.receiptId,
                            lastError = submission.lastError,
                            failedLines = submission.failedLinesJson?.let { decodeFailures(it) }.orEmpty(),
                            nextRetryAtEpochMillis = nextRetryAt,
                        )
                    }
                    updateState { it.copy(loading = false, items = items) }
                }
        }
    }

    override fun onEvent(event: SubmissionQueueUiEvent) {
        when (event) {
            is SubmissionQueueUiEvent.DiscardRequested -> updateState { it.copy(discardTarget = event.item) }
            SubmissionQueueUiEvent.DiscardCancelled -> updateState { it.copy(discardTarget = null) }
            SubmissionQueueUiEvent.DiscardConfirmed -> scope.launch {
                currentState.discardTarget?.let { draftRepository.discard(it.draftId) }
                updateState { it.copy(discardTarget = null) }
            }
        }
    }

    private fun decodeFailures(failedLinesJson: String): List<ReceiveLineFailure> =
        runCatching { json.decodeFromString<List<ReceiveLineFailure>>(failedLinesJson) }.getOrDefault(emptyList())
}

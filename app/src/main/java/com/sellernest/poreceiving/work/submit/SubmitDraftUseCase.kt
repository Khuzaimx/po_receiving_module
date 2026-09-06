package com.sellernest.poreceiving.work.submit

import com.sellernest.poreceiving.data.local.DraftRepository
import com.sellernest.poreceiving.network.ApiResult
import com.sellernest.poreceiving.network.ApiService
import com.sellernest.poreceiving.network.safeApiCall
import com.sellernest.poreceiving.work.photo.PhotoUploadScheduler
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

sealed interface SubmitDraftOutcome {
    data object Success : SubmitDraftOutcome
    /** Transient (offline, timeout) -- WorkManager's own backoff should retry. */
    data object Retry : SubmitDraftOutcome
    /** Terminal -- the identical payload would fail identically on retry. */
    data object Failure : SubmitDraftOutcome
}

/**
 * §8/§9.4/M5.2-M5.4: the actual submit algorithm, kept separate from
 * [SubmitDraftWorker] so it's a plain, `Context`/`WorkerParameters`-free class
 * a JUnit test can exercise directly against fakes -- this environment has no
 * JDK/Gradle to run `androidx.work:work-testing`'s `TestListenableWorkerBuilder`
 * (which needs a real or Robolectric `Context`, neither available here).
 *
 * The same [com.sellernest.poreceiving.data.local.entities.DraftEntity.idempotencyKey]
 * is sent on every attempt -- [DraftRepository.buildReceiveRequest] reads it
 * straight off the draft, never generates one -- so a retried submission
 * replays rather than re-receiving stock (§8, §10).
 */
class SubmitDraftUseCase @Inject constructor(
    private val draftRepository: DraftRepository,
    private val apiService: ApiService,
    private val json: Json,
    private val photoUploadScheduler: PhotoUploadScheduler,
) {
    suspend fun execute(draftId: Long): SubmitDraftOutcome {
        val draft = draftRepository.getDraft(draftId) ?: return SubmitDraftOutcome.Failure
        val submission = draftRepository.getSubmission(draftId) ?: return SubmitDraftOutcome.Failure
        val request = draftRepository.buildReceiveRequest(draftId) ?: return SubmitDraftOutcome.Failure

        draftRepository.markSubmissionSending(draftId, submission.attemptCount + 1)

        return when (val result = safeApiCall(json) { apiService.receive(draft.purchaseOrderId, request) }) {
            is ApiResult.Success -> {
                // §9.4: `updated`/`failed` can both be non-empty on the same
                // 2xx response -- that is still a successful, RECEIPTED
                // submit from the draft's point of view (M5.3).
                val failedLinesJson = result.body.failed.takeIf { it.isNotEmpty() }
                    ?.let { json.encodeToString(it) }
                draftRepository.markSubmissionReceipted(draftId, result.body.receiptId, failedLinesJson)
                // M6.1: "started once the receipt id is known" -- never before.
                draftRepository.observePhotos(draftId).first()
                    .filterNot { it.uploaded }
                    .forEach { photo -> photoUploadScheduler.enqueue(photo.id) }
                SubmitDraftOutcome.Success
            }

            is ApiResult.IdempotencyConflict -> {
                // §10: the payload changed after this key was used. Never
                // mint a new key -- surface it as a terminal failure with the
                // server's own explanation so M5.5's screen can show it.
                draftRepository.markSubmissionFailed(
                    draftId,
                    result.detail ?: "This submission's payload changed after it was first sent. " +
                        "The earlier submission stands -- view its receipt, or void and recount.",
                )
                SubmitDraftOutcome.Failure
            }

            is ApiResult.HttpError -> {
                // A rejected payload (e.g. an unpermitted over-receipt) --
                // retrying the identical payload would fail identically, so
                // this is terminal, not transient.
                draftRepository.markSubmissionFailed(draftId, result.error?.detail ?: result.error?.error ?: "HTTP ${result.code}")
                SubmitDraftOutcome.Failure
            }

            is ApiResult.NetworkError -> {
                // Offline or transient -- let WorkManager's exponential
                // backoff retry rather than terminally failing (§8).
                SubmitDraftOutcome.Retry
            }
        }
    }
}

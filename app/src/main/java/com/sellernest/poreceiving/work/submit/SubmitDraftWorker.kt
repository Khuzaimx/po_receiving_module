package com.sellernest.poreceiving.work.submit

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * §8/§9.4/M5.2-M5.4: the one place `/receive/` is actually called, via
 * [SubmitDraftUseCase] (see that class's doc for why the algorithm lives
 * there and not here). Enqueued exclusively via [SubmitScheduler] (unique
 * work per draft, so a double tap on SUBMIT can enqueue only one unit of
 * work) and configured with [com.sellernest.poreceiving.work.WorkRequestFactory]'s
 * exponential backoff plus a network constraint, so an offline submit is
 * deferred rather than failed (§8).
 */
@HiltWorker
class SubmitDraftWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val useCase: SubmitDraftUseCase,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val draftId = inputData.getLong(KEY_DRAFT_ID, -1L)
        if (draftId == -1L) return Result.failure()

        return when (useCase.execute(draftId)) {
            SubmitDraftOutcome.Success -> Result.success()
            SubmitDraftOutcome.Retry -> Result.retry()
            SubmitDraftOutcome.Failure -> Result.failure()
        }
    }

    companion object {
        const val KEY_DRAFT_ID = "draftId"
    }
}

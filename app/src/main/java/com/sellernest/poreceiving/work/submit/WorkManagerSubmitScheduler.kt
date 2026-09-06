package com.sellernest.poreceiving.work.submit

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.sellernest.poreceiving.work.WorkRequestFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [SubmitScheduler] backed by the real, process-wide [WorkManager]. See that
 * interface's doc for the double-tap/retry semantics `ExistingWorkPolicy.KEEP`
 * plus a deterministic [uniqueWorkName] provide.
 */
@Singleton
class WorkManagerSubmitScheduler @Inject constructor(@ApplicationContext private val context: Context) : SubmitScheduler {

    override fun enqueue(draftId: Long) {
        val request = WorkRequestFactory.oneTimeRequest<SubmitDraftWorker>(
            inputData = Data.Builder().putLong(SubmitDraftWorker.KEY_DRAFT_ID, draftId).build(),
        )
        WorkManager.getInstance(context).enqueueUniqueWork(uniqueWorkName(draftId), ExistingWorkPolicy.KEEP, request)
    }

    override fun observeWorkInfo(draftId: Long): Flow<List<WorkInfo>> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(uniqueWorkName(draftId))

    companion object {
        fun uniqueWorkName(draftId: Long) = "submit-draft-$draftId"
    }
}

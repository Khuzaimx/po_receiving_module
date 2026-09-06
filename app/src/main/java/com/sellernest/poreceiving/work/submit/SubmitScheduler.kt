package com.sellernest.poreceiving.work.submit

import androidx.work.WorkInfo
import kotlinx.coroutines.flow.Flow

/**
 * M5.2: enqueues the actual submit work for a draft, and lets a screen observe
 * its live attempt/backoff state. An interface -- not [WorkManagerSubmitScheduler]
 * directly -- so a ViewModel test can substitute a fake instead of touching
 * a real, process-wide `WorkManager` instance.
 */
interface SubmitScheduler {
    /**
     * Unique work per draft (M5.2: "a double tap cannot enqueue two
     * submissions"). Safe to call repeatedly for the same draft -- while an
     * attempt is in flight it's a no-op; once that attempt has terminated
     * (success or a terminal failure) a fresh call enqueues a real retry,
     * which is exactly M5.5's EDIT & RESUBMIT.
     */
    fun enqueue(draftId: Long)

    /** M5.5: live attempt/backoff info for the submissions screen. */
    fun observeWorkInfo(draftId: Long): Flow<List<WorkInfo>>
}

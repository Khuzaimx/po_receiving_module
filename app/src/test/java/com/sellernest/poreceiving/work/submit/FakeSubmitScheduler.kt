package com.sellernest.poreceiving.work.submit

import androidx.work.WorkInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Records every [enqueue] call instead of touching a real, process-wide `WorkManager`. */
internal class FakeSubmitScheduler : SubmitScheduler {
    val enqueuedDraftIds = mutableListOf<Long>()
    private val workInfoFlows = mutableMapOf<Long, MutableStateFlow<List<WorkInfo>>>()

    override fun enqueue(draftId: Long) {
        enqueuedDraftIds.add(draftId)
    }

    /** Tests that need specific [WorkInfo] values can push them via the returned flow's [MutableStateFlow.value]. */
    fun workInfoFlowFor(draftId: Long): MutableStateFlow<List<WorkInfo>> =
        workInfoFlows.getOrPut(draftId) { MutableStateFlow(emptyList()) }

    override fun observeWorkInfo(draftId: Long): Flow<List<WorkInfo>> = workInfoFlowFor(draftId)
}

package com.sellernest.poreceiving.data.local

import com.sellernest.poreceiving.data.local.dao.QueuedSubmissionDao
import com.sellernest.poreceiving.data.local.entities.QueuedSubmissionEntity
import com.sellernest.poreceiving.data.local.entities.SubmissionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

internal class FakeQueuedSubmissionDao : QueuedSubmissionDao {
    private val submissionsFlow = MutableStateFlow<List<QueuedSubmissionEntity>>(emptyList())

    override suspend fun upsert(submission: QueuedSubmissionEntity) {
        submissionsFlow.value = submissionsFlow.value.filterNot { it.draftId == submission.draftId } + submission
    }

    override suspend fun update(submission: QueuedSubmissionEntity) {
        submissionsFlow.value = submissionsFlow.value.map { if (it.draftId == submission.draftId) submission else it }
    }

    override suspend fun getForDraft(draftId: Long): QueuedSubmissionEntity? =
        submissionsFlow.value.firstOrNull { it.draftId == draftId }

    override suspend fun deleteForDraft(draftId: Long) {
        submissionsFlow.value = submissionsFlow.value.filterNot { it.draftId == draftId }
    }

    override fun observeAll(): Flow<List<QueuedSubmissionEntity>> = submissionsFlow

    override fun observePendingCount(): Flow<Int> = submissionsFlow.map { all ->
        all.count { it.status == SubmissionStatus.PENDING || it.status == SubmissionStatus.SENDING }
    }
}

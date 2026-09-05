package com.sellernest.poreceiving.ui.screens.warehouseselection

import com.sellernest.poreceiving.data.local.dao.DraftDao
import com.sellernest.poreceiving.data.local.entities.DraftEntity
import com.sellernest.poreceiving.data.local.entities.DraftState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Minimal in-memory [DraftDao] for tests that only need `getAnyActiveDraft`. */
internal class FakeDraftDao : DraftDao {

    private val drafts = mutableListOf<DraftEntity>()

    fun seed(draft: DraftEntity) {
        drafts.add(draft)
    }

    override suspend fun insert(draft: DraftEntity): Long {
        drafts.add(draft)
        return draft.id
    }

    override suspend fun update(draft: DraftEntity) {
        drafts.removeAll { it.id == draft.id }
        drafts.add(draft)
    }

    override suspend fun getById(draftId: Long): DraftEntity? = drafts.firstOrNull { it.id == draftId }

    override fun observeById(draftId: Long): Flow<DraftEntity?> =
        MutableStateFlow(drafts.firstOrNull { it.id == draftId })

    override suspend fun getActiveDraftFor(
        purchaseOrderId: Long,
        warehouseId: Long,
        terminalStates: List<DraftState>,
    ): DraftEntity? = drafts.firstOrNull {
        it.purchaseOrderId == purchaseOrderId && it.warehouseId == warehouseId && it.state !in terminalStates
    }

    override fun observeCountInState(state: DraftState): Flow<Int> =
        MutableStateFlow(drafts.count { it.state == state })

    override suspend fun getAnyActiveDraft(): DraftEntity? =
        drafts.firstOrNull { it.state != DraftState.DISCARDED && it.state != DraftState.RECEIPTED }
}

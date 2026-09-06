package com.sellernest.poreceiving.ui.screens.warehouseselection

import com.sellernest.poreceiving.data.local.dao.DraftDao
import com.sellernest.poreceiving.data.local.entities.DraftEntity
import com.sellernest.poreceiving.data.local.entities.DraftState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Minimal in-memory [DraftDao]. Backed by one real [MutableStateFlow] (not a
 * snapshot re-created per call), and assigns ids itself on [insert] rather
 * than trusting the caller's `id = 0` placeholder -- the same two properties
 * [com.sellernest.poreceiving.ui.screens.poheader.FakeDraftLineDao] needs for
 * the same reason: production code always passes freshly-constructed entities
 * and relies on the DAO to assign a real id and to notify observers on change,
 * exactly as Room's generated implementation would.
 */
internal class FakeDraftDao : DraftDao {

    private val draftsFlow = MutableStateFlow<List<DraftEntity>>(emptyList())
    private var nextId = 1L

    fun seed(draft: DraftEntity) {
        val assigned = if (draft.id == 0L) draft.copy(id = nextId++) else draft
        draftsFlow.value = draftsFlow.value + assigned
    }

    override suspend fun insert(draft: DraftEntity): Long {
        val id = nextId++
        draftsFlow.value = draftsFlow.value + draft.copy(id = id)
        return id
    }

    override suspend fun update(draft: DraftEntity) {
        draftsFlow.value = draftsFlow.value.map { if (it.id == draft.id) draft else it }
    }

    override suspend fun getById(draftId: Long): DraftEntity? = draftsFlow.value.firstOrNull { it.id == draftId }

    override fun observeById(draftId: Long): Flow<DraftEntity?> =
        draftsFlow.map { all -> all.firstOrNull { it.id == draftId } }

    override suspend fun getActiveDraftFor(
        purchaseOrderId: Long,
        warehouseId: Long,
        terminalStates: List<DraftState>,
    ): DraftEntity? = draftsFlow.value.firstOrNull {
        it.purchaseOrderId == purchaseOrderId && it.warehouseId == warehouseId && it.state !in terminalStates
    }

    override fun observeCountInState(state: DraftState): Flow<Int> =
        draftsFlow.map { all -> all.count { it.state == state } }

    override suspend fun getAnyActiveDraft(): DraftEntity? =
        draftsFlow.value.firstOrNull { it.state != DraftState.DISCARDED && it.state != DraftState.RECEIPTED }
}

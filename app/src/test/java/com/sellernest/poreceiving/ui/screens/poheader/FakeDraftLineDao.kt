package com.sellernest.poreceiving.ui.screens.poheader

import com.sellernest.poreceiving.data.local.dao.DraftLineDao
import com.sellernest.poreceiving.data.local.entities.DraftLineEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * A real [MutableStateFlow] backing store, not a snapshot re-created per call:
 * [observeForDraft] must re-emit after every [insert]/[update], the same way
 * Room's generated DAO does, or a ViewModel that collects it once (as
 * `ScanToCountViewModel` does) would only ever see the state as of its first
 * subscription. Ids are assigned here, not trusted from the caller, since
 * production code (`DraftRepository`) always passes freshly-constructed
 * entities with `id = 0` and relies on the DAO to assign a real one --
 * exactly as Room's autoGenerate would.
 */
internal class FakeDraftLineDao : DraftLineDao {
    private val linesFlow = MutableStateFlow<List<DraftLineEntity>>(emptyList())
    private var nextId = 1L

    fun seed(line: DraftLineEntity) {
        val assigned = if (line.id == 0L) line.copy(id = nextId++) else line
        linesFlow.value = linesFlow.value + assigned
    }

    override suspend fun insert(line: DraftLineEntity): Long {
        val id = nextId++
        linesFlow.value = linesFlow.value + line.copy(id = id)
        return id
    }

    override suspend fun update(line: DraftLineEntity) {
        linesFlow.value = linesFlow.value.map { if (it.id == line.id) line else it }
    }

    override fun observeForDraft(draftId: Long): Flow<List<DraftLineEntity>> =
        linesFlow.map { all -> all.filter { it.draftId == draftId } }

    override suspend fun getByPurchaseOrderItem(draftId: Long, purchaseOrderItemId: Long): DraftLineEntity? =
        linesFlow.value.firstOrNull { it.draftId == draftId && it.purchaseOrderItemId == purchaseOrderItemId }

    override suspend fun getById(id: Long): DraftLineEntity? =
        linesFlow.value.firstOrNull { it.id == id }

    override suspend fun getTotalCountedQuantity(draftId: Long): Int =
        linesFlow.value.filter { it.draftId == draftId }.sumOf { it.countedQuantity }
}

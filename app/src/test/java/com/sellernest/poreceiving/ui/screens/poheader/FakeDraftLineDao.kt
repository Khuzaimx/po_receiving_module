package com.sellernest.poreceiving.ui.screens.poheader

import com.sellernest.poreceiving.data.local.dao.DraftLineDao
import com.sellernest.poreceiving.data.local.entities.DraftLineEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal class FakeDraftLineDao : DraftLineDao {
    private val lines = mutableListOf<DraftLineEntity>()

    fun seed(line: DraftLineEntity) {
        lines.add(line)
    }

    override suspend fun insert(line: DraftLineEntity): Long {
        lines.add(line)
        return line.id
    }

    override suspend fun update(line: DraftLineEntity) {
        lines.removeAll { it.id == line.id }
        lines.add(line)
    }

    override fun observeForDraft(draftId: Long): Flow<List<DraftLineEntity>> =
        MutableStateFlow(lines.filter { it.draftId == draftId })

    override suspend fun getByPurchaseOrderItem(draftId: Long, purchaseOrderItemId: Long): DraftLineEntity? =
        lines.firstOrNull { it.draftId == draftId && it.purchaseOrderItemId == purchaseOrderItemId }

    override suspend fun getTotalCountedQuantity(draftId: Long): Int =
        lines.filter { it.draftId == draftId }.sumOf { it.countedQuantity }
}

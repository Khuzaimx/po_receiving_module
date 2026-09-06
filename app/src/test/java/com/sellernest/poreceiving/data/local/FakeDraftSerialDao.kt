package com.sellernest.poreceiving.data.local

import com.sellernest.poreceiving.data.local.dao.DraftLineDao
import com.sellernest.poreceiving.data.local.dao.DraftSerialDao
import com.sellernest.poreceiving.data.local.entities.DraftSerialEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Mirrors the real [DraftSerialDao]'s unique-index behaviour: [insert] throws
 * on a (draftLineId, serialValue) clash rather than silently overwriting, so
 * [DraftRepository.addSerial]'s backstop `catch` is exercised the same way it
 * would be against Room. [getTotalCountForDraft] needs a [DraftLineDao] the
 * same way the real query joins against `draft_lines` -- pass the same fake
 * instance the test's `DraftRepository` uses.
 */
internal class FakeDraftSerialDao(private val draftLineDao: DraftLineDao? = null) : DraftSerialDao {
    private val serialsFlow = MutableStateFlow<List<DraftSerialEntity>>(emptyList())
    private var nextId = 1L

    override suspend fun insert(serial: DraftSerialEntity): Long {
        val clash = serialsFlow.value.any { it.draftLineId == serial.draftLineId && it.serialValue == serial.serialValue }
        if (clash) throw android.database.sqlite.SQLiteConstraintException("duplicate serial")
        val id = nextId++
        serialsFlow.value = serialsFlow.value + serial.copy(id = id)
        return id
    }

    override suspend fun delete(serial: DraftSerialEntity) {
        serialsFlow.value = serialsFlow.value.filterNot { it.id == serial.id }
    }

    override fun observeForLine(draftLineId: Long): Flow<List<DraftSerialEntity>> =
        serialsFlow.map { all -> all.filter { it.draftLineId == draftLineId } }

    override suspend fun getTotalCountForDraft(draftId: Long): Int {
        val lineIds = draftLineDao?.observeForDraft(draftId)?.first()?.map { it.id } ?: return 0
        return serialsFlow.value.count { it.draftLineId in lineIds }
    }
}

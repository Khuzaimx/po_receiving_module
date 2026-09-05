package com.sellernest.poreceiving.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sellernest.poreceiving.data.local.entities.DraftLineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DraftLineDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(line: DraftLineEntity): Long

    @Update
    suspend fun update(line: DraftLineEntity)

    @Query("SELECT * FROM draft_lines WHERE draftId = :draftId ORDER BY id")
    fun observeForDraft(draftId: Long): Flow<List<DraftLineEntity>>

    @Query("SELECT * FROM draft_lines WHERE draftId = :draftId AND purchaseOrderItemId = :purchaseOrderItemId")
    suspend fun getByPurchaseOrderItem(draftId: Long, purchaseOrderItemId: Long): DraftLineEntity?

    /** M1.8: "RESUME DRAFT (n scanned)" -- n is total counted units, not lines. */
    @Query("SELECT COALESCE(SUM(countedQuantity), 0) FROM draft_lines WHERE draftId = :draftId")
    suspend fun getTotalCountedQuantity(draftId: Long): Int
}

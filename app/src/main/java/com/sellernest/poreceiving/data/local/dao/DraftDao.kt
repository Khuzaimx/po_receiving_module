package com.sellernest.poreceiving.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sellernest.poreceiving.data.local.entities.DraftEntity
import com.sellernest.poreceiving.data.local.entities.DraftState
import kotlinx.coroutines.flow.Flow

@Dao
interface DraftDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(draft: DraftEntity): Long

    @Update
    suspend fun update(draft: DraftEntity)

    @Query("SELECT * FROM drafts WHERE id = :draftId")
    suspend fun getById(draftId: Long): DraftEntity?

    @Query("SELECT * FROM drafts WHERE id = :draftId")
    fun observeById(draftId: Long): Flow<DraftEntity?>

    /**
     * Resolves whether a draft already exists for this PO/warehouse -- opening
     * the same PO again must resume it, never fork a second draft (§8, M3.5).
     * Excludes DISCARDED and RECEIPTED rows: those are terminal and do not count
     * as "an in-progress draft" for resume purposes.
     */
    @Query(
        """
        SELECT * FROM drafts
        WHERE purchaseOrderId = :purchaseOrderId AND warehouseId = :warehouseId
          AND state NOT IN (:terminalStates)
        LIMIT 1
        """,
    )
    suspend fun getActiveDraftFor(
        purchaseOrderId: Long,
        warehouseId: Long,
        terminalStates: List<DraftState> = listOf(DraftState.DISCARDED, DraftState.RECEIPTED),
    ): DraftEntity?

    @Query("SELECT COUNT(*) FROM drafts WHERE state = :state")
    fun observeCountInState(state: DraftState): Flow<Int>

    /**
     * M1.5: "Changing warehouse mid-session is possible from the menu but
     * blocked while a count is in progress, with an explanation naming the
     * in-progress PO." Any non-terminal draft counts, regardless of which PO
     * or warehouse it belongs to -- returns the draft (not just a boolean) so
     * the caller can name it.
     */
    @Query("SELECT * FROM drafts WHERE state NOT IN ('DISCARDED', 'RECEIPTED') LIMIT 1")
    suspend fun getAnyActiveDraft(): DraftEntity?
}

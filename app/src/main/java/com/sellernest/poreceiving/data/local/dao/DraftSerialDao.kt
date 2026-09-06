package com.sellernest.poreceiving.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sellernest.poreceiving.data.local.entities.DraftSerialEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DraftSerialDao {

    /**
     * ABORT (not IGNORE/REPLACE): the unique (draftLineId, serialValue) index
     * exists precisely so a duplicate scan fails loudly here and can be surfaced
     * as the "duplicate serial" rejection (§7.9, §10), not silently absorbed.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(serial: DraftSerialEntity): Long

    @Delete
    suspend fun delete(serial: DraftSerialEntity)

    @Query("SELECT * FROM draft_serials WHERE draftLineId = :draftLineId ORDER BY id")
    fun observeForLine(draftLineId: Long): Flow<List<DraftSerialEntity>>

    /** M5.1: backs the Review-and-Submit totals -- the whole draft's captured
     *  serial count, not any one line's. */
    @Query(
        """
        SELECT COUNT(*) FROM draft_serials
        WHERE draftLineId IN (SELECT id FROM draft_lines WHERE draftId = :draftId)
        """,
    )
    suspend fun getTotalCountForDraft(draftId: Long): Int
}

package com.sellernest.poreceiving.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sellernest.poreceiving.data.local.entities.QueuedSubmissionEntity
import com.sellernest.poreceiving.data.local.entities.SubmissionStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface QueuedSubmissionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(submission: QueuedSubmissionEntity)

    @Update
    suspend fun update(submission: QueuedSubmissionEntity)

    @Query("SELECT * FROM queued_submissions WHERE draftId = :draftId")
    suspend fun getForDraft(draftId: Long): QueuedSubmissionEntity?

    /** M5.5: DISCARD on a failed submission removes its queue row too, so a
     *  discarded draft doesn't keep showing as FAILED on this screen forever. */
    @Query("DELETE FROM queued_submissions WHERE draftId = :draftId")
    suspend fun deleteForDraft(draftId: Long)

    @Query("SELECT * FROM queued_submissions ORDER BY enqueuedAtEpochMillis DESC")
    fun observeAll(): Flow<List<QueuedSubmissionEntity>>

    /** Backs the persistent status bar's pending count (§3.2, M0.5). */
    @Query("SELECT COUNT(*) FROM queued_submissions WHERE status IN ('PENDING', 'SENDING')")
    fun observePendingCount(): Flow<Int>
}

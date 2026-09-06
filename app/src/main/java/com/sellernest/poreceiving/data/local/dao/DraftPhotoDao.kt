package com.sellernest.poreceiving.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sellernest.poreceiving.data.local.entities.DraftPhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DraftPhotoDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(photo: DraftPhotoEntity): Long

    @Update
    suspend fun update(photo: DraftPhotoEntity)

    /** M4.4: "Removing a photo before submit removes it from the queue too." */
    @Delete
    suspend fun delete(photo: DraftPhotoEntity)

    @Query("SELECT * FROM draft_photos WHERE id = :photoId")
    suspend fun getById(photoId: Long): DraftPhotoEntity?

    @Query("SELECT * FROM draft_photos WHERE draftId = :draftId ORDER BY id")
    fun observeForDraft(draftId: Long): Flow<List<DraftPhotoEntity>>

    @Query("SELECT * FROM draft_photos WHERE uploaded = 0")
    suspend fun getAllPendingUpload(): List<DraftPhotoEntity>
}

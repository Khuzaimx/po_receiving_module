package com.sellernest.poreceiving.data.local

import com.sellernest.poreceiving.data.local.dao.DraftPhotoDao
import com.sellernest.poreceiving.data.local.entities.DraftPhotoEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * One real [MutableStateFlow] backing store, not a snapshot re-created per
 * call -- see [com.sellernest.poreceiving.ui.screens.poheader.FakeDraftLineDao]'s
 * doc for why that distinction matters for any ViewModel that subscribes once.
 */
internal class FakeDraftPhotoDao : DraftPhotoDao {
    private val photosFlow = MutableStateFlow<List<DraftPhotoEntity>>(emptyList())
    private var nextId = 1L

    override suspend fun insert(photo: DraftPhotoEntity): Long {
        val id = nextId++
        photosFlow.value = photosFlow.value + photo.copy(id = id)
        return id
    }

    override suspend fun update(photo: DraftPhotoEntity) {
        photosFlow.value = photosFlow.value.map { if (it.id == photo.id) photo else it }
    }

    override suspend fun delete(photo: DraftPhotoEntity) {
        photosFlow.value = photosFlow.value.filterNot { it.id == photo.id }
    }

    override suspend fun getById(photoId: Long): DraftPhotoEntity? =
        photosFlow.value.firstOrNull { it.id == photoId }

    override fun observeForDraft(draftId: Long): Flow<List<DraftPhotoEntity>> =
        photosFlow.map { all -> all.filter { it.draftId == draftId } }

    override suspend fun getAllPendingUpload(): List<DraftPhotoEntity> =
        photosFlow.value.filter { !it.uploaded }
}

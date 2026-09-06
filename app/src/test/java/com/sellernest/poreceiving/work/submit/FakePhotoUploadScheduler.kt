package com.sellernest.poreceiving.work.submit

import com.sellernest.poreceiving.work.photo.PhotoUploadScheduler

internal class FakePhotoUploadScheduler : PhotoUploadScheduler {
    val enqueuedPhotoIds = mutableListOf<Long>()

    override fun enqueue(photoId: Long) {
        enqueuedPhotoIds.add(photoId)
    }
}

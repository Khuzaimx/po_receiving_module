package com.sellernest.poreceiving.work.photo

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.sellernest.poreceiving.work.WorkRequestFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkManagerPhotoUploadScheduler @Inject constructor(@ApplicationContext private val context: Context) : PhotoUploadScheduler {

    override fun enqueue(photoId: Long) {
        val request = WorkRequestFactory.oneTimeRequest<UploadPhotoWorker>(
            inputData = Data.Builder().putLong(UploadPhotoWorker.KEY_PHOTO_ID, photoId).build(),
        )
        WorkManager.getInstance(context).enqueueUniqueWork(uniqueWorkName(photoId), ExistingWorkPolicy.KEEP, request)
    }

    companion object {
        fun uniqueWorkName(photoId: Long) = "upload-photo-$photoId"
    }
}

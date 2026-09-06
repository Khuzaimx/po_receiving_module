package com.sellernest.poreceiving.work.photo

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** §9.5/§10/M6.1: see [UploadPhotoUseCase]'s doc for the actual algorithm and
 *  why it lives there rather than here. */
@HiltWorker
class UploadPhotoWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val useCase: UploadPhotoUseCase,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val photoId = inputData.getLong(KEY_PHOTO_ID, -1L)
        if (photoId == -1L) return Result.failure()

        return when (useCase.execute(photoId)) {
            UploadPhotoOutcome.Success -> Result.success()
            UploadPhotoOutcome.Retry -> Result.retry()
            UploadPhotoOutcome.Failure -> Result.failure()
        }
    }

    companion object {
        const val KEY_PHOTO_ID = "photoId"
    }
}

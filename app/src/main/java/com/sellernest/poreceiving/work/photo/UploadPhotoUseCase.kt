package com.sellernest.poreceiving.work.photo

import com.sellernest.poreceiving.data.local.DraftRepository
import com.sellernest.poreceiving.network.ApiResult
import com.sellernest.poreceiving.network.ApiService
import com.sellernest.poreceiving.network.safeApiCall
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject

sealed interface UploadPhotoOutcome {
    data object Success : UploadPhotoOutcome
    data object Retry : UploadPhotoOutcome
    data object Failure : UploadPhotoOutcome
}

/**
 * §9.5/§10/M6.1: kept separate from [UploadPhotoWorker] for the same testability
 * reason as [com.sellernest.poreceiving.work.submit.SubmitDraftUseCase] --
 * a plain class a JUnit test can exercise against fakes, with no `Context`/
 * `WorkerParameters` involved.
 *
 * By the time this can even run, the receipt has already posted successfully
 * (only ever enqueued from [com.sellernest.poreceiving.work.submit.SubmitDraftUseCase]'s
 * success branch) -- this class has no code path that touches
 * [com.sellernest.poreceiving.data.local.entities.QueuedSubmissionEntity] or
 * the draft's state at all, which is what makes "a photo failure must never
 * block or reverse a posted receipt" (§10) true structurally.
 *
 * `purchase_order_receipt_item` (§9.5) is assumed to be the same
 * [com.sellernest.poreceiving.data.local.entities.DraftLineEntity.purchaseOrderItemId]
 * used everywhere else in this app -- §9 never introduces a distinct
 * "receipt item id" concept anywhere. Confirm against the real backend.
 */
class UploadPhotoUseCase @Inject constructor(
    private val draftRepository: DraftRepository,
    private val apiService: ApiService,
    private val json: Json,
) {
    suspend fun execute(photoId: Long): UploadPhotoOutcome {
        // Removed before it ever uploaded (M4.4: "removing a photo before
        // submit removes it from the queue too") -- nothing left to do.
        val photo = draftRepository.getPhoto(photoId) ?: return UploadPhotoOutcome.Success
        if (photo.uploaded) return UploadPhotoOutcome.Success

        val submission = draftRepository.getSubmission(photo.draftId) ?: return UploadPhotoOutcome.Retry
        val receiptId = submission.receiptId ?: return UploadPhotoOutcome.Retry

        val file = File(photo.localFilePath)
        if (!file.exists()) return UploadPhotoOutcome.Failure

        val purchaseOrderReceiptItem = photo.draftLineId?.let { draftRepository.getLine(it)?.purchaseOrderItemId }
        val part = MultipartBody.Part.createFormData("file", file.name, file.asRequestBody("image/jpeg".toMediaType()))

        return when (
            val result = safeApiCall(json) {
                apiService.uploadPhoto(receiptId, part, purchaseOrderReceiptItem, photo.caption)
            }
        ) {
            is ApiResult.Success -> {
                draftRepository.markPhotoUploaded(photoId, result.body.id)
                file.delete()
                UploadPhotoOutcome.Success
            }
            is ApiResult.NetworkError -> UploadPhotoOutcome.Retry
            // A rejected upload (bad file, unsupported type) won't succeed on
            // an identical retry -- terminal, but the local file is kept
            // (never deleted before a 201) so nothing is lost.
            is ApiResult.HttpError, is ApiResult.IdempotencyConflict -> UploadPhotoOutcome.Failure
        }
    }
}

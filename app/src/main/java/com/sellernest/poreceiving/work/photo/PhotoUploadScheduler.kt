package com.sellernest.poreceiving.work.photo

/**
 * M6.1: "A separate WorkManager chain per photo, started once the receipt id
 * is known." Enqueued by [com.sellernest.poreceiving.work.submit.SubmitDraftWorker]
 * right after a successful `/receive/` call, never before -- a photo has
 * nowhere to upload to until then. An interface for the same reason
 * [com.sellernest.poreceiving.work.submit.SubmitScheduler] is one: so a
 * worker/ViewModel test can substitute a fake instead of touching a real
 * `WorkManager`.
 */
interface PhotoUploadScheduler {
    /** Unique work per photo -- retried independently, so one failing photo
     *  never blocks or reverses another line's already-posted photo. */
    fun enqueue(photoId: Long)
}

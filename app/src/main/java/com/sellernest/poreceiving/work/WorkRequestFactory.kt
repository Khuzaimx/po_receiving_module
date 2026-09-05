package com.sellernest.poreceiving.work

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkRequest
import java.time.Duration

/**
 * Every queued piece of work (submit in M5.2, photo upload in M6.1) is built
 * through here so exponential backoff is configured once, centrally, rather than
 * copy-pasted per worker (§8: "Queued via WorkManager with exponential backoff").
 * [WorkRequest.MIN_BACKOFF_MILLIS] is WorkManager's own floor -- this does not
 * invent a shorter one.
 */
object WorkRequestFactory {

    val backoffPolicy: BackoffPolicy = BackoffPolicy.EXPONENTIAL
    val initialBackoffDelay: Duration = Duration.ofMillis(WorkRequest.MIN_BACKOFF_MILLIS)

    /** Deferred (not failed) while offline, per §8's "submit while offline -> queued". */
    val requiresConnection: Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    inline fun <reified W : ListenableWorker> oneTimeRequest(
        inputData: Data = Data.EMPTY,
        constraints: Constraints = requiresConnection,
    ): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<W>()
            .setInputData(inputData)
            .setConstraints(constraints)
            .setBackoffCriteria(backoffPolicy, initialBackoffDelay)
            .build()
}

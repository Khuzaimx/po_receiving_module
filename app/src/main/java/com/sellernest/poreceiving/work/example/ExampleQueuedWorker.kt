package com.sellernest.poreceiving.work.example

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Worked example of the `@HiltWorker` convention every later queued-work class
 * (the M5.2 submit worker, the M6.1 photo upload worker) follows: assisted
 * injection for the required `Context`/`WorkerParameters`, plus constructor
 * injection for any real dependency. Not part of the receiving flow -- exists so
 * this pattern has one concrete, compiling reference (mirroring
 * `ExampleCounterViewModel` for the MVVM convention in M0.1).
 *
 * Enqueue via [com.sellernest.poreceiving.work.WorkRequestFactory.oneTimeRequest]
 * so exponential backoff is configured the same way every real worker's is.
 */
@HiltWorker
class ExampleQueuedWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = Result.success()
}

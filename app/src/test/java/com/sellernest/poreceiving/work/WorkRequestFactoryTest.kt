package com.sellernest.poreceiving.work

import androidx.work.BackoffPolicy
import androidx.work.NetworkType
import androidx.work.WorkRequest
import com.sellernest.poreceiving.work.example.ExampleQueuedWorker
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * §8: "Submit while offline: Queued via WorkManager with exponential backoff."
 * Confirms every request built through [WorkRequestFactory] actually carries
 * that policy and the network constraint, rather than trusting each call site
 * to set it correctly.
 */
class WorkRequestFactoryTest {

    @Test
    fun `request carries exponential backoff at WorkManager's minimum delay`() {
        val request = WorkRequestFactory.oneTimeRequest<ExampleQueuedWorker>()

        assertEquals(BackoffPolicy.EXPONENTIAL, request.workSpec.backoffPolicy)
        assertEquals(WorkRequest.MIN_BACKOFF_MILLIS, request.workSpec.backoffDelayDuration)
    }

    @Test
    fun `request defaults to requiring a network connection`() {
        val request = WorkRequestFactory.oneTimeRequest<ExampleQueuedWorker>()

        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
    }
}

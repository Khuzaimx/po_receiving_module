package com.sellernest.poreceiving.work

import androidx.hilt.work.HiltWorkerFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sellernest.poreceiving.PoReceivingApp
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M0.3: "Hilt-provided WorkManager with a HiltWorkerFactory." Any `@HiltWorker`
 * (the M5.2 submit worker, the M6.1 photo worker, and the worked example
 * [com.sellernest.poreceiving.work.example.ExampleQueuedWorker]) can only be
 * constructed by WorkManager if the app's [android.app.Application] actually
 * supplies one -- this confirms the real app does, rather than assuming the
 * `Configuration.Provider` wiring in [PoReceivingApp] compiles correctly.
 */
@RunWith(AndroidJUnit4::class)
class HiltWorkerFactoryWiringTest {

    @Test
    fun applicationSuppliesAHiltWorkerFactory() {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as PoReceivingApp

        val factory = app.workManagerConfiguration.workerFactory
        assertTrue(
            "Expected the app's WorkManager Configuration to use HiltWorkerFactory, was ${factory::class}",
            factory is HiltWorkerFactory,
        )
    }
}

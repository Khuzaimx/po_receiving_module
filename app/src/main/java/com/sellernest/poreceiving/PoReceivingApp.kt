package com.sellernest.poreceiving

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.sellernest.poreceiving.scan.datawedge.DataWedgeIntegration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point. `@HiltAndroidApp` roots the DI graph used by every
 * ViewModel, Worker, and singleton created in later milestones.
 *
 * Implements [Configuration.Provider] so WorkManager uses [HiltWorkerFactory] —
 * required for any `@HiltWorker`-annotated worker (M0.3, M5, M6) to receive
 * injected dependencies rather than a no-arg constructor.
 */
@HiltAndroidApp
class PoReceivingApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var dataWedgeIntegration: DataWedgeIntegration

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // §2.1: profile creation/registration on every launch, not gated on
        // "first launch" -- see DataWedgeProfileManager's doc for why.
        dataWedgeIntegration.start()
    }
}

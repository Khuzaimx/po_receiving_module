package com.sellernest.poreceiving.scan

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ScanModule {

    @Binds
    @Singleton
    abstract fun bindScanFeedbackService(impl: ToneAndHapticScanFeedbackService): ScanFeedbackService
}

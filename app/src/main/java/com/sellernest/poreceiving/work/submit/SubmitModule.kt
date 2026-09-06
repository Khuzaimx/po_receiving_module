package com.sellernest.poreceiving.work.submit

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SubmitModule {

    @Binds
    @Singleton
    abstract fun bindSubmitScheduler(impl: WorkManagerSubmitScheduler): SubmitScheduler
}

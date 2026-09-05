package com.sellernest.poreceiving.session

import com.sellernest.poreceiving.core.connectivity.AndroidConnectivityObserver
import com.sellernest.poreceiving.core.connectivity.ConnectivityObserver
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SessionModule {

    @Binds
    @Singleton
    abstract fun bindConnectivityObserver(impl: AndroidConnectivityObserver): ConnectivityObserver

    @Binds
    @Singleton
    abstract fun bindSessionInfoProvider(impl: NoOpSessionInfoProvider): SessionInfoProvider
}

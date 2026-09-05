package com.sellernest.poreceiving.session

import com.sellernest.poreceiving.core.connectivity.AndroidConnectivityObserver
import com.sellernest.poreceiving.core.connectivity.ConnectivityObserver
import com.sellernest.poreceiving.network.ActiveOrgProvider
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

    /** Replaces the M0.5 `NoOpSessionInfoProvider` stub -- see [MeBackedSessionInfoProvider]. */
    @Binds
    @Singleton
    abstract fun bindSessionInfoProvider(impl: MeBackedSessionInfoProvider): SessionInfoProvider

    /** Replaces the M0.2 `NoOpActiveOrgProvider` stub -- see [MeBackedActiveOrgProvider]. */
    @Binds
    @Singleton
    abstract fun bindActiveOrgProvider(impl: MeBackedActiveOrgProvider): ActiveOrgProvider
}

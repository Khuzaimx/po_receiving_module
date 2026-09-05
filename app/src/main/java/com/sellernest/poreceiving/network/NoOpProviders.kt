package com.sellernest.poreceiving.network

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stand-in for [TokenProvider] until M1.2 (encrypted token storage) lands. Always
 * signed-out. Exists only so the DI graph compiles and [AuthInterceptor] can be
 * exercised by M0.2's tests today; replace the `@Binds` below in M1.2, not this class.
 */
internal class NoOpTokenProvider @Inject constructor() : TokenProvider {
    override suspend fun currentAccessToken(): String? = null
}

/** Stand-in for [ActiveOrgProvider] until M1.3 lands. See [NoOpTokenProvider]. */
internal class NoOpActiveOrgProvider @Inject constructor() : ActiveOrgProvider {
    override suspend fun currentActiveOrgId(): String? = null
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ProviderBindingsModule {

    @Binds
    @Singleton
    abstract fun bindTokenProvider(impl: NoOpTokenProvider): TokenProvider

    @Binds
    @Singleton
    abstract fun bindActiveOrgProvider(impl: NoOpActiveOrgProvider): ActiveOrgProvider
}

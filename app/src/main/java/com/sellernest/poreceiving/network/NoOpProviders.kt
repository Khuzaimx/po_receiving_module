package com.sellernest.poreceiving.network

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stand-in for [ActiveOrgProvider] until M1.3 lands (the active company resolved
 * from `/api/me/`). [TokenProvider]'s equivalent stand-in was replaced in M1.2 by
 * `com.sellernest.poreceiving.auth.EncryptedTokenStorage` -- see `AuthBindsModule`.
 */
internal class NoOpActiveOrgProvider @Inject constructor() : ActiveOrgProvider {
    override suspend fun currentActiveOrgId(): String? = null
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ProviderBindingsModule {

    @Binds
    @Singleton
    abstract fun bindActiveOrgProvider(impl: NoOpActiveOrgProvider): ActiveOrgProvider
}

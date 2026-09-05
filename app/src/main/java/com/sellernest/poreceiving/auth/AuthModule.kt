package com.sellernest.poreceiving.auth

import android.content.Context
import com.sellernest.poreceiving.network.TokenProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.openid.appauth.AuthorizationService
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthProvidesModule {

    /**
     * One instance for the app's process lifetime. AppAuth documents disposing
     * this when it's no longer needed to release the Custom Tabs connection;
     * for an app-scoped singleton that's process death, which reclaims it anyway.
     */
    @Provides
    @Singleton
    fun provideAuthorizationService(@ApplicationContext context: Context): AuthorizationService =
        AuthorizationService(context)
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AuthBindsModule {

    @Binds
    @Singleton
    abstract fun bindAuthGateway(impl: AppAuthGateway): AuthGateway

    @Binds
    @Singleton
    abstract fun bindTokenStorage(impl: EncryptedTokenStorage): TokenStorage

    /**
     * Replaces the M0.2 `NoOpTokenProvider` binding: [EncryptedTokenStorage] is
     * now the real, encrypted, proactively-refreshing implementation the
     * `Authorization` header interceptor reads from.
     */
    @Binds
    @Singleton
    abstract fun bindTokenProvider(impl: EncryptedTokenStorage): TokenProvider
}

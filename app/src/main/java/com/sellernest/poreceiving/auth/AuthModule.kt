package com.sellernest.poreceiving.auth

import android.content.Context
import android.net.Uri
import com.sellernest.poreceiving.BuildConfig
import com.sellernest.poreceiving.network.TokenProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.openid.appauth.AppAuthConfiguration
import net.openid.appauth.AuthorizationService
import net.openid.appauth.connectivity.ConnectionBuilder
import java.net.HttpURLConnection
import java.net.URL
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
    fun provideAuthorizationService(@ApplicationContext context: Context): AuthorizationService {
        // AppAuth's DefaultConnectionBuilder refuses non-https connections
        // outright, which would also break debug builds pointed at the local
        // plain-http mock backend (tools/mockserver/MockServer.java). Only
        // relaxed for debug builds; release keeps AppAuth's https-only default.
        val configuration = if (BuildConfig.DEBUG) {
            AppAuthConfiguration.Builder()
                .setConnectionBuilder(PlaintextAllowedConnectionBuilder)
                .build()
        } else {
            AppAuthConfiguration.DEFAULT
        }
        return AuthorizationService(context, configuration)
    }
}

private object PlaintextAllowedConnectionBuilder : ConnectionBuilder {
    override fun openConnection(uri: Uri): HttpURLConnection =
        URL(uri.toString()).openConnection() as HttpURLConnection
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

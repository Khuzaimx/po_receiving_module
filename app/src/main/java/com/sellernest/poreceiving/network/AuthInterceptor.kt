package com.sellernest.poreceiving.network

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Attaches `Authorization: Bearer <token>` to every request. This is the *only*
 * place that header is set — §5.2: "Attach it in an OkHttp interceptor, never
 * per-call." [InterceptorHeaderOwnershipTest] enforces that no other source file
 * sets it.
 *
 * `runBlocking` is safe here: OkHttp interceptors already execute on OkHttp's own
 * dispatcher thread, off the caller's coroutine, so this cannot block the UI or a
 * ViewModel's `viewModelScope`.
 */
class AuthInterceptor @Inject constructor(
    private val tokenProvider: TokenProvider,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { tokenProvider.currentAccessToken() }
        val request = chain.request().newBuilder().apply {
            if (token != null) {
                addHeader("Authorization", "Bearer $token")
            }
        }.build()
        return chain.proceed(request)
    }
}

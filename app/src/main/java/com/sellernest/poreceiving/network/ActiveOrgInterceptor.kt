package com.sellernest.poreceiving.network

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Attaches `X-Active-Org: <org external id>` to every request, from the active
 * company resolved from `/api/me/` (§9 preamble, §5.2). This is the only place
 * that header is set; see [InterceptorHeaderOwnershipTest].
 */
class ActiveOrgInterceptor @Inject constructor(
    private val activeOrgProvider: ActiveOrgProvider,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val orgId = runBlocking { activeOrgProvider.currentActiveOrgId() }
        val request = chain.request().newBuilder().apply {
            if (orgId != null) {
                addHeader("X-Active-Org", orgId)
            }
        }.build()
        return chain.proceed(request)
    }
}

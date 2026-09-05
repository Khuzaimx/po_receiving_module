package com.sellernest.poreceiving.network

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * M1.3 acceptance criterion: "Every outbound request carries both
 * `Authorization` and `X-Active-Org`." [AuthInterceptor] and
 * [ActiveOrgInterceptor] are added to the client independently in
 * [com.sellernest.poreceiving.network.NetworkModule]; this proves they compose
 * correctly together on the same request rather than one clobbering the other.
 */
class BothAuthHeadersPresentTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `a single request carries both headers when both providers have a value`() = runTest {
        val tokenProvider = TokenProvider { "the-access-token" }
        val activeOrgProvider = ActiveOrgProvider { "the-org-id" }

        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenProvider))
            .addInterceptor(ActiveOrgInterceptor(activeOrgProvider))
            .build()

        server.enqueue(MockResponse().setBody("{}"))
        client.newCall(Request.Builder().url(server.url("/purchase-orders/")).build()).execute().close()

        val received = server.takeRequest()
        assertEquals("Bearer the-access-token", received.getHeader("Authorization"))
        assertEquals("the-org-id", received.getHeader("X-Active-Org"))
    }
}

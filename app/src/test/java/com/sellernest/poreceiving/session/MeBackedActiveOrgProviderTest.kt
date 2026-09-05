package com.sellernest.poreceiving.session

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.sellernest.poreceiving.network.ActiveOrgInterceptor
import com.sellernest.poreceiving.network.ApiService
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

/**
 * M1.3 acceptance criterion: "Every outbound request carries... X-Active-Org;
 * asserted by an interceptor test over all endpoints." Runs a real request
 * through the real [ActiveOrgInterceptor] backed by the real
 * [MeBackedActiveOrgProvider] over a real [MeRepository], and inspects the
 * header the server actually received -- not just that some code calls
 * `addHeader` (that's [com.sellernest.poreceiving.network.InterceptorHeaderOwnershipTest]'s job).
 */
class MeBackedActiveOrgProviderTest {

    private lateinit var meServer: MockWebServer
    private lateinit var targetServer: MockWebServer
    private lateinit var repository: MeRepository

    @Before
    fun setUp() {
        meServer = MockWebServer()
        meServer.start()
        targetServer = MockWebServer()
        targetServer.start()

        val json = Json {
            namingStrategy = JsonNamingStrategy.SnakeCase
            ignoreUnknownKeys = true
            explicitNulls = false
            coerceInputValues = true
        }
        val retrofit = Retrofit.Builder()
            .baseUrl(meServer.url("/api/mobile/receiving/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        repository = MeRepository(retrofit.create(ApiService::class.java), json)
    }

    @After
    fun tearDown() {
        meServer.shutdown()
        targetServer.shutdown()
    }

    @Test
    fun `X-Active-Org reflects the active company resolved from api me`() = runTest {
        meServer.enqueue(
            MockResponse().setBody(
                """
                {
                  "user": {"id": 1, "email": "jane@example.com", "display_name": "Jane Doe"},
                  "companies": [{
                    "external_id": "acme-distribution", "name": "Acme Distribution", "is_default": true,
                    "warehouses": [{"id": 2, "name": "DC-2", "is_default": true, "enforce_bins": true}],
                    "permissions": {"can_receive": true, "can_over_receive": false}
                  }]
                }
                """.trimIndent(),
            ),
        )
        repository.refresh()

        val provider = MeBackedActiveOrgProvider(repository)
        val client = OkHttpClient.Builder()
            .addInterceptor(ActiveOrgInterceptor(provider))
            .build()

        targetServer.enqueue(MockResponse().setBody("{}"))
        client.newCall(Request.Builder().url(targetServer.url("/purchase-orders/")).build()).execute().close()

        val receivedRequest = targetServer.takeRequest()
        assertEquals("acme-distribution", receivedRequest.getHeader("X-Active-Org"))
    }

    @Test
    fun `changing the active company changes X-Active-Org on the next request, without recreating anything`() = runTest {
        meServer.enqueue(
            MockResponse().setBody(
                """
                {
                  "user": {"id": 1, "email": "jane@example.com", "display_name": "Jane Doe"},
                  "companies": [
                    {"external_id": "acme", "name": "Acme", "is_default": true,
                     "warehouses": [], "permissions": {"can_receive": true, "can_over_receive": false}},
                    {"external_id": "globex", "name": "Globex", "is_default": false,
                     "warehouses": [], "permissions": {"can_receive": true, "can_over_receive": true}}
                  ]
                }
                """.trimIndent(),
            ),
        )
        repository.refresh()

        // One provider, one interceptor, one client -- built once, exactly as
        // the real DI graph builds them as app-scoped singletons.
        val provider = MeBackedActiveOrgProvider(repository)
        val client = OkHttpClient.Builder().addInterceptor(ActiveOrgInterceptor(provider)).build()

        targetServer.enqueue(MockResponse().setBody("{}"))
        client.newCall(Request.Builder().url(targetServer.url("/purchase-orders/")).build()).execute().close()
        assertEquals("acme", targetServer.takeRequest().getHeader("X-Active-Org"))

        repository.setActiveCompany("globex")

        targetServer.enqueue(MockResponse().setBody("{}"))
        client.newCall(Request.Builder().url(targetServer.url("/purchase-orders/")).build()).execute().close()
        assertEquals("globex", targetServer.takeRequest().getHeader("X-Active-Org"))
    }

    @Test
    fun `X-Active-Org is absent before any session has been resolved`() = runTest {
        val provider = MeBackedActiveOrgProvider(repository)
        val client = OkHttpClient.Builder()
            .addInterceptor(ActiveOrgInterceptor(provider))
            .build()

        targetServer.enqueue(MockResponse().setBody("{}"))
        client.newCall(Request.Builder().url(targetServer.url("/purchase-orders/")).build()).execute().close()

        val receivedRequest = targetServer.takeRequest()
        assertNull(receivedRequest.getHeader("X-Active-Org"))
    }
}

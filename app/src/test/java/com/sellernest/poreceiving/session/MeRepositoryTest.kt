package com.sellernest.poreceiving.session

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.sellernest.poreceiving.network.ApiResult
import com.sellernest.poreceiving.network.ApiService
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

/**
 * M1.3 acceptance criteria: the active company (and so `X-Active-Org`, via
 * [MeBackedActiveOrgProvider]) resolves from `/api/me/`'s default company, and
 * "changing company changes the header without an app restart" is a pure state
 * change over the already-fetched response, not a new network call -- this test
 * proves that by enqueueing exactly one MockWebServer response for the whole
 * refresh-then-switch sequence.
 */
class MeRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: MeRepository

    private val twoCompanyResponse = """
        {
          "user": {"id": 1, "email": "jane@example.com", "display_name": "Jane Doe"},
          "companies": [
            {
              "external_id": "acme", "name": "Acme Distribution", "is_default": false,
              "warehouses": [{"id": 2, "name": "DC-2", "is_default": true, "enforce_bins": true}],
              "permissions": {"can_receive": true, "can_over_receive": false}
            },
            {
              "external_id": "globex", "name": "Globex Supply", "is_default": true,
              "warehouses": [{"id": 5, "name": "DC-5", "is_default": true, "enforce_bins": false}],
              "permissions": {"can_receive": true, "can_over_receive": true}
            }
          ]
        }
    """.trimIndent()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val json = Json {
            namingStrategy = JsonNamingStrategy.SnakeCase
            ignoreUnknownKeys = true
            explicitNulls = false
            coerceInputValues = true
        }
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/api/mobile/receiving/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        repository = MeRepository(retrofit.create(ApiService::class.java), json)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `refresh selects the default company and its default warehouse`() = runTest {
        server.enqueue(MockResponse().setBody(twoCompanyResponse))

        val result = repository.refresh()

        assertTrue(result is ApiResult.Success)
        assertEquals("globex", repository.state.value.activeCompanyExternalId)
        assertEquals("Globex Supply", repository.state.value.activeCompanyName)
        assertEquals("DC-5", repository.state.value.activeWarehouseName)
        assertEquals("Jane Doe", repository.state.value.userDisplayName)
        assertEquals(true, repository.state.value.permissions?.canOverReceive)
    }

    @Test
    fun `setActiveCompany switches state without another network call`() = runTest {
        server.enqueue(MockResponse().setBody(twoCompanyResponse))
        repository.refresh()
        assertEquals(1, server.requestCount)

        repository.setActiveCompany("acme")

        assertEquals("acme", repository.state.value.activeCompanyExternalId)
        assertEquals("DC-2", repository.state.value.activeWarehouseName)
        assertEquals(false, repository.state.value.permissions?.canOverReceive)
        // Still exactly one request: switching company never re-fetches /api/me/.
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `setActiveCompany with an unknown id is a no-op`() = runTest {
        server.enqueue(MockResponse().setBody(twoCompanyResponse))
        repository.refresh()

        repository.setActiveCompany("does-not-exist")

        assertNull(repository.state.value.activeCompanyExternalId)
    }

    @Test
    fun `clear resets state and forgets the cached response`() = runTest {
        server.enqueue(MockResponse().setBody(twoCompanyResponse))
        repository.refresh()

        repository.clear()

        assertEquals(MeSessionState(), repository.state.value)
        // With the cached response forgotten, switching company is now a no-op.
        repository.setActiveCompany("globex")
        assertEquals(MeSessionState(), repository.state.value)
    }

    @Test
    fun `a failed refresh leaves state untouched`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        val result = repository.refresh()

        assertTrue(result !is ApiResult.Success)
        assertEquals(MeSessionState(), repository.state.value)
    }
}

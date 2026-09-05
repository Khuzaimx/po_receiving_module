package com.sellernest.poreceiving.scan.resolve

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.sellernest.poreceiving.network.ApiResult
import com.sellernest.poreceiving.network.ApiService
import com.sellernest.poreceiving.network.dto.ScanResolution
import com.sellernest.poreceiving.scan.ScanFeedbackService
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

/** M2.6: MATCHED signals accepted(); every other outcome, including a failed
 *  call, signals rejected(). */
class ResolveScanAndSignalOutcomeTest {

    private lateinit var server: MockWebServer
    private lateinit var apiService: ApiService
    private lateinit var json: Json

    private class FakeScanFeedbackService : ScanFeedbackService {
        var acceptedCount = 0
        var rejectedCount = 0
        override fun accepted() {
            acceptedCount++
        }
        override fun rejected() {
            rejectedCount++
        }
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        json = Json {
            namingStrategy = JsonNamingStrategy.SnakeCase
            ignoreUnknownKeys = true
            explicitNulls = false
            coerceInputValues = true
        }
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/api/mobile/receiving/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        apiService = retrofit.create(ApiService::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `matched signals accepted, not rejected`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"outcome": "matched", "matched_field": "upc",
                     "line": {"purchase_order_item_id": 1, "sku": "A", "name": "A",
                              "quantity_already_received": 0, "requires_serial_number": false,
                              "fully_received": false}}""",
            ),
        )
        val feedback = FakeScanFeedbackService()

        val result = resolveScanAndSignalOutcome(apiService, json, feedback, 1, "code")

        assertTrue(result is ApiResult.Success && result.body is ScanResolution.Matched)
        assertEquals(1, feedback.acceptedCount)
        assertEquals(0, feedback.rejectedCount)
    }

    @Test
    fun `not_on_purchase_order signals rejected, not accepted`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"outcome": "not_on_purchase_order", "item": {"sku": "X", "name": "X"}}""",
            ),
        )
        val feedback = FakeScanFeedbackService()

        resolveScanAndSignalOutcome(apiService, json, feedback, 1, "code")

        assertEquals(0, feedback.acceptedCount)
        assertEquals(1, feedback.rejectedCount)
    }

    @Test
    fun `unknown_code signals rejected`() = runTest {
        server.enqueue(MockResponse().setBody("""{"outcome": "unknown_code", "code": "XYZ"}"""))
        val feedback = FakeScanFeedbackService()

        resolveScanAndSignalOutcome(apiService, json, feedback, 1, "code")

        assertEquals(1, feedback.rejectedCount)
    }

    @Test
    fun `multiple_matches signals rejected`() = runTest {
        server.enqueue(MockResponse().setBody("""{"outcome": "multiple_matches", "lines": []}"""))
        val feedback = FakeScanFeedbackService()

        resolveScanAndSignalOutcome(apiService, json, feedback, 1, "code")

        assertEquals(1, feedback.rejectedCount)
    }

    @Test
    fun `a failed HTTP call also signals rejected`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        val feedback = FakeScanFeedbackService()

        val result = resolveScanAndSignalOutcome(apiService, json, feedback, 1, "code")

        assertTrue(result !is ApiResult.Success)
        assertEquals(0, feedback.acceptedCount)
        assertEquals(1, feedback.rejectedCount)
    }
}

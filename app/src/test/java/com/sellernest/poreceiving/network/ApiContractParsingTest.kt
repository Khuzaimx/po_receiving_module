package com.sellernest.poreceiving.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.sellernest.poreceiving.network.dto.ReceiveRequest
import com.sellernest.poreceiving.network.dto.ResolveScanRequest
import com.sellernest.poreceiving.network.dto.ScanResolution
import com.sellernest.poreceiving.network.dto.VoidRequest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

/**
 * Round-trips every response shape from §9 -- including the 409 idempotency
 * conflict -- through the real [ApiService] and [Json] configuration against a
 * [MockWebServer], so this exercises the actual wire format, not hand-built
 * Kotlin objects. Deliberately does not go through Hilt: this is a networking
 * layer test, not a DI test.
 */
class ApiContractParsingTest {

    private lateinit var server: MockWebServer
    private lateinit var api: ApiService

    private val json = Json {
        namingStrategy = JsonNamingStrategy.SnakeCase
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/api/mobile/receiving/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        api = retrofit.create(ApiService::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun workQueueResponseParses() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "count": 12, "next": "...", "previous": null,
                  "results": [{
                    "id": 10482, "number": "PO-10482",
                    "vendor_name": "Globex Supply Co.",
                    "warehouse": {"id": 2, "name": "DC-2", "enforce_bins": true},
                    "line_count": 14, "total_units_expected": 320,
                    "status": "PARTIALLY_RECEIVED",
                    "expected_delivery_date": "2026-09-05"
                  }]
                }
                """.trimIndent(),
            ),
        )

        val response = api.getWorkQueue()
        assertTrue(response.isSuccessful)
        val body = requireNotNull(response.body())
        assertEquals(12, body.count)
        assertEquals("PO-10482", body.results.single().number)
        assertEquals(320, body.results.single().totalUnitsExpected)
        assertTrue(body.results.single().warehouse.enforceBins)
    }

    @Test
    fun poDetailWithBlindCountTrueOmitsQuantityExpectedAndStillParses() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "id": 10482, "number": "PO-10482",
                  "vendor_name": "Globex Supply Co.",
                  "warehouse": {"id": 2, "name": "DC-2", "enforce_bins": true},
                  "blind_count": true,
                  "lines": [{
                    "purchase_order_item_id": 88213,
                    "sku": "WM-4410-BLK", "name": "Widget Mount, Black",
                    "identifiers": {"upc": "0468673502897", "ean": null,
                                    "gtin": null, "fnsku": null},
                    "quantity_already_received": 2,
                    "requires_serial_number": false
                  }]
                }
                """.trimIndent(),
            ),
        )

        val response = api.getPurchaseOrderDetail(10482)
        assertTrue(response.isSuccessful)
        val body = requireNotNull(response.body())
        assertTrue(body.blindCount)
        assertEquals("WM-4410-BLK", body.lines.single().sku)
        assertEquals("0468673502897", body.lines.single().identifiers.upc)
    }

    @Test
    fun allFourResolveScanOutcomesParseToTheirSealedVariant() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"outcome": "matched", "matched_field": "upc",
                     "line": {"purchase_order_item_id": 88213, "sku": "WM-4410-BLK",
                              "name": "Widget Mount, Black", "quantity_already_received": 2,
                              "requires_serial_number": false, "fully_received": false}}""",
            ),
        )
        val matched = api.resolveScan(10482, ResolveScanRequest("x")).body()
        assertTrue(matched is ScanResolution.Matched)
        assertEquals("upc", (matched as ScanResolution.Matched).matchedField)

        server.enqueue(
            MockResponse().setBody(
                """{"outcome": "not_on_purchase_order", "item": {"sku": "CB-9911", "name": "Cable Brace 9911"}}""",
            ),
        )
        val notOnPo = api.resolveScan(10482, ResolveScanRequest("y")).body()
        assertTrue(notOnPo is ScanResolution.NotOnPurchaseOrder)

        server.enqueue(MockResponse().setBody("""{"outcome": "multiple_matches", "lines": []}"""))
        val multiple = api.resolveScan(10482, ResolveScanRequest("z")).body()
        assertTrue(multiple is ScanResolution.MultipleMatches)

        server.enqueue(MockResponse().setBody("""{"outcome": "unknown_code", "code": "0468673502897"}"""))
        val unknown = api.resolveScan(10482, ResolveScanRequest("w")).body()
        assertTrue(unknown is ScanResolution.UnknownCode)
        assertEquals("0468673502897", (unknown as ScanResolution.UnknownCode).code)
    }

    @Test
    fun receiveResponseWithPartialFailureExposesBothUpdatedAndFailed() = runTest {
        val overReceiptError = buildString {
            append("Over-receipt requires the ")
            append('\'')
            append("Allow To Over Receive")
            append('\'')
            append(" permission.")
        }
        val bodyJson = """
            {
              "receipt_id": 4412,
              "replayed": false,
              "updated": [88213],
              "failed": [{ "purchase_order_item_id": 88220, "error": "$overReceiptError" }]
            }
        """.trimIndent()
        server.enqueue(MockResponse().setBody(bodyJson))

        val response = api.receive(10482, ReceiveRequest(idempotencyKey = "0f8c1e2a-1111", lines = emptyList()))
        val body = requireNotNull(response.body())
        assertEquals(listOf(88213L), body.updated)
        assertEquals(1, body.failed.size)
        assertEquals(88220L, body.failed.single().purchaseOrderItemId)
        assertEquals(overReceiptError, body.failed.single().error)
    }

    @Test
    fun conflict409IsSurfacedAsIdempotencyConflictNotGenericHttpError() = runTest {
        val detailText = "This key was already used with a different payload."
        server.enqueue(
            MockResponse()
                .setResponseCode(409)
                .setBody("""{"error": "idempotency_key_conflict", "detail": "$detailText"}"""),
        )

        val result = safeApiCall(json) {
            api.receive(10482, ReceiveRequest(idempotencyKey = "0f8c1e2a-1111", lines = emptyList()))
        }

        assertTrue(result is ApiResult.IdempotencyConflict)
        assertEquals(detailText, (result as ApiResult.IdempotencyConflict).detail)
    }

    @Test
    fun photoUploadResponseParses() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"id": 91, "original_filename": "IMG_0042.jpg",
                     "file_size": 1841204, "content_type": "image/jpeg"}""",
            ),
        )
        val requestBody = ByteArray(0).toRequestBody("image/jpeg".toMediaType())
        val part = MultipartBody.Part.createFormData("file", "IMG_0042.jpg", requestBody)

        val response = api.uploadPhoto(91, part)
        val body = requireNotNull(response.body())
        assertEquals("IMG_0042.jpg", body.originalFilename)
        assertEquals(1841204L, body.fileSize)
    }

    @Test
    fun receiptHistoryAndVoidResponseParse() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                { "results": [{ "id": 4412, "purchase_order_number": "PO-10477",
                                "vendor_name": "Initech", "line_count": 3,
                                "total_units": 48, "received_at": "2026-09-05T09:14:22Z",
                                "is_voided": false, "has_variance": false,
                                "void_available_until": "2026-09-05T11:14:22Z" }],
                  "count": 1, "next": null, "previous": null
                }
                """.trimIndent(),
            ),
        )
        val history = api.getReceipts().body()
        assertEquals("PO-10477", history?.results?.single()?.purchaseOrderNumber)

        server.enqueue(MockResponse().setBody("""{"id": 4412, "is_voided": true}"""))
        val voidResponse = api.voidReceipt(4412, VoidRequest("Counted against the wrong PO")).body()
        assertTrue(voidResponse?.isVoided == true)
    }
}

package com.sellernest.poreceiving.ui.screens.receipthistory

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.sellernest.poreceiving.network.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import java.time.Instant
import java.time.temporal.ChronoUnit

/** M6.2/M6.3/§9.6 acceptance criteria. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReceiptHistoryViewModelTest {

    private lateinit var server: MockWebServer
    private lateinit var apiService: ApiService
    private lateinit var json: Json

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
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
        Dispatchers.resetMain()
        server.shutdown()
    }

    private fun viewModel() = ReceiptHistoryViewModel(apiService = apiService, json = json)

    private fun receiptBody(voidAvailableUntil: String?, isVoided: Boolean = false, hasVariance: Boolean = false) = """
        {"results": [{
            "id": 4412, "purchase_order_number": "PO-10477", "vendor_name": "Initech",
            "line_count": 3, "total_units": 48, "received_at": "2026-09-05T09:14:22Z",
            "is_voided": $isVoided, "has_variance": $hasVariance,
            "void_available_until": ${voidAvailableUntil?.let { "\"$it\"" } ?: "null"}
        }]}
    """.trimIndent()

    @Test
    fun `receipts load on entry`() = runTest {
        val future = Instant.now().plus(2, ChronoUnit.HOURS).toString()
        server.enqueue(MockResponse().setBody(receiptBody(future)))

        val vm = viewModel()

        assertEquals(1, vm.state.value.receipts.size)
        assertEquals("PO-10477", vm.state.value.receipts.single().purchaseOrderNumber)
    }

    @Test
    fun `requesting VOID within the window opens the reason dialog`() = runTest {
        val future = Instant.now().plus(2, ChronoUnit.HOURS).toString()
        server.enqueue(MockResponse().setBody(receiptBody(future)))
        val vm = viewModel()

        vm.onEvent(ReceiptHistoryUiEvent.VoidRequested(vm.state.value.receipts.single()))

        assertNotNull(vm.state.value.voidTarget)
        assertNull(vm.state.value.voidBlockedMessage)
    }

    @Test
    fun `requesting VOID past the window states plainly a supervisor must void it, never a dead button`() = runTest {
        val past = Instant.now().minus(1, ChronoUnit.HOURS).toString()
        server.enqueue(MockResponse().setBody(receiptBody(past)))
        val vm = viewModel()

        vm.onEvent(ReceiptHistoryUiEvent.VoidRequested(vm.state.value.receipts.single()))

        assertNull(vm.state.value.voidTarget)
        assertEquals("Void window expired. Ask a supervisor to void this receipt.", vm.state.value.voidBlockedMessage)
    }

    @Test
    fun `VOID cannot be submitted without a reason`() = runTest {
        val future = Instant.now().plus(2, ChronoUnit.HOURS).toString()
        server.enqueue(MockResponse().setBody(receiptBody(future)))
        val vm = viewModel()
        vm.onEvent(ReceiptHistoryUiEvent.VoidRequested(vm.state.value.receipts.single()))

        vm.onEvent(ReceiptHistoryUiEvent.VoidConfirmed)

        // No network call was even attempted -- still showing the dialog.
        assertNotNull(vm.state.value.voidTarget)
    }

    @Test
    fun `a confirmed void with a reason marks the receipt voided`() = runTest {
        val future = Instant.now().plus(2, ChronoUnit.HOURS).toString()
        server.enqueue(MockResponse().setBody(receiptBody(future)))
        server.enqueue(MockResponse().setBody("""{"id": 4412, "is_voided": true}"""))
        val vm = viewModel()
        vm.onEvent(ReceiptHistoryUiEvent.VoidRequested(vm.state.value.receipts.single()))
        vm.onEvent(ReceiptHistoryUiEvent.VoidReasonChanged("Counted against the wrong PO"))

        vm.onEvent(ReceiptHistoryUiEvent.VoidConfirmed)

        assertTrue(vm.state.value.receipts.single().isVoided)
        assertNull(vm.state.value.voidTarget)
    }

    @Test
    fun `a 403 that closes between render and tap is surfaced verbatim`() = runTest {
        val future = Instant.now().plus(2, ChronoUnit.HOURS).toString()
        server.enqueue(MockResponse().setBody(receiptBody(future)))
        server.enqueue(
            MockResponse().setResponseCode(403).setBody(
                """{"error": "void_window_expired", "detail": "Void window expired. Ask a supervisor to void this receipt."}""",
            ),
        )
        val vm = viewModel()
        vm.onEvent(ReceiptHistoryUiEvent.VoidRequested(vm.state.value.receipts.single()))
        vm.onEvent(ReceiptHistoryUiEvent.VoidReasonChanged("Counted against the wrong PO"))

        vm.onEvent(ReceiptHistoryUiEvent.VoidConfirmed)

        assertEquals("Void window expired. Ask a supervisor to void this receipt.", vm.state.value.voidBlockedMessage)
        assertNull(vm.state.value.voidTarget)
        assertTrue("the receipt must not be marked voided on a rejected void", !vm.state.value.receipts.single().isVoided)
    }
}

package com.sellernest.poreceiving.ui.screens.workqueue

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.sellernest.poreceiving.network.ApiService
import com.sellernest.poreceiving.session.MeRepository
import com.sellernest.poreceiving.session.SelectedWarehouse
import com.sellernest.poreceiving.session.WarehouseSelectionStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

/** M1.7 acceptance criteria: ordering, the missing-permission empty state,
 *  and preserving the last-loaded list on a failed refresh. */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkQueueViewModelTest {

    private lateinit var server: MockWebServer
    private lateinit var apiService: ApiService
    private lateinit var meRepository: MeRepository
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
        meRepository = MeRepository(apiService, json)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        server.shutdown()
    }

    private fun meWithReceivingPermission(canReceive: Boolean) = """
        {
          "user": {"id": 1, "email": "jane@example.com", "display_name": "Jane Doe"},
          "companies": [{
            "external_id": "acme", "name": "Acme", "is_default": true,
            "warehouses": [{"id": 2, "name": "DC-2", "is_default": true, "enforce_bins": true}],
            "permissions": {"can_receive": $canReceive, "can_over_receive": false}
          }]
        }
    """.trimIndent()

    @Test
    fun `missing receiving permission shows the empty state without calling the work queue endpoint`() = runTest {
        server.enqueue(MockResponse().setBody(meWithReceivingPermission(canReceive = false)))
        meRepository.refresh()

        val viewModel = WorkQueueViewModel(apiService, json, meRepository, FakeWarehouseSelectionStorage())

        assertTrue(viewModel.state.value.missingPermission)
        assertEquals(1, server.requestCount) // only /api/me/, never /purchase-orders/
    }

    @Test
    fun `results are sorted oldest-due first regardless of server order`() = runTest {
        server.enqueue(MockResponse().setBody(meWithReceivingPermission(canReceive = true)))
        meRepository.refresh()
        server.enqueue(
            MockResponse().setBody(
                """
                { "count": 2, "next": null, "previous": null, "results": [
                  {"id": 1, "number": "PO-1", "vendor_name": "V1",
                   "warehouse": {"id": 2, "name": "DC-2", "enforce_bins": true},
                   "line_count": 1, "status": "OPEN", "expected_delivery_date": "2026-09-10"},
                  {"id": 2, "number": "PO-2", "vendor_name": "V2",
                   "warehouse": {"id": 2, "name": "DC-2", "enforce_bins": true},
                   "line_count": 1, "status": "OPEN", "expected_delivery_date": "2026-09-01"}
                ]}
                """.trimIndent(),
            ),
        )

        val viewModel = WorkQueueViewModel(apiService, json, meRepository, FakeWarehouseSelectionStorage())

        assertEquals(listOf("PO-2", "PO-1"), viewModel.state.value.results.map { it.number })
    }

    @Test
    fun `a failed refresh keeps the last-loaded results and surfaces an error`() = runTest {
        server.enqueue(MockResponse().setBody(meWithReceivingPermission(canReceive = true)))
        meRepository.refresh()
        server.enqueue(
            MockResponse().setBody(
                """{ "count": 1, "next": null, "previous": null, "results": [
                  {"id": 1, "number": "PO-1", "vendor_name": "V1",
                   "warehouse": {"id": 2, "name": "DC-2", "enforce_bins": true},
                   "line_count": 1, "status": "OPEN"}
                ]}""",
            ),
        )
        val viewModel = WorkQueueViewModel(apiService, json, meRepository, FakeWarehouseSelectionStorage())
        assertEquals(1, viewModel.state.value.results.size)

        server.enqueue(MockResponse().setResponseCode(500))
        viewModel.onEvent(WorkQueueUiEvent.RefreshRequested)

        assertEquals(1, viewModel.state.value.results.size)
        assertTrue(viewModel.state.value.errorMessage != null)
    }

    @Test
    fun `changing the search query re-fetches with the search parameter`() = runTest {
        server.enqueue(MockResponse().setBody(meWithReceivingPermission(canReceive = true)))
        meRepository.refresh()
        server.enqueue(MockResponse().setBody("""{"count": 0, "next": null, "previous": null, "results": []}"""))
        val viewModel = WorkQueueViewModel(apiService, json, meRepository, FakeWarehouseSelectionStorage())

        server.enqueue(MockResponse().setBody("""{"count": 0, "next": null, "previous": null, "results": []}"""))
        viewModel.onEvent(WorkQueueUiEvent.SearchQueryChanged("Globex"))

        // Requests are recorded FIFO: [0] /api/me/, [1] initial work queue load,
        // [2] the search-triggered refresh -- the one under test.
        assertEquals(3, server.requestCount)
        server.takeRequest() // /api/me/
        server.takeRequest() // initial load
        val searchRequest: RecordedRequest = server.takeRequest()
        val url: HttpUrl = searchRequest.requestUrl!!
        assertEquals("Globex", url.queryParameter("search"))
    }

    @Test
    fun `scanning a PO barcode that matches in this warehouse navigates directly to it`() = runTest {
        server.enqueue(MockResponse().setBody(meWithReceivingPermission(canReceive = true)))
        meRepository.refresh()
        server.enqueue(MockResponse().setBody("""{"count": 0, "next": null, "previous": null, "results": []}"""))
        val viewModel = WorkQueueViewModel(apiService, json, meRepository, FakeWarehouseSelectionStorage())

        server.enqueue(
            MockResponse().setBody(
                """{ "count": 1, "next": null, "previous": null, "results": [
                  {"id": 10482, "number": "PO-10482", "vendor_name": "Globex",
                   "warehouse": {"id": 2, "name": "DC-2", "enforce_bins": true},
                   "line_count": 1, "status": "OPEN"}
                ]}""",
            ),
        )
        viewModel.onEvent(WorkQueueUiEvent.PoBarcodeScanned("PO-10482"))

        assertEquals(10482L, viewModel.state.value.navigateToPoId)
        assertEquals(null, viewModel.state.value.scanMessage)
    }

    @Test
    fun `scanning a PO barcode belonging to another warehouse names that warehouse`() = runTest {
        server.enqueue(MockResponse().setBody(meWithReceivingPermission(canReceive = true)))
        meRepository.refresh()
        server.enqueue(MockResponse().setBody("""{"count": 0, "next": null, "previous": null, "results": []}"""))
        val viewModel = WorkQueueViewModel(apiService, json, meRepository, FakeWarehouseSelectionStorage())

        // First (scoped) lookup: no match in this warehouse.
        server.enqueue(MockResponse().setBody("""{"count": 0, "next": null, "previous": null, "results": []}"""))
        // Second (unscoped) lookup: it exists, but in a different warehouse.
        server.enqueue(
            MockResponse().setBody(
                """{ "count": 1, "next": null, "previous": null, "results": [
                  {"id": 999, "number": "PO-9999", "vendor_name": "Initech",
                   "warehouse": {"id": 5, "name": "DC-5", "enforce_bins": false},
                   "line_count": 1, "status": "OPEN"}
                ]}""",
            ),
        )
        viewModel.onEvent(WorkQueueUiEvent.PoBarcodeScanned("PO-9999"))

        assertEquals(null, viewModel.state.value.navigateToPoId)
        assertEquals("PO-9999 belongs to DC-5, not your active warehouse.", viewModel.state.value.scanMessage)
    }

    @Test
    fun `scanning a value matching nothing at all shows a named not-found message`() = runTest {
        server.enqueue(MockResponse().setBody(meWithReceivingPermission(canReceive = true)))
        meRepository.refresh()
        server.enqueue(MockResponse().setBody("""{"count": 0, "next": null, "previous": null, "results": []}"""))
        val viewModel = WorkQueueViewModel(apiService, json, meRepository, FakeWarehouseSelectionStorage())

        server.enqueue(MockResponse().setBody("""{"count": 0, "next": null, "previous": null, "results": []}"""))
        server.enqueue(MockResponse().setBody("""{"count": 0, "next": null, "previous": null, "results": []}"""))
        viewModel.onEvent(WorkQueueUiEvent.PoBarcodeScanned("GARBAGE123"))

        assertEquals(null, viewModel.state.value.navigateToPoId)
        assertEquals("No receivable PO found matching \"GARBAGE123\".", viewModel.state.value.scanMessage)
    }

    private class FakeWarehouseSelectionStorage : WarehouseSelectionStorage {
        override suspend fun save(selection: SelectedWarehouse) {}
        override suspend fun current(): SelectedWarehouse? = SelectedWarehouse("acme", 2)
        override suspend fun clear() {}
    }
}

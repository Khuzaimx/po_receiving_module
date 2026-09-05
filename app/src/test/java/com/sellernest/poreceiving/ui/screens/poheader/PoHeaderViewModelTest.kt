package com.sellernest.poreceiving.ui.screens.poheader

import androidx.lifecycle.SavedStateHandle
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.sellernest.poreceiving.data.local.entities.DraftEntity
import com.sellernest.poreceiving.data.local.entities.DraftLineEntity
import com.sellernest.poreceiving.data.local.entities.DraftState
import com.sellernest.poreceiving.network.ApiService
import com.sellernest.poreceiving.session.SelectedWarehouse
import com.sellernest.poreceiving.session.WarehouseSelectionStorage
import com.sellernest.poreceiving.ui.screens.warehouseselection.FakeDraftDao
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
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

/** M1.8 acceptance criteria: blind-count fields never leak a per-line
 *  quantity, the optional PO-level total is shown only when present, the
 *  specific offline message, and the resume-draft scanned count. */
@OptIn(ExperimentalCoroutinesApi::class)
class PoHeaderViewModelTest {

    private lateinit var server: MockWebServer
    private lateinit var apiService: ApiService
    private lateinit var json: Json
    private val warehouseStorage = object : WarehouseSelectionStorage {
        override suspend fun save(selection: SelectedWarehouse) {}
        override suspend fun current(): SelectedWarehouse? = SelectedWarehouse("acme", 2)
        override suspend fun clear() {}
    }

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

    private fun viewModel(draftDao: FakeDraftDao = FakeDraftDao(), draftLineDao: FakeDraftLineDao = FakeDraftLineDao()) =
        PoHeaderViewModel(
            savedStateHandle = SavedStateHandle(mapOf("poId" to 10482L)),
            apiService = apiService,
            json = json,
            draftDao = draftDao,
            draftLineDao = draftLineDao,
            warehouseSelectionStorage = warehouseStorage,
        )

    @Test
    fun `blind count true never exposes a per-line quantity and shows the total only when present`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "id": 10482, "number": "PO-10482", "vendor_name": "Globex Supply Co.",
                  "warehouse": {"id": 2, "name": "DC-2", "enforce_bins": true},
                  "blind_count": true, "total_units_expected": 320, "status": "PARTIALLY_RECEIVED",
                  "lines": [{
                    "purchase_order_item_id": 88213, "sku": "WM-4410-BLK", "name": "Widget Mount, Black",
                    "identifiers": {"upc": "0468673502897"},
                    "quantity_already_received": 2, "requires_serial_number": false
                  }]
                }
                """.trimIndent(),
            ),
        )

        val vm = viewModel()

        val detail = vm.state.value.detail!!
        assertTrue(detail.blindCount)
        assertEquals(320, detail.totalUnitsExpected)
        assertEquals("PARTIALLY_RECEIVED", detail.status)
        // The type itself has no per-line expected-quantity property to check --
        // see BlindCountDtoTest for the structural guarantee. This just confirms
        // the rest of the payload still parses correctly alongside it.
        assertEquals(1, detail.lines.size)
    }

    @Test
    fun `total units expected is absent from state when the backend omits it`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "id": 10482, "number": "PO-10482", "vendor_name": "Globex Supply Co.",
                  "warehouse": {"id": 2, "name": "DC-2", "enforce_bins": true},
                  "blind_count": false, "lines": []
                }
                """.trimIndent(),
            ),
        )

        val vm = viewModel()

        assertNull(vm.state.value.detail?.totalUnitsExpected)
        assertNull(vm.state.value.detail?.status)
    }

    @Test
    fun `a network failure shows the specific connect-to-load message`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val vm = viewModel()

        assertEquals("Connect to load PO #10482", vm.state.value.offlineMessage)
        assertNull(vm.state.value.detail)
    }

    @Test
    fun `an existing draft surfaces its total counted quantity for resume`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "id": 10482, "number": "PO-10482", "vendor_name": "Globex Supply Co.",
                  "warehouse": {"id": 2, "name": "DC-2", "enforce_bins": true},
                  "blind_count": true, "lines": []
                }
                """.trimIndent(),
            ),
        )
        val draftDao = FakeDraftDao().apply {
            seed(
                DraftEntity(
                    id = 1, purchaseOrderId = 10482, purchaseOrderNumber = "PO-10482", warehouseId = 2,
                    state = DraftState.COUNTING, idempotencyKey = "key-1",
                    createdAtEpochMillis = 0, updatedAtEpochMillis = 0,
                ),
            )
        }
        val draftLineDao = FakeDraftLineDao().apply {
            seed(DraftLineEntity(draftId = 1, purchaseOrderItemId = 1, sku = "A", name = "A", countedQuantity = 10))
            seed(DraftLineEntity(draftId = 1, purchaseOrderItemId = 2, sku = "B", name = "B", countedQuantity = 8))
        }

        val vm = viewModel(draftDao, draftLineDao)

        assertEquals(18, vm.state.value.existingDraftScannedCount)
    }

    @Test
    fun `no existing draft leaves the resume count null`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "id": 10482, "number": "PO-10482", "vendor_name": "Globex Supply Co.",
                  "warehouse": {"id": 2, "name": "DC-2", "enforce_bins": true},
                  "blind_count": true, "lines": []
                }
                """.trimIndent(),
            ),
        )

        val vm = viewModel()

        assertNull(vm.state.value.existingDraftScannedCount)
        assertFalse(vm.state.value.loading)
    }
}

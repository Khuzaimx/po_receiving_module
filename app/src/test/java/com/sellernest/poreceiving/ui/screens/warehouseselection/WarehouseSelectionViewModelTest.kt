package com.sellernest.poreceiving.ui.screens.warehouseselection

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.sellernest.poreceiving.data.local.entities.DraftEntity
import com.sellernest.poreceiving.data.local.entities.DraftState
import com.sellernest.poreceiving.network.ApiService
import com.sellernest.poreceiving.session.MeRepository
import com.sellernest.poreceiving.session.SelectedWarehouse
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

/**
 * M1.5 acceptance criteria: single-company/single-warehouse auto-skip,
 * warehouse-scoped visibility, persistence across restart (covered by
 * `SharedPrefsWarehouseSelectionStorageInstrumentedTest`), and blocking a
 * warehouse change while a count is in progress.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WarehouseSelectionViewModelTest {

    private lateinit var server: MockWebServer
    private lateinit var meRepository: MeRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
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
        meRepository = MeRepository(retrofit.create(ApiService::class.java), json)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        server.shutdown()
    }

    private fun enqueueSingleCompanySingleWarehouse() {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "user": {"id": 1, "email": "jane@example.com", "display_name": "Jane Doe"},
                  "companies": [{
                    "external_id": "acme", "name": "Acme", "is_default": true,
                    "warehouses": [{"id": 2, "name": "DC-2", "is_default": true, "enforce_bins": true}],
                    "permissions": {"can_receive": true, "can_over_receive": false}
                  }]
                }
                """.trimIndent(),
            ),
        )
    }

    private fun enqueueTwoWarehousesOneCompany() {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "user": {"id": 1, "email": "jane@example.com", "display_name": "Jane Doe"},
                  "companies": [{
                    "external_id": "acme", "name": "Acme", "is_default": true,
                    "warehouses": [
                      {"id": 2, "name": "DC-2", "is_default": true, "enforce_bins": true},
                      {"id": 5, "name": "DC-5", "is_default": false, "enforce_bins": false}
                    ],
                    "permissions": {"can_receive": true, "can_over_receive": false}
                  }]
                }
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `single company and single warehouse auto-skips and persists the selection`() = runTest {
        enqueueSingleCompanySingleWarehouse()
        meRepository.refresh()
        val storage = FakeWarehouseSelectionStorage()

        val viewModel = WarehouseSelectionViewModel(meRepository, storage, FakeDraftDao())

        assertTrue(viewModel.state.value.proceedToWorkQueue)
        assertEquals(SelectedWarehouse("acme", 2), storage.current())
    }

    @Test
    fun `multiple warehouses in one company shows the warehouse selector and does not auto-skip`() = runTest {
        enqueueTwoWarehousesOneCompany()
        meRepository.refresh()

        val viewModel = WarehouseSelectionViewModel(meRepository, FakeWarehouseSelectionStorage(), FakeDraftDao())

        assertFalse(viewModel.state.value.proceedToWorkQueue)
        assertTrue(viewModel.state.value.showWarehouseSelector)
        assertFalse(viewModel.state.value.showCompanySelector)
        assertEquals(2L, viewModel.state.value.selectedWarehouseId)
    }

    @Test
    fun `continue with no prior selection proceeds without checking for an active draft`() = runTest {
        enqueueTwoWarehousesOneCompany()
        meRepository.refresh()
        val storage = FakeWarehouseSelectionStorage(initial = null)
        val draftDao = FakeDraftDao().apply {
            seed(activeDraft()) // present, but irrelevant: there is no prior selection to "change"
        }

        val viewModel = WarehouseSelectionViewModel(meRepository, storage, draftDao)
        viewModel.onEvent(WarehouseSelectionUiEvent.WarehouseSelected(5))
        viewModel.onEvent(WarehouseSelectionUiEvent.ContinueTapped)

        assertTrue(viewModel.state.value.proceedToWorkQueue)
        assertNull(viewModel.state.value.blockedMessage)
    }

    @Test
    fun `changing warehouse while a count is in progress is blocked and names the PO`() = runTest {
        enqueueTwoWarehousesOneCompany()
        meRepository.refresh()
        val storage = FakeWarehouseSelectionStorage(initial = SelectedWarehouse("acme", 2))
        val draftDao = FakeDraftDao().apply { seed(activeDraft()) }

        val viewModel = WarehouseSelectionViewModel(meRepository, storage, draftDao)
        viewModel.onEvent(WarehouseSelectionUiEvent.WarehouseSelected(5))
        viewModel.onEvent(WarehouseSelectionUiEvent.ContinueTapped)

        assertFalse(viewModel.state.value.proceedToWorkQueue)
        assertTrue(viewModel.state.value.blockedMessage?.contains("PO-10482") == true)
        assertEquals(SelectedWarehouse("acme", 2), storage.current())
    }

    @Test
    fun `changing warehouse with no active draft proceeds normally`() = runTest {
        enqueueTwoWarehousesOneCompany()
        meRepository.refresh()
        val storage = FakeWarehouseSelectionStorage(initial = SelectedWarehouse("acme", 2))

        val viewModel = WarehouseSelectionViewModel(meRepository, storage, FakeDraftDao())
        viewModel.onEvent(WarehouseSelectionUiEvent.WarehouseSelected(5))
        viewModel.onEvent(WarehouseSelectionUiEvent.ContinueTapped)

        assertTrue(viewModel.state.value.proceedToWorkQueue)
        assertEquals(SelectedWarehouse("acme", 5), storage.current())
    }

    private fun activeDraft() = DraftEntity(
        id = 1,
        purchaseOrderId = 10482,
        purchaseOrderNumber = "PO-10482",
        warehouseId = 2,
        state = DraftState.COUNTING,
        idempotencyKey = "0f8c1e2a-1111",
        createdAtEpochMillis = 1_000L,
        updatedAtEpochMillis = 1_000L,
    )
}

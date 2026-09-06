package com.sellernest.poreceiving.ui.screens.binconfirmation

import androidx.lifecycle.SavedStateHandle
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.sellernest.poreceiving.data.local.DraftRepository
import com.sellernest.poreceiving.data.local.FakeDraftPhotoDao
import com.sellernest.poreceiving.data.local.FakeDraftSerialDao
import com.sellernest.poreceiving.data.local.FakeQueuedSubmissionDao
import com.sellernest.poreceiving.network.ApiService
import com.sellernest.poreceiving.ui.screens.poheader.FakeDraftLineDao
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

/** M4.6/§10 acceptance criteria. */
@OptIn(ExperimentalCoroutinesApi::class)
class BinConfirmationViewModelTest {

    private lateinit var server: MockWebServer
    private lateinit var apiService: ApiService
    private lateinit var json: Json
    private lateinit var draftRepository: DraftRepository
    private var draftId: Long = 0

    @Before
    fun setUp() = runTest {
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
        draftRepository = DraftRepository(
            FakeDraftDao(), FakeDraftLineDao(), FakeDraftPhotoDao(), FakeDraftSerialDao(), FakeQueuedSubmissionDao(),
        )

        val draft = draftRepository.openPurchaseOrder(10482, "PO-10482", warehouseId = 2)
        draftId = draft.id
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        server.shutdown()
    }

    private fun viewModel() = BinConfirmationViewModel(
        savedStateHandle = SavedStateHandle(mapOf("draftId" to draftId)),
        apiService = apiService,
        json = json,
        draftRepository = draftRepository,
    )

    private fun binsBody(vararg bins: String) = """{"count": ${bins.size}, "next": null, "previous": null, "results": [${bins.joinToString(",")}]}"""

    private fun bin(id: Long, label: String, warehouseId: Long = 2, warehouseName: String = "DC-2", isDefault: Boolean = false) = """
        {"id": $id, "label": "$label", "warehouse_id": $warehouseId, "warehouse_name": "$warehouseName", "is_default_receiving_bin": $isDefault}
    """.trimIndent()

    @Test
    fun `the default receiving bin is surfaced as suggested`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                binsBody(bin(1, "RECEIVING-01", isDefault = true), bin(2, "AISLE-04")),
            ),
        )
        val vm = viewModel()

        assertEquals(1, vm.state.value.suggestedBins.size)
        assertEquals("RECEIVING-01", vm.state.value.suggestedBins.single().label)
    }

    @Test
    fun `CONTINUE is disabled until a bin is scanned or selected`() = runTest {
        server.enqueue(MockResponse().setBody(binsBody()))
        val vm = viewModel()

        assertFalse(vm.state.value.canContinue)
    }

    @Test
    fun `selecting a suggested bin enables CONTINUE`() = runTest {
        server.enqueue(MockResponse().setBody(binsBody(bin(1, "RECEIVING-01", isDefault = true))))
        val vm = viewModel()

        vm.onEvent(BinConfirmationUiEvent.BinSelected(vm.state.value.suggestedBins.single()))

        assertTrue(vm.state.value.canContinue)
    }

    @Test
    fun `scanning a bin belonging to another warehouse is rejected and names the real warehouse`() = runTest {
        server.enqueue(MockResponse().setBody(binsBody())) // initial suggested-bins load
        server.enqueue(MockResponse().setBody(binsBody())) // scoped search: no match
        server.enqueue(MockResponse().setBody(binsBody(bin(9, "DC1-BIN-01", warehouseId = 1, warehouseName = "DC-1")))) // unscoped: found elsewhere
        val vm = viewModel()

        vm.onEvent(BinConfirmationUiEvent.BinScanned("DC1-BIN-01"))

        assertFalse(vm.state.value.canContinue)
        assertEquals("\"DC1-BIN-01\" belongs to DC-1, not your active warehouse.", vm.state.value.scanMessage)
    }

    @Test
    fun `scanning a bin in the active warehouse selects it`() = runTest {
        server.enqueue(MockResponse().setBody(binsBody())) // initial suggested-bins load
        server.enqueue(MockResponse().setBody(binsBody(bin(1, "RECEIVING-01"))))
        val vm = viewModel()

        vm.onEvent(BinConfirmationUiEvent.BinScanned("RECEIVING-01"))

        assertTrue(vm.state.value.canContinue)
        assertEquals("RECEIVING-01", vm.state.value.selectedBin?.label)
    }

    @Test
    fun `CONTINUE persists the selected bin to the draft and signals navigation`() = runTest {
        server.enqueue(MockResponse().setBody(binsBody(bin(1, "RECEIVING-01", isDefault = true))))
        val vm = viewModel()
        vm.onEvent(BinConfirmationUiEvent.BinSelected(vm.state.value.suggestedBins.single()))

        vm.onEvent(BinConfirmationUiEvent.ContinueTapped)

        assertEquals(1L, draftRepository.getDraft(draftId)?.binId)
        assertEquals(draftId, vm.state.value.navigateToReviewDraftId)
    }
}

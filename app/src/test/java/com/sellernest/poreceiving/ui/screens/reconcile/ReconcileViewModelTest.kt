package com.sellernest.poreceiving.ui.screens.reconcile

import androidx.lifecycle.SavedStateHandle
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.sellernest.poreceiving.data.local.DraftRepository
import com.sellernest.poreceiving.data.local.FakeDraftPhotoDao
import com.sellernest.poreceiving.data.local.FakeDraftSerialDao
import com.sellernest.poreceiving.data.local.FakeQueuedSubmissionDao
import com.sellernest.poreceiving.data.local.entities.DraftLineEntity
import com.sellernest.poreceiving.data.local.entities.DraftState
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

/**
 * M4.1/M4.2 acceptance criteria: variances sort above matches, CONTINUE is
 * gated on every variance having a reason, an unpermitted over-receipt blocks
 * regardless of reasons, and reasons persist to the draft immediately.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReconcileViewModelTest {

    private lateinit var server: MockWebServer
    private lateinit var apiService: ApiService
    private lateinit var json: Json
    private lateinit var draftDao: FakeDraftDao
    private lateinit var draftLineDao: FakeDraftLineDao
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
        draftDao = FakeDraftDao()
        draftLineDao = FakeDraftLineDao()
        draftRepository = DraftRepository(draftDao, draftLineDao, FakeDraftPhotoDao(), FakeDraftSerialDao(), FakeQueuedSubmissionDao())

        val draft = draftRepository.openPurchaseOrder(10482, "PO-10482", warehouseId = 2)
        draftId = draft.id
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        server.shutdown()
    }

    private fun viewModel() = ReconcileViewModel(
        savedStateHandle = SavedStateHandle(mapOf("draftId" to draftId)),
        apiService = apiService,
        json = json,
        draftRepository = draftRepository,
    )

    private fun validPoDetailBody(enforceBins: Boolean = false) = """
        {
          "id": 10482, "number": "PO-10482", "vendor_name": "Globex Supply Co.",
          "warehouse": {"id": 2, "name": "DC-2", "enforce_bins": $enforceBins},
          "blind_count": true, "lines": []
        }
    """.trimIndent()

    private val twoVarianceOneMatchBody = """
        {
          "lines": [
            {"purchase_order_item_id": 1, "sku": "MATCH-1", "name": "Matches",
             "quantity_expected": 120, "quantity_counted": 120, "delta": 0,
             "is_over_receipt": false, "over_receipt_permitted": true},
            {"purchase_order_item_id": 2, "sku": "UNDER-1", "name": "Under",
             "quantity_expected": 8, "quantity_counted": 6, "delta": -2,
             "is_over_receipt": false, "over_receipt_permitted": true},
            {"purchase_order_item_id": 3, "sku": "OVER-1", "name": "Over, blocked",
             "quantity_expected": 4, "quantity_counted": 5, "delta": 1,
             "is_over_receipt": true, "over_receipt_permitted": false}
          ],
          "variance_reasons": [{"id": 7, "label": "Damaged In Transit"}, {"id": 9, "label": "Miscount"}]
        }
    """.trimIndent()

    @Test
    fun `variance lines sort above matching lines`() = runTest {
        server.enqueue(MockResponse().setBody(validPoDetailBody()))
        server.enqueue(MockResponse().setBody(twoVarianceOneMatchBody))
        val vm = viewModel()

        assertEquals(listOf("UNDER-1", "OVER-1", "MATCH-1"), vm.state.value.lines.map { it.sku })
        assertEquals(2, vm.state.value.varianceLines.size)
        assertEquals(1, vm.state.value.matchingLines.size)
    }

    @Test
    fun `CONTINUE is disabled while any variance line has no reason`() = runTest {
        server.enqueue(MockResponse().setBody(validPoDetailBody()))
        server.enqueue(MockResponse().setBody(twoVarianceOneMatchBody))
        val vm = viewModel()

        assertFalse(vm.state.value.canContinue)
    }

    @Test
    fun `an unpermitted over-receipt blocks CONTINUE even with all reasons chosen`() = runTest {
        server.enqueue(MockResponse().setBody(validPoDetailBody()))
        server.enqueue(MockResponse().setBody(twoVarianceOneMatchBody))
        val vm = viewModel()

        vm.onEvent(ReconcileUiEvent.ReasonSelected(2, 7))
        vm.onEvent(ReconcileUiEvent.ReasonSelected(3, 9))

        assertFalse(vm.state.value.canContinue)
        assertEquals(1, vm.state.value.blockedOverReceiptLines.size)
        assertEquals("OVER-1", vm.state.value.blockedOverReceiptLines.single().sku)
    }

    @Test
    fun `choosing reasons for every permitted variance enables CONTINUE`() = runTest {
        server.enqueue(MockResponse().setBody(validPoDetailBody()))
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "lines": [
                    {"purchase_order_item_id": 2, "sku": "UNDER-1", "name": "Under",
                     "quantity_expected": 8, "quantity_counted": 6, "delta": -2,
                     "is_over_receipt": false, "over_receipt_permitted": true}
                  ],
                  "variance_reasons": [{"id": 7, "label": "Damaged In Transit"}]
                }
                """.trimIndent(),
            ),
        )
        val vm = viewModel()
        assertFalse(vm.state.value.canContinue)

        vm.onEvent(ReconcileUiEvent.ReasonSelected(2, 7))

        assertTrue(vm.state.value.canContinue)
    }

    @Test
    fun `a chosen reason persists to the draft line immediately`() = runTest {
        server.enqueue(MockResponse().setBody(validPoDetailBody()))
        server.enqueue(MockResponse().setBody(twoVarianceOneMatchBody))
        viewModel()
        // Seed a draft line matching purchaseOrderItemId 2 so persistence has
        // somewhere to land (the reconcile response describes server-side
        // truth; the locally-persisted line is what the receipt is built from).
        draftLineDao.insert(
            DraftLineEntity(draftId = draftId, purchaseOrderItemId = 2, sku = "UNDER-1", name = "Under", countedQuantity = 6),
        )
        server.enqueue(MockResponse().setBody(validPoDetailBody()))
        server.enqueue(MockResponse().setBody(twoVarianceOneMatchBody))
        val vm = viewModel()

        vm.onEvent(ReconcileUiEvent.ReasonSelected(2, 7))
        vm.onEvent(ReconcileUiEvent.NoteChanged(2, "Corner crushed"))

        val persisted = draftLineDao.getByPurchaseOrderItem(draftId, 2)
        assertEquals(7L, persisted?.varianceReasonId)
        assertEquals("Corner crushed", persisted?.varianceNote)
    }

    @Test
    fun `CONTINUE transitions the draft to REVIEW and signals navigation to Review when bins aren't enforced`() = runTest {
        server.enqueue(MockResponse().setBody(validPoDetailBody(enforceBins = false)))
        server.enqueue(MockResponse().setBody("""{"lines": [], "variance_reasons": []}"""))
        // Force PO_OPEN -> COUNTING (as a real scan would) before committing --
        // commitCount only accepts a transition out of COUNTING.
        draftRepository.setLineQuantity(draftId, purchaseOrderItemId = 999, quantity = 0)
        draftRepository.commitCount(draftId)
        val vm = viewModel()

        vm.onEvent(ReconcileUiEvent.ContinueTapped)

        assertEquals(DraftState.REVIEW, draftDao.getById(draftId)?.state)
        assertEquals(draftId, vm.state.value.navigateToReviewDraftId)
        assertEquals(null, vm.state.value.navigateToBinConfirmationDraftId)
    }

    @Test
    fun `CONTINUE signals navigation to Bin Confirmation when the warehouse enforces bins`() = runTest {
        server.enqueue(MockResponse().setBody(validPoDetailBody(enforceBins = true)))
        server.enqueue(MockResponse().setBody("""{"lines": [], "variance_reasons": []}"""))
        draftRepository.setLineQuantity(draftId, purchaseOrderItemId = 999, quantity = 0)
        draftRepository.commitCount(draftId)
        val vm = viewModel()

        vm.onEvent(ReconcileUiEvent.ContinueTapped)

        assertEquals(DraftState.REVIEW, draftDao.getById(draftId)?.state)
        assertEquals(draftId, vm.state.value.navigateToBinConfirmationDraftId)
        assertEquals(null, vm.state.value.navigateToReviewDraftId)
    }

    @Test
    fun `a line requiring a serial number is flagged for the SERIALS action`() = runTest {
        server.enqueue(MockResponse().setBody(validPoDetailBody()))
        server.enqueue(MockResponse().setBody(twoVarianceOneMatchBody))
        draftLineDao.insert(
            DraftLineEntity(
                draftId = draftId, purchaseOrderItemId = 2, sku = "UNDER-1", name = "Under",
                countedQuantity = 6, requiresSerialNumber = true,
            ),
        )
        draftLineDao.insert(
            DraftLineEntity(draftId = draftId, purchaseOrderItemId = 3, sku = "OVER-1", name = "Over, blocked", countedQuantity = 5),
        )

        val vm = viewModel()

        assertEquals(true, vm.state.value.requiresSerialByItem[2])
        assertEquals(false, vm.state.value.requiresSerialByItem[3])
    }
}

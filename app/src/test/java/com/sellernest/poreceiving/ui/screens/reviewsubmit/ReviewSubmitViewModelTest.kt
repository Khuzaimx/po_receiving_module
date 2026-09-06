package com.sellernest.poreceiving.ui.screens.reviewsubmit

import androidx.lifecycle.SavedStateHandle
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.sellernest.poreceiving.data.local.DraftRepository
import com.sellernest.poreceiving.data.local.FakeDraftPhotoDao
import com.sellernest.poreceiving.data.local.FakeDraftSerialDao
import com.sellernest.poreceiving.data.local.FakeQueuedSubmissionDao
import com.sellernest.poreceiving.data.local.entities.DraftLineEntity
import com.sellernest.poreceiving.data.local.entities.DraftState
import com.sellernest.poreceiving.network.ApiService
import com.sellernest.poreceiving.network.dto.ScanMatchedLine
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

/** M5.1 acceptance criteria. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReviewSubmitViewModelTest {

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
        draftRepository = DraftRepository(
            draftDao, draftLineDao, FakeDraftPhotoDao(), FakeDraftSerialDao(draftLineDao), FakeQueuedSubmissionDao(),
        )

        val draft = draftRepository.openPurchaseOrder(10482, "PO-10482", warehouseId = 2)
        draftId = draft.id
        // Two lines, one with damage, matching what commitCount + completeReconciliation would leave behind.
        draftRepository.recordScan(
            draftId,
            ScanMatchedLine(
                purchaseOrderItemId = 1, sku = "WM-4410-BLK", name = "Widget Mount, Black",
                quantityAlreadyReceived = 0, requiresSerialNumber = false, fullyReceived = false,
            ),
        )
        repeat(5) {
            draftRepository.recordScan(
                draftId,
                ScanMatchedLine(
                    purchaseOrderItemId = 1, sku = "WM-4410-BLK", name = "Widget Mount, Black",
                    quantityAlreadyReceived = 0, requiresSerialNumber = false, fullyReceived = false,
                ),
            )
        }
        draftRepository.setDamagedQuantity(draftId, 1, 2)
        draftRepository.commitCount(draftId)
        draftRepository.setVarianceReason(draftId, 1, reasonId = 7, note = null)
        draftRepository.completeReconciliation(draftId)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        server.shutdown()
    }

    private fun viewModel() = ReviewSubmitViewModel(
        savedStateHandle = SavedStateHandle(mapOf("draftId" to draftId)),
        apiService = apiService,
        json = json,
        draftRepository = draftRepository,
    )

    private val poDetailBody = """
        {"id": 10482, "number": "PO-10482", "vendor_name": "Globex Supply Co.",
         "warehouse": {"id": 2, "name": "DC-2", "enforce_bins": false}, "blind_count": true, "lines": []}
    """.trimIndent()

    private val reconcileBodyWithVariance = """
        {"lines": [
            {"purchase_order_item_id": 1, "sku": "WM-4410-BLK", "name": "Widget Mount, Black",
             "quantity_expected": 8, "quantity_counted": 6, "delta": -2,
             "is_over_receipt": false, "over_receipt_permitted": true}
         ], "variance_reasons": [{"id": 7, "label": "Damaged In Transit"}]}
    """.trimIndent()

    @Test
    fun `totals reconcile exactly with the draft's local contents`() = runTest {
        server.enqueue(MockResponse().setBody(poDetailBody))
        server.enqueue(MockResponse().setBody(reconcileBodyWithVariance))
        val vm = viewModel()

        assertEquals(1, vm.state.value.lineCount)
        assertEquals(4, vm.state.value.goodQuantity) // 6 counted - 2 damaged
        assertEquals(2, vm.state.value.damagedQuantity)
        assertEquals(2, vm.state.value.missingQuantity) // expected 8 - counted 6
        assertEquals("DC-2", vm.state.value.warehouseName)
    }

    @Test
    fun `SUBMIT succeeds when every variance line already has a reason`() = runTest {
        server.enqueue(MockResponse().setBody(poDetailBody))
        server.enqueue(MockResponse().setBody(reconcileBodyWithVariance))
        val vm = viewModel()

        vm.onEvent(ReviewSubmitUiEvent.SubmitTapped)

        assertTrue(vm.state.value.submitted)
        assertNull(vm.state.value.blockingMessage)
        assertEquals(DraftState.QUEUED, draftDao.getById(draftId)?.state)
    }

    @Test
    fun `SUBMIT is blocked with a specific message when a variance line has no reason`() = runTest {
        // Clear the reason set in setUp so this line is missing one again.
        draftRepository.setVarianceReason(draftId, 1, reasonId = null, note = null)
        server.enqueue(MockResponse().setBody(poDetailBody))
        server.enqueue(MockResponse().setBody(reconcileBodyWithVariance))
        val vm = viewModel()

        vm.onEvent(ReviewSubmitUiEvent.SubmitTapped)

        assertFalse(vm.state.value.submitted)
        assertEquals("1 line(s) still need a variance reason.", vm.state.value.blockingMessage)
    }

    @Test
    fun `SUBMIT is blocked with a specific message on a serial count mismatch`() = runTest {
        draftLineDao.insert(
            DraftLineEntity(
                draftId = draftId, purchaseOrderItemId = 2, sku = "CB-2201", name = "Cable Brace",
                countedQuantity = 3, requiresSerialNumber = true,
            ),
        )
        server.enqueue(MockResponse().setBody(poDetailBody))
        server.enqueue(
            MockResponse().setBody(
                """{"lines": [
                    {"purchase_order_item_id": 1, "sku": "WM-4410-BLK", "name": "Widget Mount, Black",
                     "quantity_expected": 6, "quantity_counted": 6, "delta": 0,
                     "is_over_receipt": false, "over_receipt_permitted": true},
                    {"purchase_order_item_id": 2, "sku": "CB-2201", "name": "Cable Brace",
                     "quantity_expected": 3, "quantity_counted": 3, "delta": 0,
                     "is_over_receipt": false, "over_receipt_permitted": true}
                   ], "variance_reasons": []}
                """.trimIndent(),
            ),
        )
        val vm = viewModel()

        vm.onEvent(ReviewSubmitUiEvent.SubmitTapped)

        assertFalse(vm.state.value.submitted)
        assertEquals("1 line(s) have a serial count mismatch.", vm.state.value.blockingMessage)
    }

    @Test
    fun `Save draft and exit leaves the draft state untouched`() = runTest {
        server.enqueue(MockResponse().setBody(poDetailBody))
        server.enqueue(MockResponse().setBody(reconcileBodyWithVariance))
        val vm = viewModel()

        vm.onEvent(ReviewSubmitUiEvent.SaveAndExitTapped)

        assertTrue(vm.state.value.savedAndExited)
        assertEquals(DraftState.REVIEW, draftDao.getById(draftId)?.state)
    }
}

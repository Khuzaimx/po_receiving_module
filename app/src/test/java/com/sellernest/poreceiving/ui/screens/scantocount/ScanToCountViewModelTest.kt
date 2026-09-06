package com.sellernest.poreceiving.ui.screens.scantocount

import androidx.lifecycle.SavedStateHandle
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.sellernest.poreceiving.data.local.DraftRepository
import com.sellernest.poreceiving.data.local.FakeDraftPhotoDao
import com.sellernest.poreceiving.data.local.FakeDraftSerialDao
import com.sellernest.poreceiving.data.local.FakeQueuedSubmissionDao
import com.sellernest.poreceiving.data.local.entities.DraftEntity
import com.sellernest.poreceiving.data.local.entities.DraftState
import com.sellernest.poreceiving.network.ApiService
import com.sellernest.poreceiving.network.dto.ScanResolution
import com.sellernest.poreceiving.scan.ScanFeedbackService
import com.sellernest.poreceiving.scan.ScanSource
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

/**
 * M3.3/M3.4 acceptance criteria: repeated scans of the same item increment
 * its line and the header total; the matched field is carried through; manual
 * quantity entry replaces rather than appends; COMMIT COUNT transitions the
 * draft into RECONCILE.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScanToCountViewModelTest {

    private lateinit var server: MockWebServer
    private lateinit var apiService: ApiService
    private lateinit var json: Json
    private lateinit var draftDao: FakeDraftDao
    private lateinit var draftLineDao: FakeDraftLineDao
    private lateinit var draftRepository: DraftRepository
    private lateinit var feedback: FakeScanFeedbackService
    private var draftId: Long = 0

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
        feedback = FakeScanFeedbackService()

        val draft = draftRepository.openPurchaseOrder(10482, "PO-10482", warehouseId = 2)
        draftId = draft.id
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        server.shutdown()
    }

    private fun viewModel() = ScanToCountViewModel(
        savedStateHandle = SavedStateHandle(mapOf("draftId" to draftId)),
        apiService = apiService,
        json = json,
        scanFeedbackService = feedback,
        draftRepository = draftRepository,
    )

    private val matchedResponseBody = """
        {"outcome": "matched", "matched_field": "upc",
         "line": {"purchase_order_item_id": 88213, "sku": "WM-4410-BLK",
                  "name": "Widget Mount, Black", "quantity_already_received": 2,
                  "requires_serial_number": false, "fully_received": false}}
    """.trimIndent()

    @Test
    fun `a matched scan is recorded, shows the matched field, and signals accepted`() = runTest {
        server.enqueue(MockResponse().setBody(matchedResponseBody))
        val vm = viewModel()

        vm.onEvent(ScanToCountUiEvent.ScanReceived("0468673502897", ScanSource.CAMERA))

        val outcome = vm.state.value.lastScanOutcome as? ScanResolution.Matched
        assertEquals("upc", outcome?.matchedField)
        assertEquals(1, feedback.acceptedCount)
        assertEquals(0, feedback.rejectedCount)
        assertEquals(1, vm.state.value.totalCountedQuantity)
    }

    @Test
    fun `repeated scans of the same item increment its line and the header total`() = runTest {
        server.enqueue(MockResponse().setBody(matchedResponseBody))
        server.enqueue(MockResponse().setBody(matchedResponseBody))
        server.enqueue(MockResponse().setBody(matchedResponseBody))
        val vm = viewModel()

        repeat(3) {
            vm.onEvent(ScanToCountUiEvent.ScanReceived("0468673502897", ScanSource.CAMERA))
        }

        assertEquals(3, vm.state.value.totalCountedQuantity)
        assertEquals(1, vm.state.value.lines.size)
        assertEquals(3, vm.state.value.lines.single().countedQuantity)
    }

    @Test
    fun `manual quantity entry replaces rather than appends`() = runTest {
        server.enqueue(MockResponse().setBody(matchedResponseBody))
        val vm = viewModel()
        vm.onEvent(ScanToCountUiEvent.ScanReceived("0468673502897", ScanSource.CAMERA))
        assertEquals(1, vm.state.value.lines.single().countedQuantity)

        vm.onEvent(ScanToCountUiEvent.ManualQuantitySubmitted(88213, 120))

        assertEquals(120, vm.state.value.lines.single().countedQuantity)
        assertNull(vm.state.value.manualQuantityEntryForLineId)
    }

    @Test
    fun `plus and minus adjust the currently matched line by one`() = runTest {
        server.enqueue(MockResponse().setBody(matchedResponseBody))
        val vm = viewModel()
        vm.onEvent(ScanToCountUiEvent.ScanReceived("0468673502897", ScanSource.CAMERA))

        vm.onEvent(ScanToCountUiEvent.QuantityIncremented(88213))
        vm.onEvent(ScanToCountUiEvent.QuantityIncremented(88213))
        vm.onEvent(ScanToCountUiEvent.QuantityDecremented(88213))

        assertEquals(2, vm.state.value.lines.single().countedQuantity)
    }

    @Test
    fun `an unmatched outcome does not record any scan or increment the total`() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"outcome": "unknown_code", "code": "GARBAGE"}"""),
        )
        val vm = viewModel()

        vm.onEvent(ScanToCountUiEvent.ScanReceived("GARBAGE", ScanSource.CAMERA))

        assertEquals(0, vm.state.value.totalCountedQuantity)
        assertTrue(vm.state.value.lastScanOutcome is ScanResolution.UnknownCode)
        assertEquals(0, feedback.acceptedCount)
        assertEquals(1, feedback.rejectedCount)
    }

    @Test
    fun `commit count transitions the draft to RECONCILE and signals navigation`() = runTest {
        server.enqueue(MockResponse().setBody(matchedResponseBody))
        val vm = viewModel()
        vm.onEvent(ScanToCountUiEvent.ScanReceived("0468673502897", ScanSource.CAMERA))

        vm.onEvent(ScanToCountUiEvent.CommitCountTapped)

        assertEquals(draftId, vm.state.value.navigateToReconcileDraftId)
        assertEquals(DraftState.RECONCILE, draftDao.getById(draftId)?.state)
    }
}

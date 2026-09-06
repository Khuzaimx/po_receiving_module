package com.sellernest.poreceiving.ui.screens.damagecapture

import androidx.lifecycle.SavedStateHandle
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.sellernest.poreceiving.data.local.DraftRepository
import com.sellernest.poreceiving.data.local.FakeDraftPhotoDao
import com.sellernest.poreceiving.data.local.FakeDraftSerialDao
import com.sellernest.poreceiving.data.local.FakeQueuedSubmissionDao
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
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

/** M4.3 acceptance criteria. */
@OptIn(ExperimentalCoroutinesApi::class)
class DamageCaptureViewModelTest {

    private lateinit var server: MockWebServer
    private lateinit var apiService: ApiService
    private lateinit var json: Json
    private lateinit var draftLineDao: FakeDraftLineDao
    private lateinit var draftRepository: DraftRepository
    private var draftId: Long = 0
    private val purchaseOrderItemId = 88213L

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
        draftLineDao = FakeDraftLineDao()
        draftRepository = DraftRepository(
            FakeDraftDao(), draftLineDao, FakeDraftPhotoDao(), FakeDraftSerialDao(), FakeQueuedSubmissionDao(),
        )

        val draft = draftRepository.openPurchaseOrder(10482, "PO-10482", warehouseId = 2)
        draftId = draft.id
        repeat(6) {
            draftRepository.recordScan(
                draftId,
                ScanMatchedLine(
                    purchaseOrderItemId = purchaseOrderItemId, sku = "WM-4410-BLK", name = "Widget Mount, Black",
                    quantityAlreadyReceived = 0, requiresSerialNumber = false, fullyReceived = false,
                ),
            )
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        server.shutdown()
    }

    private fun viewModel() = DamageCaptureViewModel(
        savedStateHandle = SavedStateHandle(mapOf("draftId" to draftId, "purchaseOrderItemId" to purchaseOrderItemId)),
        apiService = apiService,
        json = json,
        draftRepository = draftRepository,
    )

    private val reasonsBody = """{"lines": [], "variance_reasons": [{"id": 7, "label": "Damaged In Transit"}]}"""

    @Test
    fun `GOOD is derived from COUNTED minus DAMAGED and never needs a separate check`() = runTest {
        server.enqueue(MockResponse().setBody(reasonsBody))
        val vm = viewModel()

        vm.onEvent(DamageCaptureUiEvent.DamagedQuantityIncremented)
        vm.onEvent(DamageCaptureUiEvent.DamagedQuantityIncremented)

        assertEquals(6, vm.state.value.countedQuantity)
        assertEquals(2, vm.state.value.damagedQuantity)
        assertEquals(4, vm.state.value.goodQuantity)
    }

    @Test
    fun `the damaged control cannot exceed the counted quantity`() = runTest {
        server.enqueue(MockResponse().setBody(reasonsBody))
        val vm = viewModel()

        repeat(10) { vm.onEvent(DamageCaptureUiEvent.DamagedQuantityIncremented) }

        assertEquals(6, vm.state.value.damagedQuantity)
        assertEquals(0, vm.state.value.goodQuantity)
    }

    @Test
    fun `a chosen reason and note persist to the draft line immediately`() = runTest {
        server.enqueue(MockResponse().setBody(reasonsBody))
        val vm = viewModel()

        vm.onEvent(DamageCaptureUiEvent.ReasonSelected(7))
        vm.onEvent(DamageCaptureUiEvent.NoteChanged("Corner crushed"))

        val persisted = draftLineDao.getByPurchaseOrderItem(draftId, purchaseOrderItemId)
        assertEquals(7L, persisted?.varianceReasonId)
        assertEquals("Corner crushed", persisted?.varianceNote)
    }

    @Test
    fun `a captured photo is attached to this line`() = runTest {
        server.enqueue(MockResponse().setBody(reasonsBody))
        val vm = viewModel()

        vm.onEvent(DamageCaptureUiEvent.PhotoCaptured("/tmp/photo.jpg"))

        assertEquals(1, vm.state.value.photos.size)
        assertEquals("/tmp/photo.jpg", vm.state.value.photos.single().localFilePath)
    }
}

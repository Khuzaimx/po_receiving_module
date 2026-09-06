package com.sellernest.poreceiving.ui.screens.serialcapture

import androidx.lifecycle.SavedStateHandle
import com.sellernest.poreceiving.data.local.DraftRepository
import com.sellernest.poreceiving.data.local.FakeDraftPhotoDao
import com.sellernest.poreceiving.data.local.FakeDraftSerialDao
import com.sellernest.poreceiving.data.local.FakeQueuedSubmissionDao
import com.sellernest.poreceiving.network.dto.ScanMatchedLine
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** M4.5/§10 acceptance criteria. */
@OptIn(ExperimentalCoroutinesApi::class)
class SerialCaptureViewModelTest {

    private lateinit var draftLineDao: FakeDraftLineDao
    private lateinit var draftSerialDao: FakeDraftSerialDao
    private lateinit var draftRepository: DraftRepository
    private lateinit var feedback: FakeScanFeedbackService
    private var draftId: Long = 0
    private var purchaseOrderItemId: Long = 88213

    private class FakeScanFeedbackService : ScanFeedbackService {
        var acceptedCount = 0
        var rejectedCount = 0
        override fun accepted() { acceptedCount++ }
        override fun rejected() { rejectedCount++ }
    }

    @Before
    fun setUp() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        draftLineDao = FakeDraftLineDao()
        draftSerialDao = FakeDraftSerialDao(draftLineDao)
        draftRepository = DraftRepository(FakeDraftDao(), draftLineDao, FakeDraftPhotoDao(), draftSerialDao, FakeQueuedSubmissionDao())
        feedback = FakeScanFeedbackService()

        val draft = draftRepository.openPurchaseOrder(10482, "PO-10482", warehouseId = 2)
        draftId = draft.id
        // Four units already counted (M3.4) -- serial capture requires exactly this many.
        repeat(4) {
            draftRepository.recordScan(
                draftId,
                ScanMatchedLine(
                    purchaseOrderItemId = purchaseOrderItemId, sku = "WM-4410-BLK", name = "Widget Mount, Black",
                    quantityAlreadyReceived = 0, requiresSerialNumber = true, fullyReceived = false,
                ),
            )
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = SerialCaptureViewModel(
        savedStateHandle = SavedStateHandle(mapOf("draftId" to draftId, "purchaseOrderItemId" to purchaseOrderItemId)),
        scanFeedbackService = feedback,
        draftRepository = draftRepository,
    )

    @Test
    fun `required count matches the already-counted quantity, not an expected quantity`() = runTest {
        val vm = viewModel()

        assertEquals(4, vm.state.value.requiredCount)
        assertEquals(0, vm.state.value.capturedCount)
        assertFalse(vm.state.value.canFinish)
    }

    @Test
    fun `a scanned serial is captured and signals accepted`() = runTest {
        val vm = viewModel()

        vm.onEvent(SerialCaptureUiEvent.SerialScanned("SN-1", ScanSource.CAMERA))

        assertEquals(1, vm.state.value.capturedCount)
        assertEquals(1, feedback.acceptedCount)
    }

    @Test
    fun `a duplicate serial is rejected and names the conflicting value`() = runTest {
        val vm = viewModel()
        vm.onEvent(SerialCaptureUiEvent.SerialScanned("SN-1", ScanSource.CAMERA))

        vm.onEvent(SerialCaptureUiEvent.SerialScanned("SN-1", ScanSource.CAMERA))

        assertEquals(1, vm.state.value.capturedCount)
        assertEquals("SN-1", vm.state.value.duplicateAttempt)
        assertEquals(1, feedback.acceptedCount)
        assertEquals(1, feedback.rejectedCount)
    }

    @Test
    fun `dismissing the duplicate dialog clears it`() = runTest {
        val vm = viewModel()
        vm.onEvent(SerialCaptureUiEvent.SerialScanned("SN-1", ScanSource.CAMERA))
        vm.onEvent(SerialCaptureUiEvent.SerialScanned("SN-1", ScanSource.CAMERA))

        vm.onEvent(SerialCaptureUiEvent.DuplicateDismissed)

        assertNull(vm.state.value.duplicateAttempt)
    }

    @Test
    fun `removing a serial re-opens that slot`() = runTest {
        val vm = viewModel()
        vm.onEvent(SerialCaptureUiEvent.SerialScanned("SN-1", ScanSource.CAMERA))
        val captured = vm.state.value.serials.single()

        vm.onEvent(SerialCaptureUiEvent.SerialRemoved(captured))

        assertEquals(0, vm.state.value.capturedCount)
    }

    @Test
    fun `a scan past the required count is rejected with nowhere to go`() = runTest {
        val vm = viewModel()
        repeat(4) { index -> vm.onEvent(SerialCaptureUiEvent.SerialScanned("SN-$index", ScanSource.CAMERA)) }
        assertTrue(vm.state.value.canFinish)

        vm.onEvent(SerialCaptureUiEvent.SerialScanned("SN-EXTRA", ScanSource.CAMERA))

        assertEquals(4, vm.state.value.capturedCount)
        assertEquals(5, feedback.rejectedCount + feedback.acceptedCount) // 4 accepted, 1 rejected
        assertEquals(1, feedback.rejectedCount)
    }

    @Test
    fun `DONE is only reachable at exactly the required count`() = runTest {
        val vm = viewModel()
        repeat(3) { index -> vm.onEvent(SerialCaptureUiEvent.SerialScanned("SN-$index", ScanSource.CAMERA)) }
        vm.onEvent(SerialCaptureUiEvent.DoneTapped)
        assertFalse(vm.state.value.navigateBack)

        vm.onEvent(SerialCaptureUiEvent.SerialScanned("SN-3", ScanSource.CAMERA))
        vm.onEvent(SerialCaptureUiEvent.DoneTapped)

        assertTrue(vm.state.value.navigateBack)
    }
}

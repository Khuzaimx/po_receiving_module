package com.sellernest.poreceiving.ui.screens.submissionqueue

import com.sellernest.poreceiving.data.local.DraftRepository
import com.sellernest.poreceiving.data.local.FakeDraftPhotoDao
import com.sellernest.poreceiving.data.local.FakeDraftSerialDao
import com.sellernest.poreceiving.data.local.FakeQueuedSubmissionDao
import com.sellernest.poreceiving.data.local.entities.DraftState
import com.sellernest.poreceiving.data.local.entities.SubmissionStatus
import com.sellernest.poreceiving.network.dto.ScanMatchedLine
import com.sellernest.poreceiving.ui.screens.poheader.FakeDraftLineDao
import com.sellernest.poreceiving.ui.screens.warehouseselection.FakeDraftDao
import com.sellernest.poreceiving.work.submit.FakeSubmitScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** M5.5 acceptance criteria. */
@OptIn(ExperimentalCoroutinesApi::class)
class SubmissionQueueViewModelTest {

    private lateinit var draftDao: FakeDraftDao
    private lateinit var queuedSubmissionDao: FakeQueuedSubmissionDao
    private lateinit var draftRepository: DraftRepository
    private lateinit var submitScheduler: FakeSubmitScheduler
    private lateinit var json: Json
    private var draftId: Long = 0

    @Before
    fun setUp() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        json = Json {
            namingStrategy = JsonNamingStrategy.SnakeCase
            ignoreUnknownKeys = true
            explicitNulls = false
            coerceInputValues = true
        }
        draftDao = FakeDraftDao()
        queuedSubmissionDao = FakeQueuedSubmissionDao()
        val draftLineDao = FakeDraftLineDao()
        draftRepository = DraftRepository(draftDao, draftLineDao, FakeDraftPhotoDao(), FakeDraftSerialDao(draftLineDao), queuedSubmissionDao)
        submitScheduler = FakeSubmitScheduler()

        val draft = draftRepository.openPurchaseOrder(10482, "PO-10482", warehouseId = 2)
        draftId = draft.id
        draftRepository.recordScan(
            draftId,
            ScanMatchedLine(purchaseOrderItemId = 1, sku = "A", name = "A", quantityAlreadyReceived = 0, requiresSerialNumber = false, fullyReceived = false),
        )
        draftRepository.commitCount(draftId)
        draftRepository.completeReconciliation(draftId)
        draftRepository.queueForSubmission(draftId)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = SubmissionQueueViewModel(draftRepository, submitScheduler, json)

    @Test
    fun `a pending submission is listed with its PO number`() = runTest {
        val vm = viewModel()

        assertEquals(1, vm.state.value.items.size)
        assertEquals("PO-10482", vm.state.value.items.single().purchaseOrderNumber)
        assertEquals(SubmissionStatus.PENDING, vm.state.value.items.single().status)
    }

    @Test
    fun `a failed submission's per-line errors decode from the persisted JSON`() = runTest {
        draftRepository.markSubmissionFailed(draftId, "network hiccup, but recorded as an example")
        val submission = queuedSubmissionDao.getForDraft(draftId)!!
        queuedSubmissionDao.update(
            submission.copy(
                failedLinesJson = """[{"purchase_order_item_id": 1, "error": "Over-receipt requires permission."}]""",
            ),
        )

        val vm = viewModel()

        val failure = vm.state.value.items.single().failedLines.single()
        assertEquals(1L, failure.purchaseOrderItemId)
        assertEquals("Over-receipt requires permission.", failure.error)
    }

    @Test
    fun `DISCARD requires confirmation naming the PO before it takes effect`() = runTest {
        draftRepository.markSubmissionFailed(draftId, "Over-receipt requires permission.")
        val vm = viewModel()
        val item = vm.state.value.items.single()

        vm.onEvent(SubmissionQueueUiEvent.DiscardRequested(item))
        assertEquals(item, vm.state.value.discardTarget)
        // Not yet discarded -- only requested.
        assertEquals(DraftState.QUEUED, draftDao.getById(draftId)?.state)

        vm.onEvent(SubmissionQueueUiEvent.DiscardConfirmed)

        assertEquals(DraftState.DISCARDED, draftDao.getById(draftId)?.state)
        assertNull(vm.state.value.discardTarget)
    }

    @Test
    fun `cancelling a discard leaves the submission untouched`() = runTest {
        draftRepository.markSubmissionFailed(draftId, "Over-receipt requires permission.")
        val vm = viewModel()
        vm.onEvent(SubmissionQueueUiEvent.DiscardRequested(vm.state.value.items.single()))

        vm.onEvent(SubmissionQueueUiEvent.DiscardCancelled)

        assertNull(vm.state.value.discardTarget)
        assertEquals(DraftState.QUEUED, draftDao.getById(draftId)?.state)
        assertTrue("the submission row must still exist after cancelling", queuedSubmissionDao.getForDraft(draftId) != null)
    }
}

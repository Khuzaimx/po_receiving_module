package com.sellernest.poreceiving

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.sellernest.poreceiving.data.local.DraftRepository
import com.sellernest.poreceiving.data.local.FakeDraftPhotoDao
import com.sellernest.poreceiving.data.local.FakeDraftSerialDao
import com.sellernest.poreceiving.data.local.FakeQueuedSubmissionDao
import com.sellernest.poreceiving.data.local.entities.DraftState
import com.sellernest.poreceiving.network.ApiService
import com.sellernest.poreceiving.network.dto.ScanMatchedLine
import com.sellernest.poreceiving.ui.screens.poheader.FakeDraftLineDao
import com.sellernest.poreceiving.ui.screens.warehouseselection.FakeDraftDao
import com.sellernest.poreceiving.work.submit.FakePhotoUploadScheduler
import com.sellernest.poreceiving.work.submit.SubmitDraftOutcome
import com.sellernest.poreceiving.work.submit.SubmitDraftUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

/**
 * M7.3: "Each [§11 verification scenario] must be demonstrated." Three of the
 * eight scenarios (2: airplane mode, 3: force-stop + relaunch, and the "rugged
 * device + consumer phone profile" / CI-video-artifact requirements around all
 * of them) need a real device, a staging backend, and adb -- none of which
 * exist in this environment (no JDK, no emulator, no physical hardware; see
 * the standing caveat repeated in every PR this session). What follows is not
 * a substitute for running the real thing on a device before this milestone
 * is marked done -- it is the closest local, automatable proof for each
 * scenario, plus a pointer to where a scenario's real proof already lives:
 *
 * | Scenario | Proof |
 * |---|---|
 * | 1: 14-line PO, 3 variances, 2 photos, 4 serials | [aFourteenLinePoWithThreeVariancesTwoPhotosAndFourSerialsAssemblesACorrectReceivePayload] below |
 * | 2: airplane mode mid-count, submit, reconnect -> exactly one receipt | Needs a real device for the airplane-mode toggle itself; the "exactly one receipt" half is [aReplayedRetryNeverProducesMoreThanOneReceiptLocally] below, same as scenario 4 |
 * | 3: force-stop mid-count, relaunch, resume with scans intact | `com.sellernest.poreceiving.data.local.DraftPersistenceInstrumentedTest` (needs a device/emulator) |
 * | 4: retry the same submission, stock moves once | `com.sellernest.poreceiving.work.submit.SubmitDraftUseCaseTest`'s idempotency tests, plus [aReplayedRetryNeverProducesMoreThanOneReceiptLocally] below |
 * | 5: scan an item not on the PO, confirm it is named | `ScanOutcomeBanner`'s `NotOnPurchaseOrder` branch; `ScanToCountViewModelTest` |
 * | 6: over-receipt without permission, blocked and named | `ReconcileViewModelTest`'s "an unpermitted over-receipt blocks CONTINUE" test |
 * | 7: void inside the window, attempt again outside it | `ReceiptHistoryViewModelTest`'s window tests |
 * | 8: warehouse-scoped receiver, other warehouses absent | `WorkQueueViewModelTest`'s "the work queue request is scoped to the active warehouse" test |
 *
 * A CI-runnable video/step-log artefact and a rugged-device-vs-consumer-phone
 * device matrix are process/infra requirements this session cannot produce in
 * any environment, not only this one -- they need to be set up once real
 * hardware and a staging backend are available.
 */
class VerificationScenariosAuditTest {

    private lateinit var server: MockWebServer
    private lateinit var apiService: ApiService
    private lateinit var json: Json
    private lateinit var draftRepository: DraftRepository
    private lateinit var draftLineDao: FakeDraftLineDao
    private var draftId: Long = 0

    @Before
    fun setUp() = runTest {
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
            FakeDraftDao(), draftLineDao, FakeDraftPhotoDao(), FakeDraftSerialDao(draftLineDao), FakeQueuedSubmissionDao(),
        )

        val draft = draftRepository.openPurchaseOrder(10482, "PO-10482", warehouseId = 2)
        draftId = draft.id
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    /**
     * §11 scenario 1, exercised entirely against the local pipeline every
     * real screen in this app drives: 14 lines counted, 3 of them left as
     * variances at reconcile, 2 photos attached (one per-line, one
     * receipt-level), and 4 serials captured on the one line that requires
     * them -- then asserts the exact §9.4 payload
     * [DraftRepository.buildReceiveRequest] would actually submit.
     */
    @Test
    fun aFourteenLinePoWithThreeVariancesTwoPhotosAndFourSerialsAssemblesACorrectReceivePayload() = runTest {
        val serialRequiredItemId = 1L
        repeat(14) { index ->
            val itemId = index.toLong() + 1
            draftRepository.recordScan(
                draftId,
                ScanMatchedLine(
                    purchaseOrderItemId = itemId, sku = "SKU-$itemId", name = "Item $itemId",
                    quantityAlreadyReceived = 0, requiresSerialNumber = itemId == serialRequiredItemId, fullyReceived = false,
                ),
            )
        }
        // Three variance-adjacent lines: one with damage (still a "matches
        // expected" count-wise, since damaged units are still received), one
        // under-counted, one with an extra scan (an over-receipt).
        draftRepository.setDamagedQuantity(draftId, purchaseOrderItemId = 2, damagedQuantity = 1)
        draftRepository.setLineQuantity(draftId, purchaseOrderItemId = 3, quantity = 0) // under-received
        draftRepository.recordScan(
            draftId,
            ScanMatchedLine(purchaseOrderItemId = 4, sku = "SKU-4", name = "Item 4", quantityAlreadyReceived = 0, requiresSerialNumber = false, fullyReceived = false),
        ) // second scan -> over-received relative to a 1-unit expectation

        // Three more scans of the serial-required line, so its counted
        // quantity is 4 -- matching "four serials" the way M4.5's required
        // count (the counted quantity, never blind) actually works.
        repeat(3) {
            draftRepository.recordScan(
                draftId,
                ScanMatchedLine(
                    purchaseOrderItemId = serialRequiredItemId, sku = "SKU-$serialRequiredItemId", name = "Item $serialRequiredItemId",
                    quantityAlreadyReceived = 0, requiresSerialNumber = true, fullyReceived = false,
                ),
            )
        }
        val serialLine = draftLineDao.getByPurchaseOrderItem(draftId, serialRequiredItemId)!!
        repeat(4) { index -> draftRepository.addSerial(serialLine.id, "SN-$index") }

        draftRepository.addPhoto(draftId, purchaseOrderItemId = 3, localFilePath = "/tmp/line3.jpg")
        draftRepository.addPhoto(draftId, purchaseOrderItemId = null, localFilePath = "/tmp/receipt.jpg")

        draftRepository.commitCount(draftId)
        draftRepository.setVarianceReason(draftId, purchaseOrderItemId = 3, reasonId = 7, note = "Short-shipped")
        draftRepository.setVarianceReason(draftId, purchaseOrderItemId = 4, reasonId = 9, note = "Extra unit found on pallet")
        draftRepository.setMissingQuantity(draftId, purchaseOrderItemId = 3, missingQuantity = 1)
        draftRepository.completeReconciliation(draftId)
        draftRepository.setBin(draftId, 41)
        draftRepository.queueForSubmission(draftId)

        val request = draftRepository.buildReceiveRequest(draftId)!!

        assertEquals(14, request.lines.size)
        assertEquals(41L, request.binId)
        val serialLineRequest = request.lines.single { it.purchaseOrderItemId == serialRequiredItemId }
        assertEquals(setOf("SN-0", "SN-1", "SN-2", "SN-3"), serialLineRequest.serials.toSet())
        val damagedLineRequest = request.lines.single { it.purchaseOrderItemId == 2L }
        assertEquals(1, damagedLineRequest.quantityDamaged)
        val underReceivedLineRequest = request.lines.single { it.purchaseOrderItemId == 3L }
        assertEquals(1, underReceivedLineRequest.quantityMissing)
        assertEquals(7L, underReceivedLineRequest.varianceReasonId)
        val overReceivedLineRequest = request.lines.single { it.purchaseOrderItemId == 4L }
        assertEquals(2, overReceivedLineRequest.quantityReceived)
        assertEquals(9L, overReceivedLineRequest.varianceReasonId)
        assertEquals(2, draftRepository.observePhotos(draftId).first().size)
        assertEquals(DraftState.QUEUED, draftRepository.getDraft(draftId)?.state)
    }

    /**
     * §11 scenarios 2 & 4: "confirm exactly one receipt" / "stock moved
     * once." `replayed: true` is the server's signal that this attempt is a
     * retry of an already-processed submission -- this asserts the app never
     * treats that as a second, distinct success (there is exactly one
     * [com.sellernest.poreceiving.data.local.entities.QueuedSubmissionEntity]
     * row for the draft either way, holding exactly one `receiptId`).
     */
    @Test
    fun aReplayedRetryNeverProducesMoreThanOneReceiptLocally() = runTest {
        draftRepository.recordScan(
            draftId,
            ScanMatchedLine(purchaseOrderItemId = 1, sku = "A", name = "A", quantityAlreadyReceived = 0, requiresSerialNumber = false, fullyReceived = false),
        )
        draftRepository.commitCount(draftId)
        draftRepository.completeReconciliation(draftId)
        draftRepository.queueForSubmission(draftId)
        val useCase = SubmitDraftUseCase(draftRepository, apiService, json, FakePhotoUploadScheduler())

        server.enqueue(MockResponse().setBody("""{"receipt_id": 4412, "replayed": false, "updated": [1], "failed": []}"""))
        val first = useCase.execute(draftId)

        server.enqueue(MockResponse().setBody("""{"receipt_id": 4412, "replayed": true, "updated": [1], "failed": []}"""))
        val retried = useCase.execute(draftId)

        assertEquals(SubmitDraftOutcome.Success, first)
        assertEquals(SubmitDraftOutcome.Success, retried)
        assertEquals(4412L, draftRepository.getSubmission(draftId)?.receiptId)
        assertTrue("a replayed retry must not create a second submission record", draftRepository.observeSubmissions().first().size == 1)
    }
}

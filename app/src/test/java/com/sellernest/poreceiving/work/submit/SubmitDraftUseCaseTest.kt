package com.sellernest.poreceiving.work.submit

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.sellernest.poreceiving.data.local.DraftRepository
import com.sellernest.poreceiving.data.local.FakeDraftPhotoDao
import com.sellernest.poreceiving.data.local.FakeDraftSerialDao
import com.sellernest.poreceiving.data.local.FakeQueuedSubmissionDao
import com.sellernest.poreceiving.data.local.entities.DraftPhotoEntity
import com.sellernest.poreceiving.data.local.entities.DraftState
import com.sellernest.poreceiving.data.local.entities.SubmissionStatus
import com.sellernest.poreceiving.network.ApiService
import com.sellernest.poreceiving.network.dto.ScanMatchedLine
import com.sellernest.poreceiving.ui.screens.poheader.FakeDraftLineDao
import com.sellernest.poreceiving.ui.screens.warehouseselection.FakeDraftDao
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

/** M5.2-M5.4/§9.4 acceptance criteria. */
class SubmitDraftUseCaseTest {

    private lateinit var server: MockWebServer
    private lateinit var apiService: ApiService
    private lateinit var json: Json
    private lateinit var draftDao: FakeDraftDao
    private lateinit var queuedSubmissionDao: FakeQueuedSubmissionDao
    private lateinit var draftPhotoDao: FakeDraftPhotoDao
    private lateinit var draftRepository: DraftRepository
    private lateinit var photoUploadScheduler: FakePhotoUploadScheduler
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
        draftDao = FakeDraftDao()
        queuedSubmissionDao = FakeQueuedSubmissionDao()
        draftPhotoDao = FakeDraftPhotoDao()
        val draftLineDao = FakeDraftLineDao()
        draftRepository = DraftRepository(draftDao, draftLineDao, draftPhotoDao, FakeDraftSerialDao(draftLineDao), queuedSubmissionDao)
        photoUploadScheduler = FakePhotoUploadScheduler()

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
        server.shutdown()
    }

    private fun useCase() = SubmitDraftUseCase(draftRepository, apiService, json, photoUploadScheduler)

    @Test
    fun `a clean 2xx response marks the submission RECEIPTED and transitions the draft`() = runTest {
        server.enqueue(MockResponse().setBody("""{"receipt_id": 4412, "replayed": false, "updated": [1], "failed": []}"""))

        val outcome = useCase().execute(draftId)

        assertEquals(SubmitDraftOutcome.Success, outcome)
        assertEquals(DraftState.RECEIPTED, draftDao.getById(draftId)?.state)
        val submission = queuedSubmissionDao.getForDraft(draftId)
        assertEquals(SubmissionStatus.RECEIPTED, submission?.status)
        assertEquals(4412L, submission?.receiptId)
        assertNull(submission?.failedLinesJson)
    }

    @Test
    fun `partial failure is still a successful RECEIPTED submission, with the failures retained verbatim`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"receipt_id": 4412, "replayed": false, "updated": [1],
                    "failed": [{"purchase_order_item_id": 2, "error": "Over-receipt requires permission."}]}
                """.trimIndent(),
            ),
        )

        val outcome = useCase().execute(draftId)

        assertEquals(SubmitDraftOutcome.Success, outcome)
        val submission = queuedSubmissionDao.getForDraft(draftId)
        assertEquals(SubmissionStatus.RECEIPTED, submission?.status)
        assertTrue(submission?.failedLinesJson.orEmpty().contains("Over-receipt requires permission."))
    }

    @Test
    fun `a successful submit enqueues photo upload only after the receipt id is known`() = runTest {
        val photoId = draftPhotoDao.insert(DraftPhotoEntity(draftId = draftId, localFilePath = "/tmp/photo.jpg"))
        server.enqueue(MockResponse().setBody("""{"receipt_id": 4412, "replayed": false, "updated": [1], "failed": []}"""))

        useCase().execute(draftId)

        assertEquals(listOf(photoId), photoUploadScheduler.enqueuedPhotoIds)
    }

    @Test
    fun `the same idempotency key is sent on the request regardless of attempt`() = runTest {
        val draft = draftRepository.getDraft(draftId)!!
        server.enqueue(MockResponse().setBody("""{"receipt_id": 4412, "replayed": false, "updated": [1], "failed": []}"""))

        useCase().execute(draftId)

        val sentBody = server.takeRequest().body.readUtf8()
        assertTrue(sentBody.contains(draft.idempotencyKey))
    }

    @Test
    fun `a 409 idempotency conflict is terminal and never regenerates the key`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(409).setBody(
                """{"error": "idempotency_key_conflict", "detail": "This key was already used with a different payload."}""",
            ),
        )
        val originalKey = draftRepository.getDraft(draftId)!!.idempotencyKey

        val outcome = useCase().execute(draftId)

        assertEquals(SubmitDraftOutcome.Failure, outcome)
        assertEquals(SubmissionStatus.FAILED, queuedSubmissionDao.getForDraft(draftId)?.status)
        assertEquals("This key was already used with a different payload.", queuedSubmissionDao.getForDraft(draftId)?.lastError)
        assertEquals(originalKey, draftRepository.getDraft(draftId)?.idempotencyKey)
        assertEquals(DraftState.QUEUED, draftDao.getById(draftId)?.state)
    }

    @Test
    fun `a rejected payload is a terminal failure, not a retry`() = runTest {
        server.enqueue(MockResponse().setResponseCode(422).setBody("""{"error": "validation_error", "detail": "quantity_damaged exceeds quantity_received"}"""))

        val outcome = useCase().execute(draftId)

        assertEquals(SubmitDraftOutcome.Failure, outcome)
        assertEquals(SubmissionStatus.FAILED, queuedSubmissionDao.getForDraft(draftId)?.status)
    }

    @Test
    fun `a network failure is retried, not failed terminally`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val outcome = useCase().execute(draftId)

        assertEquals(SubmitDraftOutcome.Retry, outcome)
    }

    @Test
    fun `every attempt increments the recorded attempt count`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        server.enqueue(MockResponse().setBody("""{"receipt_id": 4412, "replayed": false, "updated": [1], "failed": []}"""))

        useCase().execute(draftId)
        useCase().execute(draftId)

        assertEquals(2, queuedSubmissionDao.getForDraft(draftId)?.attemptCount)
    }
}

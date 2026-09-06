package com.sellernest.poreceiving.work.submit

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
 * M7.1: "Implement and audit the full error and edge-case catalogue... this
 * issue is the audit that proves every one holds in the assembled app."
 * Every §10 row already has a dedicated test somewhere in this codebase; this
 * class maps each one to where that proof lives, and adds the one row this
 * session found with no existing direct test: a PO closing (or otherwise
 * becoming unreceivable) while a submission is in flight.
 *
 * | §10 row | Covered by |
 * |---|---|
 * | Unknown barcode: raw string, rescan or manual entry | `ScanOutcomeBanner` (M2.6) renders [com.sellernest.poreceiving.network.dto.ScanResolution.UnknownCode.code] verbatim; `ScanToCountViewModelTest`'s unmatched-outcome test |
 * | Item not on this PO: names the item, offers the correct PO | `ScanOutcomeBanner`'s `NotOnPurchaseOrder` branch names [com.sellernest.poreceiving.network.dto.UnmatchedItem]; `ScanToCountUiEvent.OpenCorrectPoTapped` |
 * | Multiple candidate lines: explicit choice required | `ScanOutcomeBanner`'s `MultipleMatches` branch; `ScanToCountViewModelTest`'s candidate-selection test |
 * | Over-receipt without permission: blocked at reconcile, permission named | `ReconcileViewModelTest`'s "an unpermitted over-receipt blocks CONTINUE" test |
 * | Access token expired mid-count: silent refresh, draft preserved | `AuthInterceptor`/M1.4's token-refresh tests (pre-existing, M1 milestone) |
 * | Device revoked mid-session: blocking screen, drafts retained | `DeviceRevokedScreen`/`SessionInvalidationNotifier` (M1.6); drafts are untouched Room rows, unaffected by any auth event |
 * | Mobile access disabled: blocking screen naming the reason | `MobileAccessDisabledScreen` (M1.6) |
 * | Network lost mid-count: status bar OFFLINE, counting continues | `com.sellernest.poreceiving.data.local.OfflineBehaviorAuditTest` (M5.6) |
 * | Duplicate submission: one receipt, never two | `SubmitDraftUseCaseTest`'s idempotency tests; `replayed: true` still maps to exactly one [com.sellernest.poreceiving.data.local.entities.QueuedSubmissionEntity] row (§9.4, M5.4) |
 * | PO received by someone else while counting | [submittingAgainstAPoThatClosedMidCountNamesTheStatusAndRetainsTheDraft] below |
 * | Serial count mismatch: required/captured both shown, blocked locally | `ReviewSubmitViewModelTest`'s serial-count-mismatch test; `SerialCaptureUiState.canFinish` |
 * | Duplicate serial: conflicting value shown, rejected server-side too | `SerialCaptureViewModelTest`'s duplicate test; `DraftSerialDao`'s unique index |
 * | Bin from another warehouse: names the real warehouse | `BinConfirmationViewModelTest`'s cross-warehouse test |
 * | Photo upload fails: queued/retried independently, never blocks the receipt | `com.sellernest.poreceiving.work.photo.UploadPhotoUseCaseTest` |
 */
class ErrorCatalogueAuditTest {

    private lateinit var server: MockWebServer
    private lateinit var apiService: ApiService
    private lateinit var json: Json
    private lateinit var draftDao: FakeDraftDao
    private lateinit var queuedSubmissionDao: FakeQueuedSubmissionDao
    private lateinit var draftRepository: DraftRepository
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
        val draftLineDao = FakeDraftLineDao()
        draftRepository = DraftRepository(draftDao, draftLineDao, FakeDraftPhotoDao(), FakeDraftSerialDao(draftLineDao), queuedSubmissionDao)

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

    /**
     * §10: "PO received by someone else while counting: Submission still
     * posts (quantities are additive). If the PO closed, the failure names
     * the PO status and the draft is retained." A closed-PO rejection is
     * just another [com.sellernest.poreceiving.network.ApiResult.HttpError] --
     * this proves the specific server error text survives verbatim to the
     * submissions screen, and that the draft is never discarded or altered
     * by a failed submit.
     */
    @Test
    fun submittingAgainstAPoThatClosedMidCountNamesTheStatusAndRetainsTheDraft() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(409).setBody(
                """{"error": "po_closed", "detail": "PO-10482 is already CLOSED and can no longer receive stock."}""",
            ),
        )
        val useCase = SubmitDraftUseCase(draftRepository, apiService, json, FakePhotoUploadScheduler())

        val outcome = useCase.execute(draftId)

        assertEquals(SubmitDraftOutcome.Failure, outcome)
        assertTrue(
            queuedSubmissionDao.getForDraft(draftId)?.lastError.orEmpty().contains("PO-10482 is already CLOSED"),
        )
        // §10: "the draft is retained" -- still QUEUED (not discarded), lines
        // and every other field untouched, exactly as any other terminal
        // /receive/ failure leaves it (M5.5's EDIT & RESUBMIT / DISCARD).
        assertEquals(DraftState.QUEUED, draftDao.getById(draftId)?.state)
        assertEquals(1, draftRepository.observeLines(draftId).first().size)
    }
}

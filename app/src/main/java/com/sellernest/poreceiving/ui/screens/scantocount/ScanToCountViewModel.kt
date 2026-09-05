package com.sellernest.poreceiving.ui.screens.scantocount

import androidx.lifecycle.SavedStateHandle
import com.sellernest.poreceiving.core.mvvm.BaseViewModel
import com.sellernest.poreceiving.data.local.DraftRepository
import com.sellernest.poreceiving.network.ApiResult
import com.sellernest.poreceiving.network.ApiService
import com.sellernest.poreceiving.network.dto.ScanResolution
import com.sellernest.poreceiving.scan.ScanFeedbackService
import com.sellernest.poreceiving.scan.ScanSource
import com.sellernest.poreceiving.scan.resolve.resolveScanAndSignalOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * §7.5, the primary screen. §6.1/§6.2: this ViewModel never fetches, caches,
 * or derives an expected quantity -- [com.sellernest.poreceiving.network.dto.PurchaseOrderLine]
 * (the type the resolved PO detail is made of) structurally cannot carry one,
 * and [ScanResolution.Matched.line] is a [com.sellernest.poreceiving.network.dto.ScanMatchedLine],
 * equally blind-safe by construction.
 */
@HiltViewModel
class ScanToCountViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val apiService: ApiService,
    private val json: Json,
    private val scanFeedbackService: ScanFeedbackService,
    private val draftRepository: DraftRepository,
) : BaseViewModel<ScanToCountUiState, ScanToCountUiEvent>(ScanToCountUiState()) {

    private val draftId: Long = checkNotNull(savedStateHandle["draftId"])
    private var purchaseOrderId: Long? = null

    init {
        updateState { it.copy(draftId = draftId) }
        scope.launch {
            val draft = draftRepository.getDraft(draftId)
            purchaseOrderId = draft?.purchaseOrderId
            updateState { it.copy(purchaseOrderNumber = draft?.purchaseOrderNumber) }
            draftRepository.observeLines(draftId).collectLatest { lines ->
                updateState {
                    it.copy(
                        loading = false,
                        lines = lines,
                        totalCountedQuantity = lines.sumOf { line -> line.countedQuantity },
                    )
                }
            }
        }
    }

    override fun onEvent(event: ScanToCountUiEvent) {
        when (event) {
            is ScanToCountUiEvent.ScanReceived -> scope.launch { resolveAndHandle(event.code) }
            is ScanToCountUiEvent.CandidateLineSelected -> scope.launch { selectCandidate(event.purchaseOrderItemId) }
            ScanToCountUiEvent.ScanAgainTapped -> updateState { it.copy(lastScanOutcome = null) }
            ScanToCountUiEvent.OpenCorrectPoTapped -> updateState {
                it.copy(lastScanOutcome = null, navigateToWorkQueue = true)
            }

            ScanToCountUiEvent.ManualSkuEntryRequested -> updateState { it.copy(manualSkuEntryOpen = true) }
            is ScanToCountUiEvent.ManualSkuSubmitted -> scope.launch {
                updateState { it.copy(manualSkuEntryOpen = false) }
                resolveAndHandle(event.sku)
            }
            ScanToCountUiEvent.ManualSkuEntryDismissed -> updateState { it.copy(manualSkuEntryOpen = false) }

            is ScanToCountUiEvent.QuantityIncremented -> scope.launch { adjustQuantity(event.purchaseOrderItemId, +1) }
            is ScanToCountUiEvent.QuantityDecremented -> scope.launch { adjustQuantity(event.purchaseOrderItemId, -1) }
            is ScanToCountUiEvent.ManualQuantityEntryRequested ->
                updateState { it.copy(manualQuantityEntryForLineId = event.purchaseOrderItemId) }
            is ScanToCountUiEvent.ManualQuantitySubmitted -> scope.launch {
                draftRepository.setLineQuantity(draftId, event.purchaseOrderItemId, event.quantity)
                updateState { it.copy(manualQuantityEntryForLineId = null) }
            }
            ScanToCountUiEvent.ManualQuantityEntryDismissed -> updateState { it.copy(manualQuantityEntryForLineId = null) }

            ScanToCountUiEvent.CameraOpened -> updateState { it.copy(cameraOpen = true) }
            ScanToCountUiEvent.CameraClosed -> updateState { it.copy(cameraOpen = false) }
            ScanToCountUiEvent.CommitCountTapped -> scope.launch { commitCount() }
            ScanToCountUiEvent.NavigationHandled ->
                updateState { it.copy(navigateToReconcileDraftId = null, navigateToWorkQueue = false) }
        }
    }

    private suspend fun resolveAndHandle(code: String) {
        val poId = purchaseOrderId ?: return
        val result = resolveScanAndSignalOutcome(apiService, json, scanFeedbackService, poId, code)
        when (result) {
            is ApiResult.Success -> {
                updateState { it.copy(lastScanOutcome = result.body, errorMessage = null) }
                (result.body as? ScanResolution.Matched)?.let { draftRepository.recordScan(draftId, it.line) }
            }
            else -> updateState { it.copy(errorMessage = "Couldn't resolve that scan. Check your connection.") }
        }
    }

    private suspend fun selectCandidate(purchaseOrderItemId: Long) {
        val candidates = (currentState.lastScanOutcome as? ScanResolution.MultipleMatches)?.lines ?: return
        val chosen = candidates.firstOrNull { it.purchaseOrderItemId == purchaseOrderItemId } ?: return
        draftRepository.recordScan(draftId, chosen)
        updateState { it.copy(lastScanOutcome = ScanResolution.Matched(matchedField = "manual selection", line = chosen)) }
    }

    private suspend fun adjustQuantity(purchaseOrderItemId: Long, delta: Int) {
        val current = currentState.lines.firstOrNull { it.purchaseOrderItemId == purchaseOrderItemId }?.countedQuantity ?: 0
        draftRepository.setLineQuantity(draftId, purchaseOrderItemId, current + delta)
    }

    private suspend fun commitCount() {
        draftRepository.commitCount(draftId)
        updateState { it.copy(navigateToReconcileDraftId = draftId) }
    }
}

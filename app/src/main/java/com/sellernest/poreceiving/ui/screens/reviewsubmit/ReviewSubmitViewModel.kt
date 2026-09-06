package com.sellernest.poreceiving.ui.screens.reviewsubmit

import androidx.lifecycle.SavedStateHandle
import com.sellernest.poreceiving.core.mvvm.BaseViewModel
import com.sellernest.poreceiving.data.local.DraftRepository
import com.sellernest.poreceiving.data.local.entities.DraftLineEntity
import com.sellernest.poreceiving.network.ApiResult
import com.sellernest.poreceiving.network.ApiService
import com.sellernest.poreceiving.network.dto.ReconcileRequest
import com.sellernest.poreceiving.network.dto.ReconcileRequestLine
import com.sellernest.poreceiving.network.safeApiCall
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * §7.11, §9.4. [missingQuantity][ReviewSubmitUiState.missingQuantity] is
 * re-derived by posting the current counts to `/reconcile/` again -- the same
 * endpoint [com.sellernest.poreceiving.ui.screens.reconcile.ReconcileViewModel]
 * uses -- rather than persisting an expected quantity onto the draft locally.
 * The §9.4 receive payload itself (M5.2, not built here) is assembled from
 * local counts alone; this call exists only to display a number this screen
 * has no other way to know.
 */
@HiltViewModel
class ReviewSubmitViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val apiService: ApiService,
    private val json: Json,
    private val draftRepository: DraftRepository,
) : BaseViewModel<ReviewSubmitUiState, ReviewSubmitUiEvent>(ReviewSubmitUiState()) {

    private val draftId: Long = checkNotNull(savedStateHandle["draftId"])
    private var warehouseEnforcesBins = false
    private var varianceItemIdsMissingReason: Set<Long> = emptySet()

    init {
        scope.launch { load() }
    }

    override fun onEvent(event: ReviewSubmitUiEvent) {
        when (event) {
            is ReviewSubmitUiEvent.NotesChanged -> {
                updateState { it.copy(notes = event.notes) }
                scope.launch { draftRepository.setNotes(draftId, event.notes.ifBlank { null }) }
            }
            ReviewSubmitUiEvent.ReviewVariancesTapped -> updateState { it.copy(showVarianceDetail = true) }
            ReviewSubmitUiEvent.VarianceDetailDismissed -> updateState { it.copy(showVarianceDetail = false) }
            ReviewSubmitUiEvent.SubmitTapped -> scope.launch { validateAndSubmit() }
            ReviewSubmitUiEvent.SaveAndExitTapped -> updateState { it.copy(savedAndExited = true) }
            ReviewSubmitUiEvent.BlockingMessageDismissed -> updateState { it.copy(blockingMessage = null) }
        }
    }

    private suspend fun load() {
        val draft = draftRepository.getDraft(draftId) ?: return
        val lines = draftRepository.observeLines(draftId).first()

        updateState {
            it.copy(
                purchaseOrderNumber = draft.purchaseOrderNumber,
                notes = draft.notes.orEmpty(),
                lineCount = lines.size,
                goodQuantity = lines.sumOf { line -> line.countedQuantity - line.damagedQuantity },
                damagedQuantity = lines.sumOf { line -> line.damagedQuantity },
                photoCount = draftRepository.observePhotos(draftId).first().size,
                serialCount = draftRepository.getTotalSerialCount(draftId),
            )
        }

        val detailResult = safeApiCall(json) { apiService.getPurchaseOrderDetail(draft.purchaseOrderId) }
        if (detailResult is ApiResult.Success) {
            warehouseEnforcesBins = detailResult.body.warehouse.enforceBins
            updateState { it.copy(warehouseName = detailResult.body.warehouse.name) }
        }

        draft.binId?.let { binId ->
            val binsResult = safeApiCall(json) { apiService.getBins(warehouseId = draft.warehouseId) }
            val label = (binsResult as? ApiResult.Success)?.body?.results?.firstOrNull { it.id == binId }?.label
            updateState { it.copy(binLabel = label) }
        }

        val reconcileRequest = ReconcileRequest(lines = lines.map { ReconcileRequestLine(it.purchaseOrderItemId, it.countedQuantity) })
        val reconcileResult = safeApiCall(json) { apiService.reconcile(draft.purchaseOrderId, reconcileRequest) }
        if (reconcileResult is ApiResult.Success) {
            val varianceLines = reconcileResult.body.lines.filter { line -> line.delta != 0 }
            varianceItemIdsMissingReason = varianceLines
                .map { it.purchaseOrderItemId }
                .filterTo(mutableSetOf()) { itemId ->
                    lines.firstOrNull { line -> line.purchaseOrderItemId == itemId }?.varianceReasonId == null
                }
            updateState {
                it.copy(
                    missingQuantity = varianceLines.sumOf { line -> if (line.delta < 0) -line.delta else 0 },
                    varianceLines = varianceLines,
                )
            }
        }

        updateState { it.copy(loading = false) }
    }

    /**
     * M5.1: "Blocks submit on any local precondition failure ... a specific
     * message for each." Checked in a fixed order so the receiver always
     * sees one concrete, actionable reason rather than a generic "can't
     * submit" -- the missing-reason check reuses [varianceItemIdsMissingReason]
     * from [load] rather than re-deriving it, since re-deriving would mean a
     * second `/reconcile/` call for no new information.
     */
    private suspend fun validateAndSubmit() {
        if (varianceItemIdsMissingReason.isNotEmpty()) {
            updateState { it.copy(blockingMessage = "${varianceItemIdsMissingReason.size} line(s) still need a variance reason.") }
            return
        }

        val lines = draftRepository.observeLines(draftId).first()
        val mismatched = serialMismatchLines(lines)
        if (mismatched.isNotEmpty()) {
            updateState { it.copy(blockingMessage = "${mismatched.size} line(s) have a serial count mismatch.") }
            return
        }

        if (warehouseEnforcesBins && draftRepository.getDraft(draftId)?.binId == null) {
            updateState { it.copy(blockingMessage = "Select a destination bin before submitting.") }
            return
        }

        draftRepository.queueForSubmission(draftId)
        updateState { it.copy(submitted = true) }
    }

    private suspend fun serialMismatchLines(lines: List<DraftLineEntity>): List<DraftLineEntity> =
        lines.filter { it.requiresSerialNumber }
            .filter { line -> draftRepository.observeSerials(line.id).first().size != line.countedQuantity }
}

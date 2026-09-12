package com.sellernest.poreceiving.ui.screens.reconcile

import androidx.lifecycle.SavedStateHandle
import com.sellernest.poreceiving.core.mvvm.BaseViewModel
import com.sellernest.poreceiving.data.local.DraftRepository
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
 * §7.7, §6.1, §6.3. Fetches expected quantities exactly once, on entry --
 * this ViewModel is only ever constructed by navigating here via
 * [DraftRepository.commitCount], the sole transition into RECONCILE.
 */
@HiltViewModel
class ReconcileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val apiService: ApiService,
    private val json: Json,
    private val draftRepository: DraftRepository,
) : BaseViewModel<ReconcileUiState, ReconcileUiEvent>(ReconcileUiState()) {

    private val draftId: Long = checkNotNull(savedStateHandle["draftId"])
    private var warehouseEnforcesBins = false

    init {
        scope.launch { loadReconciliation() }
    }

    override fun onEvent(event: ReconcileUiEvent) {
        when (event) {
            is ReconcileUiEvent.ReasonSelected -> {
                updateState {
                    it.copy(selectedReasonIdByLine = it.selectedReasonIdByLine + (event.purchaseOrderItemId to event.reasonId))
                }
                persistReason(event.purchaseOrderItemId)
            }

            is ReconcileUiEvent.NoteChanged -> {
                updateState { it.copy(noteByLine = it.noteByLine + (event.purchaseOrderItemId to event.note)) }
                persistReason(event.purchaseOrderItemId)
            }

            ReconcileUiEvent.ContinueTapped -> scope.launch {
                if (currentState.canContinue) {
                    draftRepository.completeReconciliation(draftId)
                    // M4.6: "With enforce_bins: false, the screen is skipped" --
                    // skipped by never navigating to it in the first place.
                    if (warehouseEnforcesBins) {
                        updateState { it.copy(navigateToBinConfirmationDraftId = draftId) }
                    } else {
                        updateState { it.copy(navigateToReviewDraftId = draftId) }
                    }
                }
            }

            ReconcileUiEvent.RetryRequested -> scope.launch {
                updateState { it.copy(loading = true, errorMessage = null) }
                loadReconciliation()
            }

            ReconcileUiEvent.NavigationHandled ->
                updateState { it.copy(navigateToBinConfirmationDraftId = null, navigateToReviewDraftId = null) }
        }
    }

    private fun persistReason(purchaseOrderItemId: Long) {
        scope.launch {
            draftRepository.setVarianceReason(
                draftId,
                purchaseOrderItemId,
                currentState.selectedReasonIdByLine[purchaseOrderItemId],
                currentState.noteByLine[purchaseOrderItemId],
            )
        }
    }

    private suspend fun loadReconciliation() {
        val draft = draftRepository.getDraft(draftId) ?: return
        updateState { it.copy(purchaseOrderNumber = draft.purchaseOrderNumber) }

        val lines = draftRepository.observeLines(draftId).first()
        updateState { it.copy(requiresSerialByItem = lines.associate { line -> line.purchaseOrderItemId to line.requiresSerialNumber }) }

        val detailResult = safeApiCall(json) { apiService.getPurchaseOrderDetail(draft.purchaseOrderId) }
        if (detailResult is ApiResult.Success) {
            warehouseEnforcesBins = detailResult.body.warehouse.enforceBins
        }

        val request = ReconcileRequest(
            lines = lines.map { ReconcileRequestLine(it.purchaseOrderItemId, it.countedQuantity) },
        )
        val result = safeApiCall(json) { apiService.reconcile(draft.purchaseOrderId, request) }

        when (result) {
            is ApiResult.Success -> updateState {
                it.copy(
                    loading = false,
                    // §7.7: "Variances first, matches collapsed below."
                    lines = result.body.lines.sortedByDescending { line -> line.delta != 0 },
                    varianceReasons = result.body.varianceReasons,
                )
            }
            else -> updateState { it.copy(loading = false, errorMessage = "Couldn't load reconciliation. Try again.") }
        }
    }
}

package com.sellernest.poreceiving.ui.screens.binconfirmation

import androidx.lifecycle.SavedStateHandle
import com.sellernest.poreceiving.core.mvvm.BaseViewModel
import com.sellernest.poreceiving.data.local.DraftRepository
import com.sellernest.poreceiving.network.ApiResult
import com.sellernest.poreceiving.network.ApiService
import com.sellernest.poreceiving.network.dto.BinRef
import com.sellernest.poreceiving.network.dto.PagedResponse
import com.sellernest.poreceiving.network.safeApiCall
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

/** §7.10, §10. */
@HiltViewModel
class BinConfirmationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val apiService: ApiService,
    private val json: Json,
    private val draftRepository: DraftRepository,
) : BaseViewModel<BinConfirmationUiState, BinConfirmationUiEvent>(BinConfirmationUiState()) {

    private val draftId: Long = checkNotNull(savedStateHandle["draftId"])
    private var warehouseId: Long? = null

    init {
        scope.launch { load() }
    }

    override fun onEvent(event: BinConfirmationUiEvent) {
        when (event) {
            is BinConfirmationUiEvent.BinScanned -> scope.launch { handleScan(event.code) }
            is BinConfirmationUiEvent.BinSelected -> selectBin(event.bin)
            BinConfirmationUiEvent.ContinueTapped -> scope.launch { continueTapped() }
            BinConfirmationUiEvent.NavigationHandled -> updateState { it.copy(navigateToReviewDraftId = null) }
        }
    }

    private suspend fun load() {
        val draft = draftRepository.getDraft(draftId) ?: return
        warehouseId = draft.warehouseId
        updateState { it.copy(purchaseOrderNumber = draft.purchaseOrderNumber) }

        val result = safeApiCall(json) { apiService.getBins(warehouseId = draft.warehouseId) }
        val suggested = (result as? ApiResult.Success)?.body?.results.orEmpty().filter { it.isDefaultReceivingBin }
        updateState { it.copy(loading = false, suggestedBins = suggested) }
    }

    /**
     * §10: "Scanning a bin belonging to another warehouse is rejected with a
     * clear message naming the scanned bin's actual warehouse." Searches the
     * active warehouse first; only if that comes up empty does it search
     * unscoped, purely to give a specific reason (the bin's real warehouse)
     * rather than a generic "not found" -- the same two-step shape
     * [com.sellernest.poreceiving.ui.screens.workqueue.WorkQueueViewModel]'s
     * PO-barcode scan already uses.
     */
    private suspend fun handleScan(code: String) {
        val whId = warehouseId ?: return
        val scopedResult = safeApiCall(json) { apiService.getBins(warehouseId = whId, search = code) }
        val scopedMatch = scopedResult.exactMatch(code)
        if (scopedMatch != null) {
            selectBin(scopedMatch)
            return
        }

        val unscopedResult = safeApiCall(json) { apiService.getBins(search = code) }
        val unscopedMatch = unscopedResult.exactMatch(code)

        val message = when {
            unscopedMatch != null -> "\"$code\" belongs to ${unscopedMatch.warehouseName}, not your active warehouse."
            scopedResult !is ApiResult.Success && unscopedResult !is ApiResult.Success ->
                "Couldn't look up \"$code\". Check your connection and try again."
            else -> "\"$code\" isn't a known bin."
        }
        updateState { it.copy(scanMessage = message) }
    }

    private fun ApiResult<PagedResponse<BinRef>>.exactMatch(code: String): BinRef? =
        (this as? ApiResult.Success)?.body?.results?.firstOrNull { it.label.equals(code, ignoreCase = true) }

    private fun selectBin(bin: BinRef) {
        updateState { it.copy(selectedBin = bin, scanMessage = null) }
    }

    private suspend fun continueTapped() {
        val bin = currentState.selectedBin ?: return
        draftRepository.setBin(draftId, bin.id)
        updateState { it.copy(navigateToReviewDraftId = draftId) }
    }
}

package com.sellernest.poreceiving.ui.screens.workqueue

import com.sellernest.poreceiving.core.mvvm.BaseViewModel
import com.sellernest.poreceiving.network.ApiResult
import com.sellernest.poreceiving.network.ApiService
import com.sellernest.poreceiving.network.dto.PagedResponse
import com.sellernest.poreceiving.network.dto.PurchaseOrderSummary
import com.sellernest.poreceiving.network.safeApiCall
import com.sellernest.poreceiving.session.MeRepository
import com.sellernest.poreceiving.session.WarehouseSelectionStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

/** §7.3, §9.1. */
@HiltViewModel
class WorkQueueViewModel @Inject constructor(
    private val apiService: ApiService,
    private val json: Json,
    private val meRepository: MeRepository,
    private val warehouseSelectionStorage: WarehouseSelectionStorage,
) : BaseViewModel<WorkQueueUiState, WorkQueueUiEvent>(WorkQueueUiState()) {

    init {
        scope.launch {
            if (meRepository.state.value.permissions?.canReceive == false) {
                updateState { it.copy(loading = false, missingPermission = true) }
            } else {
                refresh()
            }
        }
    }

    override fun onEvent(event: WorkQueueUiEvent) {
        when (event) {
            is WorkQueueUiEvent.SearchQueryChanged -> {
                updateState { it.copy(searchQuery = event.query) }
                scope.launch { refresh() }
            }

            WorkQueueUiEvent.RefreshRequested -> scope.launch { refresh() }

            is WorkQueueUiEvent.PoBarcodeScanned -> scope.launch { handlePoBarcodeScan(event.code) }
            WorkQueueUiEvent.NavigationHandled -> updateState { it.copy(navigateToPoId = null) }
            WorkQueueUiEvent.ScanMessageDismissed -> updateState { it.copy(scanMessage = null) }
        }
    }

    private suspend fun refresh() {
        updateState { it.copy(loading = true, errorMessage = null) }

        val warehouseId = warehouseSelectionStorage.current()?.warehouseId
        val result = safeApiCall(json) {
            apiService.getWorkQueue(
                warehouseId = warehouseId,
                search = currentState.searchQuery.trim().takeIf { it.isNotEmpty() },
            )
        }

        when (result) {
            is ApiResult.Success -> updateState {
                it.copy(loading = false, results = result.body.results.oldestDueFirst(), errorMessage = null)
            }
            // §1.7 acceptance criterion: "Network loss shows an actionable
            // message and leaves the last-loaded list visible" -- `results` is
            // deliberately not touched here.
            else -> updateState {
                it.copy(loading = false, errorMessage = "Couldn't refresh. Check your connection and try again.")
            }
        }
    }

    /**
     * M2.7: "Exactly one match opens the PO header immediately. No match, or
     * a match outside the receiver's permitted warehouses, shows an
     * explanatory message naming the scanned value -- never a silent no-op."
     * Searches the active warehouse first; only if that comes up empty does
     * it search without a warehouse filter, purely to give a specific reason
     * (the PO's real warehouse) rather than a generic "not found."
     */
    private suspend fun handlePoBarcodeScan(code: String) {
        val warehouseId = warehouseSelectionStorage.current()?.warehouseId
        val scopedResult = safeApiCall(json) { apiService.getWorkQueue(warehouseId = warehouseId, search = code) }
        val scopedMatch = scopedResult.exactMatch(code)

        if (scopedMatch != null) {
            updateState { it.copy(navigateToPoId = scopedMatch.id, scanMessage = null) }
            return
        }

        val unscopedResult = safeApiCall(json) { apiService.getWorkQueue(search = code) }
        val unscopedMatch = unscopedResult.exactMatch(code)

        val message = when {
            unscopedMatch != null ->
                "$code belongs to ${unscopedMatch.warehouse.name}, not your active warehouse."

            scopedResult !is ApiResult.Success && unscopedResult !is ApiResult.Success ->
                "Couldn't look up \"$code\". Check your connection and try again."

            else -> "No receivable PO found matching \"$code\"."
        }
        updateState { it.copy(scanMessage = message) }
    }

    private fun ApiResult<PagedResponse<PurchaseOrderSummary>>.exactMatch(code: String): PurchaseOrderSummary? =
        (this as? ApiResult.Success)?.body?.results?.firstOrNull { it.number.equals(code, ignoreCase = true) }

    /** §7.3: "Oldest-due first." Sorted client-side so the ordering holds
     *  regardless of what order the server happens to return results in;
     *  POs with no delivery date sort last. */
    private fun List<PurchaseOrderSummary>.oldestDueFirst(): List<PurchaseOrderSummary> =
        sortedWith(compareBy(nullsLast()) { it.expectedDeliveryDate })
}

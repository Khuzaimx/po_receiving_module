package com.sellernest.poreceiving.ui.screens.workqueue

import com.sellernest.poreceiving.core.mvvm.BaseViewModel
import com.sellernest.poreceiving.network.ApiResult
import com.sellernest.poreceiving.network.ApiService
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

    /** §7.3: "Oldest-due first." Sorted client-side so the ordering holds
     *  regardless of what order the server happens to return results in;
     *  POs with no delivery date sort last. */
    private fun List<PurchaseOrderSummary>.oldestDueFirst(): List<PurchaseOrderSummary> =
        sortedWith(compareBy(nullsLast()) { it.expectedDeliveryDate })
}

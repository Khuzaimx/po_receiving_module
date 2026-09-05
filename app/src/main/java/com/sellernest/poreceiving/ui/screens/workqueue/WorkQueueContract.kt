package com.sellernest.poreceiving.ui.screens.workqueue

import com.sellernest.poreceiving.core.mvvm.UiEvent
import com.sellernest.poreceiving.core.mvvm.UiState
import com.sellernest.poreceiving.network.dto.PurchaseOrderSummary

data class WorkQueueUiState(
    val loading: Boolean = true,
    val results: List<PurchaseOrderSummary> = emptyList(),
    val searchQuery: String = "",
    val errorMessage: String? = null,
    /** §5.3: "Lacks receiving permission -> ...the work queue shows an
     *  explanatory empty state naming the missing permission." Set once at
     *  load from already-known session data; no network call is attempted
     *  when this is true. */
    val missingPermission: Boolean = false,
) : UiState

sealed interface WorkQueueUiEvent : UiEvent {
    data class SearchQueryChanged(val query: String) : WorkQueueUiEvent
    data object RefreshRequested : WorkQueueUiEvent
}

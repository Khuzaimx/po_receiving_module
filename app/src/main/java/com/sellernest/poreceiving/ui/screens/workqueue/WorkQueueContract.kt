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
    /** M2.7: a scanned PO barcode that didn't resolve to an openable PO in
     *  this warehouse -- names why (wrong warehouse, unknown, or a lookup
     *  failure), never a silent no-op. */
    val scanMessage: String? = null,
    /** One-shot: the id to navigate to once a scanned PO barcode resolves to
     *  exactly one PO. Consumed then reset by the screen. */
    val navigateToPoId: Long? = null,
) : UiState

sealed interface WorkQueueUiEvent : UiEvent {
    data class SearchQueryChanged(val query: String) : WorkQueueUiEvent
    data object RefreshRequested : WorkQueueUiEvent

    /** §7.3/M2.7: a PO barcode scanned while this screen owns scan focus. */
    data class PoBarcodeScanned(val code: String) : WorkQueueUiEvent
    data object NavigationHandled : WorkQueueUiEvent
    data object ScanMessageDismissed : WorkQueueUiEvent
}

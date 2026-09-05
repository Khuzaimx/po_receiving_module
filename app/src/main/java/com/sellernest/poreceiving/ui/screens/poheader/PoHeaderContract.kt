package com.sellernest.poreceiving.ui.screens.poheader

import com.sellernest.poreceiving.core.mvvm.UiEvent
import com.sellernest.poreceiving.core.mvvm.UiState
import com.sellernest.poreceiving.network.dto.PurchaseOrderDetail

data class PoHeaderUiState(
    val loading: Boolean = true,
    val detail: PurchaseOrderDetail? = null,
    /** §7.4/§8: "Connect to load PO-xxxxx" -- distinct from a generic error so
     *  the receiver knows exactly what to do. Non-null only for this specific
     *  offline-open case. */
    val offlineMessage: String? = null,
    /** Any other failure (5xx, malformed response, etc.). */
    val errorMessage: String? = null,
    /** Null when no local draft exists for this PO/warehouse; otherwise the
     *  total counted quantity to show on "RESUME DRAFT (n scanned)". */
    val existingDraftScannedCount: Int? = null,
) : UiState

sealed interface PoHeaderUiEvent : UiEvent {
    data object RetryRequested : PoHeaderUiEvent
}

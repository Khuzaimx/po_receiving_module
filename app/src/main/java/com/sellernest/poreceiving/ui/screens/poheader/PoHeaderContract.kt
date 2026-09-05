package com.sellernest.poreceiving.ui.screens.poheader

import com.sellernest.poreceiving.core.mvvm.UiEvent
import com.sellernest.poreceiving.core.mvvm.UiState
import com.sellernest.poreceiving.data.local.entities.DraftState
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
    /** Null when no local draft exists for this PO/warehouse. */
    val existingDraftId: Long? = null,
    val existingDraftState: DraftState? = null,
    /** Total counted quantity, for "RESUME DRAFT (n scanned)". */
    val existingDraftScannedCount: Int? = null,
    /** One-shot: consumed then reset by the screen once it navigates. */
    val navigateToDraftId: Long? = null,
    val showDiscardConfirmation: Boolean = false,
) : UiState {

    /**
     * §6.3: PO_OPEN is the only state "abandon" (-> DISCARDED) is a legal
     * transition from -- once counting has begun, discard is no longer
     * offered here (see [com.sellernest.poreceiving.data.local.DraftStateMachine]).
     */
    val canDiscardExistingDraft: Boolean get() = existingDraftState == DraftState.PO_OPEN
}

sealed interface PoHeaderUiEvent : UiEvent {
    data object RetryRequested : PoHeaderUiEvent
    data object StartReceivingTapped : PoHeaderUiEvent
    data object ResumeDraftTapped : PoHeaderUiEvent
    data object DiscardRequested : PoHeaderUiEvent
    data object DiscardConfirmed : PoHeaderUiEvent
    data object DiscardCancelled : PoHeaderUiEvent
    data object NavigationHandled : PoHeaderUiEvent
}

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
    /** Which screen [navigateToDraftId] should be opened on -- RESUME DRAFT
     *  must land wherever the draft actually left off, not always Scan-to-Count
     *  (see [PoHeaderViewModel.resumeDraft]'s doc for why that matters). */
    val navigationTarget: PoHeaderNavigationTarget? = null,
    val showDiscardConfirmation: Boolean = false,
) : UiState {

    /**
     * §6.3: PO_OPEN is the only state "abandon" (-> DISCARDED) is a legal
     * transition from -- once counting has begun, discard is no longer
     * offered here (see [com.sellernest.poreceiving.data.local.DraftStateMachine]).
     */
    val canDiscardExistingDraft: Boolean get() = existingDraftState == DraftState.PO_OPEN
}

/** Where a draft's [DraftState] routes it: everything up to and including
 *  COUNTING is still being counted (Scan-to-Count); RECONCILE/VARIANCE_CAPTURE
 *  are mid variance-reason capture (Reconcile); REVIEW is ready for the final
 *  screen; QUEUED has already been handed to the submit worker, so there is
 *  nothing left to resume counting -- the Submission Queue screen shows its
 *  status instead. RECEIPTED/DISCARDED are terminal and never offered as a
 *  resumable draft in the first place (see DraftDao.getActiveDraftFor). */
enum class PoHeaderNavigationTarget {
    SCAN_TO_COUNT,
    RECONCILE,
    REVIEW,
    SUBMISSION_QUEUE,
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

package com.sellernest.poreceiving.ui.screens.reconcile

import com.sellernest.poreceiving.core.mvvm.UiEvent
import com.sellernest.poreceiving.core.mvvm.UiState
import com.sellernest.poreceiving.network.dto.ReconcileResponseLine
import com.sellernest.poreceiving.network.dto.VarianceReason

/** §7.7. This is the *only* screen expected quantities ever reach the device
 *  on -- reached only via [com.sellernest.poreceiving.data.local.DraftRepository.commitCount],
 *  and this ViewModel fetches them itself, once, on entry. */
data class ReconcileUiState(
    val loading: Boolean = true,
    val purchaseOrderNumber: String? = null,
    val lines: List<ReconcileResponseLine> = emptyList(),
    val varianceReasons: List<VarianceReason> = emptyList(),
    val selectedReasonIdByLine: Map<Long, Long> = emptyMap(),
    val noteByLine: Map<Long, String> = emptyMap(),
    /** M4.5: which lines to show a SERIALS action for, from the locally-known
     *  [com.sellernest.poreceiving.data.local.entities.DraftLineEntity.requiresSerialNumber] --
     *  not part of the reconcile response itself. */
    val requiresSerialByItem: Map<Long, Boolean> = emptyMap(),
    val errorMessage: String? = null,
    /** One-shot: CONTINUE routes here when the warehouse enforces bins (M4.6). */
    val navigateToBinConfirmationDraftId: Long? = null,
    /** One-shot: CONTINUE routes here otherwise. */
    val navigateToReviewDraftId: Long? = null,
) : UiState {
    /** §7.7: "Variances first, matches collapsed below." */
    val varianceLines: List<ReconcileResponseLine> get() = lines.filter { it.delta != 0 }
    val matchingLines: List<ReconcileResponseLine> get() = lines.filter { it.delta == 0 }

    val blockedOverReceiptLines: List<ReconcileResponseLine>
        get() = varianceLines.filter { it.isOverReceipt && !it.overReceiptPermitted }

    val allReasonsChosen: Boolean
        get() = varianceLines.all { selectedReasonIdByLine.containsKey(it.purchaseOrderItemId) }

    /** §7.7/§10: "CONTINUE stays disabled until every variance has a reason.
     *  If the receiver lacks over-receipt permission... the count must be
     *  corrected or the line dropped." A failed load leaves [lines] empty, which
     *  would otherwise vacuously satisfy "every variance has a reason" -- errorMessage
     *  must be null too, or a network failure silently lets CONTINUE through with
     *  zero variances captured. */
    val canContinue: Boolean get() = !loading && errorMessage == null && allReasonsChosen && blockedOverReceiptLines.isEmpty()
}

sealed interface ReconcileUiEvent : UiEvent {
    data class ReasonSelected(val purchaseOrderItemId: Long, val reasonId: Long) : ReconcileUiEvent
    data class NoteChanged(val purchaseOrderItemId: Long, val note: String) : ReconcileUiEvent
    data object ContinueTapped : ReconcileUiEvent
    data object RetryRequested : ReconcileUiEvent
    data object NavigationHandled : ReconcileUiEvent
}

package com.sellernest.poreceiving.ui.screens.reviewsubmit

import com.sellernest.poreceiving.core.mvvm.UiEvent
import com.sellernest.poreceiving.core.mvvm.UiState
import com.sellernest.poreceiving.network.dto.ReconcileResponseLine

/**
 * §7.11: "The last chance to review before stock moves." [missingQuantity]
 * is the one figure here derived from an expected quantity, and is null
 * (rendered as "—", never zero) when it couldn't be re-derived -- e.g. no
 * connection -- so a receiver never mistakes "unknown" for "nothing missing".
 */
data class ReviewSubmitUiState(
    val loading: Boolean = true,
    val purchaseOrderNumber: String? = null,
    val warehouseName: String? = null,
    val binLabel: String? = null,
    val lineCount: Int = 0,
    val goodQuantity: Int = 0,
    val damagedQuantity: Int = 0,
    val missingQuantity: Int? = null,
    val photoCount: Int = 0,
    val serialCount: Int = 0,
    val varianceLines: List<ReconcileResponseLine> = emptyList(),
    val notes: String = "",
    val showVarianceDetail: Boolean = false,
    val blockingMessage: String? = null,
    val submitted: Boolean = false,
    val savedAndExited: Boolean = false,
    /** §9.4: the `/reconcile/` call this screen re-derives [missingQuantity] and
     *  its variance-reason check from failed -- SUBMIT must stay blocked rather
     *  than silently trusting a zero/empty result, see [ReviewSubmitViewModel]. */
    val reconcileUnavailable: Boolean = false,
) : UiState

sealed interface ReviewSubmitUiEvent : UiEvent {
    data class NotesChanged(val notes: String) : ReviewSubmitUiEvent
    data object ReviewVariancesTapped : ReviewSubmitUiEvent
    data object VarianceDetailDismissed : ReviewSubmitUiEvent
    data object SubmitTapped : ReviewSubmitUiEvent
    data object SaveAndExitTapped : ReviewSubmitUiEvent
    data object BlockingMessageDismissed : ReviewSubmitUiEvent
    data object RetryReconcileTapped : ReviewSubmitUiEvent
}

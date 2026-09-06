package com.sellernest.poreceiving.ui.screens.binconfirmation

import com.sellernest.poreceiving.core.mvvm.UiEvent
import com.sellernest.poreceiving.core.mvvm.UiState
import com.sellernest.poreceiving.network.dto.BinRef

/**
 * §7.10. This screen is only ever navigated to when the warehouse enforces
 * bins (M4.6: "with enforce_bins: false, the screen is skipped") -- the skip
 * itself is [com.sellernest.poreceiving.ui.screens.reconcile.ReconcileViewModel]'s
 * job, at the point it decides where CONTINUE goes, not this screen's.
 */
data class BinConfirmationUiState(
    val loading: Boolean = true,
    val purchaseOrderNumber: String? = null,
    val suggestedBins: List<BinRef> = emptyList(),
    val selectedBin: BinRef? = null,
    val scanMessage: String? = null,
    val navigateToReviewDraftId: Long? = null,
) : UiState {
    val canContinue: Boolean get() = selectedBin != null
}

sealed interface BinConfirmationUiEvent : UiEvent {
    data class BinScanned(val code: String) : BinConfirmationUiEvent
    data class BinSelected(val bin: BinRef) : BinConfirmationUiEvent
    data object ContinueTapped : BinConfirmationUiEvent
    data object NavigationHandled : BinConfirmationUiEvent
}

package com.sellernest.poreceiving.ui.screens.receipthistory

import com.sellernest.poreceiving.core.mvvm.UiEvent
import com.sellernest.poreceiving.core.mvvm.UiState
import com.sellernest.poreceiving.network.dto.ReceiptSummary

/** §7.13, §9.6: own receipts, today by default. */
data class ReceiptHistoryUiState(
    val loading: Boolean = true,
    val receipts: List<ReceiptSummary> = emptyList(),
    val errorMessage: String? = null,
    /** Non-null while the void-reason dialog for this receipt is showing. */
    val voidTarget: ReceiptSummary? = null,
    val voidReason: String = "",
    /** §7.13: "States plainly that a supervisor must void it" -- shown
     *  whether the window was already expired at render time, or expired
     *  between render and tap (the 403's verbatim body). */
    val voidBlockedMessage: String? = null,
) : UiState

sealed interface ReceiptHistoryUiEvent : UiEvent {
    data object RefreshRequested : ReceiptHistoryUiEvent
    data class VoidRequested(val receipt: ReceiptSummary) : ReceiptHistoryUiEvent
    data class VoidReasonChanged(val reason: String) : ReceiptHistoryUiEvent
    data object VoidConfirmed : ReceiptHistoryUiEvent
    data object VoidCancelled : ReceiptHistoryUiEvent
    data object VoidBlockedMessageDismissed : ReceiptHistoryUiEvent
}

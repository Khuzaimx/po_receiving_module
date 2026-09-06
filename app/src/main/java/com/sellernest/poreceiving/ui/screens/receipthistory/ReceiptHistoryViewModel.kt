package com.sellernest.poreceiving.ui.screens.receipthistory

import com.sellernest.poreceiving.core.mvvm.BaseViewModel
import com.sellernest.poreceiving.network.ApiResult
import com.sellernest.poreceiving.network.ApiService
import com.sellernest.poreceiving.network.dto.ReceiptSummary
import com.sellernest.poreceiving.network.dto.VoidRequest
import com.sellernest.poreceiving.network.safeApiCall
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.Instant
import javax.inject.Inject

/**
 * §7.13, §9.6. `GET /receipts/` already scopes to "own receipts" server-side
 * (§9.6: the endpoint's whole reason for keying off the signed-in session) --
 * this ViewModel never filters by user itself, since there is no other
 * receiver's receipt in the response to filter out.
 */
@HiltViewModel
class ReceiptHistoryViewModel @Inject constructor(
    private val apiService: ApiService,
    private val json: Json,
) : BaseViewModel<ReceiptHistoryUiState, ReceiptHistoryUiEvent>(ReceiptHistoryUiState()) {

    init {
        scope.launch { refresh() }
    }

    override fun onEvent(event: ReceiptHistoryUiEvent) {
        when (event) {
            ReceiptHistoryUiEvent.RefreshRequested -> scope.launch { refresh() }
            is ReceiptHistoryUiEvent.VoidRequested -> requestVoid(event.receipt)
            is ReceiptHistoryUiEvent.VoidReasonChanged -> updateState { it.copy(voidReason = event.reason) }
            ReceiptHistoryUiEvent.VoidConfirmed -> scope.launch { confirmVoid() }
            ReceiptHistoryUiEvent.VoidCancelled -> updateState { it.copy(voidTarget = null, voidReason = "") }
            ReceiptHistoryUiEvent.VoidBlockedMessageDismissed -> updateState { it.copy(voidBlockedMessage = null) }
        }
    }

    private suspend fun refresh() {
        updateState { it.copy(loading = true, errorMessage = null) }
        when (val result = safeApiCall(json) { apiService.getReceipts() }) {
            is ApiResult.Success -> updateState { it.copy(loading = false, receipts = result.body.results) }
            else -> updateState { it.copy(loading = false, errorMessage = "Couldn't load receipts. Try again.") }
        }
    }

    /**
     * §7.13: "Outside the window, the app states plainly that a supervisor
     * must void it on the web -- it does not offer a dead button." Checked
     * pre-emptively so VOID never even opens the reason dialog past
     * [ReceiptSummary.voidAvailableUntil].
     */
    private fun requestVoid(receipt: ReceiptSummary) {
        if (isVoidWindowExpired(receipt)) {
            updateState { it.copy(voidBlockedMessage = "Void window expired. Ask a supervisor to void this receipt.") }
            return
        }
        updateState { it.copy(voidTarget = receipt, voidReason = "") }
    }

    private suspend fun confirmVoid() {
        val target = currentState.voidTarget ?: return
        if (currentState.voidReason.isBlank()) return

        when (val result = safeApiCall(json) { apiService.voidReceipt(target.id, VoidRequest(currentState.voidReason)) }) {
            is ApiResult.Success -> updateState {
                it.copy(
                    voidTarget = null,
                    voidReason = "",
                    receipts = it.receipts.map { r -> if (r.id == target.id) r.copy(isVoided = true) else r },
                )
            }

            is ApiResult.HttpError -> updateState {
                // §9.6: the 403 body surfaced verbatim if the window closed
                // between render and tap.
                it.copy(
                    voidTarget = null,
                    voidBlockedMessage = result.error?.detail
                        ?: result.error?.error
                        ?: "Void window expired. Ask a supervisor to void this receipt.",
                )
            }

            else -> updateState {
                it.copy(voidTarget = null, voidBlockedMessage = "Couldn't void this receipt. Check your connection and try again.")
            }
        }
    }

    private fun isVoidWindowExpired(receipt: ReceiptSummary): Boolean {
        val until = receipt.voidAvailableUntil ?: return true
        return runCatching { Instant.parse(until) }.getOrNull()?.isBefore(Instant.now()) ?: true
    }
}

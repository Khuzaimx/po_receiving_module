package com.sellernest.poreceiving.ui.screens.poheader

import androidx.lifecycle.SavedStateHandle
import com.sellernest.poreceiving.core.mvvm.BaseViewModel
import com.sellernest.poreceiving.data.local.DraftRepository
import com.sellernest.poreceiving.data.local.dao.DraftDao
import com.sellernest.poreceiving.data.local.dao.DraftLineDao
import com.sellernest.poreceiving.data.local.entities.DraftState
import com.sellernest.poreceiving.network.ApiResult
import com.sellernest.poreceiving.network.ApiService
import com.sellernest.poreceiving.network.safeApiCall
import com.sellernest.poreceiving.session.WarehouseSelectionStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

/** §7.4, §9.2, §6.2. */
@HiltViewModel
class PoHeaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val apiService: ApiService,
    private val json: Json,
    private val draftDao: DraftDao,
    private val draftLineDao: DraftLineDao,
    private val draftRepository: DraftRepository,
    private val warehouseSelectionStorage: WarehouseSelectionStorage,
) : BaseViewModel<PoHeaderUiState, PoHeaderUiEvent>(PoHeaderUiState()) {

    private val purchaseOrderId: Long = checkNotNull(savedStateHandle["poId"])

    init {
        scope.launch { load() }
    }

    override fun onEvent(event: PoHeaderUiEvent) {
        when (event) {
            PoHeaderUiEvent.RetryRequested -> scope.launch { load() }
            PoHeaderUiEvent.StartReceivingTapped -> scope.launch { startReceiving() }
            PoHeaderUiEvent.ResumeDraftTapped -> resumeDraft()
            PoHeaderUiEvent.DiscardRequested -> updateState { it.copy(showDiscardConfirmation = true) }
            PoHeaderUiEvent.DiscardCancelled -> updateState { it.copy(showDiscardConfirmation = false) }
            PoHeaderUiEvent.DiscardConfirmed -> scope.launch { discard() }
            PoHeaderUiEvent.NavigationHandled -> updateState { it.copy(navigateToDraftId = null, navigationTarget = null) }
        }
    }

    private suspend fun load() {
        updateState { it.copy(loading = true, offlineMessage = null, errorMessage = null) }

        val result = safeApiCall(json) { apiService.getPurchaseOrderDetail(purchaseOrderId) }
        when (result) {
            is ApiResult.Success -> {
                val warehouseId = warehouseSelectionStorage.current()?.warehouseId
                val existingDraft = warehouseId?.let { draftDao.getActiveDraftFor(purchaseOrderId, it) }
                val scannedCount = existingDraft?.let { draftLineDao.getTotalCountedQuantity(it.id) }

                updateState {
                    it.copy(
                        loading = false,
                        detail = result.body,
                        existingDraftId = existingDraft?.id,
                        existingDraftState = existingDraft?.state,
                        existingDraftScannedCount = scannedCount,
                    )
                }
            }

            // §8: "Open a PO while offline: Fails clearly ('Connect to load
            // PO-10482'). POs are not pre-downloaded."
            is ApiResult.NetworkError ->
                updateState { it.copy(loading = false, offlineMessage = "Connect to load PO #$purchaseOrderId") }

            else ->
                updateState { it.copy(loading = false, errorMessage = "Couldn't load this PO. Try again.") }
        }
    }

    private suspend fun startReceiving() {
        val detail = currentState.detail ?: return
        val warehouseId = warehouseSelectionStorage.current()?.warehouseId ?: return
        val draft = draftRepository.openPurchaseOrder(detail.id, detail.number, warehouseId)
        // existingDraftId/existingDraftState must reflect this draft immediately,
        // not stay at whatever load() found (typically null, on a fresh PO) --
        // otherwise canDiscardExistingDraft stays false and DISCARD DRAFT never
        // appears until the screen happens to reload from scratch.
        updateState {
            it.copy(
                navigateToDraftId = draft.id,
                navigationTarget = PoHeaderNavigationTarget.SCAN_TO_COUNT,
                existingDraftId = draft.id,
                existingDraftState = draft.state,
            )
        }
    }

    /**
     * Routes to wherever the draft actually left off, not always Scan-to-Count:
     * a draft that already reached RECONCILE/REVIEW/QUEUED must reopen on that
     * same screen. Landing it on Scan-to-Count instead would let the receiver
     * silently mutate a count that reconciliation has already computed deltas
     * and reasons against, and tapping COMMIT COUNT again would then hit an
     * illegal-transition crash in [DraftRepository.transition] (RECONCILE has
     * no self-loop in [com.sellernest.poreceiving.data.local.DraftStateMachine]).
     */
    private fun resumeDraft() {
        val draftId = currentState.existingDraftId ?: return
        val target = when (currentState.existingDraftState) {
            DraftState.PO_OPEN, DraftState.COUNTING, null -> PoHeaderNavigationTarget.SCAN_TO_COUNT
            DraftState.RECONCILE, DraftState.VARIANCE_CAPTURE -> PoHeaderNavigationTarget.RECONCILE
            DraftState.REVIEW -> PoHeaderNavigationTarget.REVIEW
            DraftState.QUEUED -> PoHeaderNavigationTarget.SUBMISSION_QUEUE
            // Never offered as a resumable draft in the first place (excluded
            // by DraftDao.getActiveDraftFor) -- nothing sane to navigate to.
            DraftState.RECEIPTED, DraftState.DISCARDED -> return
        }
        updateState { it.copy(navigateToDraftId = draftId, navigationTarget = target) }
    }

    /** §6.3/M3.7: "Explicit abandon from PO_OPEN transitions to DISCARDED
     *  and requires a confirmation naming the PO" -- the naming happens in
     *  the dialog itself (see PoHeaderScreen); this only performs the
     *  transition once the receiver has confirmed. */
    private suspend fun discard() {
        val draftId = currentState.existingDraftId ?: return
        draftRepository.discard(draftId)
        updateState {
            it.copy(
                showDiscardConfirmation = false,
                existingDraftId = null,
                existingDraftState = null,
                existingDraftScannedCount = null,
            )
        }
    }
}

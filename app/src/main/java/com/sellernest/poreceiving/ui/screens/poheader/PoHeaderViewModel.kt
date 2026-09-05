package com.sellernest.poreceiving.ui.screens.poheader

import androidx.lifecycle.SavedStateHandle
import com.sellernest.poreceiving.core.mvvm.BaseViewModel
import com.sellernest.poreceiving.data.local.dao.DraftDao
import com.sellernest.poreceiving.data.local.dao.DraftLineDao
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
    private val warehouseSelectionStorage: WarehouseSelectionStorage,
) : BaseViewModel<PoHeaderUiState, PoHeaderUiEvent>(PoHeaderUiState()) {

    private val purchaseOrderId: Long = checkNotNull(savedStateHandle["poId"])

    init {
        scope.launch { load() }
    }

    override fun onEvent(event: PoHeaderUiEvent) {
        when (event) {
            PoHeaderUiEvent.RetryRequested -> scope.launch { load() }
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
}

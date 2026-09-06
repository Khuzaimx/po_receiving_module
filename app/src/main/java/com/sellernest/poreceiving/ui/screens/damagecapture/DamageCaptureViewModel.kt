package com.sellernest.poreceiving.ui.screens.damagecapture

import androidx.lifecycle.SavedStateHandle
import com.sellernest.poreceiving.core.mvvm.BaseViewModel
import com.sellernest.poreceiving.data.local.DraftRepository
import com.sellernest.poreceiving.network.ApiResult
import com.sellernest.poreceiving.network.ApiService
import com.sellernest.poreceiving.network.dto.ReconcileRequest
import com.sellernest.poreceiving.network.dto.ReconcileRequestLine
import com.sellernest.poreceiving.network.safeApiCall
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * §7.8. The reason/note fields are the same [com.sellernest.poreceiving.data.local.entities.DraftLineEntity]
 * columns Reconcile (M4.2) edits -- a line's variance reason has one value,
 * however many screens can set it. The reason list itself is fetched the same
 * way [com.sellernest.poreceiving.ui.screens.reconcile.ReconcileViewModel]
 * fetches it (posting current counts to `/reconcile/`), so this screen doesn't
 * need its own list endpoint or to smuggle the list through nav args.
 */
@HiltViewModel
class DamageCaptureViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val apiService: ApiService,
    private val json: Json,
    private val draftRepository: DraftRepository,
) : BaseViewModel<DamageCaptureUiState, DamageCaptureUiEvent>(DamageCaptureUiState()) {

    private val draftId: Long = checkNotNull(savedStateHandle["draftId"])
    private val purchaseOrderItemId: Long = checkNotNull(savedStateHandle["purchaseOrderItemId"])

    init {
        scope.launch {
            combine(
                draftRepository.observeLines(draftId),
                draftRepository.observePhotos(draftId),
            ) { lines, photos ->
                lines.firstOrNull { it.purchaseOrderItemId == purchaseOrderItemId } to photos
            }.collectLatest { (line, photos) ->
                if (line != null) {
                    updateState {
                        it.copy(
                            loading = false,
                            sku = line.sku,
                            name = line.name,
                            countedQuantity = line.countedQuantity,
                            damagedQuantity = line.damagedQuantity,
                            varianceReasonId = line.varianceReasonId,
                            note = line.varianceNote.orEmpty(),
                            photos = photos.filter { photo -> photo.draftLineId == line.id },
                        )
                    }
                }
            }
        }
        scope.launch { loadReasons() }
    }

    override fun onEvent(event: DamageCaptureUiEvent) {
        when (event) {
            DamageCaptureUiEvent.DamagedQuantityIncremented -> setDamaged(currentState.damagedQuantity + 1)
            DamageCaptureUiEvent.DamagedQuantityDecremented -> setDamaged(currentState.damagedQuantity - 1)

            is DamageCaptureUiEvent.ReasonSelected -> {
                updateState { it.copy(varianceReasonId = event.reasonId) }
                persistReason()
            }
            is DamageCaptureUiEvent.NoteChanged -> {
                updateState { it.copy(note = event.note) }
                persistReason()
            }

            DamageCaptureUiEvent.CameraOpened -> updateState { it.copy(cameraOpen = true) }
            DamageCaptureUiEvent.CameraClosed -> updateState { it.copy(cameraOpen = false) }
            is DamageCaptureUiEvent.PhotoCaptured -> scope.launch {
                updateState { it.copy(cameraOpen = false) }
                draftRepository.addPhoto(draftId, purchaseOrderItemId, event.localFilePath)
            }
            is DamageCaptureUiEvent.PhotoRemoved -> scope.launch { draftRepository.removePhoto(event.photo) }
        }
    }

    private fun setDamaged(quantity: Int) = scope.launch {
        draftRepository.setDamagedQuantity(draftId, purchaseOrderItemId, quantity)
    }

    private fun persistReason() = scope.launch {
        draftRepository.setVarianceReason(
            draftId,
            purchaseOrderItemId,
            currentState.varianceReasonId,
            currentState.note.ifBlank { null },
        )
    }

    private suspend fun loadReasons() {
        val draft = draftRepository.getDraft(draftId) ?: return
        val lines = draftRepository.observeLines(draftId).first()
        val request = ReconcileRequest(lines = lines.map { ReconcileRequestLine(it.purchaseOrderItemId, it.countedQuantity) })
        val result = safeApiCall(json) { apiService.reconcile(draft.purchaseOrderId, request) }
        if (result is ApiResult.Success) {
            updateState { it.copy(reasons = result.body.varianceReasons) }
        }
    }
}

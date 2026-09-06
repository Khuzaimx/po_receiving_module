package com.sellernest.poreceiving.ui.screens.serialcapture

import androidx.lifecycle.SavedStateHandle
import com.sellernest.poreceiving.core.mvvm.BaseViewModel
import com.sellernest.poreceiving.data.local.DraftRepository
import com.sellernest.poreceiving.data.local.entities.DraftSerialEntity
import com.sellernest.poreceiving.scan.ScanFeedbackService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/** §7.9, §10. */
@HiltViewModel
class SerialCaptureViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val scanFeedbackService: ScanFeedbackService,
    private val draftRepository: DraftRepository,
) : BaseViewModel<SerialCaptureUiState, SerialCaptureUiEvent>(SerialCaptureUiState()) {

    private val draftId: Long = checkNotNull(savedStateHandle["draftId"])
    private val purchaseOrderItemId: Long = checkNotNull(savedStateHandle["purchaseOrderItemId"])
    private var draftLineId: Long? = null

    init {
        scope.launch {
            draftRepository.observeLines(draftId)
                .map { lines -> lines.firstOrNull { it.purchaseOrderItemId == purchaseOrderItemId } }
                .flatMapLatest { line ->
                    if (line == null) {
                        flowOf(null to emptyList<DraftSerialEntity>())
                    } else {
                        draftRepository.observeSerials(line.id).map { serials -> line to serials }
                    }
                }
                .collectLatest { (line, serials) ->
                    if (line != null) {
                        draftLineId = line.id
                        updateState {
                            it.copy(loading = false, sku = line.sku, requiredCount = line.countedQuantity, serials = serials)
                        }
                    }
                }
        }
    }

    override fun onEvent(event: SerialCaptureUiEvent) {
        when (event) {
            is SerialCaptureUiEvent.SerialScanned -> scope.launch { handleScan(event.code) }
            is SerialCaptureUiEvent.SerialRemoved -> scope.launch { draftRepository.removeSerial(event.serial) }
            SerialCaptureUiEvent.DuplicateDismissed -> updateState { it.copy(duplicateAttempt = null) }
            SerialCaptureUiEvent.DoneTapped -> if (currentState.canFinish) updateState { it.copy(navigateBack = true) }
            SerialCaptureUiEvent.NavigationHandled -> updateState { it.copy(navigateBack = false) }
        }
    }

    /**
     * §7.9: "Duplicate serials are rejected locally on scan, with the
     * conflicting value shown," and once every required slot is filled there
     * is nowhere left for another scan to go -- both reject the same way a
     * genuinely unmatched PO scan does (§9.3), via [ScanFeedbackService.rejected].
     */
    private suspend fun handleScan(code: String) {
        val lineId = draftLineId ?: return
        if (currentState.capturedCount >= currentState.requiredCount) {
            scanFeedbackService.rejected()
            return
        }
        when (val result = draftRepository.addSerial(lineId, code)) {
            is DraftRepository.AddSerialResult.Added -> scanFeedbackService.accepted()
            is DraftRepository.AddSerialResult.Duplicate -> {
                scanFeedbackService.rejected()
                updateState { it.copy(duplicateAttempt = result.conflictingValue) }
            }
        }
    }
}

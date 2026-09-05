package com.sellernest.poreceiving.ui.screens.scantocount

import com.sellernest.poreceiving.core.mvvm.UiEvent
import com.sellernest.poreceiving.core.mvvm.UiState
import com.sellernest.poreceiving.data.local.entities.DraftLineEntity
import com.sellernest.poreceiving.network.dto.ScanResolution
import com.sellernest.poreceiving.scan.ScanSource

/**
 * §7.5, the primary screen. Deliberately absent from this type: any field
 * that could represent an *expected* quantity, a ratio, or a percentage --
 * see this package's grep-level "no progress bar" guardrail test. Only
 * [totalCountedQuantity] (a sum of what was actually counted) and per-line
 * [DraftLineEntity.countedQuantity] appear here, both blind-safe by
 * construction (M3.1).
 */
data class ScanToCountUiState(
    val draftId: Long = 0,
    val purchaseOrderNumber: String? = null,
    val loading: Boolean = true,
    val lines: List<DraftLineEntity> = emptyList(),
    val totalCountedQuantity: Int = 0,
    val lastScanOutcome: ScanResolution? = null,
    val manualSkuEntryOpen: Boolean = false,
    val manualQuantityEntryForLineId: Long? = null,
    val cameraOpen: Boolean = false,
    val errorMessage: String? = null,
    /** One-shot: consumed then reset by the screen. */
    val navigateToReconcileDraftId: Long? = null,
    val navigateToWorkQueue: Boolean = false,
) : UiState

sealed interface ScanToCountUiEvent : UiEvent {
    data class ScanReceived(val code: String, val source: ScanSource) : ScanToCountUiEvent
    data class CandidateLineSelected(val purchaseOrderItemId: Long) : ScanToCountUiEvent
    data object ScanAgainTapped : ScanToCountUiEvent
    data object OpenCorrectPoTapped : ScanToCountUiEvent

    data object ManualSkuEntryRequested : ScanToCountUiEvent
    data class ManualSkuSubmitted(val sku: String) : ScanToCountUiEvent
    data object ManualSkuEntryDismissed : ScanToCountUiEvent

    data class QuantityIncremented(val purchaseOrderItemId: Long) : ScanToCountUiEvent
    data class QuantityDecremented(val purchaseOrderItemId: Long) : ScanToCountUiEvent
    data class ManualQuantityEntryRequested(val purchaseOrderItemId: Long) : ScanToCountUiEvent
    data class ManualQuantitySubmitted(val purchaseOrderItemId: Long, val quantity: Int) : ScanToCountUiEvent
    data object ManualQuantityEntryDismissed : ScanToCountUiEvent

    data object CameraOpened : ScanToCountUiEvent
    data object CameraClosed : ScanToCountUiEvent
    data object CommitCountTapped : ScanToCountUiEvent
    data object NavigationHandled : ScanToCountUiEvent
}

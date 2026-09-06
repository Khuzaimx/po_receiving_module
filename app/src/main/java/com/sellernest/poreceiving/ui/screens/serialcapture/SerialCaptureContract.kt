package com.sellernest.poreceiving.ui.screens.serialcapture

import com.sellernest.poreceiving.core.mvvm.UiEvent
import com.sellernest.poreceiving.core.mvvm.UiState
import com.sellernest.poreceiving.data.local.entities.DraftSerialEntity
import com.sellernest.poreceiving.scan.ScanSource

/**
 * §7.9. [requiredCount] is the counted quantity already committed for this
 * line (M3.4) -- not an expected quantity -- so showing "3 of 4" here reveals
 * nothing the receiver doesn't already know (§7.9: "Serial capture is not blind").
 */
data class SerialCaptureUiState(
    val loading: Boolean = true,
    val sku: String? = null,
    val requiredCount: Int = 0,
    val serials: List<DraftSerialEntity> = emptyList(),
    /** The conflicting value from the most recent rejected duplicate scan. */
    val duplicateAttempt: String? = null,
    val navigateBack: Boolean = false,
) : UiState {
    val capturedCount: Int get() = serials.size
    val canFinish: Boolean get() = requiredCount > 0 && capturedCount == requiredCount
}

sealed interface SerialCaptureUiEvent : UiEvent {
    data class SerialScanned(val code: String, val source: ScanSource) : SerialCaptureUiEvent
    data class SerialRemoved(val serial: DraftSerialEntity) : SerialCaptureUiEvent
    data object DuplicateDismissed : SerialCaptureUiEvent
    data object DoneTapped : SerialCaptureUiEvent
    data object NavigationHandled : SerialCaptureUiEvent
}

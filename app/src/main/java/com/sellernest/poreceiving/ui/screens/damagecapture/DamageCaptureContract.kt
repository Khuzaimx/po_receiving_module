package com.sellernest.poreceiving.ui.screens.damagecapture

import com.sellernest.poreceiving.core.mvvm.UiEvent
import com.sellernest.poreceiving.core.mvvm.UiState
import com.sellernest.poreceiving.data.local.entities.DraftPhotoEntity
import com.sellernest.poreceiving.network.dto.VarianceReason

/**
 * §7.8. [countedQuantity] is what was actually counted (never an expected
 * quantity), so [goodQuantity] is derived purely from locally-known numbers --
 * "GOOD + DAMAGED always equals COUNTED" holds by construction, not by a
 * separate check.
 */
data class DamageCaptureUiState(
    val loading: Boolean = true,
    val sku: String? = null,
    val name: String? = null,
    val countedQuantity: Int = 0,
    val damagedQuantity: Int = 0,
    val varianceReasonId: Long? = null,
    val note: String = "",
    val reasons: List<VarianceReason> = emptyList(),
    val photos: List<DraftPhotoEntity> = emptyList(),
    val cameraOpen: Boolean = false,
) : UiState {
    val goodQuantity: Int get() = countedQuantity - damagedQuantity
}

sealed interface DamageCaptureUiEvent : UiEvent {
    data object DamagedQuantityIncremented : DamageCaptureUiEvent
    data object DamagedQuantityDecremented : DamageCaptureUiEvent
    data class ReasonSelected(val reasonId: Long) : DamageCaptureUiEvent
    data class NoteChanged(val note: String) : DamageCaptureUiEvent
    data object CameraOpened : DamageCaptureUiEvent
    data object CameraClosed : DamageCaptureUiEvent
    data class PhotoCaptured(val localFilePath: String) : DamageCaptureUiEvent
    data class PhotoRemoved(val photo: DraftPhotoEntity) : DamageCaptureUiEvent
}

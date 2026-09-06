package com.sellernest.poreceiving.ui.screens.damagecapture

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sellernest.poreceiving.data.local.entities.DraftPhotoEntity
import com.sellernest.poreceiving.network.dto.VarianceReason
import com.sellernest.poreceiving.scan.camera.PhotoCaptureScreen
import com.sellernest.poreceiving.ui.components.PrimaryButton
import com.sellernest.poreceiving.ui.theme.QuantityTextStyles
import com.sellernest.poreceiving.ui.theme.Spacing
import com.sellernest.poreceiving.ui.theme.TouchTarget

/** §7.8. */
@Composable
fun DamageCaptureScreen(onNavigateBack: () -> Unit, viewModel: DamageCaptureViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    if (state.cameraOpen) {
        PhotoCaptureScreen(
            onCaptured = { localFilePath -> viewModel.onEvent(DamageCaptureUiEvent.PhotoCaptured(localFilePath)) },
            onClose = { viewModel.onEvent(DamageCaptureUiEvent.CameraClosed) },
        )
        return
    }

    DamageCaptureContent(
        state = state,
        onIncrement = { viewModel.onEvent(DamageCaptureUiEvent.DamagedQuantityIncremented) },
        onDecrement = { viewModel.onEvent(DamageCaptureUiEvent.DamagedQuantityDecremented) },
        onReasonSelected = { viewModel.onEvent(DamageCaptureUiEvent.ReasonSelected(it)) },
        onNoteChanged = { viewModel.onEvent(DamageCaptureUiEvent.NoteChanged(it)) },
        onAddPhoto = { viewModel.onEvent(DamageCaptureUiEvent.CameraOpened) },
        onRemovePhoto = { viewModel.onEvent(DamageCaptureUiEvent.PhotoRemoved(it)) },
        onSave = onNavigateBack,
    )
}

@Composable
internal fun DamageCaptureContent(
    state: DamageCaptureUiState,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onReasonSelected: (Long) -> Unit,
    onNoteChanged: (String) -> Unit,
    onAddPhoto: () -> Unit,
    onRemovePhoto: (DraftPhotoEntity) -> Unit,
    onSave: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(Spacing.screenPadding)) {
        Text(text = state.sku.orEmpty(), style = MaterialTheme.typography.headlineLarge)
        Text(text = "COUNTED: ${state.countedQuantity}", style = MaterialTheme.typography.titleLarge)
        Text(text = "Of these, how many are damaged?", style = MaterialTheme.typography.bodyLarge)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.padding(vertical = Spacing.sm),
        ) {
            IconButton(onClick = onDecrement, modifier = Modifier.size(TouchTarget.primary)) {
                Text(text = "−", style = MaterialTheme.typography.headlineLarge)
            }
            Text(text = "${state.damagedQuantity}", style = QuantityTextStyles.quantityDominant)
            IconButton(onClick = onIncrement, modifier = Modifier.size(TouchTarget.primary)) {
                Text(text = "+", style = MaterialTheme.typography.headlineLarge)
            }
        }

        // §7.8: "The good/damaged split is shown explicitly with its
        // consequence spelled out -- damaged units never enter sellable stock."
        Text(text = "GOOD (to sellable stock)          ${state.goodQuantity}", style = MaterialTheme.typography.bodyLarge)
        Text(text = "DAMAGED (not sellable)            ${state.damagedQuantity}", style = MaterialTheme.typography.bodyLarge)

        ReasonDropdown(
            reasons = state.reasons,
            selectedReasonId = state.varianceReasonId,
            onReasonSelected = onReasonSelected,
        )

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
            value = state.note,
            onValueChange = onNoteChanged,
            placeholder = { Text("Note (optional)") },
        )

        Text(text = "PHOTOS (${state.photos.size})", style = MaterialTheme.typography.labelLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(vertical = Spacing.sm)) {
            item {
                IconButton(onClick = onAddPhoto, modifier = Modifier.size(TouchTarget.primary)) {
                    Icon(imageVector = Icons.Filled.AddAPhoto, contentDescription = "Add photo")
                }
            }
            items(items = state.photos, key = { it.id }) { photo ->
                PhotoThumbnail(photo = photo, onRemove = { onRemovePhoto(photo) })
            }
        }

        PrimaryButton(text = "SAVE", onClick = onSave, modifier = Modifier.padding(top = Spacing.sm))
    }
}

@Composable
private fun PhotoThumbnail(photo: DraftPhotoEntity, onRemove: () -> Unit) {
    val bitmap = remember(photo.localFilePath) {
        BitmapFactory.decodeFile(photo.localFilePath, BitmapFactory.Options().apply { inSampleSize = 4 })
    }
    Row {
        bitmap?.let {
            Image(bitmap = it.asImageBitmap(), contentDescription = photo.caption ?: "Photo", modifier = Modifier.size(64.dp))
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(TouchTarget.primary)) {
            Icon(imageVector = Icons.Filled.Close, contentDescription = "Remove photo")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReasonDropdown(reasons: List<VarianceReason>, selectedReasonId: Long?, onReasonSelected: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = reasons.firstOrNull { it.id == selectedReasonId }?.label ?: "None"

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            readOnly = true,
            value = selectedLabel,
            onValueChange = {},
            label = { Text("Reason") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            reasons.forEach { reason ->
                DropdownMenuItem(
                    text = { Text(reason.label) },
                    onClick = {
                        onReasonSelected(reason.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

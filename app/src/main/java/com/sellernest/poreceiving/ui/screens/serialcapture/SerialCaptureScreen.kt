package com.sellernest.poreceiving.ui.screens.serialcapture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.sellernest.poreceiving.data.local.entities.DraftSerialEntity
import com.sellernest.poreceiving.scan.compose.KeyboardWedgeCapture
import com.sellernest.poreceiving.scan.compose.ScanFocusEffect
import com.sellernest.poreceiving.ui.components.PrimaryButton
import com.sellernest.poreceiving.ui.components.StateBadge
import com.sellernest.poreceiving.ui.theme.Spacing
import com.sellernest.poreceiving.ui.theme.StateTone
import com.sellernest.poreceiving.ui.theme.TouchTarget

/** §7.9, §10. Fully operable by hardware trigger (M0.6). */
@Composable
fun SerialCaptureScreen(onNavigateBack: () -> Unit, viewModel: SerialCaptureViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    ScanFocusEffect { code, source -> viewModel.onEvent(SerialCaptureUiEvent.SerialScanned(code, source)) }
    KeyboardWedgeCapture()

    LaunchedEffect(state.navigateBack) {
        if (state.navigateBack) {
            onNavigateBack()
            viewModel.onEvent(SerialCaptureUiEvent.NavigationHandled)
        }
    }

    SerialCaptureContent(
        state = state,
        onRemove = { viewModel.onEvent(SerialCaptureUiEvent.SerialRemoved(it)) },
        onDuplicateDismissed = { viewModel.onEvent(SerialCaptureUiEvent.DuplicateDismissed) },
        onDone = { viewModel.onEvent(SerialCaptureUiEvent.DoneTapped) },
    )
}

@Composable
internal fun SerialCaptureContent(
    state: SerialCaptureUiState,
    onRemove: (DraftSerialEntity) -> Unit,
    onDuplicateDismissed: () -> Unit,
    onDone: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "SERIALS — ${state.sku.orEmpty()}",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(Spacing.screenPadding),
        )
        Text(
            text = "${state.requiredCount} required · ${state.capturedCount} captured",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = Spacing.screenPadding),
        )

        LazyColumn(modifier = Modifier.weight(1f, fill = true).fillMaxWidth()) {
            items(items = state.serials, key = { it.id }) { serial ->
                CapturedSerialRow(index = state.serials.indexOf(serial) + 1, serial = serial, onRemove = { onRemove(serial) })
                HorizontalDivider()
            }
            if (state.capturedCount < state.requiredCount) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(Spacing.screenPadding),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(text = "${state.capturedCount + 1}", style = MaterialTheme.typography.bodyLarge)
                        Text(text = "awaiting scan…", style = MaterialTheme.typography.bodyLarge)
                    }
                    HorizontalDivider()
                }
            }
        }

        StateBadge(
            modifier = Modifier.padding(Spacing.screenPadding),
            tone = StateTone.Pending,
            icon = Icons.Filled.Warning,
            label = "Duplicate serials are rejected",
        )

        PrimaryButton(
            text = "DONE",
            enabled = state.canFinish,
            onClick = onDone,
            modifier = Modifier.padding(Spacing.screenPadding),
        )
    }

    state.duplicateAttempt?.let { conflictingValue ->
        AlertDialog(
            onDismissRequest = onDuplicateDismissed,
            title = { Text("Duplicate serial") },
            text = { Text("\"$conflictingValue\" is already captured for this line.") },
            confirmButton = { TextButton(onClick = onDuplicateDismissed) { Text("OK") } },
        )
    }
}

@Composable
private fun CapturedSerialRow(index: Int, serial: DraftSerialEntity, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(Spacing.screenPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = "$index", style = MaterialTheme.typography.bodyLarge)
        Text(text = serial.serialValue, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(imageVector = Icons.Filled.CheckCircle, contentDescription = "Captured")
        IconButton(onClick = onRemove, modifier = Modifier.size(TouchTarget.primary)) {
            Icon(imageVector = Icons.Filled.Close, contentDescription = "Remove serial")
        }
    }
}

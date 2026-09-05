package com.sellernest.poreceiving.core.mvvm.example

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.sellernest.poreceiving.ui.components.PrimaryButton
import com.sellernest.poreceiving.ui.theme.Spacing

/**
 * Renders [ExampleCounterUiState] and forwards taps as [ExampleCounterUiEvent]s.
 * The Composable holds no state of its own beyond what the ViewModel exposes.
 */
@Composable
fun ExampleCounterScreen(viewModel: ExampleCounterViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "COUNT: ${state.count}")
        state.lastEventLabel?.let { Text(text = "last event: $it") }

        PrimaryButton(text = "INCREMENT", onClick = { viewModel.onEvent(ExampleCounterUiEvent.Increment) })
        PrimaryButton(text = "DECREMENT", onClick = { viewModel.onEvent(ExampleCounterUiEvent.Decrement) })
        PrimaryButton(text = "RESET", onClick = { viewModel.onEvent(ExampleCounterUiEvent.Reset) })
    }
}

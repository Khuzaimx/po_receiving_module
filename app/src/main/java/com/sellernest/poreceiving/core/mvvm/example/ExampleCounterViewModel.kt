package com.sellernest.poreceiving.core.mvvm.example

import com.sellernest.poreceiving.core.mvvm.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ExampleCounterViewModel @Inject constructor() :
    BaseViewModel<ExampleCounterUiState, ExampleCounterUiEvent>(ExampleCounterUiState()) {

    override fun onEvent(event: ExampleCounterUiEvent) {
        when (event) {
            is ExampleCounterUiEvent.Increment -> updateState {
                it.copy(count = it.count + 1, lastEventLabel = "Increment")
            }
            is ExampleCounterUiEvent.Decrement -> updateState {
                it.copy(count = (it.count - 1).coerceAtLeast(0), lastEventLabel = "Decrement")
            }
            is ExampleCounterUiEvent.Reset -> setState(ExampleCounterUiState(lastEventLabel = "Reset"))
        }
    }
}

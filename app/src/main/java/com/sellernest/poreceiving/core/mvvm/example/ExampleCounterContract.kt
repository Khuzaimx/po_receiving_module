package com.sellernest.poreceiving.core.mvvm.example

import com.sellernest.poreceiving.core.mvvm.UiEvent
import com.sellernest.poreceiving.core.mvvm.UiState

/**
 * Worked example of the screen contract convention (see [BaseViewModel][com.sellernest.poreceiving.core.mvvm.BaseViewModel]).
 *
 * This is not a product screen. It exists so every later screen has one concrete,
 * compiling reference for "immutable UiState in, UiEvent out" to copy from — required
 * by M0.1's acceptance criteria. Real counting state lives in the M3 draft/session
 * ViewModel; this mirrors its shape (a running count driven by discrete events)
 * without any of its business rules.
 */
data class ExampleCounterUiState(
    val count: Int = 0,
    val lastEventLabel: String? = null,
) : UiState

sealed interface ExampleCounterUiEvent : UiEvent {
    data object Increment : ExampleCounterUiEvent
    data object Decrement : ExampleCounterUiEvent
    data object Reset : ExampleCounterUiEvent
}

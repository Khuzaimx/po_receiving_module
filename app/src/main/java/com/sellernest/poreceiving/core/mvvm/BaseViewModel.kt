package com.sellernest.poreceiving.core.mvvm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Base for the event-in / state-out convention every screen ViewModel follows.
 *
 * Subclasses implement [onEvent] and mutate state only via [setState] or [updateState].
 * The exposed [state] is a read-only [StateFlow]; there is no public mutable setter,
 * so a Composable cannot bypass the ViewModel to change what it renders.
 */
abstract class BaseViewModel<S : UiState, E : UiEvent>(initialState: S) : ViewModel() {

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state.asStateFlow()

    /** Current state snapshot, for synchronous reads inside event handling. */
    protected val currentState: S
        get() = _state.value

    protected fun setState(newState: S) {
        _state.value = newState
    }

    protected fun updateState(transform: (S) -> S) {
        _state.update(transform)
    }

    /** Entry point for every user action and every scan event on this screen. */
    abstract fun onEvent(event: E)

    protected val scope get() = viewModelScope
}

package com.sellernest.poreceiving.core.mvvm

/**
 * Marker for the single immutable state type a screen renders from.
 *
 * Every screen exposes exactly one `StateFlow<T : UiState>` from its ViewModel.
 * Scans and user actions are [UiEvent]s dispatched into the ViewModel; the view
 * never mutates state directly (requirements §2.1: "One ViewModel per screen
 * exposing an immutable UiState. Scans are events into a state machine, never
 * direct view mutations.").
 */
interface UiState

/** Marker for the events a screen's ViewModel accepts. */
interface UiEvent

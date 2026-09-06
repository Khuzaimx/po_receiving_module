package com.sellernest.poreceiving.data.local

import com.sellernest.poreceiving.data.local.entities.DraftState

/**
 * §6.3's state diagram as an explicit transition table -- a sealed set of
 * legal (from, to) pairs, not a chain of `if`s scattered across call sites.
 * The two properties §6.3 calls mandatory fall directly out of this table:
 * COUNTING only ever leads to itself (the scan loop) or RECONCILE, and
 * RECONCILE has exactly one incoming edge, from COUNTING, which
 * [DraftRepository.commitCount] is the only method that takes.
 */
object DraftStateMachine {

    private val allowedTransitions: Map<DraftState, Set<DraftState>> = mapOf(
        DraftState.PO_OPEN to setOf(DraftState.COUNTING, DraftState.DISCARDED),
        DraftState.COUNTING to setOf(DraftState.COUNTING, DraftState.RECONCILE),
        DraftState.RECONCILE to setOf(DraftState.REVIEW, DraftState.VARIANCE_CAPTURE),
        DraftState.VARIANCE_CAPTURE to setOf(DraftState.REVIEW),
        DraftState.REVIEW to setOf(DraftState.QUEUED),
        // M5.5: a FAILED submission (a QueuedSubmissionEntity status, not a
        // DraftState -- this state machine has no notion of "failed") can
        // still be DISCARDED, with confirmation naming the PO, same as an
        // abandoned PO_OPEN draft.
        DraftState.QUEUED to setOf(DraftState.QUEUED, DraftState.RECEIPTED, DraftState.DISCARDED),
        DraftState.RECEIPTED to emptySet(),
        DraftState.DISCARDED to emptySet(),
    )

    fun isValidTransition(from: DraftState, to: DraftState): Boolean =
        allowedTransitions[from]?.contains(to) == true
}

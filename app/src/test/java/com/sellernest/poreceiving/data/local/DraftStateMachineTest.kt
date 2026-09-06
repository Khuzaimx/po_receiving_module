package com.sellernest.poreceiving.data.local

import com.sellernest.poreceiving.data.local.entities.DraftState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M3.2 acceptance criterion: "A test enumerates every state pair and asserts
 * only the arrows above are permitted." Exhaustively checks all
 * [DraftState] x [DraftState] pairs, not just the ones expected to pass --
 * a state machine bug is exactly as likely to be an accidentally-permitted
 * edge as a missing legitimate one.
 */
class DraftStateMachineTest {

    private val expectedAllowedPairs = setOf(
        DraftState.PO_OPEN to DraftState.COUNTING,
        DraftState.PO_OPEN to DraftState.DISCARDED,
        DraftState.COUNTING to DraftState.COUNTING,
        DraftState.COUNTING to DraftState.RECONCILE,
        DraftState.RECONCILE to DraftState.REVIEW,
        DraftState.RECONCILE to DraftState.VARIANCE_CAPTURE,
        DraftState.VARIANCE_CAPTURE to DraftState.REVIEW,
        DraftState.REVIEW to DraftState.QUEUED,
        DraftState.QUEUED to DraftState.QUEUED,
        DraftState.QUEUED to DraftState.RECEIPTED,
        DraftState.QUEUED to DraftState.DISCARDED,
    )

    @Test
    fun `exactly the diagrammed transitions are allowed, no more, no fewer`() {
        val allPairs = DraftState.entries.flatMap { from -> DraftState.entries.map { to -> from to to } }

        for (pair in allPairs) {
            val (from, to) = pair
            val shouldBeAllowed = pair in expectedAllowedPairs
            assertEquals(
                "Transition $from -> $to: expected allowed=$shouldBeAllowed",
                shouldBeAllowed,
                DraftStateMachine.isValidTransition(from, to),
            )
        }
    }

    @Test
    fun `RECONCILE has exactly one incoming edge, from COUNTING`() {
        val incomingToReconcile = DraftState.entries.filter {
            DraftStateMachine.isValidTransition(it, DraftState.RECONCILE)
        }
        assertEquals(listOf(DraftState.COUNTING), incomingToReconcile)
    }

    @Test
    fun `RECEIPTED and DISCARDED are terminal -- no outgoing transitions at all`() {
        for (terminal in listOf(DraftState.RECEIPTED, DraftState.DISCARDED)) {
            for (candidate in DraftState.entries) {
                assertFalse(
                    "$terminal must have no outgoing transitions, but $terminal -> $candidate was allowed",
                    DraftStateMachine.isValidTransition(terminal, candidate),
                )
            }
        }
    }

    @Test
    fun `COUNTING never transitions directly to REVIEW, QUEUED, or RECEIPTED, skipping RECONCILE`() {
        for (skipped in listOf(DraftState.REVIEW, DraftState.QUEUED, DraftState.RECEIPTED)) {
            assertTrue(
                "COUNTING -> $skipped must not be allowed; RECONCILE must not be skippable",
                !DraftStateMachine.isValidTransition(DraftState.COUNTING, skipped),
            )
        }
    }
}

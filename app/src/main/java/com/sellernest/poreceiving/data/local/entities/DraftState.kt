package com.sellernest.poreceiving.data.local.entities

/**
 * Mirrors the receiving state machine (§6.3). The transition rules themselves —
 * which moves are legal, and that RECONCILE is reachable only via explicit commit
 * — are M3.2's job; this enum only gives the persisted `state` column type safety.
 * `QUEUE` itself is not a draft state: a [DraftEntity] row does not exist until a
 * PO is opened.
 */
enum class DraftState {
    PO_OPEN,
    COUNTING,
    RECONCILE,
    VARIANCE_CAPTURE,
    REVIEW,
    QUEUED,
    RECEIPTED,
    DISCARDED,
}

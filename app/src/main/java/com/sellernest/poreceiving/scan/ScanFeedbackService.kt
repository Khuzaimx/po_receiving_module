package com.sellernest.poreceiving.scan

/**
 * Requirements §3.1 Feedback: "Every accepted scan produces three simultaneous
 * signals: a distinct sound, a haptic pulse, and a visible on-screen change.
 * Rejected scans use a clearly different sound and a longer haptic."
 *
 * This service owns exactly two of those three signals -- sound and haptic. The
 * visual change is deliberately **not** this service's job: it is asserted by
 * the calling screen's own `UiState` (e.g. the MATCHED/NOT_ON_PO/etc. colouring
 * on [com.sellernest.poreceiving.ui.components.StateBadge] in M2.6/M3.3), so a
 * screen cannot satisfy "feedback" by only calling this service and skipping a
 * real state change.
 */
interface ScanFeedbackService {
    /** Call the instant a scan is accepted (MATCHED, or any locally-valid scan). */
    fun accepted()

    /** Call the instant a scan is rejected (unknown code, duplicate, ambiguous, etc.). */
    fun rejected()
}

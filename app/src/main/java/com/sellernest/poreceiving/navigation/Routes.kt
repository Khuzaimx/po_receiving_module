package com.sellernest.poreceiving.navigation

/**
 * Every screen route from requirements §7, plus two developer-only entries used
 * to inspect the M0 design system (§3, §7 numbers are in comments for cross-reference).
 * Routes that operate on a specific PO or line carry that id as a nav argument now,
 * rather than being retrofitted when the real screen lands in a later milestone.
 */
object Routes {
    const val SIGN_IN = "sign_in" // §7.1
    const val WAREHOUSE_SELECTION = "warehouse_selection" // §7.2
    const val WORK_QUEUE = "work_queue" // §7.3

    private const val PO_ID_ARG = "poId"
    private const val LINE_ID_ARG = "lineId"

    const val PO_HEADER = "po_header/{$PO_ID_ARG}" // §7.4
    fun poHeader(poId: Long) = "po_header/$poId"

    const val SCAN_TO_COUNT = "scan_to_count/{$PO_ID_ARG}" // §7.5
    fun scanToCount(poId: Long) = "scan_to_count/$poId"

    const val RECONCILE = "reconcile/{$PO_ID_ARG}" // §7.7
    fun reconcile(poId: Long) = "reconcile/$poId"

    const val DAMAGE_CAPTURE = "damage_capture/{$PO_ID_ARG}/{$LINE_ID_ARG}" // §7.8
    fun damageCapture(poId: Long, lineId: Long) = "damage_capture/$poId/$lineId"

    const val SERIAL_CAPTURE = "serial_capture/{$PO_ID_ARG}/{$LINE_ID_ARG}" // §7.9
    fun serialCapture(poId: Long, lineId: Long) = "serial_capture/$poId/$lineId"

    const val BIN_CONFIRMATION = "bin_confirmation/{$PO_ID_ARG}" // §7.10
    fun binConfirmation(poId: Long) = "bin_confirmation/$poId"

    const val REVIEW_AND_SUBMIT = "review_and_submit/{$PO_ID_ARG}" // §7.11
    fun reviewAndSubmit(poId: Long) = "review_and_submit/$poId"

    const val SUBMISSION_QUEUE = "submission_queue" // §7.12
    const val RECEIPT_HISTORY = "receipt_history" // §7.13

    // Developer-only entries, not part of §7's receiving flow.
    const val COMPONENT_GALLERY = "dev/component_gallery"
    const val EXAMPLE_COUNTER = "dev/example_counter"

    /** Every §7 route, for the "each screen exists as a stub" test in M0.1. */
    val allSpecRoutes = listOf(
        SIGN_IN, WAREHOUSE_SELECTION, WORK_QUEUE, PO_HEADER, SCAN_TO_COUNT,
        RECONCILE, DAMAGE_CAPTURE, SERIAL_CAPTURE, BIN_CONFIRMATION,
        REVIEW_AND_SUBMIT, SUBMISSION_QUEUE, RECEIPT_HISTORY,
    )
}

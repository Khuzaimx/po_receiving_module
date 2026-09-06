package com.sellernest.poreceiving.navigation

/**
 * Every screen route from requirements §7, plus two developer-only entries used
 * to inspect the M0 design system (§3, §7 numbers are in comments for cross-reference).
 * Routes that operate on a specific PO or line carry that id as a nav argument now,
 * rather than being retrofitted when the real screen lands in a later milestone.
 *
 * SCAN_TO_COUNT, RECONCILE, DAMAGE_CAPTURE, SERIAL_CAPTURE, and
 * BIN_CONFIRMATION all carry a *draft* id, not a PO id: every one of them
 * operates on a draft that must already exist (created by
 * [com.sellernest.poreceiving.ui.screens.poheader.PoHeaderScreen] tapping
 * START RECEIVING/RESUME DRAFT), never on the PO directly. DAMAGE_CAPTURE and
 * SERIAL_CAPTURE additionally carry the purchase-order-item id identifying
 * *which* draft line, the same identifier [com.sellernest.poreceiving.data.local.DraftRepository]
 * addresses lines by everywhere else.
 */
object Routes {
    const val SIGN_IN = "sign_in" // §7.1
    const val WAREHOUSE_SELECTION = "warehouse_selection" // §7.2
    const val WORK_QUEUE = "work_queue" // §7.3

    private const val PO_ID_ARG = "poId"
    private const val DRAFT_ID_ARG = "draftId"
    private const val PURCHASE_ORDER_ITEM_ID_ARG = "purchaseOrderItemId"

    const val PO_HEADER = "po_header/{$PO_ID_ARG}" // §7.4
    fun poHeader(poId: Long) = "po_header/$poId"

    const val SCAN_TO_COUNT = "scan_to_count/{$DRAFT_ID_ARG}" // §7.5
    fun scanToCount(draftId: Long) = "scan_to_count/$draftId"

    const val RECONCILE = "reconcile/{$DRAFT_ID_ARG}" // §7.7
    fun reconcile(draftId: Long) = "reconcile/$draftId"

    const val DAMAGE_CAPTURE = "damage_capture/{$DRAFT_ID_ARG}/{$PURCHASE_ORDER_ITEM_ID_ARG}" // §7.8
    fun damageCapture(draftId: Long, purchaseOrderItemId: Long) = "damage_capture/$draftId/$purchaseOrderItemId"

    const val SERIAL_CAPTURE = "serial_capture/{$DRAFT_ID_ARG}/{$PURCHASE_ORDER_ITEM_ID_ARG}" // §7.9
    fun serialCapture(draftId: Long, purchaseOrderItemId: Long) = "serial_capture/$draftId/$purchaseOrderItemId"

    const val BIN_CONFIRMATION = "bin_confirmation/{$DRAFT_ID_ARG}" // §7.10
    fun binConfirmation(draftId: Long) = "bin_confirmation/$draftId"

    const val REVIEW_AND_SUBMIT = "review_and_submit/{$DRAFT_ID_ARG}" // §7.11
    fun reviewAndSubmit(draftId: Long) = "review_and_submit/$draftId"

    const val SUBMISSION_QUEUE = "submission_queue" // §7.12
    const val RECEIPT_HISTORY = "receipt_history" // §7.13

    // §5.3's blocking access gates (M1.6) -- terminal screens, not part of the
    // §7 numbering, but full nav destinations all the same.
    const val MOBILE_ACCESS_DISABLED = "mobile_access_disabled"
    const val DEVICE_REVOKED = "device_revoked"

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

package com.sellernest.poreceiving.network.dto

import kotlinx.serialization.Serializable

/**
 * `POST /purchase-orders/{id}/reconcile/` -- like `/api/me/`, the spec never
 * gives this endpoint an exact contract; it only describes the screen's
 * behaviour (§7.7: "Fetch expected quantities only at this point, and only
 * via the commit transition"). This shape is a design filling that gap: the
 * app posts what was counted, the server is the only place expected
 * quantities and over-receipt permission are computed, and returns both
 * plus the reason list. Confirm against the real backend before this ships.
 */
@Serializable
data class ReconcileRequestLine(
    val purchaseOrderItemId: Long,
    val quantityCounted: Int,
)

@Serializable
data class ReconcileRequest(
    val lines: List<ReconcileRequestLine>,
)

/** [delta] is signed: negative for under-receipt, positive for over-receipt. */
@Serializable
data class ReconcileResponseLine(
    val purchaseOrderItemId: Long,
    val sku: String,
    val name: String,
    val quantityExpected: Int,
    val quantityCounted: Int,
    val delta: Int,
    val isOverReceipt: Boolean,
    /** §7.7/§10: over-receipt without this permission blocks CONTINUE. */
    val overReceiptPermitted: Boolean,
)

@Serializable
data class VarianceReason(
    val id: Long,
    val label: String,
)

@Serializable
data class ReconcileResponse(
    val lines: List<ReconcileResponseLine>,
    val varianceReasons: List<VarianceReason>,
)

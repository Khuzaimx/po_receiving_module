package com.sellernest.poreceiving.network.dto

import kotlinx.serialization.Serializable

/** One line of a §9.4 submit payload. */
@Serializable
data class ReceiveRequestLine(
    val purchaseOrderItemId: Long,
    val quantityReceived: Int,
    val quantityDamaged: Int,
    val quantityMissing: Int,
    val varianceReasonId: Long? = null,
    val varianceNote: String? = null,
    val serials: List<String> = emptyList(),
)

/**
 * §9.4 `POST /purchase-orders/{id}/receive/` request body.
 *
 * `idempotencyKey` must be the value generated once at COUNTING start (M3.6) and
 * reused verbatim on every retry — this type has no mechanism to generate one,
 * by design, so a caller cannot accidentally mint a fresh key per attempt.
 */
@Serializable
data class ReceiveRequest(
    val idempotencyKey: String,
    val notes: String? = null,
    val binId: Long? = null,
    val lines: List<ReceiveRequestLine>,
)

/** A single failed line from a §9.4 response — always paired with its `error` text. */
@Serializable
data class ReceiveLineFailure(
    val purchaseOrderItemId: Long,
    val error: String,
)

/**
 * §9.4 response. `updated` and `failed` can both be non-empty on the same
 * response — "partial failure is normal" — so callers (M5.3) must render both,
 * never collapse this into a single success/failure boolean.
 */
@Serializable
data class ReceiveResponse(
    val receiptId: Long,
    val replayed: Boolean,
    val updated: List<Long>,
    val failed: List<ReceiveLineFailure> = emptyList(),
)

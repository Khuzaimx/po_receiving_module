package com.sellernest.poreceiving.network.dto

import kotlinx.serialization.Serializable

/** §9.6 `GET /receipts/?date=today` list item. */
@Serializable
data class ReceiptSummary(
    val id: Long,
    val purchaseOrderNumber: String,
    val vendorName: String,
    val lineCount: Int,
    val totalUnits: Int,
    val receivedAt: String,
    val isVoided: Boolean,
    val hasVariance: Boolean,
    val voidAvailableUntil: String? = null,
)

/** §9.6 `POST /receipts/{id}/void/` request — void always requires a reason. */
@Serializable
data class VoidRequest(
    val reason: String,
)

/** §9.6 `POST /receipts/{id}/void/` 200 response. */
@Serializable
data class VoidResponse(
    val id: Long,
    val isVoided: Boolean,
)

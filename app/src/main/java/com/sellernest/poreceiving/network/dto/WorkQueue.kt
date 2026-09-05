package com.sellernest.poreceiving.network.dto

import kotlinx.serialization.Serializable

/**
 * §9.1 `GET /purchase-orders/` list item. `totalUnitsExpected` is a **PO-level**
 * total, never a per-line figure, and per §7.4 is rendered only when present —
 * hence nullable, not defaulted to zero.
 */
@Serializable
data class PurchaseOrderSummary(
    val id: Long,
    val number: String,
    val vendorName: String,
    val warehouse: WarehouseRef,
    val lineCount: Int,
    val totalUnitsExpected: Int? = null,
    val status: String,
    val expectedDeliveryDate: String? = null,
)

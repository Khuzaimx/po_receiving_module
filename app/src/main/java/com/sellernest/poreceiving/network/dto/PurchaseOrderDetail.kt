package com.sellernest.poreceiving.network.dto

import kotlinx.serialization.Serializable

/** §9.2 `identifiers` block: any of these may be null for a given SKU. */
@Serializable
data class ItemIdentifiers(
    val upc: String? = null,
    val ean: String? = null,
    val gtin: String? = null,
    val fnsku: String? = null,
)

/**
 * §9.2 PO detail line item.
 *
 * Deliberately absent: `quantity_expected`. The backend omits this key entirely
 * when [PurchaseOrderDetail.blindCount] is true (§6.2: "the backend omits
 * quantity_expected from the payload entirely... The field is absent, not null,
 * not zero"), and this app must never display or hold an expected quantity before
 * commit (§6.1) regardless of mode. Modelling it here — even as a nullable field —
 * would let a future change leak it onto a pre-commit screen. The reveal at
 * RECONCILE (M4.1) is a **separate** response shape fetched only after commit,
 * not a field on this type.
 */
@Serializable
data class PurchaseOrderLine(
    val purchaseOrderItemId: Long,
    val sku: String,
    val name: String,
    val identifiers: ItemIdentifiers,
    val quantityAlreadyReceived: Int,
    val requiresSerialNumber: Boolean,
)

/** §9.2 `GET /purchase-orders/{id}/`. */
@Serializable
data class PurchaseOrderDetail(
    val id: Long,
    val number: String,
    val vendorName: String,
    val warehouse: WarehouseRef,
    val blindCount: Boolean,
    val lines: List<PurchaseOrderLine>,
)

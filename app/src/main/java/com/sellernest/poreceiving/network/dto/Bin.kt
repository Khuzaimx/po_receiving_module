package com.sellernest.poreceiving.network.dto

import kotlinx.serialization.Serializable

/**
 * `GET /bins/` -- like [ReconcileRequest] and `/api/me/`, §9 never gives bin
 * lookup an exact contract; this shape is a gap-filling design. M4.6's cross-
 * warehouse rejection ("names the scanned bin's actual warehouse") and its
 * "SUGGESTED" default bin both require the backend to be the source of truth
 * for which bins exist and which warehouse each belongs to -- an on-device
 * catalog isn't possible. Confirm against the real backend before this ships.
 */
@Serializable
data class BinRef(
    val id: Long,
    val label: String,
    val warehouseId: Long,
    val warehouseName: String,
    val isDefaultReceivingBin: Boolean = false,
)

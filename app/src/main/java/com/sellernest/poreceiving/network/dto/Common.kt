package com.sellernest.poreceiving.network.dto

import kotlinx.serialization.Serializable

/** Generic paginated envelope shared by every listing endpoint in §9 (e.g. §9.1). */
@Serializable
data class PagedResponse<T>(
    val count: Int,
    val next: String?,
    val previous: String?,
    val results: List<T>,
)

/** §9.1, §9.2: `warehouse.enforce_bins` drives the M4.6 bin-confirmation gate. */
@Serializable
data class WarehouseRef(
    val id: Long,
    val name: String,
    val enforceBins: Boolean,
)

/**
 * The generic `{"error": "...", "detail": "..."}` shape used for the 409
 * idempotency conflict (§9.4) and other non-2xx bodies (e.g. the 403 void-window
 * body in §9.6, and the plain-string errors on gates in §5.3). `detail` is present
 * only on some of them, so it stays nullable.
 */
@Serializable
data class ApiErrorBody(
    val error: String,
    val detail: String? = null,
)

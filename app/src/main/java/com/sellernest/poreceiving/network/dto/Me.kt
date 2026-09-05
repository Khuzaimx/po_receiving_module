package com.sellernest.poreceiving.network.dto

import kotlinx.serialization.Serializable

/**
 * `GET /api/me/` response shape.
 *
 * Note on scope: unlike every endpoint in §9, the spec never gives this one an
 * exact JSON contract -- it only says (§5.2) that X-Active-Org is "set from the
 * active company returned by /api/me/", and (§5.3/§7.2) that the response must
 * be enough to drive warehouse-scoped permissions, a default warehouse, and the
 * company/warehouse selector. This shape is a reasonable, self-consistent design
 * filling that gap, not a literal transcription of a backend contract -- confirm
 * against the real `/api/me/` response before this ships.
 */
@Serializable
data class MeResponse(
    val user: MeUser,
    val companies: List<MeCompany>,
)

@Serializable
data class MeUser(
    val id: Long,
    val email: String,
    val displayName: String,
)

/** `externalId` is the value attached as `X-Active-Org` (§5.2, §9 preamble). */
@Serializable
data class MeCompany(
    val externalId: String,
    val name: String,
    val isDefault: Boolean,
    val warehouses: List<MeWarehouse>,
    val permissions: MePermissions,
)

@Serializable
data class MeWarehouse(
    val id: Long,
    val name: String,
    val isDefault: Boolean,
    val enforceBins: Boolean,
)

/** §5.3's access gates and §7.7's over-receipt block both read from here. */
@Serializable
data class MePermissions(
    val canReceive: Boolean,
    val canOverReceive: Boolean,
)

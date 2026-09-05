package com.sellernest.poreceiving.session

import com.sellernest.poreceiving.network.dto.MeCompany
import com.sellernest.poreceiving.network.dto.MePermissions

/**
 * The app-wide session state derived from `/api/me/` (M1.3). `permissions` is
 * the single source of truth the UI reflects for gates like over-receipt
 * (§5.3, §7.7) -- server-side enforcement remains authoritative regardless.
 */
data class MeSessionState(
    val userDisplayName: String? = null,
    val activeCompanyExternalId: String? = null,
    val activeCompanyName: String? = null,
    val activeWarehouseName: String? = null,
    val permissions: MePermissions? = null,
    val companies: List<MeCompany> = emptyList(),
)

package com.sellernest.poreceiving.session

import com.sellernest.poreceiving.network.ActiveOrgProvider
import javax.inject.Inject

/** Replaces the M0.2 `NoOpActiveOrgProvider` stub. */
class MeBackedActiveOrgProvider @Inject constructor(
    private val meRepository: MeRepository,
) : ActiveOrgProvider {
    override suspend fun currentActiveOrgId(): String? = meRepository.state.value.activeCompanyExternalId
}

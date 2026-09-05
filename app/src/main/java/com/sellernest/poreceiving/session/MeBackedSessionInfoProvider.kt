package com.sellernest.poreceiving.session

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Replaces the M0.5 `NoOpSessionInfoProvider` stub. */
class MeBackedSessionInfoProvider @Inject constructor(
    private val meRepository: MeRepository,
) : SessionInfoProvider {
    override fun observe(): Flow<SessionInfo> = meRepository.state.map {
        SessionInfo(activeWarehouseName = it.activeWarehouseName, signedInUserLabel = it.userDisplayName)
    }
}

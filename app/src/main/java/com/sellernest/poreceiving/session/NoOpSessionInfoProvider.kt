package com.sellernest.poreceiving.session

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/** Stand-in until M1.3/M1.5 land. See [SessionInfoProvider]. */
internal class NoOpSessionInfoProvider @Inject constructor() : SessionInfoProvider {
    override fun observe(): Flow<SessionInfo> = flowOf(SessionInfo())
}

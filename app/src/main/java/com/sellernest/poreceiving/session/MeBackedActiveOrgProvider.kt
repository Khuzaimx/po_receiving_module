package com.sellernest.poreceiving.session

import com.sellernest.poreceiving.network.ActiveOrgProvider
import javax.inject.Inject
import javax.inject.Provider

/**
 * Replaces the M0.2 `NoOpActiveOrgProvider` stub.
 *
 * Takes a [Provider] of [MeRepository], not [MeRepository] directly: this
 * class only ever reads its already-cached [MeRepository.state] (never
 * triggers a network call), but [MeRepository] itself depends on
 * [com.sellernest.poreceiving.network.ApiService], whose construction goes
 * through [com.sellernest.poreceiving.network.ActiveOrgInterceptor] --
 * exactly the interceptor this class exists to feed. A direct
 * [MeRepository] dependency here is therefore a genuine static Dagger
 * cycle (harmless at runtime, since nothing here is eager, but the Hilt
 * component's own annotation processing still has to fully resolve every
 * binding's constructor graph up front). [Provider] defers that resolution
 * to first call, which is exactly what breaks the cycle without changing
 * behaviour -- [MeRepository] is `@Singleton`, so `.get()` still always
 * returns the one cached instance.
 */
class MeBackedActiveOrgProvider @Inject constructor(
    private val meRepository: Provider<MeRepository>,
) : ActiveOrgProvider {
    override suspend fun currentActiveOrgId(): String? = meRepository.get().state.value.activeCompanyExternalId
}

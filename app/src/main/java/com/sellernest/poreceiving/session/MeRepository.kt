package com.sellernest.poreceiving.session

import com.sellernest.poreceiving.network.ApiResult
import com.sellernest.poreceiving.network.ApiService
import com.sellernest.poreceiving.network.dto.MeResponse
import com.sellernest.poreceiving.network.safeApiCall
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * M1.3: fetches `/api/me/` after sign-in, and is the single place the active
 * company and its permissions live. [com.sellernest.poreceiving.network.ActiveOrgInterceptor]
 * and the persistent status bar both read from [state] via thin adapters
 * ([MeBackedActiveOrgProvider], [MeBackedSessionInfoProvider]) rather than
 * from this class directly, keeping those two interfaces' existing seams intact.
 *
 * The raw [MeResponse] is cached so [setActiveCompany] can switch which company
 * is active without a network round trip -- "changing company changes the
 * header without an app restart" (M1.3 acceptance criteria) is a pure state
 * change, not a re-fetch.
 */
@Singleton
class MeRepository @Inject constructor(
    private val apiService: ApiService,
    private val json: Json,
) {
    private val _state = MutableStateFlow(MeSessionState())
    val state: StateFlow<MeSessionState> = _state.asStateFlow()

    private var lastResponse: MeResponse? = null

    /** Call once, right after sign-in (§5.2). */
    suspend fun refresh(): ApiResult<MeResponse> {
        val result = safeApiCall(json) { apiService.getMe() }
        if (result is ApiResult.Success) {
            lastResponse = result.body
            val defaultCompany = result.body.companies.firstOrNull { it.isDefault }
                ?: result.body.companies.firstOrNull()
            _state.value = buildState(result.body, defaultCompany?.externalId)
        }
        return result
    }

    /**
     * Switches the active company among the ones already fetched by [refresh].
     * The M1.5 warehouse/company selection screen is this method's real caller;
     * nothing in this milestone exercises it from a screen yet.
     */
    fun setActiveCompany(externalId: String) {
        val response = lastResponse ?: return
        _state.value = buildState(response, externalId)
    }

    /**
     * §5.2: sign-out clears the active-org selection. There is no sign-out
     * action in the app yet; today this is called on a hard logout (a failed
     * proactive token refresh) from [com.sellernest.poreceiving.navigation.PoReceivingRoot].
     * A future explicit sign-out button must call this too.
     */
    fun clear() {
        lastResponse = null
        _state.value = MeSessionState()
    }

    private fun buildState(response: MeResponse, activeExternalId: String?): MeSessionState {
        val active = response.companies.firstOrNull { it.externalId == activeExternalId }
        val defaultWarehouse = active?.warehouses?.firstOrNull { it.isDefault }
            ?: active?.warehouses?.firstOrNull()

        return MeSessionState(
            userDisplayName = response.user.displayName,
            activeCompanyExternalId = active?.externalId,
            activeCompanyName = active?.name,
            activeWarehouseName = defaultWarehouse?.name,
            permissions = active?.permissions,
            companies = response.companies,
        )
    }
}

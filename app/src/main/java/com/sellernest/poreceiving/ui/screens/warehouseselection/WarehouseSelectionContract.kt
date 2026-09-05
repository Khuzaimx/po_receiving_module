package com.sellernest.poreceiving.ui.screens.warehouseselection

import com.sellernest.poreceiving.core.mvvm.UiEvent
import com.sellernest.poreceiving.core.mvvm.UiState
import com.sellernest.poreceiving.network.dto.MeCompany

data class WarehouseSelectionUiState(
    val loading: Boolean = true,
    val companies: List<MeCompany> = emptyList(),
    val selectedCompanyExternalId: String? = null,
    val selectedWarehouseId: Long? = null,
    val blockedMessage: String? = null,
    /** One-shot: true once this screen (or an auto-skip) has decided to move
     *  on to the work queue. Consumed then reset by the screen, same pattern
     *  as `SignInUiState.navigateTo`. */
    val proceedToWorkQueue: Boolean = false,
) : UiState {

    /** §7.2: hidden entirely when there is only one company. */
    val showCompanySelector: Boolean get() = companies.size > 1

    val selectedCompany: MeCompany?
        get() = companies.firstOrNull { it.externalId == selectedCompanyExternalId }

    /** §5.3: "If exactly one [warehouse], select it automatically and hide the selector." */
    val showWarehouseSelector: Boolean get() = (selectedCompany?.warehouses?.size ?: 0) > 1
}

sealed interface WarehouseSelectionUiEvent : UiEvent {
    data class CompanySelected(val externalId: String) : WarehouseSelectionUiEvent
    data class WarehouseSelected(val warehouseId: Long) : WarehouseSelectionUiEvent
    data object ContinueTapped : WarehouseSelectionUiEvent
    data object NavigationHandled : WarehouseSelectionUiEvent
}

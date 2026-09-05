package com.sellernest.poreceiving.ui.screens.warehouseselection

import com.sellernest.poreceiving.core.mvvm.BaseViewModel
import com.sellernest.poreceiving.data.local.dao.DraftDao
import com.sellernest.poreceiving.network.dto.MeCompany
import com.sellernest.poreceiving.session.MeRepository
import com.sellernest.poreceiving.session.SelectedWarehouse
import com.sellernest.poreceiving.session.WarehouseSelectionStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * §7.2. Reads company/warehouse data already resolved by M1.3's `MeRepository`
 * (populated right after sign-in) rather than making its own network call.
 */
@HiltViewModel
class WarehouseSelectionViewModel @Inject constructor(
    private val meRepository: MeRepository,
    private val warehouseSelectionStorage: WarehouseSelectionStorage,
    private val draftDao: DraftDao,
) : BaseViewModel<WarehouseSelectionUiState, WarehouseSelectionUiEvent>(WarehouseSelectionUiState()) {

    init {
        scope.launch {
            val companies = meRepository.state.value.companies
            val persisted = warehouseSelectionStorage.current()
            val initialCompany = companies.firstOrNull { it.externalId == persisted?.companyExternalId }
                ?: companies.firstOrNull { it.externalId == meRepository.state.value.activeCompanyExternalId }
                ?: companies.firstOrNull()
            val initialWarehouseId = persisted?.warehouseId?.takeIf { id -> initialCompany?.warehouses?.any { it.id == id } == true }
                ?: initialCompany?.defaultWarehouseId()

            updateState {
                it.copy(
                    loading = false,
                    companies = companies,
                    selectedCompanyExternalId = initialCompany?.externalId,
                    selectedWarehouseId = initialWarehouseId,
                )
            }

            // §7.2: "The screen is skipped entirely when the user has one
            // company and one permitted warehouse."
            val singleCompany = companies.singleOrNull()
            if (singleCompany != null && singleCompany.warehouses.size == 1 && initialWarehouseId != null) {
                persistAndProceed(singleCompany.externalId, initialWarehouseId)
            }
        }
    }

    override fun onEvent(event: WarehouseSelectionUiEvent) {
        when (event) {
            is WarehouseSelectionUiEvent.CompanySelected -> selectCompany(event.externalId)
            is WarehouseSelectionUiEvent.WarehouseSelected ->
                updateState { it.copy(selectedWarehouseId = event.warehouseId, blockedMessage = null) }
            WarehouseSelectionUiEvent.ContinueTapped -> attemptContinue()
            WarehouseSelectionUiEvent.NavigationHandled -> updateState { it.copy(proceedToWorkQueue = false) }
        }
    }

    private fun selectCompany(externalId: String) {
        val company = currentState.companies.firstOrNull { it.externalId == externalId }
        updateState {
            it.copy(
                selectedCompanyExternalId = externalId,
                selectedWarehouseId = company?.defaultWarehouseId(),
                blockedMessage = null,
            )
        }
    }

    private fun attemptContinue() {
        val companyExternalId = currentState.selectedCompanyExternalId ?: return
        val warehouseId = currentState.selectedWarehouseId ?: return

        scope.launch {
            val persisted = warehouseSelectionStorage.current()
            val isChange = persisted != null &&
                (persisted.companyExternalId != companyExternalId || persisted.warehouseId != warehouseId)

            if (isChange) {
                val activeDraft = draftDao.getAnyActiveDraft()
                if (activeDraft != null) {
                    updateState {
                        it.copy(
                            blockedMessage = "Finish or discard the count in progress on " +
                                "${activeDraft.purchaseOrderNumber} before changing warehouse.",
                        )
                    }
                    return@launch
                }
            }

            persistAndProceed(companyExternalId, warehouseId)
        }
    }

    private suspend fun persistAndProceed(companyExternalId: String, warehouseId: Long) {
        warehouseSelectionStorage.save(SelectedWarehouse(companyExternalId, warehouseId))
        if (meRepository.state.value.activeCompanyExternalId != companyExternalId) {
            meRepository.setActiveCompany(companyExternalId)
        }
        updateState { it.copy(proceedToWorkQueue = true) }
    }

    private fun MeCompany.defaultWarehouseId(): Long? =
        warehouses.firstOrNull { it.isDefault }?.id ?: warehouses.firstOrNull()?.id
}

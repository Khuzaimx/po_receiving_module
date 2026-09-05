package com.sellernest.poreceiving.ui.screens.warehouseselection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.sellernest.poreceiving.network.dto.MeCompany
import com.sellernest.poreceiving.network.dto.MeWarehouse
import com.sellernest.poreceiving.ui.components.PrimaryButton
import com.sellernest.poreceiving.ui.components.StateBadge
import com.sellernest.poreceiving.ui.theme.Spacing
import com.sellernest.poreceiving.ui.theme.StateTone

/** §7.2. Renders nothing (beyond a brief loading state) when the screen would
 *  auto-skip -- see [WarehouseSelectionViewModel]'s init block. */
@Composable
fun WarehouseSelectionScreen(onProceedToWorkQueue: () -> Unit, viewModel: WarehouseSelectionViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.proceedToWorkQueue) {
        if (state.proceedToWorkQueue) {
            onProceedToWorkQueue()
            viewModel.onEvent(WarehouseSelectionUiEvent.NavigationHandled)
        }
    }

    if (state.loading || state.proceedToWorkQueue) return

    WarehouseSelectionContent(
        state = state,
        onCompanySelected = { viewModel.onEvent(WarehouseSelectionUiEvent.CompanySelected(it)) },
        onWarehouseSelected = { viewModel.onEvent(WarehouseSelectionUiEvent.WarehouseSelected(it)) },
        onContinueTapped = { viewModel.onEvent(WarehouseSelectionUiEvent.ContinueTapped) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WarehouseSelectionContent(
    state: WarehouseSelectionUiState,
    onCompanySelected: (String) -> Unit,
    onWarehouseSelected: (Long) -> Unit,
    onContinueTapped: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(text = "SELECT WAREHOUSE", style = MaterialTheme.typography.headlineLarge)

        if (state.showCompanySelector) {
            Text(text = "COMPANY", style = MaterialTheme.typography.labelLarge)
            CompanyDropdown(
                companies = state.companies,
                selectedExternalId = state.selectedCompanyExternalId,
                onCompanySelected = onCompanySelected,
            )
        }

        if (state.showWarehouseSelector) {
            Text(text = "WAREHOUSE", style = MaterialTheme.typography.labelLarge)
            state.selectedCompany?.warehouses?.forEach { warehouse ->
                WarehouseRow(
                    warehouse = warehouse,
                    selected = warehouse.id == state.selectedWarehouseId,
                    onSelected = { onWarehouseSelected(warehouse.id) },
                )
            }
        }

        state.blockedMessage?.let { message ->
            StateBadge(tone = StateTone.Error, icon = Icons.Filled.Warning, label = message)
        }

        PrimaryButton(
            text = "CONTINUE",
            enabled = state.selectedCompanyExternalId != null && state.selectedWarehouseId != null,
            onClick = onContinueTapped,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompanyDropdown(
    companies: List<MeCompany>,
    selectedExternalId: String?,
    onCompanySelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = companies.firstOrNull { it.externalId == selectedExternalId }?.name.orEmpty()

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        TextField(
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            readOnly = true,
            value = selectedName,
            onValueChange = {},
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            companies.forEach { company ->
                DropdownMenuItem(
                    text = { Text(company.name) },
                    onClick = {
                        onCompanySelected(company.externalId)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun WarehouseRow(warehouse: MeWarehouse, selected: Boolean, onSelected: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelected),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        RadioButton(selected = selected, onClick = onSelected)
        Text(text = warehouse.name, style = MaterialTheme.typography.bodyLarge)
        if (warehouse.isDefault) {
            Text(text = "(default)", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

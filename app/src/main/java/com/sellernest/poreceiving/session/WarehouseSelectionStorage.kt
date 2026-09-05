package com.sellernest.poreceiving.session

/** The one selected (company, warehouse) pair, persisted across app restart (§7.2). */
data class SelectedWarehouse(
    val companyExternalId: String,
    val warehouseId: Long,
)

interface WarehouseSelectionStorage {
    suspend fun save(selection: SelectedWarehouse)
    suspend fun current(): SelectedWarehouse?
    suspend fun clear()
}

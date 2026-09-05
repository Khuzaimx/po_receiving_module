package com.sellernest.poreceiving.ui.screens.warehouseselection

import com.sellernest.poreceiving.session.SelectedWarehouse
import com.sellernest.poreceiving.session.WarehouseSelectionStorage

internal class FakeWarehouseSelectionStorage(initial: SelectedWarehouse? = null) : WarehouseSelectionStorage {
    private var stored: SelectedWarehouse? = initial

    override suspend fun save(selection: SelectedWarehouse) {
        stored = selection
    }

    override suspend fun current(): SelectedWarehouse? = stored

    override suspend fun clear() {
        stored = null
    }
}

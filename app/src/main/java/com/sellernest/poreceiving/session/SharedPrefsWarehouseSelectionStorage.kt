package com.sellernest.poreceiving.session

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plain (not encrypted) SharedPreferences: a warehouse id is not sensitive, so
 * this deliberately does not reuse the EncryptedSharedPreferences machinery
 * `com.sellernest.poreceiving.auth.EncryptedTokenStorage` uses for tokens.
 */
@Singleton
class SharedPrefsWarehouseSelectionStorage @Inject constructor(
    @ApplicationContext context: Context,
) : WarehouseSelectionStorage {

    private val prefs = context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)

    override suspend fun save(selection: SelectedWarehouse) {
        prefs.edit()
            .putString(KEY_COMPANY, selection.companyExternalId)
            .putLong(KEY_WAREHOUSE, selection.warehouseId)
            .apply()
    }

    override suspend fun current(): SelectedWarehouse? {
        val companyExternalId = prefs.getString(KEY_COMPANY, null) ?: return null
        if (!prefs.contains(KEY_WAREHOUSE)) return null
        return SelectedWarehouse(companyExternalId, prefs.getLong(KEY_WAREHOUSE, -1L))
    }

    override suspend fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS_FILE_NAME = "warehouse_selection"
        const val KEY_COMPANY = "company_external_id"
        const val KEY_WAREHOUSE = "warehouse_id"
    }
}

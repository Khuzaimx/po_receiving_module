package com.sellernest.poreceiving.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M1.5 acceptance criterion: "Selection persists across app restart." A new
 * [SharedPrefsWarehouseSelectionStorage] instance against the same underlying
 * SharedPreferences file is the standard proxy for "survives process death" --
 * see `DraftPersistenceInstrumentedTest` for the same technique applied to Room.
 */
@RunWith(AndroidJUnit4::class)
class SharedPrefsWarehouseSelectionStorageInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun tearDown() {
        context.deleteSharedPreferences("warehouse_selection")
    }

    @Test
    fun selectionSurvivesReopeningTheUnderlyingPreferences() = runTest {
        val firstInstance = SharedPrefsWarehouseSelectionStorage(context)
        firstInstance.save(SelectedWarehouse("acme", 2))

        val secondInstance = SharedPrefsWarehouseSelectionStorage(context)

        assertEquals(SelectedWarehouse("acme", 2), secondInstance.current())
    }

    @Test
    fun clearRemovesTheSelection() = runTest {
        val storage = SharedPrefsWarehouseSelectionStorage(context)
        storage.save(SelectedWarehouse("acme", 2))

        storage.clear()

        assertNull(storage.current())
    }
}

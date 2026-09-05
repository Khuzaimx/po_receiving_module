package com.sellernest.poreceiving.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sellernest.poreceiving.data.local.entities.DraftEntity
import com.sellernest.poreceiving.data.local.entities.DraftState
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * §8: "Every scan: Written to Room immediately. The draft must survive process
 * death, battery pull, and reboot." Requires a connected device or emulator to
 * execute -- `./gradlew connectedDebugAndroidTest`.
 *
 * A real process-death test needs instrumentation the Room testing library
 * doesn't expose directly, so this uses the accepted proxy for it: writing to a
 * real on-disk database file, fully closing that Room instance (dropping every
 * in-memory object, exactly what process death does to them), then opening a
 * brand new [AppDatabase] instance against the same file and reading back what
 * was written. If this test method also covered a reboot, the same file-based
 * approach would apply -- reboot does not touch app-private storage.
 */
@RunWith(AndroidJUnit4::class)
class DraftPersistenceInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "test_draft_persistence.db"

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun draftWrittenBeforeSimulatedProcessDeathIsReadableAfterReopeningTheDatabase() = runTest {
        val firstInstance = Room.databaseBuilder(context, AppDatabase::class.java, databaseName).build()
        val draft = DraftEntity(
            purchaseOrderId = 10482,
            purchaseOrderNumber = "PO-10482",
            warehouseId = 2,
            state = DraftState.COUNTING,
            idempotencyKey = "0f8c1e2a-1111",
            createdAtEpochMillis = 1_000L,
            updatedAtEpochMillis = 1_000L,
        )
        val draftId = firstInstance.draftDao().insert(draft)

        // Simulates process death: no in-memory Room state carries over.
        firstInstance.close()

        val secondInstance = Room.databaseBuilder(context, AppDatabase::class.java, databaseName).build()
        val reloaded = secondInstance.draftDao().getById(draftId)

        assertNotNull("Draft written before 'process death' must be readable after reopening", reloaded)
        assertEquals("PO-10482", reloaded?.purchaseOrderNumber)
        assertEquals(DraftState.COUNTING, reloaded?.state)
        assertEquals("0f8c1e2a-1111", reloaded?.idempotencyKey)

        secondInstance.close()
    }
}

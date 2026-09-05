package com.sellernest.poreceiving.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sellernest.poreceiving.data.local.entities.DraftEntity
import com.sellernest.poreceiving.data.local.entities.DraftState
import com.sellernest.poreceiving.network.dto.ScanMatchedLine
import kotlinx.coroutines.flow.first
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

    /**
     * M3.5 acceptance criterion: "Instrumented test: scan 20 items,
     * `adb shell am force-stop`, relaunch, all 20 present with correct
     * quantities." Twenty *scans* across ten distinct SKUs (two scans each,
     * matching §7.5's "each scan of the same item increments by one"), using
     * the real [DraftRepository] rather than the DAOs directly, so this
     * exercises the exact write path a real screen would.
     */
    @Test
    fun twentyScansAcrossTenLinesSurviveSimulatedProcessDeathWithCorrectQuantities() = runTest {
        val firstDb = Room.databaseBuilder(context, AppDatabase::class.java, databaseName).build()
        val repository = DraftRepository(firstDb.draftDao(), firstDb.draftLineDao())

        val draft = repository.openPurchaseOrder(10482, "PO-10482", warehouseId = 2)
        repeat(10) { index ->
            val line = ScanMatchedLine(
                purchaseOrderItemId = index.toLong(),
                sku = "SKU-$index",
                name = "Item $index",
                quantityAlreadyReceived = 0,
                requiresSerialNumber = false,
                fullyReceived = false,
            )
            repository.recordScan(draft.id, line) // 1st scan
            repository.recordScan(draft.id, line) // 2nd scan -> counted quantity 2
        }
        firstDb.close()

        val secondDb = Room.databaseBuilder(context, AppDatabase::class.java, databaseName).build()
        val lines = secondDb.draftLineDao().observeForDraft(draft.id).first()

        assertEquals(10, lines.size)
        assertEquals(setOf(2), lines.map { it.countedQuantity }.toSet())
        assertEquals((0..9).map { "SKU-$it" }.toSet(), lines.map { it.sku }.toSet())

        secondDb.close()
    }
}

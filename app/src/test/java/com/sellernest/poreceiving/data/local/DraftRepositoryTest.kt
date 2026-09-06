package com.sellernest.poreceiving.data.local

import com.sellernest.poreceiving.data.local.entities.DraftState
import com.sellernest.poreceiving.data.local.entities.SubmissionStatus
import com.sellernest.poreceiving.network.dto.ScanMatchedLine
import com.sellernest.poreceiving.ui.screens.poheader.FakeDraftLineDao
import com.sellernest.poreceiving.ui.screens.warehouseselection.FakeDraftDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M3.1/M3.2/M3.6 acceptance criteria: no expected quantity anywhere in this
 * path, only the diagrammed state transitions are reachable, and the
 * idempotency key is generated exactly once and never rewritten.
 */
class DraftRepositoryTest {

    private val matchedLine = ScanMatchedLine(
        purchaseOrderItemId = 88213,
        sku = "WM-4410-BLK",
        name = "Widget Mount, Black",
        quantityAlreadyReceived = 2,
        requiresSerialNumber = false,
        fullyReceived = false,
    )

    private fun repository(
        draftDao: FakeDraftDao = FakeDraftDao(),
        draftLineDao: FakeDraftLineDao = FakeDraftLineDao(),
        draftPhotoDao: FakeDraftPhotoDao = FakeDraftPhotoDao(),
        draftSerialDao: FakeDraftSerialDao = FakeDraftSerialDao(),
        queuedSubmissionDao: FakeQueuedSubmissionDao = FakeQueuedSubmissionDao(),
    ) = DraftRepository(draftDao, draftLineDao, draftPhotoDao, draftSerialDao, queuedSubmissionDao)

    @Test
    fun `opening the same PO and warehouse twice resumes, never forks`() = runTest {
        val repo = repository()

        val first = repo.openPurchaseOrder(10482, "PO-10482", 2)
        val second = repo.openPurchaseOrder(10482, "PO-10482", 2)

        assertEquals(first.id, second.id)
        assertEquals(first.idempotencyKey, second.idempotencyKey)
    }

    @Test
    fun `the idempotency key survives every subsequent mutation untouched`() = runTest {
        val draftDao = FakeDraftDao()
        val repo = repository(draftDao)
        val draft = repo.openPurchaseOrder(10482, "PO-10482", 2)
        val originalKey = draft.idempotencyKey

        repo.recordScan(draft.id, matchedLine)
        repo.recordScan(draft.id, matchedLine)
        repo.setLineQuantity(draft.id, matchedLine.purchaseOrderItemId, 42)
        repo.commitCount(draft.id)

        assertEquals(originalKey, draftDao.getById(draft.id)?.idempotencyKey)
    }

    @Test
    fun `the idempotency key is generated once and is never blank`() = runTest {
        val draft = repository().openPurchaseOrder(10482, "PO-10482", 2)

        assertTrue(draft.idempotencyKey.isNotBlank())
    }

    @Test
    fun `first scan transitions PO_OPEN to COUNTING`() = runTest {
        val draftDao = FakeDraftDao()
        val repo = repository(draftDao)
        val draft = repo.openPurchaseOrder(10482, "PO-10482", 2)
        assertEquals(DraftState.PO_OPEN, draftDao.getById(draft.id)?.state)

        repo.recordScan(draft.id, matchedLine)

        assertEquals(DraftState.COUNTING, draftDao.getById(draft.id)?.state)
    }

    @Test
    fun `repeated scans of the same item increment its counted quantity by one each`() = runTest {
        val repo = repository()
        val draft = repo.openPurchaseOrder(10482, "PO-10482", 2)

        repo.recordScan(draft.id, matchedLine)
        repo.recordScan(draft.id, matchedLine)
        val third = repo.recordScan(draft.id, matchedLine)

        assertEquals(3, third.countedQuantity)
    }

    @Test
    fun `scanning a new item creates its own line starting at one`() = runTest {
        val repo = repository()
        val draft = repo.openPurchaseOrder(10482, "PO-10482", 2)
        val otherLine = matchedLine.copy(purchaseOrderItemId = 2, sku = "CB-2201", name = "Cable Brace")

        repo.recordScan(draft.id, matchedLine)
        val second = repo.recordScan(draft.id, otherLine)

        assertEquals(1, second.countedQuantity)
    }

    @Test
    fun `setLineQuantity never goes below zero, and the zeroed line stays visible`() = runTest {
        val draftLineDao = FakeDraftLineDao()
        val repo = repository(draftLineDao = draftLineDao)
        val draft = repo.openPurchaseOrder(10482, "PO-10482", 2)
        repo.recordScan(draft.id, matchedLine)

        repo.setLineQuantity(draft.id, matchedLine.purchaseOrderItemId, -5)

        val persisted = draftLineDao.getByPurchaseOrderItem(draft.id, matchedLine.purchaseOrderItemId)
        assertEquals(0, persisted?.countedQuantity)
    }

    @Test
    fun `setLineQuantity applies a positive value directly, such as typing 120 after one scan`() = runTest {
        val draftLineDao = FakeDraftLineDao()
        val repo = repository(draftLineDao = draftLineDao)
        val draft = repo.openPurchaseOrder(10482, "PO-10482", 2)
        repo.recordScan(draft.id, matchedLine) // one scan -> counted quantity 1

        repo.setLineQuantity(draft.id, matchedLine.purchaseOrderItemId, 120)

        val persisted = draftLineDao.getByPurchaseOrderItem(draft.id, matchedLine.purchaseOrderItemId)
        assertEquals(120, persisted?.countedQuantity)
    }

    @Test
    fun `commitCount transitions COUNTING to RECONCILE`() = runTest {
        val draftDao = FakeDraftDao()
        val repo = repository(draftDao)
        val draft = repo.openPurchaseOrder(10482, "PO-10482", 2)
        repo.recordScan(draft.id, matchedLine)

        repo.commitCount(draft.id)

        assertEquals(DraftState.RECONCILE, draftDao.getById(draft.id)?.state)
    }

    @Test
    fun `commitCount from PO_OPEN (no scans yet) is rejected`() = runTest {
        val repo = repository()
        val draft = repo.openPurchaseOrder(10482, "PO-10482", 2)

        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking { repo.commitCount(draft.id) }
        }
    }

    @Test
    fun `discard transitions PO_OPEN to DISCARDED`() = runTest {
        val draftDao = FakeDraftDao()
        val repo = repository(draftDao)
        val draft = repo.openPurchaseOrder(10482, "PO-10482", 2)

        repo.discard(draft.id)

        assertEquals(DraftState.DISCARDED, draftDao.getById(draft.id)?.state)
    }

    @Test
    fun `setDamagedQuantity clamps to the counted quantity so GOOD plus DAMAGED never exceeds COUNTED`() = runTest {
        val draftLineDao = FakeDraftLineDao()
        val repo = repository(draftLineDao = draftLineDao)
        val draft = repo.openPurchaseOrder(10482, "PO-10482", 2)
        repo.recordScan(draft.id, matchedLine)
        repo.recordScan(draft.id, matchedLine) // countedQuantity = 2

        repo.setDamagedQuantity(draft.id, matchedLine.purchaseOrderItemId, 5)

        val persisted = draftLineDao.getByPurchaseOrderItem(draft.id, matchedLine.purchaseOrderItemId)
        assertEquals(2, persisted?.damagedQuantity)
    }

    @Test
    fun `setDamagedQuantity never goes below zero`() = runTest {
        val draftLineDao = FakeDraftLineDao()
        val repo = repository(draftLineDao = draftLineDao)
        val draft = repo.openPurchaseOrder(10482, "PO-10482", 2)
        repo.recordScan(draft.id, matchedLine)

        repo.setDamagedQuantity(draft.id, matchedLine.purchaseOrderItemId, -3)

        val persisted = draftLineDao.getByPurchaseOrderItem(draft.id, matchedLine.purchaseOrderItemId)
        assertEquals(0, persisted?.damagedQuantity)
    }

    @Test
    fun `addPhoto resolves the purchase order item id to the line's own row id`() = runTest {
        val draftLineDao = FakeDraftLineDao()
        val draftPhotoDao = FakeDraftPhotoDao()
        val repo = repository(draftLineDao = draftLineDao, draftPhotoDao = draftPhotoDao)
        val draft = repo.openPurchaseOrder(10482, "PO-10482", 2)
        val line = repo.recordScan(draft.id, matchedLine)

        val photo = repo.addPhoto(draft.id, matchedLine.purchaseOrderItemId, "/tmp/photo.jpg")

        assertEquals(line.id, photo.draftLineId)
        assertEquals(1, repo.observePhotos(draft.id).first().size)
    }

    @Test
    fun `removePhoto removes it from the draft's photo list`() = runTest {
        val repo = repository()
        val draft = repo.openPurchaseOrder(10482, "PO-10482", 2)
        val photo = repo.addPhoto(draft.id, purchaseOrderItemId = null, localFilePath = "/tmp/photo.jpg")

        repo.removePhoto(photo)

        assertTrue(repo.observePhotos(draft.id).first().isEmpty())
    }

    @Test
    fun `addSerial rejects a duplicate for the same line and names the conflicting value`() = runTest {
        val repo = repository()
        val draft = repo.openPurchaseOrder(10482, "PO-10482", 2)
        val line = repo.recordScan(draft.id, matchedLine)
        repo.addSerial(line.id, "SN-1")

        val result = repo.addSerial(line.id, "SN-1")

        assertTrue(result is DraftRepository.AddSerialResult.Duplicate)
        assertEquals("SN-1", (result as DraftRepository.AddSerialResult.Duplicate).conflictingValue)
        assertEquals(1, repo.observeSerials(line.id).first().size)
    }

    @Test
    fun `removeSerial re-opens that slot`() = runTest {
        val repo = repository()
        val draft = repo.openPurchaseOrder(10482, "PO-10482", 2)
        val line = repo.recordScan(draft.id, matchedLine)
        val added = repo.addSerial(line.id, "SN-1") as DraftRepository.AddSerialResult.Added

        repo.removeSerial(added.serial)

        assertTrue(repo.observeSerials(line.id).first().isEmpty())
    }

    @Test
    fun `getTotalSerialCount sums serials across every line in the draft`() = runTest {
        val draftLineDao = FakeDraftLineDao()
        val draftSerialDao = FakeDraftSerialDao(draftLineDao)
        val repo = repository(draftLineDao = draftLineDao, draftSerialDao = draftSerialDao)
        val draft = repo.openPurchaseOrder(10482, "PO-10482", 2)
        val firstLine = repo.recordScan(draft.id, matchedLine)
        val secondLine = repo.recordScan(draft.id, matchedLine.copy(purchaseOrderItemId = 2, sku = "CB-2201", name = "Cable Brace"))
        repo.addSerial(firstLine.id, "SN-1")
        repo.addSerial(secondLine.id, "SN-2")
        repo.addSerial(secondLine.id, "SN-3")

        assertEquals(3, repo.getTotalSerialCount(draft.id))
    }

    @Test
    fun `setBin persists the selected bin id to the draft`() = runTest {
        val draftDao = FakeDraftDao()
        val repo = repository(draftDao)
        val draft = repo.openPurchaseOrder(10482, "PO-10482", 2)

        repo.setBin(draft.id, 55)

        assertEquals(55L, draftDao.getById(draft.id)?.binId)
    }

    @Test
    fun `setNotes persists the receipt-level notes to the draft`() = runTest {
        val draftDao = FakeDraftDao()
        val repo = repository(draftDao)
        val draft = repo.openPurchaseOrder(10482, "PO-10482", 2)

        repo.setNotes(draft.id, "Pallet was resealed on arrival")

        assertEquals("Pallet was resealed on arrival", draftDao.getById(draft.id)?.notes)
    }

    @Test
    fun `queueForSubmission transitions REVIEW to QUEUED and records a pending submission`() = runTest {
        val draftDao = FakeDraftDao()
        val queuedSubmissionDao = FakeQueuedSubmissionDao()
        val repo = repository(draftDao = draftDao, queuedSubmissionDao = queuedSubmissionDao)
        val draft = repo.openPurchaseOrder(10482, "PO-10482", 2)
        repo.recordScan(draft.id, matchedLine)
        repo.commitCount(draft.id)
        repo.completeReconciliation(draft.id)

        repo.queueForSubmission(draft.id)

        assertEquals(DraftState.QUEUED, draftDao.getById(draft.id)?.state)
        val submission = queuedSubmissionDao.getForDraft(draft.id)
        assertEquals(SubmissionStatus.PENDING, submission?.status)
    }
}

package com.sellernest.poreceiving.data.local

import android.database.sqlite.SQLiteConstraintException
import com.sellernest.poreceiving.data.local.dao.DraftDao
import com.sellernest.poreceiving.data.local.dao.DraftLineDao
import com.sellernest.poreceiving.data.local.dao.DraftPhotoDao
import com.sellernest.poreceiving.data.local.dao.DraftSerialDao
import com.sellernest.poreceiving.data.local.dao.QueuedSubmissionDao
import com.sellernest.poreceiving.data.local.entities.DraftEntity
import com.sellernest.poreceiving.data.local.entities.DraftLineEntity
import com.sellernest.poreceiving.data.local.entities.DraftPhotoEntity
import com.sellernest.poreceiving.data.local.entities.DraftSerialEntity
import com.sellernest.poreceiving.data.local.entities.DraftState
import com.sellernest.poreceiving.data.local.entities.QueuedSubmissionEntity
import com.sellernest.poreceiving.data.local.entities.SubmissionStatus
import com.sellernest.poreceiving.network.dto.ScanMatchedLine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single place that creates, transitions, and mutates a draft (§6.1-§6.3,
 * §8, §9.4's idempotency key). Every screen that touches a draft goes through
 * here rather than the DAOs directly, so the state machine (M3.2) and the
 * once-only idempotency key (M3.6) can't be bypassed by a future screen that
 * forgets to check them.
 */
@Singleton
class DraftRepository @Inject constructor(
    private val draftDao: DraftDao,
    private val draftLineDao: DraftLineDao,
    private val draftPhotoDao: DraftPhotoDao,
    private val draftSerialDao: DraftSerialDao,
    private val queuedSubmissionDao: QueuedSubmissionDao,
) {

    /**
     * §8: "One draft per (PO, warehouse); opening the same PO again resumes
     * rather than forks." §3.6: the idempotency key is generated exactly once,
     * right here, at the point a draft row is first created -- never again for
     * the lifetime of this draft.
     */
    suspend fun openPurchaseOrder(
        purchaseOrderId: Long,
        purchaseOrderNumber: String,
        warehouseId: Long,
    ): DraftEntity {
        draftDao.getActiveDraftFor(purchaseOrderId, warehouseId)?.let { return it }

        val now = System.currentTimeMillis()
        val draft = DraftEntity(
            purchaseOrderId = purchaseOrderId,
            purchaseOrderNumber = purchaseOrderNumber,
            warehouseId = warehouseId,
            state = DraftState.PO_OPEN,
            idempotencyKey = UUID.randomUUID().toString(),
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )
        val id = draftDao.insert(draft)
        return draft.copy(id = id)
    }

    suspend fun getDraft(draftId: Long): DraftEntity? = draftDao.getById(draftId)

    fun observeDraft(draftId: Long): Flow<DraftEntity?> = draftDao.observeById(draftId)

    fun observeLines(draftId: Long): Flow<List<DraftLineEntity>> = draftLineDao.observeForDraft(draftId)

    /**
     * §6.3: "COUNTING is fully local." First scan transitions PO_OPEN ->
     * COUNTING; every scan after that is a self-transition. Each accepted
     * scan of the same SKU increments its line by exactly one (§7.5) -- this
     * never touches an expected quantity, because there is none to touch:
     * [ScanMatchedLine] (from M0.2's blind-count-safe DTOs) has no such field.
     */
    suspend fun recordScan(draftId: Long, matchedLine: ScanMatchedLine): DraftLineEntity {
        ensureCounting(draftId)

        val existing = draftLineDao.getByPurchaseOrderItem(draftId, matchedLine.purchaseOrderItemId)
        return if (existing != null) {
            val updated = existing.copy(countedQuantity = existing.countedQuantity + 1)
            draftLineDao.update(updated)
            updated
        } else {
            val line = DraftLineEntity(
                draftId = draftId,
                purchaseOrderItemId = matchedLine.purchaseOrderItemId,
                sku = matchedLine.sku,
                name = matchedLine.name,
                countedQuantity = 1,
                requiresSerialNumber = matchedLine.requiresSerialNumber,
            )
            val id = draftLineDao.insert(line)
            line.copy(id = id)
        }
    }

    /**
     * M3.4: "Quantity cannot go below zero; setting a line to zero keeps the
     * line visible." Clamping here, not in the UI layer, is what makes that
     * true regardless of which screen calls this.
     */
    suspend fun setLineQuantity(draftId: Long, purchaseOrderItemId: Long, quantity: Int) {
        ensureCounting(draftId)
        val line = draftLineDao.getByPurchaseOrderItem(draftId, purchaseOrderItemId) ?: return
        draftLineDao.update(line.copy(countedQuantity = quantity.coerceAtLeast(0)))
    }

    /** §6.3: the only transition into RECONCILE, and the only place expected
     *  quantities may subsequently be fetched (M4.1's job, not this method's). */
    suspend fun commitCount(draftId: Long) {
        transition(draftId, DraftState.RECONCILE)
    }

    /**
     * M4.3: "GOOD + DAMAGED always equals COUNTED." Clamping the damaged split
     * to the counted quantity here, the same way [setLineQuantity] clamps
     * counting to zero, is what makes that invariant hold regardless of which
     * screen calls this.
     */
    suspend fun setDamagedQuantity(draftId: Long, purchaseOrderItemId: Long, damagedQuantity: Int) {
        val line = draftLineDao.getByPurchaseOrderItem(draftId, purchaseOrderItemId) ?: return
        draftLineDao.update(line.copy(damagedQuantity = damagedQuantity.coerceIn(0, line.countedQuantity)))
    }

    /** §6.3: PO_OPEN -> DISCARDED. The confirmation UI naming the PO is the
     *  caller's job (M3.7); this only performs the transition itself. */
    suspend fun discard(draftId: Long) {
        transition(draftId, DraftState.DISCARDED)
    }

    /** M4.2: "Reasons and notes persist to the draft immediately." */
    suspend fun setVarianceReason(
        draftId: Long,
        purchaseOrderItemId: Long,
        reasonId: Long?,
        note: String?,
    ) {
        val line = draftLineDao.getByPurchaseOrderItem(draftId, purchaseOrderItemId) ?: return
        draftLineDao.update(line.copy(varianceReasonId = reasonId, varianceNote = note))
    }

    fun observePhotos(draftId: Long): Flow<List<DraftPhotoEntity>> = draftPhotoDao.observeForDraft(draftId)

    /**
     * M4.4. [purchaseOrderItemId] null makes this a receipt-level photo; a
     * non-null value is resolved to the line's own row id, since
     * [DraftPhotoEntity.draftLineId] is a foreign key to that row, not to
     * [purchaseOrderItemId] itself.
     */
    suspend fun addPhoto(
        draftId: Long,
        purchaseOrderItemId: Long?,
        localFilePath: String,
        caption: String? = null,
    ): DraftPhotoEntity {
        val draftLineId = purchaseOrderItemId?.let { draftLineDao.getByPurchaseOrderItem(draftId, it)?.id }
        val photo = DraftPhotoEntity(draftId = draftId, draftLineId = draftLineId, localFilePath = localFilePath, caption = caption)
        val id = draftPhotoDao.insert(photo)
        return photo.copy(id = id)
    }

    /** M4.4: "Removing a photo before submit removes it from the queue too." */
    suspend fun removePhoto(photo: DraftPhotoEntity) {
        draftPhotoDao.delete(photo)
    }

    fun observeSerials(draftLineId: Long): Flow<List<DraftSerialEntity>> = draftSerialDao.observeForLine(draftLineId)

    sealed interface AddSerialResult {
        data class Added(val serial: DraftSerialEntity) : AddSerialResult
        /** M4.5: "Duplicate serials are rejected... with the conflicting value shown." */
        data class Duplicate(val conflictingValue: String) : AddSerialResult
    }

    /**
     * Checked here first so the common case never round-trips through a
     * thrown [SQLiteConstraintException]; the `try`/catch is a backstop for
     * the race between that check and the insert, not the primary path --
     * [DraftSerialDao.insert]'s unique index is what makes duplicate
     * rejection actually reliable (§7.9, §10).
     */
    suspend fun addSerial(draftLineId: Long, serialValue: String): AddSerialResult {
        draftSerialDao.observeForLine(draftLineId).first().firstOrNull { it.serialValue == serialValue }?.let {
            return AddSerialResult.Duplicate(it.serialValue)
        }
        return try {
            val serial = DraftSerialEntity(draftLineId = draftLineId, serialValue = serialValue)
            val id = draftSerialDao.insert(serial)
            AddSerialResult.Added(serial.copy(id = id))
        } catch (e: SQLiteConstraintException) {
            AddSerialResult.Duplicate(serialValue)
        }
    }

    /** M4.5: "Removing a serial re-opens that slot." */
    suspend fun removeSerial(serial: DraftSerialEntity) {
        draftSerialDao.delete(serial)
    }

    /** M5.1: the Review-and-Submit totals' "Serials" figure -- the whole draft, not one line. */
    suspend fun getTotalSerialCount(draftId: Long): Int = draftSerialDao.getTotalCountForDraft(draftId)

    /** M4.6: populates [DraftEntity.binId], which §9.4's submit payload reads directly. */
    suspend fun setBin(draftId: Long, binId: Long?) {
        val draft = draftDao.getById(draftId) ?: return
        draftDao.update(draft.copy(binId = binId, updatedAtEpochMillis = System.currentTimeMillis()))
    }

    /** M5.1: the optional receipt-level notes field, persisted immediately like every other draft field. */
    suspend fun setNotes(draftId: Long, notes: String?) {
        val draft = draftDao.getById(draftId) ?: return
        draftDao.update(draft.copy(notes = notes, updatedAtEpochMillis = System.currentTimeMillis()))
    }

    /**
     * M5.1: REVIEW -> QUEUED, and a [QueuedSubmissionEntity] row for the
     * submissions screen and the pending-count status bar to pick up.
     * Actually sending the receipt over the network -- retries, backoff,
     * [QueuedSubmissionEntity.workRequestId] -- is the enqueued WorkManager
     * job's job (M5.2), not this method's; this only marks the draft ready
     * for that worker to find.
     */
    suspend fun queueForSubmission(draftId: Long) {
        transition(draftId, DraftState.QUEUED)
        queuedSubmissionDao.upsert(
            QueuedSubmissionEntity(
                draftId = draftId,
                status = SubmissionStatus.PENDING,
                enqueuedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    /**
     * §6.3/§7.7: reasons are captured on the same screen that reveals the
     * deltas (the wireframe shows both together), so this app's RECONCILE
     * always resolves straight to REVIEW -- there is no separately-rendered
     * VARIANCE_CAPTURE screen to pass through first, even though it remains a
     * legal intermediate state in [DraftStateMachine] for a future UI that
     * might split them apart.
     */
    suspend fun completeReconciliation(draftId: Long) {
        transition(draftId, DraftState.REVIEW)
    }

    suspend fun transition(draftId: Long, newState: DraftState) {
        val draft = draftDao.getById(draftId) ?: error("No draft with id $draftId")
        check(DraftStateMachine.isValidTransition(draft.state, newState)) {
            "Illegal transition for draft $draftId: ${draft.state} -> $newState"
        }
        draftDao.update(draft.copy(state = newState, updatedAtEpochMillis = System.currentTimeMillis()))
    }

    private suspend fun ensureCounting(draftId: Long) {
        val draft = draftDao.getById(draftId) ?: error("No draft with id $draftId")
        if (draft.state == DraftState.PO_OPEN) {
            transition(draftId, DraftState.COUNTING)
        }
    }
}

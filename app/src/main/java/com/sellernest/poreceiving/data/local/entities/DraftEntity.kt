package com.sellernest.poreceiving.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The persisted count in progress for one PO. Written on every mutation (§8:
 * "Every scan: Written to Room immediately") and never deleted except by an
 * explicit discard (state = DISCARDED) or a successful submit — see M3.5/M3.7.
 * There is exactly one non-discarded draft per (purchaseOrderId, warehouseId).
 *
 * [idempotencyKey] is generated exactly once, at the PO_OPEN -> COUNTING
 * transition, and is never regenerated for the lifetime of this row (§8, §9.4,
 * M3.6) -- reused for the original submit and every retry.
 */
@Entity(
    tableName = "drafts",
    indices = [Index(value = ["purchaseOrderId", "warehouseId"], unique = true)],
)
data class DraftEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val purchaseOrderId: Long,
    val purchaseOrderNumber: String,
    val warehouseId: Long,
    val state: DraftState,
    val idempotencyKey: String,
    val binId: Long? = null,
    val notes: String? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

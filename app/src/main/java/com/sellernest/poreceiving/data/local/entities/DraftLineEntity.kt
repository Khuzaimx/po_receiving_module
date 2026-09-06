package com.sellernest.poreceiving.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One counted line within a [DraftEntity]. [countedQuantity] is what the receiver
 * scanned or typed -- never an expected quantity (§6.1, §6.2; see
 * `PurchaseOrderLine` in the network layer, which has no such field to copy from).
 * [damagedQuantity] is a subset of [countedQuantity] (§7.8): good stock is always
 * `countedQuantity - damagedQuantity`.
 *
 * [missingQuantity] is the one field on this entity that IS expected-quantity
 * derived -- set by [com.sellernest.poreceiving.data.local.DraftRepository.setMissingQuantity]
 * when M5.1's Review-and-Submit screen loads (long after RECONCILE has
 * already revealed it), then left untouched for the rest of that submit
 * cycle: the background submit worker's own automatic retries all read this
 * same persisted value back rather than re-deriving it, which is what keeps
 * the §9.4 payload identical across those retries (a manual EDIT & RESUBMIT
 * that revisits this screen is a distinct, deliberate new attempt, and may
 * legitimately re-derive a different value).
 */
@Entity(
    tableName = "draft_lines",
    foreignKeys = [
        ForeignKey(
            entity = DraftEntity::class,
            parentColumns = ["id"],
            childColumns = ["draftId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["draftId", "purchaseOrderItemId"], unique = true)],
)
data class DraftLineEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val draftId: Long,
    val purchaseOrderItemId: Long,
    val sku: String,
    val name: String,
    val countedQuantity: Int = 0,
    val damagedQuantity: Int = 0,
    val varianceReasonId: Long? = null,
    val varianceNote: String? = null,
    val requiresSerialNumber: Boolean = false,
    val missingQuantity: Int = 0,
)

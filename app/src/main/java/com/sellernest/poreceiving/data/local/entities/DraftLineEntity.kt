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
)

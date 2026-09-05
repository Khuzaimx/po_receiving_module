package com.sellernest.poreceiving.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A captured, downscaled photo (M4.4: <= 1600 px, <= 2 MB before queueing) held
 * locally until the independent upload worker (M6.1) posts it to §9.5 and records
 * [remotePhotoId]. [draftLineId] is null for a receipt-level photo -- §9.5's
 * `purchase_order_receipt_item` is optional on the wire for exactly this reason.
 *
 * [localFilePath] must survive as long as this row does: a photo failure must
 * never block or reverse a posted receipt (§10), so the file is deleted only
 * after [uploaded] becomes true.
 */
@Entity(
    tableName = "draft_photos",
    foreignKeys = [
        ForeignKey(
            entity = DraftEntity::class,
            parentColumns = ["id"],
            childColumns = ["draftId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = DraftLineEntity::class,
            parentColumns = ["id"],
            childColumns = ["draftLineId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("draftId"), Index("draftLineId")],
)
data class DraftPhotoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val draftId: Long,
    val draftLineId: Long? = null,
    val localFilePath: String,
    val caption: String? = null,
    val uploaded: Boolean = false,
    val remotePhotoId: Long? = null,
)

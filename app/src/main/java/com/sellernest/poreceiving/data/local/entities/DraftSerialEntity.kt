package com.sellernest.poreceiving.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A captured serial number for a [DraftLineEntity] (§7.9). The unique index on
 * (draftLineId, serialValue) enforces "duplicate serials are rejected" (§7.9,
 * §10) at the storage layer, not only in the screen that captures them.
 */
@Entity(
    tableName = "draft_serials",
    foreignKeys = [
        ForeignKey(
            entity = DraftLineEntity::class,
            parentColumns = ["id"],
            childColumns = ["draftLineId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["draftLineId", "serialValue"], unique = true)],
)
data class DraftSerialEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val draftLineId: Long,
    val serialValue: String,
)

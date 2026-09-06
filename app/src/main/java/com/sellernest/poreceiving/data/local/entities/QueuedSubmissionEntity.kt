package com.sellernest.poreceiving.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

enum class SubmissionStatus {
    PENDING,
    SENDING,
    RECEIPTED,
    FAILED,
}

/**
 * Tracks the WorkManager-backed submit for one draft (§7.12, §8, M5.2). There is
 * exactly one row per draft; [attemptCount] and [lastError] back the "Attempt 2 -
 * retrying in 8s" and per-line failure display on the submissions screen.
 * [workRequestId] correlates this row with the enqueued unique WorkManager
 * request so a double-tap on SUBMIT cannot create a second one (M5.2) --
 * enforced structurally by [com.sellernest.poreceiving.work.submit.SubmitWorkScheduler]'s
 * deterministic unique work name plus `ExistingWorkPolicy.KEEP`, with this
 * field kept for display/correlation rather than being load-bearing itself.
 *
 * [failedLinesJson] is the verbatim §9.4 `failed` array from the last
 * `/receive/` response, JSON-encoded (M5.3: "partial failure is normal" --
 * a successful, RECEIPTED submission can still carry per-line failures that
 * must survive a process death to keep being shown).
 */
@Entity(
    tableName = "queued_submissions",
    foreignKeys = [
        ForeignKey(
            entity = DraftEntity::class,
            parentColumns = ["id"],
            childColumns = ["draftId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class QueuedSubmissionEntity(
    @PrimaryKey
    val draftId: Long,
    val status: SubmissionStatus,
    val attemptCount: Int = 0,
    val lastError: String? = null,
    val receiptId: Long? = null,
    val workRequestId: String? = null,
    val enqueuedAtEpochMillis: Long,
    val failedLinesJson: String? = null,
)

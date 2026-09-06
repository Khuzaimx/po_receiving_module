package com.sellernest.poreceiving.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 1 -> 2: adds the two columns M5.1/M5.3 need to make a retried submit send
 * the *same* payload every attempt (§9.4/§10's idempotency guarantee depends
 * on this -- a payload that changes between retries could legitimately trigger
 * the server's 409 conflict).
 *
 * [com.sellernest.poreceiving.data.local.entities.DraftLineEntity.missingQuantity]
 * is computed once, from the M4.1 reconcile response, at the moment a draft is
 * queued for submission (M5.1) -- not before, so it never becomes a pre-commit
 * expected-quantity leak (§6.1) -- and is then read back unchanged on every
 * retry rather than re-derived, which could disagree with the first attempt if
 * the PO's server-side state moved on between attempts.
 *
 * [com.sellernest.poreceiving.data.local.entities.QueuedSubmissionEntity.failedLinesJson]
 * holds the verbatim per-line failures from the last `/receive/` response
 * (§9.4: "partial failure is normal") so the submissions screen (M5.5) can
 * still render them after a process death, not only immediately after the
 * network call returns.
 *
 * No [androidx.room.testing.MigrationTestHelper]-based test accompanies this --
 * this environment has no JDK/Gradle to run one (see the standing note in every
 * PR from this session); the two `ALTER TABLE` statements were instead checked
 * by hand against Room's exported schema conventions (column name/type/default
 * matching the new Kotlin properties' name/type/default exactly).
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE draft_lines ADD COLUMN missingQuantity INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE queued_submissions ADD COLUMN failedLinesJson TEXT DEFAULT NULL")
    }
}

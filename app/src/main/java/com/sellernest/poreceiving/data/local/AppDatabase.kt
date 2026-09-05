package com.sellernest.poreceiving.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.sellernest.poreceiving.data.local.dao.DraftDao
import com.sellernest.poreceiving.data.local.dao.DraftLineDao
import com.sellernest.poreceiving.data.local.dao.DraftPhotoDao
import com.sellernest.poreceiving.data.local.dao.DraftSerialDao
import com.sellernest.poreceiving.data.local.dao.QueuedSubmissionDao
import com.sellernest.poreceiving.data.local.entities.DraftEntity
import com.sellernest.poreceiving.data.local.entities.DraftLineEntity
import com.sellernest.poreceiving.data.local.entities.DraftPhotoEntity
import com.sellernest.poreceiving.data.local.entities.DraftSerialEntity
import com.sellernest.poreceiving.data.local.entities.QueuedSubmissionEntity

/**
 * Holds every draft, in-progress or finished, until it is submitted or explicitly
 * discarded (§8: "Draft retention: Retained until submitted or explicitly
 * discarded. Never auto-expired."). There is deliberately no
 * `fallbackToDestructiveMigration()` call anywhere this database is built
 * ([DatabaseModule]) -- once real drafts exist on a device, a destructive
 * migration would delete a receiver's in-progress count. Schema changes from
 * here on must ship a real [androidx.room.migration.Migration].
 */
@Database(
    entities = [
        DraftEntity::class,
        DraftLineEntity::class,
        DraftSerialEntity::class,
        DraftPhotoEntity::class,
        QueuedSubmissionEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun draftDao(): DraftDao
    abstract fun draftLineDao(): DraftLineDao
    abstract fun draftSerialDao(): DraftSerialDao
    abstract fun draftPhotoDao(): DraftPhotoDao
    abstract fun queuedSubmissionDao(): QueuedSubmissionDao
}

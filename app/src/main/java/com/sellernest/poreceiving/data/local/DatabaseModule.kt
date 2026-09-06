package com.sellernest.poreceiving.data.local

import android.content.Context
import androidx.room.Room
import com.sellernest.poreceiving.data.local.dao.DraftDao
import com.sellernest.poreceiving.data.local.dao.DraftLineDao
import com.sellernest.poreceiving.data.local.dao.DraftPhotoDao
import com.sellernest.poreceiving.data.local.dao.DraftSerialDao
import com.sellernest.poreceiving.data.local.dao.QueuedSubmissionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private const val DATABASE_NAME = "po_receiving.db"

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
            // No fallbackToDestructiveMigration(): see the class doc on AppDatabase.
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun provideDraftDao(db: AppDatabase): DraftDao = db.draftDao()

    @Provides
    fun provideDraftLineDao(db: AppDatabase): DraftLineDao = db.draftLineDao()

    @Provides
    fun provideDraftSerialDao(db: AppDatabase): DraftSerialDao = db.draftSerialDao()

    @Provides
    fun provideDraftPhotoDao(db: AppDatabase): DraftPhotoDao = db.draftPhotoDao()

    @Provides
    fun provideQueuedSubmissionDao(db: AppDatabase): QueuedSubmissionDao = db.queuedSubmissionDao()
}

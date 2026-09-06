package com.sellernest.poreceiving.work.photo

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PhotoModule {

    @Binds
    @Singleton
    abstract fun bindPhotoUploadScheduler(impl: WorkManagerPhotoUploadScheduler): PhotoUploadScheduler
}

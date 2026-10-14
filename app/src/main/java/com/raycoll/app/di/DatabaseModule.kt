package com.raycoll.app.di

import android.content.Context
import androidx.room.Room
import com.raycoll.app.data.local.db.RayCollDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): RayCollDatabase =
        Room.databaseBuilder(context, RayCollDatabase::class.java, RayCollDatabase.NAME).build()
}

package com.raycoll.app.data.local.db

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context

@Database(
    entities = [DeckEntity::class, CardEntity::class, StudyModeEntity::class, ActivityDayEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class RayCollDatabase : RoomDatabase() {
    abstract fun deckDao(): DeckDao
    abstract fun studyModeDao(): StudyModeDao
    abstract fun activityDao(): ActivityDao

    companion object {
        fun create(context: Context): RayCollDatabase =
            Room.databaseBuilder(context, RayCollDatabase::class.java, "raycoll.db")
                .fallbackToDestructiveMigration()
                .build()

        fun createInMemory(context: Context): RayCollDatabase =
            Room.inMemoryDatabaseBuilder(context, RayCollDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }
}

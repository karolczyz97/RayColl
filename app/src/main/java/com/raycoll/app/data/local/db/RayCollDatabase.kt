package com.raycoll.app.data.local.db

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context

// exportSchema=false świadomie: schemat v1, zero migracji (fallbackToDestructiveMigration),
// brak wtyczki androidx.room z schemaDirectory — eksport nic tu nie daje, a warning myli.
@Database(
    entities = [DeckEntity::class, CardEntity::class, StudyModeEntity::class, ActivityDayEntity::class],
    version = 1,
    exportSchema = false,
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

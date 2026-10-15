package com.raycoll.app.data.local.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

// Schemat eksportowany do app/schemas — każda zmiana encji wymaga podbicia `version`
// i migracji (Migration albo @AutoMigration); bez niej Room odmówi otwarcia bazy.
@Database(
    entities = [
        DeckEntity::class,
        CardEntity::class,
        StudyModeEntity::class,
        ActivityDayEntity::class,
        NamespaceStateEntity::class
    ],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
    exportSchema = true
)
abstract class RayCollDatabase : RoomDatabase() {
    abstract fun deckDao(): DeckDao
    abstract fun cardDao(): CardDao
    abstract fun studyModeDao(): StudyModeDao
    abstract fun activityDao(): ActivityDao
    abstract fun namespaceStateDao(): NamespaceStateDao

    companion object {
        const val NAME = "raycoll.db"
    }
}

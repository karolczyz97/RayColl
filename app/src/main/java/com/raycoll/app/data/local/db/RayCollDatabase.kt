package com.raycoll.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [GroupEntity::class, CardEntity::class, StudyModeEntity::class, ActivityDayEntity::class], version = 1, exportSchema = false)
abstract class RayCollDatabase : RoomDatabase() {
    abstract fun groupDao(): GroupDao
    abstract fun cardDao(): CardDao
    abstract fun studyModeDao(): StudyModeDao
    abstract fun activityDayDao(): ActivityDayDao
}

package com.raycoll.app.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "activity_days")
data class ActivityDayEntity(
    @PrimaryKey val date: String = "",
    @ColumnInfo(name = "review_count") val count: Int = 0,
)

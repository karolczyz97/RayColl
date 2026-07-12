package com.raycoll.app.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "study_modes")
data class StudyModeEntity(
    @PrimaryKey val id: String = "",
    val name: String = "",
    val steps: String = "[]",
    @ColumnInfo(name = "is_built_in") val isBuiltIn: Boolean = false,
    @ColumnInfo(name = "built_in_source_id") val builtInSourceId: String? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = 0L,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)

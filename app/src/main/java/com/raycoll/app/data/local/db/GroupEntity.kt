package com.raycoll.app.data.local.db

import androidx.room.Entity

@Entity(tableName = "groups")
data class GroupEntity(
    @androidx.room.PrimaryKey val id: String = "",
    val name: String = "",
    @androidx.room.ColumnInfo(name = "active_mode_id") val activeModeId: String = "classic",
    @androidx.room.ColumnInfo(name = "page_names") val pageNames: String = "[]",
    @androidx.room.ColumnInfo(name = "page_languages") val pageLanguages: String = "[]",
    @androidx.room.ColumnInfo(name = "active_page_count") val activePageCount: Int = 2,
    @androidx.room.ColumnInfo(name = "study_filter") val studyFilter: String = "new_review",
    @androidx.room.ColumnInfo(name = "card_order") val cardOrder: String = "sequential",
    @androidx.room.ColumnInfo(name = "updated_at") val updatedAt: Long = 0L,
    @androidx.room.ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @androidx.room.ColumnInfo(name = "archived_at") val archivedAt: Long? = null,
)

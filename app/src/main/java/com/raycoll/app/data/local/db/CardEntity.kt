package com.raycoll.app.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cards",
    foreignKeys = [ForeignKey(entity = GroupEntity::class, parentColumns = ["id"], childColumns = ["group_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("group_id")]
)
data class CardEntity(
    @PrimaryKey val id: String = "",
    @ColumnInfo(name = "group_id") val groupId: String = "",
    val pages: String = "[]",
    @ColumnInfo(name = "srs_difficulty") val srsDifficulty: Double = 5.0,
    @ColumnInfo(name = "srs_stability") val srsStability: Double = 0.0,
    @ColumnInfo(name = "srs_repetitions") val srsRepetitions: Int = 0,
    @ColumnInfo(name = "srs_state") val srsState: Int = 0,
    @ColumnInfo(name = "srs_last_review") val srsLastReview: Long = 0L,
    @ColumnInfo(name = "srs_next_review") val srsNextReview: Long = 0L,
    @ColumnInfo(name = "content_updated_at") val contentUpdatedAt: Long = 0L,
    @ColumnInfo(name = "srs_updated_at") val srsUpdatedAt: Long = 0L,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)

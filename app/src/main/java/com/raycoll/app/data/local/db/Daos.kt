package com.raycoll.app.data.local.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao interface GroupDao {
    @Query("SELECT * FROM groups WHERE deleted_at IS NULL AND archived_at IS NULL ORDER BY rowid ASC")
    fun activeGroups(): Flow<List<GroupEntity>>
    @Query("SELECT * FROM groups WHERE deleted_at IS NULL AND archived_at IS NOT NULL ORDER BY archived_at DESC")
    fun archivedGroups(): Flow<List<GroupEntity>>
    @Query("SELECT * FROM groups")
    suspend fun allGroups(): List<GroupEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(group: GroupEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(groups: List<GroupEntity>)
    @Transaction suspend fun replaceAll(groups: List<GroupEntity>) { deleteAll(); if (groups.isNotEmpty()) insertAll(groups) }
    @Query("DELETE FROM groups") suspend fun deleteAll()
}

@Dao interface CardDao {
    @Query("SELECT * FROM cards WHERE group_id = :groupId AND deleted_at IS NULL")
    fun liveCardsByGroup(groupId: String): Flow<List<CardEntity>>
    @Query("SELECT * FROM cards WHERE group_id = :groupId")
    suspend fun allCardsByGroup(groupId: String): List<CardEntity>
    @Query("SELECT * FROM cards") suspend fun allCards(): List<CardEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(card: CardEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(cards: List<CardEntity>)
    @Transaction suspend fun replaceAllForGroup(groupId: String, cards: List<CardEntity>) { deleteAllForGroup(groupId); if (cards.isNotEmpty()) insertAll(cards) }
    @Query("DELETE FROM cards WHERE group_id = :groupId") suspend fun deleteAllForGroup(groupId: String)
    @Query("DELETE FROM cards") suspend fun deleteAll()
}

@Dao interface StudyModeDao {
    @Query("SELECT * FROM study_modes WHERE deleted_at IS NULL") fun liveModes(): Flow<List<StudyModeEntity>>
    @Query("SELECT * FROM study_modes") suspend fun allModes(): List<StudyModeEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(mode: StudyModeEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(modes: List<StudyModeEntity>)
    @Transaction suspend fun replaceAll(modes: List<StudyModeEntity>) { deleteAll(); if (modes.isNotEmpty()) insertAll(modes) }
    @Query("DELETE FROM study_modes") suspend fun deleteAll()
}

@Dao interface ActivityDayDao {
    @Query("SELECT * FROM activity_days") fun heatmap(): Flow<List<ActivityDayEntity>>
    @Query("SELECT * FROM activity_days") suspend fun allDays(): List<ActivityDayEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(day: ActivityDayEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(days: List<ActivityDayEntity>)
    @Transaction suspend fun replaceAll(days: List<ActivityDayEntity>) { deleteAll(); if (days.isNotEmpty()) insertAll(days) }
    @Query("DELETE FROM activity_days") suspend fun deleteAll()
}

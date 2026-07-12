package com.raycoll.app.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface DeckDao {
    @Query("SELECT * FROM decks WHERE namespace = :ns AND deletedAt IS NULL AND archivedAt IS NULL ORDER BY rowId")
    fun activeDecks(ns: String): Flow<List<DeckEntity>>

    @Query("SELECT * FROM decks WHERE namespace = :ns AND deletedAt IS NULL AND archivedAt IS NOT NULL ORDER BY archivedAt DESC")
    fun archivedDecks(ns: String): Flow<List<DeckEntity>>

    @Query("SELECT * FROM decks WHERE namespace = :ns AND id = :deckId")
    suspend fun getDeck(ns: String, deckId: String): DeckEntity?

    @Query("SELECT * FROM cards WHERE namespace = :ns AND deckId = :deckId AND deletedAt IS NULL")
    fun cardsForDeck(ns: String, deckId: String): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE namespace = :ns AND deckId = :deckId AND deletedAt IS NULL")
    suspend fun getCards(ns: String, deckId: String): List<CardEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeck(deck: DeckEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDecks(decks: List<DeckEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCard(card: CardEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCards(cards: List<CardEntity>)

    @Query("UPDATE decks SET deletedAt = :now WHERE namespace = :ns AND id = :deckId")
    suspend fun softDeleteDeck(ns: String, deckId: String, now: Long)

    @Query("UPDATE decks SET archivedAt = :now, updatedAt = :now WHERE namespace = :ns AND id = :deckId")
    suspend fun archiveDeck(ns: String, deckId: String, now: Long)

    @Query("UPDATE decks SET archivedAt = NULL, updatedAt = :now WHERE namespace = :ns AND id = :deckId")
    suspend fun restoreDeck(ns: String, deckId: String, now: Long)

    @Query("UPDATE cards SET deletedAt = :now WHERE namespace = :ns AND id = :cardId")
    suspend fun softDeleteCard(ns: String, cardId: String, now: Long)

    @Query("SELECT * FROM decks WHERE namespace = :ns")
    suspend fun allDecks(ns: String): List<DeckEntity>

    @Query("SELECT * FROM cards WHERE namespace = :ns")
    suspend fun allCards(ns: String): List<CardEntity>

    @Query("DELETE FROM decks WHERE namespace = :ns")
    suspend fun deleteAllDecks(ns: String)

    @Query("DELETE FROM cards WHERE namespace = :ns")
    suspend fun deleteAllCards(ns: String)
}

@Dao
interface StudyModeDao {
    @Query("SELECT * FROM study_modes WHERE namespace = :ns AND deletedAt IS NULL")
    fun liveModes(ns: String): Flow<List<StudyModeEntity>>

    @Query("SELECT * FROM study_modes WHERE namespace = :ns")
    suspend fun allModes(ns: String): List<StudyModeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMode(mode: StudyModeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModes(modes: List<StudyModeEntity>)

    @Query("UPDATE study_modes SET deletedAt = :now WHERE namespace = :ns AND id = :modeId")
    suspend fun softDeleteMode(ns: String, modeId: String, now: Long)

    @Query("DELETE FROM study_modes WHERE namespace = :ns")
    suspend fun deleteAllModes(ns: String)
}

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activity_days WHERE namespace = :ns")
    fun heatmap(ns: String): Flow<List<ActivityDayEntity>>

    @Query("SELECT * FROM activity_days WHERE namespace = :ns")
    suspend fun allActivity(ns: String): List<ActivityDayEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertActivity(day: ActivityDayEntity)

    @Query("UPDATE activity_days SET count = count + 1 WHERE namespace = :ns AND dayKey = :dayKey")
    suspend fun incrementActivity(ns: String, dayKey: String)

    @Query("DELETE FROM activity_days WHERE namespace = :ns")
    suspend fun deleteAllActivity(ns: String)
}

package com.raycoll.app.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DeckDao {
    @Query("SELECT * FROM decks WHERE namespace = :ns AND deletedAt IS NULL AND archivedAt IS NULL ORDER BY rowid")
    fun activeDecks(ns: String): Flow<List<DeckEntity>>

    @Query(
        "SELECT * FROM decks WHERE namespace = :ns AND deletedAt IS NULL AND archivedAt IS NOT NULL ORDER BY archivedAt DESC"
    )
    fun archivedDecks(ns: String): Flow<List<DeckEntity>>

    /** Talia do pokazania (także zarchiwizowana); usunięta (tombstone) = null. */
    @Query("SELECT * FROM decks WHERE namespace = :ns AND id = :deckId AND deletedAt IS NULL")
    fun liveDeck(ns: String, deckId: String): Flow<DeckEntity?>

    @Query("SELECT * FROM decks WHERE namespace = :ns AND id = :deckId AND deletedAt IS NULL")
    suspend fun getLiveDeck(ns: String, deckId: String): DeckEntity?

    /** Wszystkie talie, także usunięte (tombstony) i zarchiwizowane. */
    @Query("SELECT * FROM decks WHERE namespace = :ns ORDER BY rowid")
    suspend fun allDecks(ns: String): List<DeckEntity>

    @Upsert
    suspend fun upsertDeck(deck: DeckEntity)

    // Soft delete nie zmienia updatedAt: tombstone ma własny deletedAt, a późniejsza edycja ma wygrać
    // z usunięciem (MergeEngine.isAlive); to samo dla kart i trybów.
    @Query("UPDATE decks SET deletedAt = :now WHERE namespace = :ns AND id = :deckId")
    suspend fun softDeleteDeck(ns: String, deckId: String, now: Long)

    @Query("UPDATE decks SET archivedAt = :now, updatedAt = :now WHERE namespace = :ns AND id = :deckId")
    suspend fun archiveDeck(ns: String, deckId: String, now: Long)

    @Query("UPDATE decks SET archivedAt = NULL, updatedAt = :now WHERE namespace = :ns AND id = :deckId")
    suspend fun restoreDeck(ns: String, deckId: String, now: Long)

    @Query("DELETE FROM decks WHERE namespace = :ns")
    suspend fun deleteAllDecks(ns: String)

    // Punktowe zmiany ustawień talii: każda zmienia tylko swoje kolumny (+ updatedAt), więc
    // równoległe edycje różnych pól się nie nadpisują. Zwracają liczbę zmienionych wierszy.

    @Query(
        "UPDATE decks SET name = :name, updatedAt = :now " +
            "WHERE namespace = :ns AND id = :deckId AND deletedAt IS NULL"
    )
    suspend fun updateName(ns: String, deckId: String, name: String, now: Long): Int

    @Query(
        "UPDATE decks SET activeModeId = :modeId, updatedAt = :now " +
            "WHERE namespace = :ns AND id = :deckId AND deletedAt IS NULL"
    )
    suspend fun updateActiveMode(ns: String, deckId: String, modeId: String, now: Long): Int

    @Query(
        "UPDATE decks SET studyFilter = :filter, updatedAt = :now " +
            "WHERE namespace = :ns AND id = :deckId AND deletedAt IS NULL"
    )
    suspend fun updateStudyFilter(ns: String, deckId: String, filter: String, now: Long): Int

    @Query(
        "UPDATE decks SET cardOrder = :order, updatedAt = :now " +
            "WHERE namespace = :ns AND id = :deckId AND deletedAt IS NULL"
    )
    suspend fun updateCardOrder(ns: String, deckId: String, order: String, now: Long): Int

    /** Nazwy, języki i liczba stron — zmieniane razem (w transakcji repozytorium). */
    @Query(
        "UPDATE decks SET pageNamesJson = :pageNamesJson, pageLanguagesJson = :pageLanguagesJson, " +
            "activePageCount = :activePageCount, updatedAt = :now " +
            "WHERE namespace = :ns AND id = :deckId AND deletedAt IS NULL"
    )
    suspend fun updatePageMetadata(
        ns: String,
        deckId: String,
        pageNamesJson: String,
        pageLanguagesJson: String,
        activePageCount: Int,
        now: Long
    ): Int
}

@Dao
interface CardDao {
    /**
     * Karty do widoków list talii (aktywnych i zarchiwizowanych): bez usuniętych kart
     * i bez kart usuniętych talii. Snapshot synchronizacji czyta [allCards].
     */
    @Query(
        "SELECT c.* FROM cards c INNER JOIN decks d ON d.id = c.deckId AND d.namespace = c.namespace " +
            "WHERE c.namespace = :ns AND c.deletedAt IS NULL AND d.deletedAt IS NULL ORDER BY c.rowid"
    )
    fun liveCards(ns: String): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE namespace = :ns AND deckId = :deckId AND deletedAt IS NULL ORDER BY rowid")
    fun liveCardsOf(ns: String, deckId: String): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE namespace = :ns AND deckId = :deckId AND deletedAt IS NULL ORDER BY rowid")
    suspend fun getLiveCardsOf(ns: String, deckId: String): List<CardEntity>

    @Query("SELECT * FROM cards WHERE namespace = :ns AND id = :cardId")
    suspend fun getCard(ns: String, cardId: String): CardEntity?

    /** Wszystkie karty, także usunięte (tombstony) — baza snapshotu do synchronizacji. */
    @Query("SELECT * FROM cards WHERE namespace = :ns ORDER BY rowid")
    suspend fun allCards(ns: String): List<CardEntity>

    @Upsert
    suspend fun upsertCard(card: CardEntity)

    @Upsert
    suspend fun upsertCards(cards: List<CardEntity>)

    @Query("UPDATE cards SET deletedAt = :now WHERE namespace = :ns AND id = :cardId")
    suspend fun softDeleteCard(ns: String, cardId: String, now: Long)

    /** Sama treść karty; stan SRS, talia i tombstone zostają. Zwraca liczbę zmienionych wierszy. */
    @Query(
        "UPDATE cards SET pagesJson = :pagesJson, contentUpdatedAt = :now " +
            "WHERE namespace = :ns AND id = :cardId AND deletedAt IS NULL"
    )
    suspend fun updateCardContent(ns: String, cardId: String, pagesJson: String, now: Long): Int
}

@Dao
interface StudyModeDao {
    @Query("SELECT * FROM study_modes WHERE namespace = :ns AND deletedAt IS NULL ORDER BY rowid")
    fun liveModes(ns: String): Flow<List<StudyModeEntity>>

    @Query("SELECT * FROM study_modes WHERE namespace = :ns AND id = :modeId")
    suspend fun getMode(ns: String, modeId: String): StudyModeEntity?

    /** Wszystkie tryby, także usunięte (tombstony). */
    @Query("SELECT * FROM study_modes WHERE namespace = :ns ORDER BY rowid")
    suspend fun allModes(ns: String): List<StudyModeEntity>

    @Upsert
    suspend fun upsertMode(mode: StudyModeEntity)

    @Upsert
    suspend fun upsertModes(modes: List<StudyModeEntity>)

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

    @Upsert
    suspend fun upsertActivity(days: List<ActivityDayEntity>)

    /** +1 ocena w danym dniu; wiersz dnia powstaje przy pierwszej ocenie. */
    @Transaction
    suspend fun incrementActivity(ns: String, dayKey: String) {
        // dwa zapytania zamiast UPSERT w SQL — ten wymaga SQLite 3.24 (Android 11+)
        insertDayIfMissing(ns, dayKey)
        incrementDay(ns, dayKey)
    }

    @Query("INSERT OR IGNORE INTO activity_days (namespace, dayKey, count) VALUES (:ns, :dayKey, 0)")
    suspend fun insertDayIfMissing(ns: String, dayKey: String)

    @Query("UPDATE activity_days SET count = count + 1 WHERE namespace = :ns AND dayKey = :dayKey")
    suspend fun incrementDay(ns: String, dayKey: String)

    @Query("DELETE FROM activity_days WHERE namespace = :ns")
    suspend fun deleteAllActivity(ns: String)
}

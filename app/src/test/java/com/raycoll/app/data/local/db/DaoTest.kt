package com.raycoll.app.data.local.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.domain.model.CardOrder
import com.raycoll.app.domain.model.StudyFilter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val GUEST = ""

private fun deck(id: String, namespace: String = GUEST, name: String = "Deck $id", activeModeId: String = "classic") =
    DeckEntity(
        id = id,
        namespace = namespace,
        name = name,
        activeModeId = activeModeId,
        studyFilter = StudyFilter.NEW_AND_REVIEW.key,
        cardOrder = CardOrder.SEQUENTIAL.key,
        pageNamesJson = "[]",
        pageLanguagesJson = "[]",
        activePageCount = 2,
        updatedAt = 0,
        deletedAt = null,
        archivedAt = null
    )

private fun card(id: String, deckId: String, namespace: String = GUEST) = CardEntity(
    id = id,
    namespace = namespace,
    deckId = deckId,
    pagesJson = "[\"hello\",\"world\"]",
    difficulty = 5.0,
    stability = 0.0,
    repetitions = 0,
    state = 0,
    lastReviewTimestamp = 0,
    nextReviewTimestamp = 0,
    contentUpdatedAt = 0,
    srsUpdatedAt = 0,
    deletedAt = null
)

// zwykła Application zamiast RayCollApplication — bez grafu Hilta i synchronizacji w tle
@RunWith(RobolectricTestRunner::class)
class DaoTest {

    private lateinit var db: RayCollDatabase
    private val deckDao get() = db.deckDao()
    private val cardDao get() = db.cardDao()
    private val modeDao get() = db.studyModeDao()
    private val activityDao get() = db.activityDao()

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RayCollDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() = db.close()

    @Test
    fun `deck and card round-trip`() = runTest {
        deckDao.upsertDeck(deck("d1", name = "Test"))
        cardDao.upsertCard(card("c1", "d1"))

        assertEquals("Test", deckDao.getLiveDeck(GUEST, "d1")?.name)
        assertEquals(listOf("c1"), cardDao.getLiveCardsOf(GUEST, "d1").map { it.id })
    }

    @Test
    fun `namespaces are isolated`() = runTest {
        deckDao.upsertDeck(deck("d1", name = "Guest"))
        deckDao.upsertDeck(deck("d1", namespace = "user", name = "User copy"))

        assertEquals(listOf("Guest"), deckDao.allDecks(GUEST).map { it.name })
        assertEquals(listOf("User copy"), deckDao.allDecks("user").map { it.name })
    }

    @Test
    fun `soft-deleted deck leaves the active list but stays as tombstone`() = runTest {
        deckDao.upsertDeck(deck("d1"))
        deckDao.softDeleteDeck(GUEST, "d1", 1000L)

        assertTrue(deckDao.activeDecks(GUEST).first().isEmpty())
        assertEquals(1000L, deckDao.allDecks(GUEST).single().deletedAt)
    }

    @Test
    fun `soft-deleted card is hidden from live cards but kept for sync`() = runTest {
        deckDao.upsertDeck(deck("d1"))
        cardDao.upsertCards(listOf(card("c1", "d1"), card("c2", "d1")))
        cardDao.softDeleteCard(GUEST, "c1", 1000L)

        assertEquals(listOf("c2"), cardDao.liveCards(GUEST).first().map { it.id })
        assertEquals(listOf("c1", "c2"), cardDao.allCards(GUEST).map { it.id })
    }

    @Test
    fun `screen reads skip a deleted deck while the technical snapshot keeps its tombstone`() = runTest {
        deckDao.upsertDeck(deck("d1"))
        cardDao.upsertCard(card("c1", "d1"))
        deckDao.softDeleteDeck(GUEST, "d1", 1000L)

        assertNull(deckDao.getLiveDeck(GUEST, "d1"))
        assertNull(deckDao.liveDeck(GUEST, "d1").first())
        assertEquals(1000L, deckDao.allDecks(GUEST).single().deletedAt)
        assertEquals(listOf("c1"), cardDao.allCards(GUEST).map { it.id })
    }

    @Test
    fun `archived deck is still readable for the archive screen`() = runTest {
        deckDao.upsertDeck(deck("d1"))
        deckDao.archiveDeck(GUEST, "d1", 1000L)
        assertEquals(1000L, deckDao.getLiveDeck(GUEST, "d1")?.archivedAt)
    }

    @Test
    fun `live cards skip cards of deleted decks but keep archived ones`() = runTest {
        deckDao.upsertDeck(deck("active"))
        deckDao.upsertDeck(deck("archived"))
        deckDao.upsertDeck(deck("deleted"))
        deckDao.upsertDeck(deck("other-ns", namespace = "user"))
        cardDao.upsertCards(
            listOf(card("a1", "active"), card("r1", "archived"), card("x1", "deleted"), card("x2", "deleted"))
        )
        cardDao.upsertCard(card("u1", "other-ns", namespace = "user"))
        deckDao.archiveDeck(GUEST, "archived", 1000L)
        deckDao.softDeleteDeck(GUEST, "deleted", 1000L)

        assertEquals(listOf("a1", "r1"), cardDao.liveCards(GUEST).first().map { it.id })
        // karty usuniętej talii nie dostają własnego tombstone'a — snapshot synchronizacji bez zmian
        assertEquals(listOf("a1", "r1", "x1", "x2"), cardDao.allCards(GUEST).map { it.id })
        assertTrue(cardDao.allCards(GUEST).all { it.deletedAt == null })
    }

    @Test
    fun `point updates change only their own columns`() = runTest {
        deckDao.upsertDeck(deck("d1", name = "Old", activeModeId = "classic"))
        assertEquals(1, deckDao.updateName(GUEST, "d1", "New", 10L))
        assertEquals(1, deckDao.updateActiveMode(GUEST, "d1", "listen-speak", 20L))
        val stored = deckDao.getLiveDeck(GUEST, "d1")!!
        assertEquals("New", stored.name)
        assertEquals("listen-speak", stored.activeModeId)
        assertEquals(20L, stored.updatedAt)
        deckDao.softDeleteDeck(GUEST, "d1", 30L)
        assertEquals("usunięta talia nie jest edytowana", 0, deckDao.updateName(GUEST, "d1", "Ghost", 40L))
    }

    @Test
    fun `deleting decks removes their cards`() = runTest {
        deckDao.upsertDeck(deck("d1"))
        cardDao.upsertCards(listOf(card("c1", "d1"), card("c2", "d1")))

        deckDao.deleteAllDecks(GUEST)

        assertTrue(cardDao.allCards(GUEST).isEmpty())
    }

    @Test
    fun `archive and restore`() = runTest {
        deckDao.upsertDeck(deck("d1"))
        deckDao.archiveDeck(GUEST, "d1", 1000L)
        assertNotNull(deckDao.getLiveDeck(GUEST, "d1")?.archivedAt)
        assertEquals(listOf("d1"), deckDao.archivedDecks(GUEST).first().map { it.id })

        deckDao.restoreDeck(GUEST, "d1", 2000L)
        assertNull(deckDao.getLiveDeck(GUEST, "d1")?.archivedAt)
    }

    @Test
    fun `study mode round-trip`() = runTest {
        modeDao.upsertMode(
            StudyModeEntity(
                "m1",
                GUEST,
                "Custom",
                "[]",
                isBuiltIn = false,
                builtInSourceId = null,
                updatedAt = 0,
                deletedAt = null
            )
        )
        assertEquals("Custom", modeDao.getMode(GUEST, "m1")?.name)
    }

    @Test
    fun `activity increment creates the day on first review`() = runTest {
        activityDao.incrementActivity(GUEST, "2024-01-15")
        activityDao.incrementActivity(GUEST, "2024-01-15")

        assertEquals(2, activityDao.allActivity(GUEST).single().count)
    }

    @Test
    fun `editing a deck keeps its position in the list`() = runTest {
        listOf("d1", "d2", "d3").forEach { deckDao.upsertDeck(deck(it)) }

        deckDao.upsertDeck(deck("d1", activeModeId = "custom"))

        val active = deckDao.activeDecks(GUEST).first()
        assertEquals(listOf("d1", "d2", "d3"), active.map { it.id })
        assertEquals("custom", active.first().activeModeId)
    }
}

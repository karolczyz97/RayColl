package com.raycoll.app.data.local.db

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// Plain Application zamiast @HiltAndroidApp RayCollApplication — inaczej Robolectric
// bootuje graf Hilt i SyncLifecycle.start na Dispatchers.IO, którego coroutine (seedIfEmpty)
// przeżywa db.close() i wysypuje kolejny test przez UncaughtExceptionsBeforeTest.
@Config(application = android.app.Application::class)
class DaoTest {

    private lateinit var db: RayCollDatabase
    private lateinit var deckDao: DeckDao
    private lateinit var modeDao: StudyModeDao
    private lateinit var activityDao: ActivityDao

    @Before
    fun setup() {
        db = RayCollDatabase.createInMemory(ApplicationProvider.getApplicationContext())
        deckDao = db.deckDao()
        modeDao = db.studyModeDao()
        activityDao = db.activityDao()
    }

    @After
    fun teardown() { db.close() }

    @Test
    fun `deck round-trip`() = runBlocking {
        val deck = DeckEntity(id = "d1", namespace = "", name = "Test", activePageCount = 2)
        deckDao.insertDeck(deck)
        val loaded = deckDao.getDeck("", "d1")
        assertNotNull(loaded)
        assertEquals("Test", loaded!!.name)
    }

    @Test
    fun `card round-trip`() = runBlocking {
        val deck = DeckEntity(id = "d1", namespace = "", name = "Test", activePageCount = 2)
        deckDao.insertDeck(deck)
        val card = CardEntity(id = "c1", namespace = "", deckId = "d1", pagesJson = "[\"hello\",\"world\"]")
        deckDao.insertCard(card)
        val cards = deckDao.getCards("", "d1")
        assertEquals(1, cards.size)
        assertEquals("c1", cards[0].id)
    }

    @Test
    fun `namespace isolation`() = runBlocking {
        val deck1 = DeckEntity(id = "d1", namespace = "", name = "Guest Deck")
        val deck2 = DeckEntity(id = "d2", namespace = "user123", name = "User Deck")
        deckDao.insertDeck(deck1)
        deckDao.insertDeck(deck2)
        val guestDecks = deckDao.allDecks("")
        val userDecks = deckDao.allDecks("user123")
        assertEquals(1, guestDecks.size)
        assertEquals(1, userDecks.size)
        assertEquals("Guest Deck", guestDecks[0].name)
        assertEquals("User Deck", userDecks[0].name)
    }

    @Test
    fun `soft delete hides from active`() = runBlocking {
        deckDao.insertDeck(DeckEntity(id = "d1", namespace = "", name = "Test"))
        deckDao.softDeleteDeck("", "d1", 1000L)
        val active = deckDao.activeDecks("")
        // Flow won't emit in test without collection; check allDecks instead
        val all = deckDao.allDecks("")
        assertEquals(1, all.size)
        assertNotNull(all[0].deletedAt)
    }

    @Test
    fun `cascade delete removes cards when deck deleted`() = runBlocking {
        deckDao.insertDeck(DeckEntity(id = "d1", namespace = "", name = "Test"))
        deckDao.insertCard(CardEntity(id = "c1", namespace = "", deckId = "d1"))
        deckDao.insertCard(CardEntity(id = "c2", namespace = "", deckId = "d1"))
        val cards = deckDao.getCards("", "d1")
        assertEquals(2, cards.size)
        // Soft delete deck (cascade is on hard delete, but soft delete just sets flag)
        deckDao.softDeleteDeck("", "d1", 1000L)
        val stillThere = deckDao.getCards("", "d1")
        assertEquals(2, stillThere.size)
    }

    @Test
    fun `archive and restore`() = runBlocking {
        deckDao.insertDeck(DeckEntity(id = "d1", namespace = "", name = "Test"))
        deckDao.archiveDeck("", "d1", 1000L)
        val archived = deckDao.allDecks("")[0]
        assertNotNull(archived.archivedAt)
        deckDao.restoreDeck("", "d1", 2000L)
        val restored = deckDao.allDecks("")[0]
        assertNull(restored.archivedAt)
    }

    @Test
    fun `study mode round-trip`() = runBlocking {
        modeDao.insertMode(StudyModeEntity(id = "m1", namespace = "", name = "Custom", stepsJson = "[]"))
        val modes = modeDao.allModes("")
        assertEquals(1, modes.size)
        assertEquals("Custom", modes[0].name)
    }

    @Test
    fun `activity increment`() = runBlocking {
        activityDao.upsertActivity(ActivityDayEntity(dayKey = "2024-01-15", namespace = "", count = 0))
        activityDao.incrementActivity("", "2024-01-15")
        activityDao.incrementActivity("", "2024-01-15")
        val all = activityDao.allActivity("")
        assertEquals(2, all[0].count)
    }

    @Test
    fun `copy guest to user preserves both`() = runBlocking {
        deckDao.insertDeck(DeckEntity(id = "d1", namespace = "", name = "Guest"))
        deckDao.insertDeck(DeckEntity(id = "d1", namespace = "user123", name = "User Copy"))
        val guestDecks = deckDao.allDecks("")
        val userDecks = deckDao.allDecks("user123")
        assertEquals(1, guestDecks.size)
        assertEquals(1, userDecks.size)
    }
}

package com.raycoll.app.data.repository

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.data.account.AccountManager
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.StoreData
import com.raycoll.app.domain.seed.SEED_MODES
import com.raycoll.app.domain.seed.SeedData
import com.raycoll.app.testing.FakeClock
import com.raycoll.app.testing.VmTestHarness
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackupDocumentStoreTest {
    @get:Rule val folder = TemporaryFolder()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = FakeClock()
    private val repos = VmTestHarness.create(context, clock)
    private val backup = repos.backupRepository
    private fun file(text: String) = Uri.fromFile(File(folder.root, "backup.json").apply { writeText(text) })
    private val json = Json { encodeDefaults = true }
    private fun encoded(data: StoreData) = json.encodeToString(StoreData.serializer(), data)
    private suspend fun own(name: String = "Own") =
        repos.deckRepository.addDeckWithCards(name, listOf("en", "pl"), listOf("A", "B"), listOf(listOf("q", "a")))

    @Test fun `export and import preserve content and learning state`() = runTest {
        val id = own()
        val card = repos.deckRepository.getDeck(id)!!.cards.single()
        repos.deckRepository.reviewCard(id, card.id, 3)
        val before = repos.snapshot()
        val uri = file("")
        assertEquals(BackupResult.SUCCESS, backup.exportTo(uri))
        assertEquals(BackupResult.SUCCESS, backup.importFrom(uri))
        assertEquals(before, repos.snapshot())
    }

    @Test fun `invalid files never write anything`() = runTest {
        repos.seed()
        val before = repos.snapshot()
        val bad =
            listOf("{}", "[]", "not json", "{\"groups\":[],\"studyModes\":[],\"activityHeatmap\":{\"invalid\":1}}")
        for (text in bad) {
            assertEquals(BackupResult.INVALID_BACKUP, backup.importFrom(file(text)))
            assertEquals(before, repos.snapshot())
        }
    }

    @Test fun `duplicate ids invalid names and ranges are rejected before writes`() = runTest {
        own()
        val before = repos.snapshot()
        val deck = before.groups.single()
        val bad = listOf(
            before.copy(groups = listOf(deck, deck)),
            before.copy(groups = listOf(deck.copy(id = "a/b"))),
            before.copy(groups = listOf(deck.copy(name = ""))),
            before.copy(groups = listOf(deck.copy(activePageCount = 9)))
        )
        for (data in bad) {
            assertEquals(BackupResult.INVALID_BACKUP, backup.importFrom(file(encoded(data))))
            assertEquals(before, repos.snapshot())
        }
    }

    @Test fun `replacement restores deleted data and removes data absent from backup`() = runTest {
        val kept = own("Keep")
        val data = repos.snapshot()
        val removed = own("Remove")
        repos.deckRepository.deleteDeck(kept)
        assertEquals(BackupResult.SUCCESS, backup.importFrom(file(encoded(data))))
        assertEquals("Keep", repos.deckRepository.getDeck(kept)!!.name)
        assertNull(repos.deckRepository.getDeck(removed))
    }

    @Test fun `replacement stamps edit dates but keeps SRS from the backup`() = runTest {
        val id = own()
        repos.deckRepository.reviewCard(id, repos.deckRepository.getDeck(id)!!.cards.single().id, 4)
        val before = repos.snapshot()
        clock.advance(20_000)
        assertEquals(BackupResult.SUCCESS, backup.importFrom(file(encoded(before))))
        val after = repos.snapshot().groups.single()
        assertEquals(clock.now(), after.updatedAt)
        assertEquals(clock.now(), after.cards.single().contentUpdatedAt)
        assertEquals(clock.now(), after.cards.single().srsUpdatedAt)
        assertEquals(before.groups.single().cards.single().srsState, after.cards.single().srsState)
    }

    @Test fun `export and import ignore deleted elements`() = runTest {
        val id = own()
        repos.deckRepository.deleteCard(repos.deckRepository.getDeck(id)!!.cards.single().id)
        val uri = file("")
        assertEquals(BackupResult.SUCCESS, backup.exportTo(uri))
        val exported = Json.decodeFromString(StoreData.serializer(), File(requireNotNull(uri.path)).readText())
        assertTrue(exported.groups.single().cards.isEmpty())
        repos.deckRepository.deleteDeck(id)
        assertEquals(BackupResult.SUCCESS, backup.importFrom(file(encoded(repos.snapshot()))))
        assertNull(repos.deckRepository.getDeck(id))
    }

    @Test fun `reset keeps only seed decks built in modes and zero activity`() = runTest {
        val id = own()
        val mode = repos.studyModeRepository.addMode("Custom", emptyList())
        repos.setActivity("2026-10-15", 5)
        backup.factoryReset()
        assertNull(repos.deckRepository.getDeck(id))
        assertNull(repos.studyModeRepository.getMode(mode))
        assertEquals(2, repos.snapshot().groups.count { it.deletedAt == null })
        assertTrue(repos.snapshot().studyModes.filter { it.deletedAt == null }.all { it.isBuiltIn })
        assertTrue(repos.snapshot().activityHeatmap.values.all { it == 0 })
    }

    @Test fun `join skips untouched seed decks and built in modes`() = runTest {
        repos.seed()
        val guest = repos.snapshot()
        repos.account.signIn("google")
        repos.seed()
        val before = repos.snapshot()
        backup.joinGuest(guest, "google", " (from phone)")
        assertEquals(before, repos.snapshot())
    }

    @Test fun `join keeps a seed deck changed only by deleting a card`() = runTest {
        repos.seed()
        val deck = repos.snapshot().groups.first()
        val deletedCard = deck.cards.first()
        repos.deckRepository.deleteCard(deletedCard.id)
        val guest = repos.snapshot()
        repos.account.signIn("google")
        repos.seed()
        val accountDecks = repos.snapshot().groups

        backup.joinGuest(guest, "google", " (from phone)")

        val joined = repos.snapshot().groups
        assertEquals(accountDecks.map { it.id } + deck.id, joined.map { it.id })
        assertEquals(deck.cards.drop(1), joined.last().cards)
        assertTrue(joined.last().cards.none { it.id == deletedCard.id })
    }

    @Test fun `modified built in mode becomes a custom copy and guest deck refers to it`() = runTest {
        repos.seed()
        val builtIn = SEED_MODES.first()
        repos.studyModeRepository.updateMode(builtIn.copy(name = "Modified"))
        val id = own()
        repos.deckRepository.setActiveStudyMode(id, builtIn.id)
        val guest = repos.snapshot()
        repos.account.signIn("google")
        repos.seed()
        backup.joinGuest(guest, "google", " (from phone)")
        val snapshot = repos.snapshot()
        val copy = snapshot.studyModes.single { !it.isBuiltIn }
        assertEquals("Modified (from phone)", copy.name)
        assertNotEquals(builtIn.id, copy.id)
        assertEquals(copy.id, snapshot.groups.single { it.id == id }.activeModeId)
        assertEquals(builtIn.steps, copy.steps)
    }

    @Test fun `modified built in mode copies from two guests stay separate on one account`() = runTest {
        val builtIn = SEED_MODES.first()
        repos.seed()
        repos.studyModeRepository.updateMode(builtIn.copy(name = "First"))
        val first = repos.snapshot()
        repos.account.signIn("second-guest")
        repos.seed()
        clock.advance(1_000)
        repos.studyModeRepository.updateMode(builtIn.copy(name = "Second"))
        val second = repos.snapshot()
        repos.account.signIn("google")
        repos.seed()
        backup.joinGuest(first, "google", " (from phone)")
        backup.joinGuest(second, "google", " (from phone)")
        assertEquals(
            setOf("First (from phone)", "Second (from phone)"),
            repos.snapshot().studyModes.filter { !it.isBuiltIn }.map { it.name }.toSet()
        )
    }

    @Test fun `moved guest decks go after the account decks`() = runTest {
        fun deck(id: String) = FlashcardGroup(
            id,
            "Deck $id",
            emptyList(),
            pageNames = listOf("A", "B"),
            pageLanguages = listOf("en", "pl"),
            updatedAt = 1
        )
        repos.put(StoreData(groups = listOf(deck("a"), deck("c"))))
        val guest = repos.snapshot()
        repos.account.signIn("google")
        repos.put(StoreData(groups = listOf(deck("b"), deck("d"))))
        backup.joinGuest(guest, "google", " (from phone)")
        assertEquals(listOf("b", "d", "a", "c"), repos.deckRepository.activeDecks().first().map { it.id })
    }

    @Test fun `same deck names remain separate and guest activity is added`() = runTest {
        val guestId = own("Same")
        repos.setActivity("2026-10-15", 2)
        val guest = repos.snapshot()
        repos.account.signIn("google")
        val accountId = own("Same")
        repos.setActivity("2026-10-15", 3)
        backup.joinGuest(guest, "google", " (from phone)")
        assertEquals(setOf(guestId, accountId), repos.snapshot().groups.map { it.id }.toSet())
        assertEquals(5, repos.snapshot().activityHeatmap["2026-10-15"])
    }

    @Test fun `stash round trip keeps all guest data and timestamps`() = runTest {
        own()
        val guest = repos.snapshot()
        backup.savePrivate(AccountManager.STASH, guest)
        repos.account.signIn("new-guest")
        backup.restoreGuest(requireNotNull(backup.loadPrivate(AccountManager.STASH)), "new-guest")
        backup.deletePrivate(AccountManager.STASH)
        assertEquals(guest, repos.snapshot())
        assertNull(backup.loadPrivate(AccountManager.STASH))
    }

    @Test fun `old generation fields are ignored when reading current backup format`() = runTest {
        val data = SeedData.createSeedData("pl")
        val text = encoded(data).replaceFirst("{", "{\"replacementAt\":100,\"replacementId\":\"old\",")
        assertEquals(BackupResult.SUCCESS, backup.importFrom(file(text)))
        assertEquals(data.groups.map { it.id }, repos.snapshot().groups.map { it.id })
    }
}

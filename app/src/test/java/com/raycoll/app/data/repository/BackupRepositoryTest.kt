package com.raycoll.app.data.repository

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.data.files.DocumentFiles
import com.raycoll.app.domain.model.StoreData
import com.raycoll.app.testing.FakeClock
import com.raycoll.app.testing.VmTestHarness
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackupRepositoryTest {
    @get:Rule val folder = TemporaryFolder()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = FakeClock()
    private val repos = VmTestHarness.create(context, clock)
    private val backups = BackupRepository(repos.localStore, DocumentFiles(context), Dispatchers.Unconfined)

    @After fun close() = repos.db.close()

    @Test
    fun `old backup restores the entire account with a fresh generation`() = runBlocking {
        repos.namespaceManager.switchTo("account")
        repos.localStore.replaceAccount(StoreData(activityHeatmap = mapOf("2026-10-14" to 50)))
        val before = repos.localStore.snapshot()
        val oldJson = """{"groups":[],"studyModes":[],"activityHeatmap":{"2026-10-13":2}}"""
        val file = File(folder.root, "old.json").apply { writeText(oldJson) }
        assertEquals(BackupResult.SUCCESS, backups.importFrom(Uri.fromFile(file)))
        val after = repos.localStore.snapshot()
        assertEquals(mapOf("2026-10-13" to 2), after.activityHeatmap)
        assertTrue(after.replacementAt > before.replacementAt)
        assertTrue(after.replacementId != before.replacementId)
        assertTrue(repos.localStore.snapshotOf(NamespaceManager.GUEST).activityHeatmap.isEmpty())
    }

    @Test
    fun `invalid backup leaves contents and generation untouched`() = runBlocking {
        repos.localStore.seedIfEmpty()
        val before = repos.localStore.snapshot()
        val blankName = before.copy(groups = before.groups.map { it.copy(name = " ") })
        val file = File(folder.root, "invalid.json").apply {
            writeText(Json { encodeDefaults = true }.encodeToString(StoreData.serializer(), blankName))
        }
        assertEquals(BackupResult.INVALID_BACKUP, backups.importFrom(Uri.fromFile(file)))
        assertEquals(before, repos.localStore.snapshot())
    }

    @Test
    fun `export and import roundtrip retains contents but creates a new replacement`() = runBlocking {
        repos.localStore.seedIfEmpty()
        val before = repos.localStore.snapshot()
        val uri = Uri.fromFile(File(folder.root, "backup.json"))
        assertEquals(BackupResult.SUCCESS, backups.exportTo(uri))
        assertEquals(BackupResult.SUCCESS, backups.importFrom(uri))
        val after = repos.localStore.snapshot()
        assertEquals(before.groups, after.groups)
        assertEquals(before.studyModes, after.studyModes)
        assertTrue(after.replacementAt > 0)
    }

    @Test
    fun `reset does not replace a different namespace`() = runBlocking {
        repos.namespaceManager.switchTo("account")
        repos.localStore.replaceAll(StoreData(activityHeatmap = mapOf("2026-10-14" to 5)))
        repos.namespaceManager.switchToGuest()
        backups.factoryReset()
        assertEquals(mapOf("2026-10-14" to 5), repos.localStore.snapshotOf("account").activityHeatmap)
        assertTrue(repos.localStore.snapshot().groups.isNotEmpty())
    }

    @Test fun `unreadable file preserves contents and generation`() = runBlocking {
        repos.localStore.seedIfEmpty()
        val before = repos.localStore.snapshot()
        assertEquals(BackupResult.FILE_ERROR, backups.importFrom(Uri.fromFile(File(folder.root, "missing.json"))))
        assertEquals(before, repos.localStore.snapshot())
    }

    @Test fun `database failure rolls back replacement contents and generation`() = runBlocking {
        repos.localStore.seedIfEmpty()
        val before = repos.localStore.snapshot()
        val uri = Uri.fromFile(File(folder.root, "valid.json"))
        assertEquals(BackupResult.SUCCESS, backups.exportTo(uri))
        repos.db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_replace BEFORE INSERT ON decks BEGIN SELECT RAISE(ABORT, 'disk failure'); END"
        )
        org.junit.Assert.assertThrows(Exception::class.java) { runBlocking { backups.importFrom(uri) } }
        assertEquals(before, repos.localStore.snapshot())
    }

    @Test fun `backup roundtrip retains tombstones hidden pages and activity`() = runBlocking {
        repos.localStore.seedIfEmpty()
        val initial = repos.localStore.snapshot()
        val deck = initial.groups.first()
        val changed = initial.copy(
            groups = listOf(
                deck.copy(
                    activePageCount = 2,
                    pageNames = listOf("Front", "Back", "Hidden"),
                    pageLanguages = listOf("en", "pl", "de"),
                    cards = deck.cards.mapIndexed { i, card ->
                        card.copy(
                            pages = card.pages.take(2) + "hidden value",
                            deletedAt = if (i == 0) clock.now() else null
                        )
                    }
                )
            ),
            activityHeatmap = mapOf("2026-10-14" to 5)
        )
        repos.localStore.replaceAll(changed)
        val before = repos.localStore.snapshot()
        val uri = Uri.fromFile(File(folder.root, "hidden.json"))
        assertEquals(BackupResult.SUCCESS, backups.exportTo(uri))
        assertEquals(BackupResult.SUCCESS, backups.importFrom(uri))
        val after = repos.localStore.snapshot()
        assertEquals(before.groups, after.groups)
        assertEquals(before.activityHeatmap, after.activityHeatmap)
    }

    @Test fun `legacy built in name is repaired without relaxing validation of custom data`() = runBlocking {
        repos.localStore.seedIfEmpty()
        val before = repos.localStore.snapshot()
        val legacy = before.copy(studyModes = before.studyModes.map { if (it.isBuiltIn) it.copy(name = "") else it })
        val uri = Uri.fromFile(
            File(folder.root, "legacy-modes.json").apply {
                writeText(Json { encodeDefaults = true }.encodeToString(StoreData.serializer(), legacy))
            }
        )
        assertEquals(BackupResult.SUCCESS, backups.importFrom(uri))
        assertEquals(before.studyModes, repos.localStore.snapshot().studyModes)
    }
}

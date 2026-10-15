package com.raycoll.app.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.data.repository.NamespaceManager
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.StoreData
import com.raycoll.app.testing.FakeClock
import com.raycoll.app.testing.VmTestHarness
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MigrationControllerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repos = VmTestHarness.create(context, FakeClock())
    private val controller = MigrationController(repos.localStore, repos.namespaceManager)

    @After fun close() = repos.db.close()

    @Test fun `migrate copies and retains guest contents but does not inherit guest replacement`() = runBlocking {
        repos.localStore.replaceAccount(
            StoreData(
                groups = listOf(
                    FlashcardGroup(
                        "guest-deck",
                        "Mine",
                        listOf(Flashcard("word", listOf("a", "b"), com.raycoll.app.domain.model.SrsState()))
                    )
                )
            )
        )
        val guest = repos.localStore.snapshot()
        assertTrue(guest.replacementAt > 0)
        repos.namespaceManager.switchTo("account")
        controller.execute(MigrationOption.MIGRATE)
        assertEquals(guest.copy(replacementAt = 0, replacementId = ""), repos.localStore.snapshot())
        assertEquals(guest, repos.localStore.snapshotOf(NamespaceManager.GUEST))
        controller.execute(MigrationOption.MIGRATE)
        assertEquals(1, repos.localStore.snapshot().groups.size)
    }

    @Test fun `fresh starts from stable account seeds and preserves the guest on retry`() = runBlocking {
        repos.localStore.seedIfEmpty()
        val guest = repos.localStore.snapshot()
        repos.namespaceManager.switchTo("account")
        controller.execute(MigrationOption.FRESH)
        val first = repos.localStore.snapshot()
        controller.execute(MigrationOption.FRESH)
        assertEquals(first, repos.localStore.snapshot())
        assertEquals(guest, repos.localStore.snapshotOf(NamespaceManager.GUEST))
        assertTrue(first.groups.isNotEmpty())
    }
}

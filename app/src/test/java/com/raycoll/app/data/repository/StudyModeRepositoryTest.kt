package com.raycoll.app.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.data.firestore.UserPaths
import com.raycoll.app.domain.model.ShowPageStep
import com.raycoll.app.testing.FakeClock
import com.raycoll.app.testing.VmTestHarness
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StudyModeRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = FakeClock()
    private val repos = VmTestHarness.create(context, clock)
    private val modes = repos.studyModeRepository
    private val paths = UserPaths("uid-1")

    @Test
    fun `restore brings a deleted mode back with a fresh edit date`() = runTest {
        val id = modes.addMode("Own", listOf(ShowPageStep(pageIndex = 1)))
        clock.advance(1_000)
        modes.deleteMode(id)
        assertNull(modes.getMode(id))

        clock.advance(1_000)
        modes.restoreMode(id)

        val restored = checkNotNull(modes.getMode(id)) { "mode was not restored" }
        assertNull(restored.deletedAt)
        assertEquals("Own", restored.name)
        assertEquals(listOf(ShowPageStep(pageIndex = 1)), restored.steps)
        assertEquals(clock.now(), restored.updatedAt)
    }

    @Test
    fun `restore of a live mode changes nothing`() = runTest {
        val id = modes.addMode("Own", listOf(ShowPageStep(pageIndex = 1)))
        val before = repos.store.cachedDoc(paths.mode(id))
        clock.advance(1_000)

        modes.restoreMode(id)

        assertEquals(before, repos.store.cachedDoc(paths.mode(id)))
    }

    @Test
    fun `restore of an unknown mode writes nothing`() = runTest {
        modes.restoreMode("missing")
        assertNull(repos.store.cachedDoc(paths.mode("missing")))
    }
}

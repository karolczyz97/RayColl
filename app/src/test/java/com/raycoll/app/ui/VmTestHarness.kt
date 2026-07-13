package com.raycoll.app.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.core.Clock
import com.raycoll.app.data.local.db.RayCollDatabase
import com.raycoll.app.data.repository.DataMutations
import com.raycoll.app.data.repository.DeckRepository
import com.raycoll.app.data.repository.NamespaceManager
import com.raycoll.app.data.repository.StudyModeRepository
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.Executor

/** Wspólny szkielet dla testów ViewModelów: in-memory Room + controllable Clock +NamespaceManager. */
class FakeClock(var current: Long = 1_700_000_000_000L) : Clock {
    override fun now(): Long = current
    fun advance(deltaMs: Long) { current += deltaMs }
}

object VmTestHarness {
    fun create(context: Context, clock: Clock): TestRepos {
        // Synchronous executory — Room queries nie lądują na arch_disk_io executorze,
        // więc db.close() w teardown nie zostawia pending coroutines.
        val syncExecutor = Executor { it.run() }
        val db = Room.inMemoryDatabaseBuilder(context, RayCollDatabase::class.java)
            .allowMainThreadQueries()
            .setTransactionExecutor(syncExecutor)
            .setQueryExecutor(syncExecutor)
            .build()
        val ns = NamespaceManager()
        val mutations = DataMutations()
        return TestRepos(
            db = db,
            namespaceManager = ns,
            deckRepository = DeckRepository(db, clock, ns, mutations),
            studyModeRepository = StudyModeRepository(db, clock, ns, mutations),
        )
    }

    /** Wykonaj pending runnables na Android Main Looperze (Room Flow emituje na Main). */
    fun flushMainLooper() {
        ShadowLooper.idleMainLooper()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    }
}

data class TestRepos(
    val db: RayCollDatabase,
    val namespaceManager: NamespaceManager,
    val deckRepository: DeckRepository,
    val studyModeRepository: StudyModeRepository,
)



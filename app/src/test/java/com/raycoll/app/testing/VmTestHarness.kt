package com.raycoll.app.testing

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import com.raycoll.app.core.Clock
import com.raycoll.app.data.local.db.RayCollDatabase
import com.raycoll.app.data.repository.DataMutations
import com.raycoll.app.data.repository.DeckRepository
import com.raycoll.app.data.repository.LocalStore
import com.raycoll.app.data.repository.NamespaceManager
import com.raycoll.app.data.repository.StudyModeRepository
import java.util.concurrent.Executor

/** Zegar sterowany z testu. */
class FakeClock(var current: Long = 1_700_000_000_000L) : Clock {
    override fun now(): Long = current

    fun advance(deltaMs: Long) {
        current += deltaMs
    }
}

/** Repozytoria na bazie w pamięci — wspólne dla testów ViewModeli i repozytoriów. */
data class TestRepos(
    val db: RayCollDatabase,
    val namespaceManager: NamespaceManager,
    val deckRepository: DeckRepository,
    val studyModeRepository: StudyModeRepository,
    val localStore: LocalStore
)

object VmTestHarness {
    fun create(context: Context, clock: Clock): TestRepos {
        // synchroniczne wykonawcy: zapytania Room nie zostają w tle po db.close() w teardown
        val direct = Executor { it.run() }
        val db = Room.inMemoryDatabaseBuilder(context, RayCollDatabase::class.java)
            .allowMainThreadQueries()
            .setTransactionExecutor(direct)
            .setQueryExecutor(direct)
            .build()
        val namespace = NamespaceManager()
        val mutations = DataMutations()
        return TestRepos(
            db = db,
            namespaceManager = namespace,
            deckRepository = DeckRepository(db, clock, namespace, mutations),
            studyModeRepository = StudyModeRepository(db, clock, namespace, mutations),
            localStore = LocalStore(db, namespace, mutations)
        )
    }
}

/** Argumenty trasy nawigacji tak, jak przekazuje je NavHost. */
fun routeArgs(vararg args: Pair<String, Any?>): SavedStateHandle = SavedStateHandle(mapOf(*args))

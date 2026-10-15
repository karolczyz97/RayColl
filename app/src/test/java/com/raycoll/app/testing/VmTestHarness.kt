package com.raycoll.app.testing

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.raycoll.app.core.Clock
import com.raycoll.app.data.files.DocumentFiles
import com.raycoll.app.data.firestore.FieldOp
import com.raycoll.app.data.firestore.UserPaths
import com.raycoll.app.data.firestore.WriteOp
import com.raycoll.app.data.repository.BackupRepository
import com.raycoll.app.data.repository.DeckRepository
import com.raycoll.app.data.repository.StudyModeRepository
import com.raycoll.app.domain.model.StoreData
import kotlinx.coroutines.Dispatchers

class FakeClock(var current: Long = 1_700_000_000_000L) : Clock {
    override fun now(): Long = current
    fun advance(deltaMs: Long) {
        current += deltaMs
    }
}

data class TestRepos(
    val store: InMemoryDocumentStore,
    val account: FakeAccount,
    val deckRepository: DeckRepository,
    val studyModeRepository: StudyModeRepository,
    val backupRepository: BackupRepository
) {
    suspend fun seed(language: String = "en") = backupRepository.writeSeed(checkNotNull(account.uid.value), language)
    suspend fun snapshot(): StoreData = backupRepository.snapshot()
    suspend fun put(data: StoreData) {
        val paths = UserPaths(checkNotNull(account.uid.value))
        val schema = com.raycoll.app.data.remote.firestore.FirestoreSchema
        store.writeAll(
            buildList {
                data.groups.forEachIndexed { position, deck ->
                    add(WriteOp.Set(paths.deck(deck.id), schema.deckToMap(deck, position)))
                    deck.cards.forEachIndexed { index, card ->
                        add(WriteOp.Set(paths.card(deck.id, card.id), schema.cardToMap(card, index)))
                    }
                }
                data.studyModes.forEach { add(WriteOp.Set(paths.mode(it.id), schema.modeToMap(it))) }
                data.activityHeatmap.forEach { (day, count) ->
                    add(WriteOp.Set(paths.activityDay(day), mapOf("count" to count, "updatedAt" to FieldOp.ServerTime)))
                }
            }
        )
    }
    fun setActivity(day: String, count: Int) = store.write(
        WriteOp.Set(
            UserPaths(checkNotNull(account.uid.value)).activityDay(day),
            mapOf("count" to count, "updatedAt" to FieldOp.ServerTime)
        )
    )
}
object VmTestHarness {
    fun create(context: Context, clock: Clock): TestRepos {
        val store = InMemoryDocumentStore(clock)
        val account = FakeAccount()
        return TestRepos(
            store,
            account,
            DeckRepository(store, account, clock),
            StudyModeRepository(store, account, clock),
            BackupRepository(store, account, DocumentFiles(context), clock, Dispatchers.Unconfined)
        )
    }
}
fun routeArgs(vararg args: Pair<String, Any?>): SavedStateHandle = SavedStateHandle(mapOf(*args))

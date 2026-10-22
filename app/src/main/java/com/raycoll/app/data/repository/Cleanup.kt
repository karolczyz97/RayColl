package com.raycoll.app.data.repository

import com.raycoll.app.core.Clock
import com.raycoll.app.data.firestore.DocumentStore
import com.raycoll.app.data.firestore.UserPaths
import com.raycoll.app.data.firestore.WriteOp
import com.raycoll.app.data.remote.firestore.FirestoreSchema as Schema
import com.raycoll.app.domain.model.SrsConstants
import com.raycoll.app.domain.operations.isArchiveExpired
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

// Nie krócej niż oldTombstone w firestore.rules (2592000000 ms) — wcześniejsze kasowanie reguły odrzucą.
private const val TOMBSTONE_RETENTION_MS = 30 * SrsConstants.DAY_MS

/** Jeden przebieg dla konta w sesji, zawsze po aktualnych odpowiedziach serwera. */
@Singleton
class Cleanup @Inject constructor(private val store: DocumentStore, private val clock: Clock) {
    suspend fun run(uid: String) {
        val paths = UserPaths(uid)
        val now = clock.now()
        val cutoff = now - TOMBSTONE_RETENTION_MS
        val decks = store.observe(paths.decks()).first { !it.fromCache }.docs.mapNotNull(Schema::readDeck)
        val modes = store.observe(paths.modes()).first { !it.fromCache }.docs.mapNotNull(Schema::readMode)
        for (deck in decks) {
            val cards = store.observe(paths.cards(deck.id)).first { !it.fromCache }.docs
            val removeDeck = deck.deletedAt?.let { it < cutoff } == true
            val deletedCards = cards.filter {
                removeDeck ||
                    (it.data[Schema.FIELD_DELETED_AT] as? Number)?.toLong()?.let { at -> at < cutoff } == true
            }
            // Nawet przy wielu porcjach rodzic jest zawsze usuwany po kartach.
            // Po usunięciu tombstonów talia startowa nadal musi być rozpoznawana jako zmieniona.
            val parent = when {
                removeDeck -> listOf(WriteOp.Delete(paths.deck(deck.id)))

                deck.updatedAt == 0L && deletedCards.isNotEmpty() -> listOf(
                    WriteOp.Update(paths.deck(deck.id), mapOf(Schema.FIELD_UPDATED_AT to now))
                )

                else -> emptyList()
            }
            store.writeAll(
                deletedCards.map { WriteOp.Delete(paths.card(deck.id, it.id)) } + parent
            )
            if (deck.isArchiveExpired(now)) {
                store.write(
                    WriteOp.Update(
                        paths.deck(deck.id),
                        mapOf(
                            Schema.FIELD_DELETED_AT to now,
                            Schema.FIELD_UPDATED_AT to now
                        )
                    )
                )
            }
        }
        store.writeAll(
            modes.filter {
                it.deletedAt?.let { at -> at < cutoff } == true
            }.map { WriteOp.Delete(paths.mode(it.id)) }
        )
    }
}

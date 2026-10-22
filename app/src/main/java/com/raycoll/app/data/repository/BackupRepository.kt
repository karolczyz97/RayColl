package com.raycoll.app.data.repository

import android.net.Uri
import android.util.Log
import com.raycoll.app.core.Clock
import com.raycoll.app.data.account.CurrentAccount
import com.raycoll.app.data.files.DocumentFiles
import com.raycoll.app.data.firestore.DocumentStore
import com.raycoll.app.data.firestore.FieldOp
import com.raycoll.app.data.firestore.UserPaths
import com.raycoll.app.data.firestore.WriteOp
import com.raycoll.app.data.remote.firestore.FirestoreSchema as Schema
import com.raycoll.app.di.DefaultDispatcher
import com.raycoll.app.domain.backup.BackupCheck
import com.raycoll.app.domain.merge.isUntouchedSeed
import com.raycoll.app.domain.merge.withBuiltInModesCopied
import com.raycoll.app.domain.model.StoreData
import com.raycoll.app.domain.model.isLive
import com.raycoll.app.domain.seed.SeedData
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

enum class BackupResult { SUCCESS, FILE_ERROR, INVALID_BACKUP }

@Singleton
class BackupRepository @Inject constructor(
    private val store: DocumentStore,
    private val account: CurrentAccount,
    private val documentFiles: DocumentFiles,
    private val clock: Clock,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher
) {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
    private fun uid() = checkNotNull(account.uid.value)

    suspend fun snapshot(uid: String = uid()): StoreData {
        val paths = UserPaths(uid)
        return StoreData(
            groups = Schema.sorted(store.cached(paths.decks())).mapNotNull(Schema::readDeck).map { deck ->
                deck.copy(cards = Schema.sorted(store.cached(paths.cards(deck.id))).mapNotNull(Schema::readCard))
            },
            studyModes = store.cached(paths.modes()).mapNotNull(Schema::readMode),
            activityHeatmap = store.cached(paths.activity()).mapNotNull(Schema::readActivity).toMap()
        )
    }
    private fun live(data: StoreData) = data.copy(
        groups = data.groups.filter { it.isLive }.map {
            it.copy(
                cards = it.cards.filter { card ->
                    card.deletedAt ==
                        null
                }
            )
        },
        studyModes = data.studyModes.filter { it.isLive }
    )
    suspend fun exportTo(uri: Uri): BackupResult {
        val text = encode(live(snapshot()))
        return if (documentFiles.writeText(uri, text)) BackupResult.SUCCESS else BackupResult.FILE_ERROR
    }
    suspend fun importFrom(uri: Uri): BackupResult {
        val text = documentFiles.readText(uri) ?: return BackupResult.FILE_ERROR
        val data = try {
            decode(text)
        } catch (e: IllegalArgumentException) {
            // także SerializationException; komunikat mówi, które pole odrzuciło kopię
            Log.w("BackupRepository", "Backup rejected", e)
            return BackupResult.INVALID_BACKUP
        }
        replace(live(data))
        return BackupResult.SUCCESS
    }
    suspend fun replace(data: StoreData, uid: String = uid()) {
        val now = clock.now()
        val current = snapshot(uid)
        val paths = UserPaths(uid)
        val stamped = live(data).copy(
            groups = live(data).groups.map { deck ->
                deck.copy(
                    updatedAt = now,
                    cards = deck.cards.map {
                        it.copy(contentUpdatedAt = now, srsUpdatedAt = now)
                    }
                )
            },
            studyModes = live(data).studyModes.map { it.copy(updatedAt = now) }
        )
        val missing = buildList {
            current.groups.filter { old -> stamped.groups.none { it.id == old.id } && old.isLive }.forEach {
                add(
                    WriteOp.Update(
                        paths.deck(it.id),
                        mapOf(
                            Schema.FIELD_DELETED_AT to now,
                            Schema.FIELD_UPDATED_AT to now
                        )
                    )
                )
            }
            current.studyModes.filter { old ->
                !old.isBuiltIn && stamped.studyModes.none { it.id == old.id } &&
                    old.isLive
            }.forEach {
                add(
                    WriteOp.Update(
                        paths.mode(it.id),
                        mapOf(
                            Schema.FIELD_DELETED_AT to now,
                            Schema.FIELD_UPDATED_AT to now
                        )
                    )
                )
            }
            current.groups.forEach { old ->
                val replacement = stamped.groups.find { it.id == old.id } ?: return@forEach
                old.cards.filter { card ->
                    card.isLive && replacement.cards.none { it.id == card.id }
                }.forEach {
                    add(WriteOp.Update(paths.card(old.id, it.id), mapOf(Schema.FIELD_DELETED_AT to now)))
                }
            }
            (current.activityHeatmap.keys - stamped.activityHeatmap.keys).forEach {
                add(
                    WriteOp.Set(
                        paths.activityDay(it),
                        Schema.activityToMap(0) + (Schema.FIELD_UPDATED_AT to FieldOp.ServerTime)
                    )
                )
            }
        }
        store.writeAll(operations(stamped, uid) + missing)
    }
    private fun operations(data: StoreData, uid: String, firstPosition: Int = 0): List<WriteOp> {
        val paths = UserPaths(uid)
        return buildList {
            data.groups.forEachIndexed { index, deck ->
                add(WriteOp.Set(paths.deck(deck.id), Schema.deckToMap(deck, firstPosition + index)))
                deck.cards.forEachIndexed { index, card ->
                    add(WriteOp.Set(paths.card(deck.id, card.id), Schema.cardToMap(card, index)))
                }
            }
            data.studyModes.forEach { add(WriteOp.Set(paths.mode(it.id), Schema.modeToMap(it))) }
            data.activityHeatmap.forEach { (day, count) ->
                add(
                    WriteOp.Set(
                        paths.activityDay(day),
                        Schema.activityToMap(count) + (Schema.FIELD_UPDATED_AT to FieldOp.ServerTime)
                    )
                )
            }
        }
    }
    suspend fun writeSeed(uid: String, language: String, reset: Boolean = false) {
        val seed = SeedData.createSeedData(language)
        if (reset) replace(seed, uid) else store.writeAll(operations(seed, uid))
    }
    suspend fun factoryReset() {
        writeSeed(uid(), Locale.getDefault().language, reset = true)
    }
    suspend fun restoreGuest(data: StoreData, uid: String) {
        store.writeAll(operations(live(data), uid))
    }
    suspend fun joinGuest(data: StoreData, uid: String, modeSuffix: String) {
        // Rozpoznaj zmienione talie przed odfiltrowaniem usuniętych kart: samo usunięcie
        // przykładowej fiszki też jest zmianą użytkownika.
        val own = live(data.copy(groups = data.groups.filter { !it.isUntouchedSeed() }))
        val joined = own.withBuiltInModesCopied(modeSuffix)
        val paths = UserPaths(uid)
        // Talie gościa trafiają za talie konta. Konta może jeszcze nie być w pamięci telefonu,
        // więc pozycje bierzemy z odpowiedzi serwera (bez sieci: z tego, co jest na telefonie).
        val accountDecks = withTimeoutOrNull(15_000) {
            store.observe(paths.decks()).first { !it.fromCache }.docs
        } ?: store.cached(paths.decks())
        val firstPosition = (accountDecks.mapNotNull { Schema.positionFromMap(it.data) }.maxOrNull() ?: -1L) + 1
        // Aktywność jest dodawana osobno; pozostałe dokumenty zachowują swoje daty.
        store.writeAll(
            operations(joined.copy(activityHeatmap = emptyMap()), uid, firstPosition.toInt()) +
                own.activityHeatmap.map { (day, count) ->
                    WriteOp.Set(
                        paths.activityDay(day),
                        mapOf(
                            "count" to FieldOp.Increment(count.toLong()),
                            Schema.FIELD_UPDATED_AT to FieldOp.ServerTime
                        ),
                        merge = true
                    )
                }
        )
    }
    suspend fun deleteGuest(uid: String) {
        val paths = UserPaths(uid)
        val decks = store.cached(paths.decks())
        val children = decks.flatMap { deck ->
            store.cached(paths.cards(deck.id)).map { WriteOp.Delete(paths.card(deck.id, it.id)) }
        }
        store.writeAll(
            children + decks.map { WriteOp.Delete(paths.deck(it.id)) } +
                store.cached(paths.modes()).map { WriteOp.Delete(paths.mode(it.id)) } +
                store.cached(paths.activity()).map { WriteOp.Delete(paths.activityDay(it.id)) }
        )
    }
    suspend fun savePrivate(name: String, data: StoreData) = documentFiles.writePrivate(name, encode(data))
    suspend fun loadPrivate(name: String): StoreData? = documentFiles.readPrivate(name)?.let { decode(it) }
    suspend fun deletePrivate(name: String) = documentFiles.deletePrivate(name)
    private suspend fun encode(data: StoreData) =
        withContext(dispatcher) { json.encodeToString(StoreData.serializer(), data) }
    private suspend fun decode(text: String) = withContext(dispatcher) {
        val root = json.parseToJsonElement(text) as? kotlinx.serialization.json.JsonObject
        require(root != null && listOf("groups", "studyModes", "activityHeatmap").all { it in root })
        json.decodeFromJsonElement(StoreData.serializer(), root).also(BackupCheck::validate)
    }
}

package com.raycoll.app.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.data.remote.auth.Account
import com.raycoll.app.data.remote.auth.GoogleSignIn
import com.raycoll.app.data.remote.auth.IdTokenResult
import com.raycoll.app.data.remote.auth.SignInResult
import com.raycoll.app.data.repository.DataMutations
import com.raycoll.app.data.repository.ReviewWriteQueue
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.ShowRatingsStep
import com.raycoll.app.domain.model.StoreData
import com.raycoll.app.domain.model.StudyMode
import com.raycoll.app.domain.model.WaitForTapStep
import com.raycoll.app.domain.model.WaitForTapToRevealStep
import com.raycoll.app.domain.normalize.normalizeStoreData
import com.raycoll.app.domain.session.SessionEngine
import com.raycoll.app.playback.StudySessionHolder
import com.raycoll.app.testing.FakeClock
import com.raycoll.app.testing.FakeSessionDeps
import com.raycoll.app.testing.TestRepos
import com.raycoll.app.testing.VmTestHarness
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val UID = "uid-1"

/** Chmura wspólna dla urządzeń: dokumenty jak w Firestore, zapis = operacje [SyncDiff]. */
private class FakeCloudBackend {
    var exists = false
    var version = 0L // znacznik updatedAt dokumentu konta
    val decks = linkedMapOf<String, FlashcardGroup>()
    val cards = linkedMapOf<Pair<String, String>, Flashcard>()
    val modes = linkedMapOf<String, StudyMode>()
    val activity = linkedMapOf<String, Int>()
    val deckPositions = hashMapOf<String, Int>()
    val cardPositions = hashMapOf<Pair<String, String>, Int>()

    /** Jak Firestore: dokumenty przychodzą wg id, a kolejność list wynika tylko z pola position. */
    fun snapshot(): StoreData = normalizeStoreData(
        StoreData(
            groups = decks.values.sortedWith(
                compareBy<FlashcardGroup> { deckPositions[it.id] ?: Int.MAX_VALUE }.thenBy { it.id }
            ).map { deck ->
                deck.copy(
                    cards = cards.filterKeys { it.first == deck.id }.values.sortedWith(
                        compareBy<Flashcard> { cardPositions[deck.id to it.id] ?: Int.MAX_VALUE }.thenBy { it.id }
                    )
                )
            },
            studyModes = modes.values.toList(),
            activityHeatmap = activity.toMap()
        )
    )

    fun apply(op: SyncOp) {
        when (op) {
            is SyncOp.PutDeck -> {
                decks[op.deck.id] = op.deck.copy(cards = emptyList())
                deckPositions[op.deck.id] = op.position
            }

            is SyncOp.DeleteDeck -> {
                decks.remove(op.deckId)
                deckPositions.remove(op.deckId)
            }

            is SyncOp.PutCard -> {
                cards[op.deckId to op.card.id] = op.card
                cardPositions[op.deckId to op.card.id] = op.position
            }

            is SyncOp.DeleteCard -> {
                cards.remove(op.deckId to op.cardId)
                cardPositions.remove(op.deckId to op.cardId)
            }

            is SyncOp.PutMode -> modes[op.mode.id] = op.mode

            is SyncOp.DeleteMode -> modes.remove(op.modeId)

            is SyncOp.PutActivity -> activity[op.dayKey] = op.count

            is SyncOp.DeleteActivity -> activity.remove(op.dayKey)
        }
    }
}

/** Jedno urządzenie widziane przez chmurę — ta sama umowa co [SyncEngine] (baza + znacznik). */
private class FakeDeviceCloud(private val backend: FakeCloudBackend) : CloudSync {
    var online = true
    var saveFails = false
    var freshnessChecks = 0
    var loads = 0
    private var baseline: Pair<StoreData, Long>? = null
    override val status: StateFlow<SyncStatus> = MutableStateFlow(SyncStatus.IDLE)

    override suspend fun load(uid: String): CloudLoad {
        loads++
        if (!online) return CloudLoad.Failed
        val data = backend.snapshot()
        baseline = data to backend.version
        return if (backend.exists) CloudLoad.Loaded(data) else CloudLoad.Empty
    }

    override suspend fun hasFreshBaseline(uid: String): Boolean {
        freshnessChecks++
        if (!online) return false
        return baseline?.second == backend.version
    }

    override suspend fun save(uid: String, data: StoreData): Boolean {
        if (!online || saveFails) return false
        val known = baseline ?: return false
        val ops = SyncDiff.diffSave(known.first, data)
        if (ops.isNotEmpty()) {
            ops.forEach(backend::apply)
            backend.exists = true
            backend.version++
        }
        baseline = data to backend.version
        return true
    }

    override fun clearBaseline() {
        baseline = null
    }
}

private class Device(context: Context, backend: FakeCloudBackend, val clock: FakeClock) {
    val repos: TestRepos = VmTestHarness.create(context, clock)
    val cloud = FakeDeviceCloud(backend)
    private val user = object : CurrentUser, GoogleSignIn {
        override fun currentUid() = UID
        override val account: StateFlow<Account?> = MutableStateFlow(Account(UID, null))
        override suspend fun requestGoogleIdToken(activityContext: Context) = IdTokenResult.Cancelled
        override suspend fun signInWithIdToken(idToken: String) = SignInResult.Success(Account(UID, null))
        override suspend fun signOut() {}
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val holder = StudySessionHolder(context)
    val writes = ReviewWriteQueue(repos.deckRepository, scope, repos.namespaceManager)
    val sync = SyncLifecycle(
        user,
        repos.localStore,
        repos.deckRepository,
        repos.namespaceManager,
        BootstrapLoader(user, repos.localStore, repos.namespaceManager, cloud),
        MigrationController(repos.localStore, repos.namespaceManager),
        cloud,
        DataMutations(),
        holder,
        writes,
        clock,
        scope,
        user
    )

    fun decks() = runBlocking { repos.localStore.snapshot().groups.filter { it.deletedAt == null } }

    fun cardPages() = decks().flatMap { deck -> deck.cards.filter { it.deletedAt == null }.map { it.pages } }.toSet()
    fun close() {
        scope.cancel()
        repos.db.close()
    }

    fun finishOneCard(deck: FlashcardGroup, card: Flashcard) {
        val engine =
            SessionEngine(deck, listOf(ShowRatingsStep()), FakeSessionDeps(reviewWrites = writes), clock, 1f, scope)
        engine.start(listOf(card))
        holder.register(engine, deck.name, false)
        engine.rate(3)
    }
}

/** Dwa urządzenia jednego konta: dane startowe, zmiany z innego urządzenia po powrocie, edycje offline. */
@RunWith(RobolectricTestRunner::class)
class TwoDeviceSyncTest {

    @Test
    fun `retry after session completion uploads the last review without another mutation`() {
        phone.sync.start()
        val deck = phone.decks().first()
        val card = deck.cards.first()
        phone.repos.db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_review BEFORE INSERT ON cards BEGIN SELECT RAISE(ABORT, 'test review failure'); END"
        )
        phone.finishOneCard(deck, card)
        waitUntil { phone.writes.state.value.failed }
        assertEquals(card.srsState.repetitions, backend.cards.getValue(deck.id to card.id).srsState.repetitions)
        phone.repos.db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_review")
        phone.writes.retry()
        waitUntil { backend.cards.getValue(deck.id to card.id).srsState.repetitions == card.srsState.repetitions + 1 }
    }

    @Test
    fun `bootstrap upgrades untouched classic from cloud but preserves an edited mode`() = runBlocking {
        val classic = backend.modes.getValue("classic")
        val old = classic.copy(steps = classic.steps.map { if (it is WaitForTapStep) WaitForTapToRevealStep() else it })
        backend.modes["classic"] = old
        backend.version++
        phone.sync.runBootstrap()
        assertEquals(classic.steps, phone.repos.studyModeRepository.getModeOrDefault("classic").steps)
        backend.modes["classic"] = old.copy(updatedAt = 123L)
        backend.version++
        phone.sync.runBootstrap()
        assertEquals(old.steps, phone.repos.studyModeRepository.getModeOrDefault("classic").steps)
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val backend = FakeCloudBackend()
    private val phone = Device(context, backend, FakeClock())
    private val tablet = Device(context, backend, FakeClock())

    @Before
    fun bootstrapBoth() {
        runBlocking {
            phone.sync.runBootstrap()
            tablet.sync.runBootstrap()
        }
    }

    @After
    fun teardown() {
        phone.close()
        tablet.close()
    }

    @Test
    fun `new account with an empty cloud uploads its starter decks and the second device gets the same ones`() {
        val phoneDecks = phone.decks()
        assertTrue("konto dostało przykładowe talie", phoneDecks.isNotEmpty())
        assertEquals(phoneDecks.map { it.id }.toSet(), backend.decks.keys)
        assertEquals(
            "drugie urządzenie: te same talie, bez własnej kopii",
            phoneDecks.map { it.id }.toSet(),
            tablet.decks().map { it.id }.toSet()
        )

        // ponowne uruchomienie nie tworzy duplikatów
        runBlocking {
            phone.sync.runBootstrap()
            tablet.sync.runBootstrap()
        }
        assertEquals(phoneDecks.size, phone.decks().size)
        assertEquals(phoneDecks.size, tablet.decks().size)
        assertEquals(phoneDecks.size, backend.decks.size)
    }

    @Test
    fun `change made elsewhere appears after returning to the app and an offline edit survives`() {
        val deck = phone.decks().first()
        val card = deck.cards.first()
        // telefon: edycja fiszki i wysyłka
        runBlocking {
            phone.repos.deckRepository.updateCardPages(card.id, listOf("edited on phone", "zmienione"))
            phone.sync.pushNow()
        }
        assertTrue(backend.cards.values.any { it.pages.first() == "edited on phone" })

        // tablet offline: nowa fiszka, wysyłka się nie udaje
        tablet.cloud.online = false
        runBlocking {
            tablet.repos.deckRepository.addCard(deck.id, listOf("offline card", "offline"))
            tablet.sync.pushNow()
        }
        assertTrue(backend.cards.values.none { it.pages.first() == "offline card" })

        // tablet wraca do sieci i na pierwszy plan: scala chmurę z lokalnymi zmianami i odsyła wynik
        tablet.cloud.online = true
        tablet.clock.advance(FOREGROUND_CLOUD_CHECK_INTERVAL_MS)
        tablet.sync.onAppForeground()
        waitUntil { backend.cards.values.any { it.pages.first() == "offline card" } }

        val tabletPages = tablet.cardPages()
        assertTrue(
            "nowsza treść z telefonu bez ręcznej synchronizacji",
            listOf("edited on phone", "zmienione") in tabletPages
        )
        assertTrue("edycja offline zostaje", listOf("offline card", "offline") in tabletPages)
        assertTrue(backend.cards.values.any { it.pages.first() == "edited on phone" })
    }

    @Test
    fun `card and deck order from one device is the same on a fresh device`() {
        val deck = phone.decks().first()
        runBlocking {
            repeat(8) { phone.repos.deckRepository.addCard(deck.id, listOf("card $it", "karta $it")) }
            repeat(3) {
                phone.repos.deckRepository.addDeckWithCards(
                    "Talia $it",
                    listOf("en-US", "pl-PL"),
                    listOf("A", "B"),
                    listOf(listOf("q$it", "a$it"), listOf("q$it b", "a$it b"))
                )
            }
            phone.sync.pushNow()
        }
        val fresh = Device(context, backend, FakeClock())
        try {
            runBlocking { fresh.sync.runBootstrap() }
            assertEquals(phone.decks().map { it.id }, fresh.decks().map { it.id })
            assertEquals(
                phone.decks().map { deck -> deck.cards.map { it.id } },
                fresh.decks().map { deck -> deck.cards.map { it.id } }
            )
        } finally {
            fresh.close()
        }
    }

    @Test
    fun `a deleted card keeps the order of the others in the cloud`() {
        val deck = phone.decks().first()
        runBlocking {
            repeat(5) { phone.repos.deckRepository.addCard(deck.id, listOf("card $it", "karta $it")) }
            phone.sync.pushNow()
            val cardId = phone.decks().first { it.id == deck.id }.cards[2].id
            phone.repos.deckRepository.deleteCard(cardId)
            phone.sync.pushNow()
        }
        val expected = phone.decks().first { it.id == deck.id }.cards.map { it.id }
        assertEquals(expected, backend.snapshot().groups.first { it.id == deck.id }.cards.map { it.id })
    }

    @Test
    fun `quick app switching checks the cloud at most once per interval and reloads only after a change`() {
        phone.clock.advance(FOREGROUND_CLOUD_CHECK_INTERVAL_MS)
        phone.sync.onAppForeground()
        waitUntil { phone.cloud.freshnessChecks >= 1 }
        Thread.sleep(50)
        val checksAfterFirst = phone.cloud.freshnessChecks
        val loadsAfterFirst = phone.cloud.loads

        phone.sync.onAppForeground()
        phone.sync.onAppForeground()
        Thread.sleep(50)
        assertEquals("szybkie przełączanie: bez kolejnych odczytów", checksAfterFirst, phone.cloud.freshnessChecks)

        phone.clock.advance(FOREGROUND_CLOUD_CHECK_INTERVAL_MS)
        phone.sync.onAppForeground()
        waitUntil { phone.cloud.freshnessChecks > checksAfterFirst }
        Thread.sleep(50)
        assertEquals("chmura bez zmian: tylko odczyt znacznika", loadsAfterFirst, phone.cloud.loads)

        // zmiana z innego urządzenia → po kolejnym powrocie dane są pobierane i scalane
        runBlocking {
            tablet.repos.deckRepository.renameDeck(tablet.decks().first().id, "Renamed on tablet")
            tablet.sync.pushNow()
        }
        phone.clock.advance(FOREGROUND_CLOUD_CHECK_INTERVAL_MS)
        phone.sync.onAppForeground()
        waitUntil { phone.decks().any { it.name == "Renamed on tablet" } }
        assertEquals(loadsAfterFirst + 1, phone.cloud.loads)
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "warunek nie spełniony w 5 s" }
            Thread.sleep(10)
        }
    }
}

/** Dwa nowe urządzenia jednego konta zaczynają z pustą chmurą, zanim którekolwiek wyśle dane startowe. */
@RunWith(RobolectricTestRunner::class)
class TwoDeviceSeedRaceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val backend = FakeCloudBackend()
    private val phone = Device(context, backend, FakeClock())
    private val tablet = Device(context, backend, FakeClock())

    @After
    fun teardown() {
        phone.close()
        tablet.close()
    }

    @Test
    fun `both devices seed before either uploads and the account still has one set of starter decks`() {
        runBlocking {
            phone.cloud.saveFails = true // telefon seeduje, ale jego wysyłka jeszcze nie dochodzi
            phone.sync.runBootstrap()
            tablet.sync.runBootstrap() // chmura nadal pusta — tablet też seeduje i wysyła
            phone.cloud.saveFails = false
            phone.sync.pushNow() // telefon: chmura się zmieniła → scalenie, potem wysyłka
        }
        val seeded = phone.decks().size
        assertTrue(seeded > 0)
        assertEquals("bez duplikatów w chmurze", seeded, backend.decks.size)
        assertEquals(seeded, tablet.decks().size)
        assertEquals(phone.decks().map { it.id }.toSet(), tablet.decks().map { it.id }.toSet())
    }
}

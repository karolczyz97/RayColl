package com.raycoll.app.data.firestore

import android.content.Context
import android.content.ContextWrapper
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Looper
import androidx.credentials.CredentialManager
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.MemoryLruGcSettings
import com.google.firebase.firestore.Source
import com.raycoll.app.core.LocalDates
import com.raycoll.app.data.account.AccountManager
import com.raycoll.app.data.account.AccountState
import com.raycoll.app.data.account.CurrentAccount
import com.raycoll.app.data.account.GuestDataChoice
import com.raycoll.app.data.files.DocumentFiles
import com.raycoll.app.data.remote.auth.AuthRepository
import com.raycoll.app.data.remote.auth.SignInResult
import com.raycoll.app.data.remote.firestore.FirestoreSchema as Schema
import com.raycoll.app.data.repository.BackupRepository
import com.raycoll.app.data.repository.Cleanup
import com.raycoll.app.data.repository.DeckRepository
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.testing.FakeClock
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.Base64
import java.util.UUID
import javax.inject.Provider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetworkCapabilities

/** Real Android SDK, independent FirebaseApps and caches, real Firestore and Auth emulators. */
@RunWith(RobolectricTestRunner::class)
class FirestoreEmulatorTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val runner = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val devices = mutableListOf<Device>()
    private val baseTime = System.currentTimeMillis() - 60_000

    @Before fun requireEmulators() {
        assumeTrue("Use npm run test:android", System.getenv("FIRESTORE_EMULATOR_HOST") != null)
        check(System.getenv("FIREBASE_AUTH_EMULATOR_HOST") != null) { "Auth emulator is required" }
    }

    private inner class Device(val phoneFiles: File = File(context.cacheDir, "phone-" + UUID.randomUUID())) {
        val clock = FakeClock(baseTime)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val network = mock(ConnectivityManager::class.java)
        private val activeNetwork = mock(Network::class.java)
        val phone = object : ContextWrapper(context) {
            override fun getFilesDir(): File = phoneFiles.apply { mkdirs() }
            override fun getSystemService(name: String): Any? = if (name ==
                Context.CONNECTIVITY_SERVICE
            ) {
                network
            } else {
                super.getSystemService(name)
            }
        }
        val app = FirebaseApp.initializeApp(
            context,
            FirebaseOptions.Builder().setProjectId("demo-raycoll")
                .setApplicationId("1:123456789:android:1234567890abcdef").setApiKey("fake-emulator-key").build(),
            "device-" + UUID.randomUUID()
        )
        val auth = FirebaseAuth.getInstance(app).apply { useEmulator("127.0.0.1", 9099) }

        // LRU memory cache keeps acknowledged documents like the app's unlimited persistent cache; eager GC would drop them.
        val db = FirebaseFirestore.getInstance(app).apply {
            firestoreSettings =
                FirebaseFirestoreSettings.Builder().setLocalCacheSettings(
                    MemoryCacheSettings.newBuilder().setGcSettings(
                        MemoryLruGcSettings.newBuilder().setSizeBytes(100L * 1024 * 1024).build()
                    ).build()
                ).build()
            useEmulator("127.0.0.1", 8089)
        }
        val store = FirestoreDocumentStore(db, scope, phone)
        val authRepository = AuthRepository(auth, mock(CredentialManager::class.java))
        lateinit var manager: AccountManager
        private val current = object : CurrentAccount {
            override val uid get() = manager.uid
        }
        val backup = BackupRepository(store, current, DocumentFiles(phone), clock, Dispatchers.Default)
        val decks = DeckRepository(store, current, clock)
        val uid get() = requireNotNull(auth.currentUser).uid
        val paths get() = UserPaths(uid)
        init {
            `when`(network.activeNetwork).thenReturn(activeNetwork)
            `when`(network.getNetworkCapabilities(activeNetwork)).thenReturn(
                ShadowNetworkCapabilities.newInstance().also {
                    shadowOf(it).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                }
            )
            restartManager()
            devices += this
        }
        fun restartManager() {
            manager = AccountManager(authRepository, store, Provider { backup }, Cleanup(store, clock), phone, scope)
        }
        suspend fun google(token: String) {
            check(
                authRepository.signInWithCredential(
                    GoogleAuthProvider.getCredential(token, null)
                ) is SignInResult.Success
            )
            manager.retryStart()
            check(manager.state.value is AccountState.Ready)
        }
        suspend fun guest() {
            manager.retryStart()
            check((manager.state.value as AccountState.Ready).isGuest)
            check(store.awaitPendingWrites(10_000))
        }
        suspend fun addDeck(name: String = "Deck", count: Int = 1): String = decks.addDeckWithCards(
            name,
            listOf("en", "pl"),
            listOf("A", "B"),
            (0 until count).map { listOf("q$it", "a$it") }
        ).also { check(store.awaitPendingWrites(10_000)) }
        suspend fun freshDecks() = store.observe(paths.decks()).first { !it.fromCache }
        suspend fun load(deck: String) {
            store.observe(paths.decks()).first { !it.fromCache && it.docs.any { doc -> doc.id == deck } }
            store.observe(paths.cards(deck)).first { !it.fromCache && it.docs.isNotEmpty() }
        }
        suspend fun document(path: String) = db.document(path).get(Source.SERVER).await()
        suspend fun cards(deck: String, predicate: (List<Doc>) -> Boolean) = store.observe(paths.cards(deck)).first {
            !it.fromCache &&
                predicate(it.docs)
        }.docs
        suspend fun rejection() = scope.async(start = CoroutineStart.UNDISPATCHED) { store.rejections.first() }
    }

    private fun googleToken(id: String = UUID.randomUUID().toString()): String {
        fun encode(text: String) =
            Base64.getUrlEncoder().withoutPadding().encodeToString(text.toByteArray(Charsets.UTF_8))
        return encode("""{"alg":"none","typ":"JWT"}""") + "." +
            encode(
                """{"sub":"$id","email":"$id@example.com","email_verified":true,"iss":"https://accounts.google.com","aud":"demo-raycoll","iat":${System.currentTimeMillis() / 1000},"exp":${System.currentTimeMillis() / 1000 + 3600}}"""
            ) +
            "."
    }

    /** Last reached step, reported when a scenario times out. */
    @Volatile private var step = "start"

    private fun scenario(block: suspend () -> Unit) {
        val task = runner.async { withTimeout(180_000) { block() } }
        val deadline = System.nanoTime() + 190_000_000_000L
        while (!task.isCompleted && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        check(task.isCompleted) { "Emulator scenario timed out at: $step" }
        runBlocking {
            try {
                task.await()
            } catch (timeout: TimeoutCancellationException) {
                throw AssertionError("Emulator scenario timed out at: $step", timeout)
            }
        }
    }
    private fun existsAsAdmin(path: String): Boolean {
        val connection = URI(
            "http://127.0.0.1:8089/v1/projects/demo-raycoll/databases/(default)/documents/$path"
        ).toURL().openConnection() as HttpURLConnection
        connection.setRequestProperty("Authorization", "Bearer owner")
        val code = connection.responseCode
        (if (code < 400) connection.inputStream else connection.errorStream)?.close()
        connection.disconnect()
        check(code == 200 || code == 404) { "Unexpected emulator admin response $code" }
        return code == 200
    }

    @After fun close() {
        if (devices.isNotEmpty()) {
            scenario {
                // FirebaseApp is not deleted: Firebase Auth keeps a static reference to the first app between tests.
                devices.forEach { device ->
                    device.scope.cancel()
                    device.auth.signOut()
                    device.db.terminate().await()
                }
            }
        }
        runner.cancel()
    }

    @Test fun `offline deletion and an independent edit reach both devices`() = scenario {
        val token = googleToken()
        val a = Device().also { it.google(token) }
        val b = Device().also { it.google(token) }
        val removed = a.addDeck("Remove")
        val kept = a.addDeck("Keep", 2)
        b.load(removed)
        b.load(kept)
        val cards = a.decks.getDeck(kept)!!.cards
        a.db.disableNetwork().await()
        a.clock.advance(10)
        a.decks.deleteDeck(removed)
        a.decks.deleteCard(cards[0].id)
        b.clock.advance(20)
        b.decks.updateCardPages(cards[1].id, listOf("remote", "a"))
        check(b.store.awaitPendingWrites(10_000))
        a.db.enableNetwork().await()
        check(a.store.awaitPendingWrites(10_000))
        for (device in listOf(a, b)) {
            device.store.observe(device.paths.decks()).first {
                !it.fromCache &&
                    it.docs.find { d -> d.id == removed }?.data?.get("deletedAt") != null
            }
            device.cards(kept) { docs ->
                docs.find { it.id == cards[0].id }?.data?.get("deletedAt") != null &&
                    docs.find { it.id == cards[1].id }?.data?.get("pages") == listOf("remote", "a")
            }
            assertNull(device.decks.getDeck(removed))
            assertEquals(1, device.decks.getDeck(kept)!!.cards.size)
        }
    }

    @Test fun `older deletion loses to review and an older edit cannot revive a deletion`() = scenario {
        val token = googleToken()
        val a = Device().also { it.google(token) }
        val b = Device().also { it.google(token) }
        val id = a.addDeck()
        b.load(id)
        val card = a.decks.getDeck(id)!!.cards.single().id
        a.db.disableNetwork().await()
        a.clock.advance(10)
        a.decks.deleteCard(card)
        b.clock.advance(100)
        b.decks.reviewCard(id, card, 3)
        check(b.store.awaitPendingWrites(10_000))
        val rejected = a.rejection()
        a.db.enableNetwork().await()
        a.store.awaitPendingWrites(10_000)
        assertEquals(1, rejected.await())
        a.cards(id) {
            it.single().data["deletedAt"] == null &&
                ((it.single().data["srsState"] as Map<*, *>)["repetitions"] as Number).toInt() == 1
        }
        assertNotNull(a.decks.getDeck(id)!!.cards.singleOrNull())
        a.db.disableNetwork().await()
        a.clock.advance(40)
        a.decks.updateCardPages(card, listOf("old edit", "a"))
        b.clock.advance(100)
        b.decks.deleteCard(card)
        check(b.store.awaitPendingWrites(10_000))
        val rejectedEdit = a.rejection()
        a.db.enableNetwork().await()
        a.store.awaitPendingWrites(10_000)
        assertEquals(1, rejectedEdit.await())
        a.cards(id) { it.single().data["deletedAt"] != null }
        assertTrue(a.decks.getDeck(id)!!.cards.isEmpty())
    }

    @Test fun `card content and review merge while newer deck name wins despite delivery order`() = scenario {
        val token = googleToken()
        val a = Device().also { it.google(token) }
        val b = Device().also { it.google(token) }
        val id = a.addDeck()
        b.load(id)
        val card = a.decks.getDeck(id)!!.cards.single().id
        a.db.disableNetwork().await()
        a.clock.advance(10)
        a.decks.updateCardPages(card, listOf("edited offline", "answer"))
        a.decks.renameDeck(id, "Older")
        b.clock.advance(100)
        b.decks.reviewCard(id, card, 3)
        b.decks.renameDeck(id, "Newer")
        check(b.store.awaitPendingWrites(10_000))
        val rejected = a.rejection()
        a.db.enableNetwork().await()
        a.store.awaitPendingWrites(10_000)
        assertEquals(1, rejected.await())
        for (device in listOf(a, b)) {
            device.cards(id) { docs ->
                docs.single().data["pages"] == listOf("edited offline", "answer") &&
                    ((docs.single().data["srsState"] as Map<*, *>)["repetitions"] as Number).toInt() == 1
            }
            device.store.observe(device.paths.decks()).first {
                !it.fromCache && it.docs.single().data["name"] == "Newer"
            }
            assertEquals("Newer", device.decks.getDeck(id)!!.name)
        }
    }

    @Test fun `offline reviews count five actual repetitions even when one review is rejected`() = scenario {
        val token = googleToken()
        val a = Device().also { it.google(token) }
        val b = Device().also { it.google(token) }
        val id = a.addDeck(count = 4)
        b.load(id)
        val cards = a.decks.getDeck(id)!!.cards
        a.db.disableNetwork().await()
        a.clock.advance(10)
        cards.take(3).forEach { a.decks.reviewCard(id, it.id, 3) }
        b.clock.advance(100)
        b.decks.reviewCard(id, cards[3].id, 3)
        b.clock.advance(100)
        b.decks.reviewCard(id, cards[1].id, 4)
        check(b.store.awaitPendingWrites(10_000))
        val rejected = a.rejection()
        a.db.enableNetwork().await()
        a.store.awaitPendingWrites(10_000)
        assertEquals(1, rejected.await())
        val day = LocalDates.dayKey(baseTime)
        for (device in listOf(a, b)) {
            device.store.observe(device.paths.activity()).first {
                !it.fromCache &&
                    (it.docs.find { d -> d.id == day }?.data?.get("count") as? Number)?.toInt() == 5
            }
            val snapshot = device.document(device.paths.card(id, cards[1].id)).data!!
            assertEquals(b.clock.now(), (snapshot["srsUpdatedAt"] as Number).toLong())
        }
    }

    @Test fun `studying a deck protects it from an older offline deletion`() = scenario {
        val token = googleToken()
        val a = Device().also { it.google(token) }
        val b = Device().also { it.google(token) }
        val id = a.addDeck()
        b.load(id)
        a.db.disableNetwork().await()
        a.clock.advance(10)
        a.decks.deleteDeck(id)
        b.clock.advance(100)
        b.decks.markStudied(id)
        check(b.store.awaitPendingWrites(10_000))
        val rejected = a.rejection()
        a.db.enableNetwork().await()
        a.store.awaitPendingWrites(10_000)
        assertEquals(1, rejected.await())
        a.store.observe(a.paths.decks()).first { !it.fromCache && it.docs.single().data["deletedAt"] == null }
        assertNotNull(a.decks.getDeck(id))
        assertNotNull(b.decks.getDeck(id))
    }

    @Test fun `real cleanup removes 31 day tombstones and parent cards and preserves fresh ones`() = scenario {
        val a = Device().also { it.google(googleToken()) }
        val oldTime = baseTime - 31L * 86_400_000
        val oldCard =
            Flashcard("old-card", listOf("q", "a"), SrsState(), contentUpdatedAt = oldTime, srsUpdatedAt = oldTime)
        val old =
            FlashcardGroup(
                "old-deck",
                "Old",
                listOf(oldCard),
                pageNames = listOf("A", "B"),
                pageLanguages = listOf("en", "pl"),
                updatedAt = oldTime,
                deletedAt = oldTime
            )
        val fresh = old.copy(id = "fresh-deck", updatedAt = baseTime, deletedAt = baseTime)
        a.store.writeAll(
            listOf(
                WriteOp.Set(a.paths.deck(old.id), Schema.deckToMap(old)),
                WriteOp.Set(a.paths.card(old.id, oldCard.id), Schema.cardToMap(oldCard)),
                WriteOp.Set(a.paths.deck(fresh.id), Schema.deckToMap(fresh))
            )
        )
        check(a.store.awaitPendingWrites(10_000))
        Cleanup(a.store, a.clock).run(a.uid)
        check(a.store.awaitPendingWrites(10_000))
        assertFalse(a.document(a.paths.deck(old.id)).exists())
        assertFalse(a.document(a.paths.card(old.id, oldCard.id)).exists())
        assertTrue(a.document(a.paths.deck(fresh.id)).exists())
    }

    @Test fun `every login choice follows the guest data table and sign out restores only kept data`() = scenario {
        for (existing in listOf(false, true)) {
            for (choice in listOf<GuestDataChoice?>(
                null,
                GuestDataChoice.MOVE_TO_ACCOUNT,
                GuestDataChoice.KEEP_ON_DEVICE,
                GuestDataChoice.DELETE,
                GuestDataChoice.CANCEL
            )) {
                step = "$existing/$choice: prepare"
                val token = googleToken()
                val accountDevice = if (existing) {
                    Device().also {
                        it.google(token)
                        it.addDeck("Own")
                        it.store.write(
                            WriteOp.Set(
                                it.paths.activityDay(LocalDates.dayKey(baseTime)),
                                mapOf("count" to 2, "updatedAt" to FieldOp.ServerTime)
                            )
                        )
                        check(it.store.awaitPendingWrites(10_000))
                    }
                } else {
                    null
                }
                val guest = Device().also { it.guest() }
                val guestUid = guest.uid
                val ownId = if (choice !=
                    null
                ) {
                    guest.addDeck("Own").also { id ->
                        guest.decks.reviewCard(id, guest.decks.getDeck(id)!!.cards.single().id, 3)
                        check(guest.store.awaitPendingWrites(10_000))
                    }
                } else {
                    null
                }
                val before = guest.backup.snapshot()
                val oldPaths = UserPaths(guestUid)
                val guestPaths =
                    before.groups.flatMap { deck ->
                        listOf(oldPaths.deck(deck.id)) +
                            deck.cards.map { oldPaths.card(deck.id, it.id) }
                    } +
                        before.studyModes.map { oldPaths.mode(it.id) } +
                        before.activityHeatmap.keys.map(oldPaths::activityDay)
                step = "$existing/$choice: sign in"
                val result = guest.manager.signInWithGoogle(token)
                if (choice != null) {
                    assertEquals(SignInResult.ChoiceRequired, result)
                    step = "$existing/$choice: resolve"
                    assertTrue(guest.manager.resolveChoice(choice))
                } else {
                    assertTrue(result is SignInResult.Success)
                }
                if (choice == GuestDataChoice.CANCEL) {
                    assertEquals(guestUid, guest.uid)
                    assertTrue(guest.auth.currentUser!!.isAnonymous)
                    assertEquals(before, guest.backup.snapshot())
                    continue
                }
                assertFalse(guest.auth.currentUser!!.isAnonymous)
                val linked = !existing && (choice == null || choice == GuestDataChoice.MOVE_TO_ACCOUNT)
                if (linked) {
                    assertEquals(guestUid, guest.uid)
                } else {
                    assertNotEquals(guestUid, guest.uid)
                    guestPaths.forEach {
                        assertFalse("Guest document remains: $it ($existing/$choice)", existsAsAdmin(it))
                    }
                }
                step = "$existing/$choice: account data"
                guest.freshDecks()
                if (choice == GuestDataChoice.MOVE_TO_ACCOUNT) {
                    assertTrue(guest.document(guest.paths.deck(ownId!!)).exists())
                    assertEquals(
                        1,
                        (
                            (
                                guest.document(
                                    guest.paths.card(
                                        ownId,
                                        before.groups.single {
                                            it.id == ownId
                                        }.cards.single().id
                                    )
                                ).data!!["srsState"] as Map<*, *>
                                )["repetitions"] as Number
                            ).toInt()
                    )
                    if (existing) {
                        assertEquals(2, guest.freshDecks().docs.count { it.data["name"] == "Own" })
                        assertEquals(
                            3,
                            (
                                guest.document(
                                    guest.paths.activityDay(LocalDates.dayKey(baseTime))
                                ).get("count") as Number
                                ).toInt()
                        )
                    }
                } else if (existing) {
                    // A used account stays unchanged: only its own deck and activity.
                    assertEquals(accountDevice!!.uid, guest.uid)
                    assertEquals(listOf("Own"), guest.freshDecks().docs.map { it.data["name"] })
                    assertEquals(
                        2,
                        (
                            guest.document(
                                guest.paths.activityDay(LocalDates.dayKey(baseTime))
                            ).get("count") as Number
                            ).toInt()
                    )
                } else {
                    // A new account has sample decks: the linked guest's, or a fresh seed after keep or delete.
                    val decks = guest.freshDecks().docs
                    assertEquals(2, decks.size)
                    assertTrue(decks.none { it.data["name"] == "Own" })
                }
                if (choice ==
                    GuestDataChoice.KEEP_ON_DEVICE
                ) {
                    assertEquals(before, guest.backup.loadPrivate(AccountManager.STASH))
                } else {
                    assertNull(guest.backup.loadPrivate(AccountManager.STASH))
                }
                step = "$existing/$choice: sign out"
                assertTrue(guest.manager.signOut())
                assertTrue(guest.auth.currentUser!!.isAnonymous)
                val afterSignOut = guest.backup.snapshot()
                if (choice == GuestDataChoice.KEEP_ON_DEVICE) {
                    assertEquals(before, afterSignOut)
                } else {
                    assertEquals(2, afterSignOut.groups.size)
                    assertTrue(afterSignOut.activityHeatmap.isEmpty())
                }
                assertNull(guest.backup.loadPrivate(AccountManager.STASH))
            }
        }
    }

    @Test fun `pending move survives account manager restart before and after Google sign in`() = scenario {
        for (googleAlreadySignedIn in listOf(false, true)) {
            val phone = Device().also { it.guest() }
            val id = phone.addDeck("Recovered")
            phone.decks.reviewCard(id, phone.decks.getDeck(id)!!.cards.single().id, 3)
            val before = phone.backup.snapshot()
            phone.backup.savePrivate(AccountManager.PENDING, before)
            phone.backup.deleteGuest(phone.uid)
            check(phone.store.awaitPendingWrites(10_000))
            phone.authRepository.deleteCurrentUser()
            phone.authRepository.signOut()
            if (googleAlreadySignedIn) {
                assertTrue(
                    phone.authRepository.signInWithCredential(
                        GoogleAuthProvider.getCredential(googleToken(), null)
                    ) is SignInResult.Success
                )
            }
            phone.restartManager()
            phone.manager.retryStart()
            assertTrue(phone.manager.state.value is AccountState.Ready)
            assertTrue(phone.document(phone.paths.deck(id)).exists())
            assertNull(phone.backup.loadPrivate(AccountManager.PENDING))
            assertEquals(!googleAlreadySignedIn, phone.auth.currentUser!!.isAnonymous)
            assertEquals(
                1,
                (phone.document(phone.paths.activityDay(LocalDates.dayKey(baseTime))).get("count") as Number).toInt()
            )
        }
    }
}

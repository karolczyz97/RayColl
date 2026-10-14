# Verdicts: data/ + di/ + build config (area: data)

Paths relative to app/src/main/java/com/raycoll/app/ unless rooted. Sizes: XS = a few lines, S, M, L.

Summary (61 IDs): CONFIRMED 9, PARTIAL 15, WRONG 2, INTENTIONAL 1, EDGE 9, NOT_WORTH 25. Full lists at the end.

---

## R1 — EDGE — real hole, but needs two devices writing within ~1 s of each other
Between our `publishUpdatedAt` and the re-read of root `updatedAt` (SyncEngine.save 100-108), or between `hasFreshBaseline` and the end of a multi-batch upload, another device can publish. Then our baseline claims "root == B's timestamp" while `baseline.data` lacks B's changes. Effect: A does not merge B's changes until its next cold start (bootstrap always loads + merges) or until B writes again; merge is timestamp-LWW, so nothing is lost unless A edits the very same doc. For one user with 1-2 devices this needs edits on two devices inside one sync window. Fix (client token written to the root doc, compared on re-read) means a new field and extra logic for a ~0.01% case. Skip.

## R2 — EDGE — needs a first-ever upload to die between batch commit and marker, then a second device
Traced: same device next start -> `load` gives `Empty`, `bootstrapFreshAccount`: local has data so no seed, no migration -> `SeededFreshAccount` -> push diffs local against an empty baseline and re-uploads everything (idempotent set). So the same-device path is safe. Only a second device signing in before the first device finished its first upload would treat the account as fresh and upload its own data; docs with equal ids (seeds use uid-scoped stable ids) are overwritten by whichever wrote last. Optional hardening if ever wanted (S): in `load`, when the root doc does not exist, do one `decks.limit(1)` read and treat a non-empty result as Loaded. Not worth the extra read for a new-account-only race.

## R3 — CONFIRMED — `openOutputStream(uri)` uses "w", which since Android 10 does not guarantee truncation on all providers
Android docs say to pass "wt" when you need truncation (known SAF/ExternalStorage/Drive issue after Q). Export over an existing, longer backup file would leave a corrupt JSON tail (the backup then fails `validateBackupJson` on re-import).
Fix (XS, data/files/DocumentFiles.kt:23): `context.contentResolver.openOutputStream(uri, "wt")`. Test (S): Robolectric test in `app/src/test/.../data/files/DocumentFilesTest.kt`: temp file containing a long string, `DocumentFiles(context).writeText(Uri.fromFile(f), "short")`, assert file content == "short". (May pass even before the fix under Robolectric; still pins the contract.) Risk: none.

## R4 — PARTIAL — unbounded `readBytes()` is real; the CSV BOM claim is WRONG
- BOM for CSV: WRONG. `normalizeCsvText` (domain/imports/CsvParser.kt:19) already does `removePrefix("\uFEFF")` and `parseRows`/`detectSeparator` are tested with BOM (ImportParserTest lines 23 and 31-38). Nothing to do in ImportViewModel.
- BOM for the JSON backup: not handled (`Json.parseToJsonElement` rejects U+FEFF -> INVALID_BACKUP). Backups written by the app have no BOM, but a hand-edited file in Notepad on Windows may get one. Cheap to cover in the same place.
- Unbounded read: a user picking a multi-hundred-MB file gets `OutOfMemoryError` (an Error, not caught by `catch IOException`) -> crash. Real but only for a deliberately wrong file.
Fix (S, data/files/DocumentFiles.kt `readText`): cap at e.g. 20 MB: read through a bounded loop / `readNBytes(MAX + 1)` and return null (-> existing FileUnreadable / FILE_ERROR path) when size > MAX; then `decodeToString().removePrefix("\uFEFF")`. Put `private const val MAX_TEXT_BYTES = 20 * 1024 * 1024` in the file. Tests: DocumentFilesTest: file larger than the cap -> null; file with BOM -> text without BOM. Risk: low (the largest realistic backup is a few MB; 20 MB leaves ~10x headroom; check with a 10k-card export if worried).

## R5 — CONFIRMED — `@ApplicationScope` has no `CoroutineExceptionHandler`; an exception in the startup launch kills the process
`SyncLifecycle.start()` launches `runBootstrap(); purgeExpiredArchives()` straight into a `SupervisorJob + Dispatchers.IO` scope; an uncaught exception in a `launch` goes to the default uncaught-exception handler = crash at every start. Cloud errors are already swallowed (`reportingStatus`), but Room/normalize/merge exceptions on odd cloud data (or SQLiteFull/IO errors) are not, and they would repeat on every launch (crash loop) until app data is cleared. The same scope also runs foreground/background hooks and scheduler flushes.
Fix (S, di/AppModule.kt:30-33): `CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e -> Log.e("AppScope", "Unhandled background failure", e) })`. Applies only to `launch` roots, `async` still propagates to `await()` (signIn/resolveMigration unaffected). Optional in `SyncLifecycle.start()`: split the first launch so a failing bootstrap does not skip `purgeExpiredArchives()` (try/catch around `runBootstrap`). No unit test needed (module is not exercised by tests); manual check = none. Risk: low; logs instead of crashing, which is the wanted behaviour for background sync work.

## R6 — PARTIAL — mostly already handled in the ViewModels; two consumers are missing it
`flowOn` on the combined flow also moves the upstream repository operators (combine/withCards/JSON decode) off Main. Already present: DashboardViewModel:70, StatsViewModel:57, BrowseViewModel:91. Missing: ArchiveViewModel (collects `archivedDecks()` which joins ALL live cards of the namespace and decodes them on Main; one-off on entering the screen) and DeckSettingsViewModel (`deck(deckId)` decode of that deck's cards on Main on every cards change). Impact small (tens of ms at a few thousand cards) but cheap.
Fix (S, CROSS-LAYER: ui-study/dashboard/browse/components/theme/archive/stats area for ArchiveViewModel and ui-settings/decksettings for DeckSettingsViewModel): add `.flowOn(Dispatchers.Default)` before `.stateIn(...)` like the others (use the same dispatcher convention as DashboardViewModel). For ArchiveViewModel also double check tests use `Dispatchers.setMain`-style harness; `flowOn(Default)` may make assertions asynchronous, so follow how DashboardViewModelTest handles it. No repository change (a hard-coded dispatcher in DeckRepository would make every repository test nondeterministic). Risk: low-medium for test timing.

## R7 — CONFIRMED — `swapPages` captures `ns` and then `getDeck` re-reads the namespace
DeckRepository.kt:163-166. Practically unreachable (namespace switches are serialized by the sync lock and the window is microseconds), but guest ids can equal account ids after MIGRATE, so reading two different namespaces in one transaction is wrong by construction and the fix is 2 lines.
Fix (XS): replace `getDeck(deckId)` inside the transaction with `val deck = deckDao.getLiveDeck(ns, deckId)?.toDomain(cardDao.getLiveCardsOf(ns, deckId)) ?: return@withTransaction false`. Existing swap tests (DeckRepositoryTest) cover it. Risk: none.

## R8 — NOT_WORTH — delete+reinsert on merge is a deliberate simplification and fast enough
`replaceAll` runs in one transaction in the background; Room emits once at commit, so no flicker. Cost is O(rows) per bootstrap/foreground-with-changes, i.e. a few hundred ms at a few thousand cards on IO. It also preserves local rowid order because `mergeUserData` keeps local order first. A row-level diff write is a large, risky change for no visible gain. "replaceAll reads namespace itself": both `snapshot()` and `replaceAll` run inside `scheduler.exclusive` which also guards every namespace switch, so they cannot diverge.

## R9 — CONFIRMED (corrected diagnosis, M) — card order (default SEQUENTIAL!) and deck order are lost on any device that is not the origin
Not just "differs after merge": `ORDER BY rowid` is insertion order. Firestore `get()` returns documents ordered by document id; ids are random UUIDs (core/Ids.kt). `fetchDeck` and `fetchData` keep that order, `replaceAll` inserts in that order, so on a fresh install / second device / the debug+release pair (separate app ids) the cards of an imported deck come in UUID order and `CardOrder.SEQUENTIAL` (`orderCards` returns the list as-is) shows a shuffled sequence; deck list order too. The origin device keeps order because `mergeUserData` puts local items first. There is no order information in Firestore or in the JSON backup beyond list order (backup is fine, it serializes lists in order).
Fix design WITHOUT a Room migration (rowid order follows insertion order, so only the cloud needs the info), all in data/:
1. FirestoreSchema: optional Long field `position` on deck and card docs (old docs lack it; reads stay lenient for this field only, `ignoreUnknownKeys`-style tolerance keeps old app versions working). `deckToMap(deck, position)`, `cardToMap(card, position)`; `deckFromMap/cardFromMap` expose `position` (return a small `Positioned<T>` or a `Pair`).
2. SyncOp.PutDeck/PutCard get `position: Int` = index in `StoreData.groups` / `group.cards` of the snapshot (snapshot is `ORDER BY rowid` and includes tombstones that are never purged, so indexes are stable; new items append).
3. SyncDiff: emit a Put when content differs OR the index differs from the baseline's index of the same id (so the first push after the update rewrites every doc once to add `position`; for ~N cards that is N writes once, fine for Firestore free tier up to ~20k/day, chunked by `MAX_BATCH_OPS`).
4. SyncEngine.fetchDeck/fetchData: sort by `(position ?: Long.MAX_VALUE, docId)` before building the lists. Merge needs no change (local first, cloud-only appended in cloud order).
Tests: FirestoreSchemaTest (position round trip, missing position tolerated), SyncDiffTest (index change triggers Put; unchanged index + content = no op), TwoDeviceSyncTest (device B bootstrapped from cloud sees A's card order and deck order). Risk: medium (touches the sync wire format, additive and optional only). Decision for owner: matters if a reinstall / second device ever happens; on the single phone with its current Room data nothing is lost, and the first push after the change will upload the right positions. If the owner decides it is not needed, at least keep this documented as known limitation.

## R10 — WRONG — nothing can run concurrently with `clearBaseline`
Every `SyncEngine` call (`load`, `hasFreshBaseline`, `save`) happens under `scheduler.inFlight` (`flush`, `exclusive`, `bootstrap`, `refreshIfCloudChanged`, `resolveMigration`), and `clearBaseline` is called from `onSignedOut()` inside `scheduler.exclusive { ... }` (signOutAccount) as well. `abort()` only cancels the child job; `flush`'s `coroutineScope` waits for it to finish before releasing `inFlight`. So a save in flight cannot overlap `clearBaseline`. Visibility of the plain `var` is covered by the mutex happens-before chain. Optional cosmetic: `@Volatile` on `baseline` (XS), no behaviour change.

## R11 — EDGE — unreachable through the app's own write path
`modeFromMap` throws for `isBuiltIn && blank sourceId`, `modeToMap` omits a blank id. But local data is always normalized: `normalizeStudyMode` sets `isBuiltIn = (sourceId != null && known)`, so after any `replaceAll`/merge a built-in mode always has a known non-blank id; `addMode` creates `isBuiltIn=false`; seeds have valid ids; `updateMode` keeps the ids of an already-normalized mode. The asymmetry is documented in the comment ("pusty identyfikator ... jak brak"). No action.

## R12 — EDGE — real SDK behaviour, but conflict window is tiny and it self-heals
Facts: `batch.commit().await()` only completes on server ack; on the 30 s timeout the batch stays in the SDK write queue (also across restarts with disk persistence) and is applied on reconnect. Disabling disk persistence would NOT remove it within the process (the SDK queues writes in memory too), so the suggested setting does not fix the stated problem. Harm scenario: phone offline edits card X, tablet online edits X later; on reconnect the stale queued write overwrites X in the cloud before the phone merges. Needs offline edit + same doc edited elsewhere + 1-2 devices; it self-heals at the next bootstrap (merge by timestamps: the newer local/other copy wins and the diff against the stale baseline re-uploads it). Reads use `Source.SERVER`, so a previous user's cache is never read; leftover queued writes of a previous uid are rejected by the owner-only rules. Cache file of the previous user stays on disk on the owner's own phone. Skip.

## R13 — EDGE — DataStore writes are atomic (temp + rename); corruption essentially never happens
Adding `ReplaceFileCorruptionHandler` and `.catch { if (it is IOException) emit(emptyPreferences()) else throw it }` is the documented pattern and ~5 lines, but the failure it guards against is practically nonexistent. If the owner wants a defensive 5-liner, put it in UserPreferencesRepository.kt (`preferencesDataStore(name, corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() })`); not needed.

## R14 — EDGE — resurrection by edit matches the deliberate merge rule
Merge rule: a later edit than the delete revives (`isAlive`, MergeEngine). `updateMode` from the editor for a mode that was deleted on another device in the meantime follows that rule. Requires editor open + remote delete + merge + save. `resetMode` is only reachable from live-mode UI. Skip.

## R15 — EDGE — `reviewCard`: no caller can pass a deck other than the card's
Cards cannot be moved between decks (no such feature), `PendingReview.deckId` always comes from the same session list. FK exception only if the deck row is hard-deleted mid-session (it is only soft-deleted; `replaceAll` re-inserts it). The extra STUDY signal when the card vanished costs one empty push (two root reads). Using `deckId` from the stored entity would leave the parameter dead and force churn in ~12 call sites for no practical gain. Skip.

## R16 — EDGE — addCard for a deck deleted in the meantime
Soft-deleted deck row still exists, so no FK error; the card is invisible (filtered by `liveCards` join) and later cleaned like any card of a tombstoned deck. A hard-deleted row would need the deck to disappear from Room while Browse is open; merge never removes rows (tombstones kept). Skip.

## R17 — PARTIAL — trim asymmetry is a real inconsistency; "out of range still bumps updatedAt" is cheap to guard
- `renameDeck` trims, `setPageName` does not. UI: `PersistedTextField` shows its own text while focused and re-reads the stored value after focus loss, so trimming in the repository is safe (this is exactly how deck rename already behaves) and a trailing space in a page name has no use.
- `editPageMetadata` updates and signals even when `pageIndex` is out of range or the edit is a no-op. `pageNames` is padded to the stored page count by `normalizeGroup`, so a guard on `pageIndex !in deck.pageNames.indices` is safe (same check as `swapPages`).
Fix (S, DeckRepository.kt:119-126): `setPageName` -> `deck.copy(pageNames = ... if (i == pageIndex) name.trim() else old)`; make `editPageMetadata`'s lambda return `FlashcardGroup?` (null = nothing to change -> return 0 from the transaction) and return null when index is out of range, in `setPageName` and `setPageLanguage`. Tests (DeckRepositoryTest): `setPageName(" A ")` stores "A"; out-of-range index does not change `updatedAt` and emits no mutation. Risk: low. Note ImportViewModel does not trim page names either (ui-settings/imports area, CROSS-LAYER, optional: trim `pageNames` in `ImportViewModel.import`).

## R18 — EDGE — `deletedAt` of a wrong type never occurs
Only this app (Long) writes the field. Making it strict would drop a doc (parseOrSkip) on any unexpected representation, which is a worse failure than treating a malformed marker as "alive". The class KDoc says STRICT for required fields; `deletedAt`/`archivedAt` are optional by design. At most fix the comment wording (XS): "pola opcjonalne (deletedAt/archivedAt) są łagodne". Skip code change.

## R19 — NOT_WORTH — class split of SyncLifecycle for dependency count only
13 constructor deps is a smell but the class is cohesive around one lock (`inFlight`/`accountChanges`) and is covered by TwoDeviceSyncTest (so "untested" in the project facts is stale for SyncLifecycle). A generic split risks the lock ordering that was debugged. The one extraction that pays is R20.

## R20 — PARTIAL — layering smell is real; do the small extraction
`data.sync.SyncLifecycle` imports `playback.StudySessionHolder` and `domain.session.SessionStatus` for one block: "session ended AND no pending review writes -> `awaitIdle(); pushNow()`" (SyncLifecycle.kt:119-136). CROSS-LAYER (playback).
Fix (S-M): move that block into a small class in `playback/` (for example `SessionEndSync @Inject constructor(holder: StudySessionHolder, reviewWrites: ReviewWriteQueue, syncLifecycle: SyncLifecycle, @ApplicationScope scope)` with `fun start()`), started from `RayCollApplication.onCreate` after `syncLifecycle.start()`. SyncLifecycle drops 3 constructor params and the playback/domain imports, `pushNow()` is already public. Update `TwoDeviceSyncTest` (constructor + start the new class where the test relies on end-of-session flush). Risk: low-medium (a missed `start()` silently removes the end-of-session flush; the TwoDeviceSyncTest end-of-session scenario guards it). Low priority.

## R21 — NOT_WORTH — TypeConverters would remove the corrupt-column tolerance
Columns are TEXT JSON on purpose and `decodeOrEmpty` logs and falls back to empty on a damaged row; a TypeConverter would throw from inside the Room cursor and break the whole query/screen. Targeted `UPDATE ... pageNamesJson` queries rely on `encodeStringList`. No migration-free benefit.

## R22 — NOT_WORTH — scheduler primitives; covered by SyncSchedulerTest and known pitfalls
The mix (AtomicBoolean/Integer/Reference, Mutex, `@Synchronized` timer) is each used for one narrow purpose and documented in comments (self-cancel pitfall). "Redundant flush per review past the threshold": `studyReviewCount` is reset when `flush` acquires the lock; extra launches only happen while a previous slow flush holds the lock and the user finishes 10 more reviews (audio mode: minutes), and they find `pending == false` and return immediately unless the push failed. EDGE.

## R23 — NOT_WORTH — hand-rolled queue works and is tested
ReviewWriteQueueTest covers retry, order, awaitIdle. A Channel rewrite risks the "retry keeps order" guarantees. `PendingReview.namespace = GUEST` default is overwritten in `submit` (documented by the `copy(namespace = currentNamespace())`); dropping the default would only churn ~6 test call sites.

## R24 — NOT_WORTH — churn
`reviewCard(deckId, cardId, rating, baseState, namespace)` is also called positionally from 12 tests; `rating: Int` is the port type of `SessionDeps` (domain). Passing `PendingReview` into the repository saves two lines in one lambda.

## R25 — PARTIAL — the redundant null check is real, merging the three loops is not worth it
`prevCard == null || prevCard != card` (SyncDiff.kt:46) and `prevMode == null || prevMode != mode` (line 59) and `prevGroup == null || ...` are not equivalent to dropping the null part in the group case (different expression: `prevGroup.copy(cards = ...)` needs the null check), but for cards and modes `prevCard != card` alone already yields true for null.
Fix (XS): `if (prevCard != card)` and `if (prevMode != mode)`. Leave the loops. Tests: SyncDiffTest unchanged and green. Risk: none.

## R26 — NOT_WORTH — and the collectionGroup idea would not work with the current rules
1+N reads happen once per bootstrap/refresh, concurrently via `async` for a few dozen decks. A `collectionGroup("cards")` query is matched against rules of the form `/{path=**}/cards/{id}`; `firestore.rules` only has `users/{uid}/{document=**}`, so the query would be denied, and without a rules change it could also return other users' cards. No change.

## R27 — NOT_WORTH — `reportingStatus` is intentionally a free function with its own test (ReportingStatusTest)
A sentinel `onError` value is the simplest way to share the status handling between `load` and `save`.

## R28 — PARTIAL — duplicated literal is real; the activity serverTimestamp is deliberate
Activity docs get `updatedAt = serverTimestamp()` added in `SyncEngine.write` (not in `activityToMap`, which stays a pure map for unit tests because a `FieldValue` is not comparable/pure data). Existing docs have it; keep.
Fix (XS): add `const val FIELD_UPDATED_AT = "updatedAt"` to `FirestoreSchema` and use it in `deckToMap`/`modeToMap`/`cardToMap` (only decks and modes write `updatedAt`) and in `SyncEngine` (delete its private copy); add a one-line comment on the PutActivity branch ("marker dokumentu aktywności ustawia serwer"). Leave `Pair<ref, Map?>` as is (private, used in one place). Tests: FirestoreSchemaTest unchanged. Risk: none.

## R29 — PARTIAL — the `when(...).also` is awkward; the dedup with `reconcileWithCloud` is not
BootstrapLoader.kt:46-60: `when (val cloud = withTimeoutOrNull(...) ?: Failed) { ... }.also { refreshUntouchedBuiltInModes() }` hides the side effect and the timeout in the subject. `reconcileWithCloud` (Boolean) and `bootstrap` (BootstrapResult) map the same three states to different results, so merging them would obscure intent.
Fix (S): `val cloud = withTimeoutOrNull(CLOUD_LOAD_TIMEOUT_MS) { syncEngine.load(uid) } ?: CloudLoad.Failed; val result = when (cloud) {...}; localStore.refreshUntouchedBuiltInModes(); return result`. Covered by TwoDeviceSyncTest. Risk: none.

## R30 — CONFIRMED — outdated built-in predicate duplicated, `updatedAt == 0L` is a magic number
LocalStore.kt:99-104 and 114-118. Fix (S): private `fun StudyModeEntity.isUntouchedBuiltIn(): Boolean = isBuiltIn && updatedAt == 0L` (document: seeds have `updatedAt = 0`, any user edit bumps it) and one helper `private fun outdatedBuiltIns(existing: Map<String, StudyModeEntity>) = SEED_MODES.filter { seed -> existing[seed.id]?.let { it.isUntouchedBuiltIn() && it.toDomain().steps != seed.steps } == true }`; `seedIfEmpty` upserts `SEED_MODES.filter { it.id !in existing } + outdatedBuiltIns(existing)`, `refreshUntouchedBuiltInModes` upserts `outdatedBuiltIns(existing)`. Tests: DeckRepositoryTest/seed tests that cover mode refresh stay green; add none. Risk: low (same semantics).

## R31 — NOT_WORTH — churn
`val ns = ns` is the deliberate "snapshot the namespace once per operation" idiom (and the reason R7 is a bug). A `Namespace` value class would touch every DAO signature, entity and test. The repeated `flatMapLatest` (4 uses) is small; fine as is.

## R32 — PARTIAL — name the sentinel; wall-clock throttle is EDGE
`Long.MIN_VALUE / 2` (SyncLifecycle.kt:95) has no explanation. Fix (XS): `private const val NEVER_CHECKED = Long.MIN_VALUE / 2` with a one-line comment ("daleko w przeszłości, żeby `now - last` nie przepełnił Long"). Clock going back by hours would suppress foreground checks until catch-up; ignore.

## R33 — CONFIRMED — small helper removes duplication
`signIn` and `resolveMigration` both do `scope.async { accountChanges.withLock { ... } }`, which exists so that cancelling the UI does not cancel the account operation.
Fix (S, SyncLifecycle.kt): `private fun <T> inAccountScope(block: suspend () -> T): Deferred<T> = scope.async { accountChanges.withLock { block() } }`; `signIn` returns it, `resolveMigration` does `inAccountScope { ... }.await()`. TwoDeviceSyncTest covers both. Risk: none.

## R34 — PARTIAL — tiny helper yes, listener rewrite no
Fix (XS, AuthRepository.kt): `private fun FirebaseUser.toAccount() = Account(uid, email)` used in the 3 places (init value, listener, signInWithIdToken). Do not replace the listener + `MutableStateFlow` with a `callbackFlow`/`stateIn` (changes initial-value semantics used by `account.value` readers). Tests: AppSettingsSignInTest unaffected.

## R35 — PARTIAL — add logging; the rest is not an issue
- `signInWithIdToken` swallows the exception (LOGIN_FAILED) with no log; failures here (wrong SHA-1, API key, rules) are impossible to diagnose from logcat. Fix (XS): `catch (e: Exception) { Log.w(TAG, "Firebase sign-in failed", e); ... }`, and the same for the generic `GetCredentialException -> PLAY_SERVICES` branch (log the exception class/message).
- Mapping every other `GetCredentialException` to PLAY_SERVICES: acceptable, the user-visible message is generic; splitting by subclass adds strings in 5 locales for little gain.
- `CredentialManager.create(...)` per call: cheap factory; leave.
CROSS-LAYER: none (strings untouched).

## R36 — INTENTIONAL — `CurrentUser` is a port owned by the consumer
CloudSync.kt:26 comment: "Zalogowane konto widziane przez synchronizację (implementacja: AuthRepository)". Same dependency-inversion pattern as `CloudSync` (implementation in SyncEngine) and the reason TwoDeviceSyncTest can substitute fakes (SyncModule `@Binds` comment). Keep.

## R37 — NOT_WORTH — thin but purposeful
MigrationController is the only place that knows how MIGRATE/FRESH write local data. The `CANCEL -> Unit` branch with the comment makes the `when` exhaustive and documents where cancel is handled. Narrowing the enum type would add a second enum for one branch.

## R38 — CONFIRMED — backup encode/validate/decode runs on Main
`AppSettingsViewModel.exportBackup/importBackup` use `viewModelScope` (Main); `BackupRepository` then serializes the whole store with `prettyPrint` (export) or parses it three times (validate -> plain -> decode) on the caller thread. Several MB = a visible freeze.
Fix (S, data/repository/BackupRepository.kt): inject `@DefaultDispatcher private val dispatcher: CoroutineDispatcher` and wrap the pure CPU parts in `withContext(dispatcher) { ... }` (encode in `exportTo`; validate + decode in `importFrom`); `localStore.snapshot()`/`replaceAll` stay as they are (Room switches threads). Update the 2 test call sites (AppSettingsSignInTest:116, SaveFailureTest:~51) to pass `Dispatchers.Unconfined`. Parse-twice (`validateBackupJson` then `decodeFromString`) can stay; cheap follow-up (domain, CROSS-LAYER): make `validateBackupJson` return the parsed `JsonElement` and `decodeFromJsonElement` it. Risk: low.

## R39 — NOT_WORTH — three configs differ for a reason
Backup adds `prettyPrint` (human-readable file); mappers and Firestore each need `encodeDefaults`+`ignoreUnknownKeys` and live in different layers. Sharing would create a dependency between them for four identical lines.

## R40 — NOT_WORTH — contained, tested unchecked cast
`stepsToPlain` returns the list produced by `jsonElementToPlain` of a `JsonArray`; the cast cannot fail. "Validates twice" in `modeFromMap` (assertStudyModeStep then strict decode) is deliberate (readable message vs structural decode) and covered by FirestoreSchemaTest.

## R41 — NOT_WORTH — a `DocReader` rewrite of tested strict parsing code
The repetition is explicit on purpose (each requirement carries a message naming the doc). High regression risk against data already in users' Firestore for no behavioural gain.

## R42 — CONFIRMED — `withCards` lives in the wrong file
It is an entity->domain mapping used by DeckRepository and LocalStore. Fix (S): move to `data/local/mappers/EntityMappers.kt` as `fun List<DeckEntity>.toDomain(cards: List<CardEntity>): List<FlashcardGroup>` (or keep name `withCards` as an internal function there), update 2 call sites (DeckRepository.kt:78, LocalStore.kt:41, delete from DeckRepository.kt:260-263; no test references it). Risk: none.

## R43 — NOT_WORTH — single production call site
`addDeckWithCards` is called once (ImportViewModel) with the three lists in order; four test call sites. A parameter object would just rename the arguments. (Mixing up `languages` and `pageNames` is possible but the call site is readable.)

## R44 — CONFIRMED — document the single-collector contract
`receiveAsFlow` splits items between collectors. Only `SyncLifecycle.start()` collects. Fix (XS, DataMutations.kt): add to the KDoc of `events` "Jeden odbiorca (synchronizacja): drugi kolektor dostawałby co drugi sygnał." Nothing else.

## R45 — PARTIAL — fix the misleading KDoc only
`purgeExpiredArchives` KDoc says "Trwałe usunięcie" but it soft-deletes (tombstone, intended: the deletion must reach other devices). Fix (XS): reword to "Usuwa (tombstone) talie zarchiwizowane dłużej niż okres przechowywania". "Loads all decks": decks only (no cards), a handful of rows, once per foreground; leave. "Tombstones never purged": intended; a purge would resurrect deleted data from any device that was offline longer than the retention, and cleanup needs a cloud delete protocol. NOT_WORTH.

## R46 — PARTIAL — rename/move the two scheduler constants; do not centralize timeouts
`StudyConstants.PERSISTENCE_DEBOUNCE_MS` / `STUDY_REVIEW_FLUSH_THRESHOLD` are used only by `SyncScheduler` (legacy names from the old persistence layer). CROSS-LAYER (domain+core).
Fix (S): delete both from `domain/model/Constants.kt` and put `private const val SYNC_DEBOUNCE_MS = 1_200L` and `private const val STUDY_FLUSH_EVERY_REVIEWS = 10` (names free) at the top of `SyncScheduler.kt` as the constructor defaults. Check `grep PERSISTENCE_DEBOUNCE_MS` in tests first (no hits at the time of review). The other timeouts (30 s per request, 8 s bootstrap, 5 s sign-out, 30 s foreground throttle) each carry a KDoc next to their use; leave them.

## R47 — NOT_WORTH — the comment explains the two-statement version
`INSERT ... ON CONFLICT DO UPDATE` needs SQLite 3.24 (Android 11+, minSdk is 26). A one-statement `INSERT OR REPLACE ... COALESCE((SELECT count ...), 0) + 1` is possible but changes a tested, working hot path (every review) for style.

## R48 — PARTIAL — document why the soft delete does not bump `updatedAt`
Fix (XS, Daos.kt softDeleteDeck/softDeleteCard/softDeleteMode): comment "updatedAt zostaje: tombstone ma własny deletedAt, a późniejsza edycja ma wygrać z usunięciem (MergeEngine.isAlive)". Duplicate query strings are a Room constraint (each @Query is a literal); extracting constants gains nothing. Not worth it.

## R49 — NOT_WORTH — and would need a real migration for no measurable gain
SQLite uses the (id, namespace) PK index for `WHERE namespace = ? AND id = ?` regardless of predicate order; `WHERE namespace = ?` scans, but the tables are tiny and namespace holds at most guest + one account. Reordering the PK is a schema change on the installed v1 database with real data.

## R50 — NOT_WORTH — columns mirror the domain types
`SrsState.state: Int`, `studyFilter`/`cardOrder` keys are the wire/backup formats (`KeyedEnum.key`); converting columns needs a migration and mapper churn.

## R51 — NOT_WORTH — DI style
Repositories obtain DAOs from the injected `RayCollDatabase`; tests build repositories from one in-memory DB (TestRepos). Providing DAOs separately only adds module code.

## R52 — NOT_WORTH — leave dispatchers and the clock binding
`Dispatchers.IO` in the app scope and `DocumentFiles` is fine (production only; tests do not depend on it). `@Binds` is impossible for `provideClock` because `SystemClock` is an `object`. The only worthwhile injection is R38 (BackupRepository).

## R53 — NOT_WORTH — churn
Top-level `preferencesDataStore` delegate is the documented pattern and UserPreferencesRepositoryTest works with it; moving `ThemePref` would touch UI imports across areas.

---

## B1 — PARTIAL — the failure is deliberate, the eagerness is avoidable
Commit 51095cf9: "Bez klucza build kończy się czytelnym błędem" — keep the clear error. But the `throw` is at configuration time, so `:app:ktlintCheck`, `lint`, IDE sync, and a fresh `git worktree` / agent checkout (local.properties is untracked) all fail even though they do not need the key. 
Fix (S, app/build.gradle.kts:20-27, risk low-medium for build script): resolve the key lazily: `val firebaseApiKey = providers.environmentVariable("FIREBASE_API_KEY").orElse(providers.fileContents(rootProject.layout.projectDirectory.file("local.properties")).asText.map { Properties().apply { load(it.reader()) }.getProperty("firebase.apiKey").orEmpty() }).map { it.ifBlank { throw GradleException("Brak klucza Firebase: ...") } }` (keep the same message; keep priority local.properties first if desired), then replace `buildConfigField(...)` in `defaultConfig` by `androidComponents.onVariants { v -> v.buildConfigFields.put("FIREBASE_API_KEY", firebaseApiKey.map { BuildConfigField("String", "\"$it\"", null) }) }` (verify the exact AGP 9.4.1 API shape; AGP also accepts a `Provider<String>` in `buildConfigField` in recent versions). Using `providers.fileContents` instead of `java.io` also makes the configuration cache track `local.properties` changes. Verify: with the key removed, `./gradlew :app:ktlintCheck` passes and `:app:assembleDebug` fails with the message; with the key present assembleDebug/Release unchanged. Not a visible UX change.

## B2 — NOT_WORTH — plugin adds a compatibility risk
ktlint was just wired in (memory: "ktlint w Gradle: :app:ktlintCheck", config in .editorconfig). The community plugin must support AGP 9.4.1 + Gradle 9.8.1 + the config cache; the current 30 lines work, are cache-compatible and pinned to ktlint 1.8.0. `--add-opens` is required by the ktlint CLI on JDK 23. Only improvement worth considering (XS): add `inputs.files(fileTree("src"))` + an output marker if up-to-date checking is wanted; not needed.

## B3 — NOT_WORTH — duplicate but harmless, removal is release-only risk
The R8 configuration dump (app/build/outputs/mapping/release/configuration.txt, lines 1143-1223) confirms kotlinx-serialization 1.11.0 ships its own consumer rules (Companion, `serializer()`, INSTANCE, `$$serializer` descriptor). The app's block is mostly redundant (the `$$serializer { *; }` keep is broader than needed). Removing it only shrinks a few serializers; a mistake shows up as a crash in the release build only when exporting/importing backups or reading mode steps. If the owner wants it done: remove the three `-keep*` rules, keep `-keepattributes` only if needed, build release, then smoke-test backup export/import, study mode steps and sync on the phone. Low value, so skip.

## B4 — PARTIAL — lint defaults are redundant; the rest should stay
`lint { warningsAsErrors = false; abortOnError = true }` are both AGP defaults (and lint-results-debug.txt currently reports "No issues found"). Fix (XS): delete the whole `lint {}` block (app/build.gradle.kts:73-76), run `./gradlew :app:lintDebug` once. The `packaging { excludes += "/META-INF/{AL2.0,LGPL2.1}" }` is needed only if duplicate-file errors occur (coroutines/other jars ship these); removing needs a clean release build to prove it, no gain — leave. `room { schemaDirectory("$projectDir/schemas") }` is the documented form; leave.

## B5 — NOT_WORTH — half of it is WRONG
- `hilt-lifecycle-viewmodel-compose` IS used: `hiltViewModel` is imported from `androidx.hilt.lifecycle.viewmodel.compose` in 10 screens (AppSettingsScreen, DashboardScreen, StudyScreen, ...). Dropping it breaks compilation. WRONG.
- `material-icons-extended`: 40+ distinct icons are used, many only in the extended set (Tune, Style, Sync, LocalFireDepartment, GraphicEq, CloudOff, Inbox, SentimentDissatisfied, ...). R8 strips unused icons in release (the release APK is unaffected); only debug builds carry the full set. Replacing them with local vector assets is churn.

## B6 — NOT_WORTH — style only
The catalog uses `group`+`name`+`version.ref` consistently; bundles would hide which dependency a line adds. No action.

## B7 — WRONG — each sub-claim checks out as intentional or incorrect (merged manifest inspected)
Merged release manifest: app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml.
- `tools:targetApi="34"`: not stale. It suppresses lint `UnusedAttribute` for `enableOnBackInvokedCallback` (API 33) and `dataExtractionRules` (API 31) with minSdk 26; Android's own docs show `tools:targetApi="33"` for exactly this attribute. Removing it risks new lint warnings. The number 34 vs 33 does not matter.
- `enableOnBackInvokedCallback="true"`: default only becomes true for targetSdk 36+ ON Android 16 devices; on Android 13-15 (minSdk 26 users) the attribute is what opts into predictive back. Keep.
- WAKE_LOCK / FOREGROUND_SERVICE: appear exactly once in the merged manifest (lines 14-16) and no library contributes them (media3-session does not declare them); the service needs them. Not duplicated.
- `allowBackup` including the Room DB: deliberate, `backup_rules.xml` and `data_extraction_rules.xml` explicitly include `database`, the locale file and `datastore/` for both cloud backup and device transfer (restores the user's data when moving phones).
No change.

## B8 — NOT_WORTH — would break this machine's build
Only JDK 23 is installed (`C:\Program Files\Java\jdk-23`; README requires JDK 23). `jvmToolchain(17)` would need JDK 17 or the foojay resolver plugin, and `jvmToolchain(23)` would conflict with `compileOptions` 17. `compileOptions` already pins source/target 17 and AGP applies the matching Kotlin `jvmTarget`. If reproducibility matters, `./gradlew updateDaemonJvm` (gradle-daemon-jvm.properties) is the right tool, but it still needs a locally available JDK; the known JDK 23 R8 C2 crash is a "retry the build" issue.

---

## Counts and lists
CONFIRMED (9): R3, R5, R7, R9, R30, R33, R38, R42, R44
PARTIAL (15): R4, R6, R17, R20, R25, R28, R29, R32, R34, R35, R45, R46, R48, B1, B4
WRONG (2): R10, B7
INTENTIONAL (1): R36
EDGE (9): R1, R2, R11, R12, R13, R14, R15, R16, R18
NOT_WORTH (25): R8, R19, R21, R22, R23, R24, R26, R27, R31, R37, R39, R40, R41, R43, R47, R49, R50, R51, R52, R53, B2, B3, B5, B6, B8

CROSS-LAYER: R6 (ArchiveViewModel in ui-archive area, DeckSettingsViewModel in ui-settings/decksettings), R17 (optional ImportViewModel trim, ui-imports), R20 (playback + RayCollApplication), R38 (optional: domain `validateBackupJson` return value), R46 (domain/model/Constants.kt).

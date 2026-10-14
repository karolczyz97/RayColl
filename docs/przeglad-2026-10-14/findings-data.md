# Findings: data/ + di/ + build config (paths relative to app/src/main/java/com/raycoll/app/ unless rooted)

R1 BUG data/sync/SyncEngine.kt:100-108 (+SyncLifecycle.kt:221-222) — save commits then re-reads root updatedAt; another device's write in between gets absorbed into baseline → hasFreshBaseline says fresh though not merged. Suggest client token.
R2 BUG SyncEngine.kt:76-79 — first upload dying before publishUpdatedAt leaves no root doc → next load CloudLoad.Empty although decks exist → seed/migrate may overwrite.
R3 BUG data/files/DocumentFiles.kt:23 — openOutputStream(uri) mode "w" doesn't truncate on some providers → trailing garbage. Use "wt".
R4 DocumentFiles.kt:18 — readBytes unbounded (OOM uncaught); BOM not stripped for CSV (ImportViewModel.kt:134).
R5 BUG di/AppModule.kt:32 + data/sync/SyncLifecycle.kt:104-107 — ApplicationScope no CoroutineExceptionHandler; runBootstrap/purgeExpiredArchives exception crashes app at startup.
R6 data/repository/DeckRepository.kt:49-79 — combine(::withCards) decodes all cards' JSON on collector dispatcher (Main) on every cards table change incl. each review. Suggest flowOn(Default).
R7 BUG DeckRepository.kt:162-181 — swapPages captures ns but calls getDeck which re-reads namespace.
R8 data/repository/LocalStore.kt:51-76 — mergeWithCloud → replaceAll deletes+reinserts every row each bootstrap/foreground merge; replaceAll reads namespace itself.
R9 data/local/db/Daos.kt:11,100,110,132 — ORDER BY rowid → deck order differs between devices after cloud merge.
R10 SyncEngine.kt:67-72 — baseline plain var; clearBaseline outside mutex races with in-flight save.
R11 BUG data/remote/firestore/FirestoreSchema.kt:88 vs 154-162 — modeToMap omits blank builtInSourceId; modeFromMap throws if isBuiltIn && blank → mode dropped as corrupt on other devices.
R12 di/FirebaseModule.kt:17 — Firestore default disk persistence + offline write queue while reads use Source.SERVER; timed-out commit stays queued and may apply later; previous user's cache after sign-out.
R13 data/local/prefs/UserPreferencesRepository.kt:20,52 — no ReplaceFileCorruptionHandler, no .catch(IOException).
R14 data/repository/StudyModeRepository.kt:52-65 — updateMode upserts whole mode → resurrects soft-deleted; resetMode uses getMode returning tombstones.
R15 DeckRepository.kt:241-257 — reviewCard writes with caller deckId not stored card.deckId; signals STUDY even if card gone.
R16 DeckRepository.kt:210-213 — addCard doesn't check deck live (invisible card / FK exception).
R17 DeckRepository.kt:103-130 — renameDeck trims, setPageName doesn't; out-of-range index still bumps updatedAt + sync.
R18 FirestoreSchema.kt:206 — optionalTimestamp turns wrong-typed deletedAt into null (doc looks alive), contradicts STRICT comment.
R19 data/sync/SyncLifecycle.kt:51-65 — 13 deps, many responsibilities; split.
R20 SyncLifecycle.kt:14,120-122 — data.sync imports playback.StudySessionHolder and domain.session.SessionStatus (layering).
R21 data/local/db/Entities.kt + mappers/EntityMappers.kt:33-41 — JSON strings in entities, manual encode/decode; use TypeConverters.
R22 data/sync/SyncScheduler.kt:30-139 — mix of AtomicBoolean/Integer/Reference, Mutex, @Synchronized, timer Job; enqueueStudy launches redundant flush per review past threshold until reset.
R23 data/repository/ReviewWriteQueue.kt:64-135 — hand-rolled queue with synchronized, nullable worker, failed flag; PendingReview.namespace default GUEST meaningless.
R24 DeckRepository.kt:241-247 + ReviewWriteQueue.kt:53-60 — reviewCard re-spreads PendingReview; rating Int.
R25 data/sync/SyncDiff.kt:28-64 — 3 copy-pasted diff loops; `prev == null || prev != card` redundant.
R26 SyncEngine.kt:112-149 — fetchData 1+N requests, unbounded concurrency; collectionGroup.
R27 SyncEngine.kt:234-246 — reportingStatus top-level with sentinel onError.
R28 SyncEngine.kt:178-195 — write() adds serverTimestamp for activity docs but schema doesn't; Pair<ref, Map?>; FIELD_UPDATED_AT duplicates literal.
R29 data/sync/BootstrapLoader.kt:46-60 — when + .also + withTimeoutOrNull in subject hard to read; reconcileWithCloud repeats load→merge→result.
R30 LocalStore.kt:90-121 — outdated built-in predicate duplicated in seedIfEmpty and refreshUntouchedBuiltInModes; magic updatedAt==0L.
R31 DeckRepository.kt:45-77, StudyModeRepository.kt:30-35 — repeated `ns` getter, flatMapLatest + OptIn; `val ns = ns` shadowing 6+ places; suggest helper + value class Namespace.
R32 SyncLifecycle.kt:95,205 — Long.MIN_VALUE/2 sentinel; wall clock for throttle.
R33 SyncLifecycle.kt:70-76,154-164 — scope.async{withLock}.await() twice → helper.
R34 data/remote/auth/AuthRepository.kt:66-71 — Account(uid,email) built 3x; manual listener + MutableStateFlow mirror.
R35 AuthRepository.kt:92-93,114 — all GetCredentialException except cancel/no-cred → PLAY_SERVICES; signInWithIdToken swallows exception without log; CredentialManager created repeatedly.
R36 data/sync/CloudSync.kt:26-28 — CurrentUser interface in data.sync implemented in data.remote.auth.
R37 data/sync/MigrationController.kt:19-41 — thin class; CANCEL handled elsewhere.
R38 data/repository/BackupRepository.kt:30-54 — encode/validate/decode on caller dispatcher (Main); parsed twice.
R39 EntityMappers.kt:22, FirestoreSchema.kt:41, BackupRepository.kt:30 — three Json configs.
R40 FirestoreSchema.kt:209-218 — stepsToPlain + dropNulls unchecked cast; validates twice.
R41 FirestoreSchema.kt:97-172 — repeated field reading; DocReader.
R42 DeckRepository.kt:260-263, LocalStore.kt:41 — withCards placement.
R43 DeckRepository.kt:84-99 — addDeckWithCards 3 positional lists.
R44 data/repository/DataMutations.kt:25-30 — Channel receiveAsFlow splits between collectors; document single collector.
R45 DeckRepository.kt:199-206 — KDoc "Trwałe usunięcie" but soft delete; loads all decks; tombstones never purged.
R46 SyncScheduler.kt:27-28, domain/model/Constants.kt:50-51 — legacy naming PERSISTENCE_DEBOUNCE_MS; timeouts scattered (30s,8s,5s).
R47 Daos.kt:168-178 — incrementActivity @Transaction default method; single SQL statement possible.
R48 Daos.kt:100-104 — duplicated query strings; softDelete doesn't bump updatedAt (document why).
R49 Entities.kt:10,26,55,67 — PK [id, namespace] while queries filter namespace first; reorder before release (DB version 1). NB app already installed on user's phone with real data.
R50 Entities.kt:47 — CardEntity.state Int, studyFilter/cardOrder strings untyped.
R51 di/DatabaseModule.kt:16-19 — provide DAOs from module.
R52 di/AppModule.kt:32,36 — Dispatchers.IO hardcoded (also DocumentFiles.kt:26); @DefaultDispatcher unused by data; @Binds for clock.
R53 UserPreferencesRepository.kt:20-72 — top-level DataStore delegate not injectable; ThemePref enum in repo file.
B1 app/build.gradle.kts:21-27 — Firebase key read eagerly; missing key fails every Gradle invocation (IDE sync, lint, ktlint).
B2 app/build.gradle.kts:137-164 — hand-rolled ktlint config + JavaExec with --add-opens; use plugin.
B3 app/proguard-rules.pro:6-13 — kotlinx.serialization keep rules duplicate library consumer rules.
B4 app/build.gradle.kts:73-81 — lint defaults restated; packaging excludes boilerplate; room schemaDirectory string path.
B5 app/build.gradle.kts:100 / gradle/libs.versions.toml — material-icons-extended large; check hilt.lifecycle.viewmodel.compose usage.
B6 gradle/libs.versions.toml — no bundles; module= shorthand.
B7 app/src/main/AndroidManifest.xml:3,19,23,25 — stale tools:targetApi="34"; enableOnBackInvokedCallback default at 36+; FOREGROUND_SERVICE/WAKE_LOCK maybe duplicated by media3; allowBackup includes Room DB.
B8 settings.gradle.kts/gradle.properties — no jvmToolchain.

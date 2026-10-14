# Verdicts: playback area (playback/ + MainActivity, RayCollApplication, StudyViewModel, MainViewModel, navigation)

Paths relative to app/src/main/java/com/raycoll/app/ unless noted. Size S/M/L. Risk LOW/MED/HIGH-DEVICE (HIGH-DEVICE = audio/background behaviour that only a phone can verify).

Counts: CONFIRMED 20, PARTIAL 7, INTENTIONAL 6, EDGE 3, NOT_WORTH 14 (total 50).

Batching hint for the fixer (same files, do in one pass each):
- StudyViewModel.kt: P1, P19, P21, P25, P3 (VM half), P20 (comment).
- StudySessionService.kt: P12, P13, P14, P15, P16 (+P3 none).
- SttRecognizer.kt: P5, P6, P8, P9 (one edit).
- NavGraph.kt + SharedTransition.kt: P27, P29, P30, P31, P32.
- MainActivity.kt: P2.

---

## P1 — CONFIRMED — onAppBackground pauses+flags unconditionally; logic is wrong, but reach today is ~nil (cheap hardening)
Code reading is right: non-bg branch sets `pausedByAppBackground = true; engine.pause()` even when `isPaused` is already true, so onAppForeground would resume a pause the user/focus made. Reach: in the non-background mode (`runsInBackground == false`) no service runs, so NO other pause source exists (only `SessionAudioFocus`/`SessionControls` in the service, and `onTtsFailure` which is background-only). So today the only thing that pauses there is this method. Still a one-line guard, keeps the invariant "we only resume what we paused".
Fix (StudyViewModel.kt:257-267):
```
} else if (!engine.isPaused.value) {
    pausedByAppBackground = true
    engine.pause()
}
```
Make `onAppBackground`/`onAppForeground` `internal` (not private) so a test can call them without ProcessLifecycleOwner.
Tests: StudyRestartTest (or new StudyLifecycleTest, same harness `studyVm`): VM with a mode that cannot run hands-free (e.g. `ShowPageStep, ShowRatingsStep` -> runsInBackground=false); `engine.pause()`; `vm.onAppBackground(); vm.onAppForeground()` -> `engine.isPaused` stays true. Plus positive case: no prior pause -> background pauses, foreground resumes.
Size S, risk LOW. Not CROSS-LAYER.

## P2 — CONFIRMED — enableEdgeToEdge() follows SYSTEM dark mode, app theme pref can differ -> wrong status/nav bar icon colour
Verified: `enableEdgeToEdge()` default `SystemBarStyle.auto(TRANSPARENT, TRANSPARENT)` detects dark from `resources.configuration.uiMode`; `RayCollTheme` uses `ThemePref` (SYSTEM/LIGHT/DARK), so Dark pref on a light system gives dark icons on dark background (and Light pref on dark system: white icons on light). Also `themes.xml` windowBackground = `?android:attr/colorBackground` (DayNight, follows system) -> wrong-colour flash behind Compose (rotation, IME, overscroll, first frame after splash).
Default design (no visible layout change, only icon colour fix):
In `MainActivity.setContent`, compute the resolved dark flag from `prefs.theme` (`LIGHT->false, DARK->true, SYSTEM->isSystemInDarkTheme()`) and
```
DisposableEffect(isDark) {
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { isDark },
        navigationBarStyle = SystemBarStyle.auto(DefaultLightScrim, DefaultDarkScrim) { isDark } // or TRANSPARENT scrims if you want identical gesture-nav look
    )
    onDispose {}
}
```
(`enableEdgeToEdge` called again is supported; Google's own samples do this.) Keep `enableEdgeToEdge()` in onCreate for the pre-composition frame (splash holds until prefs load, so no visible gap).
For windowBackground: in the same effect `window.setBackgroundDrawable(ColorDrawable(MaterialTheme.colorScheme.background.toArgb()))` (must be inside RayCollTheme content so the scheme is the resolved one, incl. dynamic colours).
Avoid `AppCompatDelegate.setDefaultNightMode` (needs prefs before Application start -> sync DataStore read, plus activity recreate).
CROSS-LAYER (minor, optional): to avoid duplicating the 3-line `when`, add `@Composable fun ThemePref.isDark(): Boolean` in ui/theme/Theme.kt and use it in `RayCollTheme` and MainActivity. Acceptable fallback: duplicate the `when` locally in MainActivity (no cross-layer).
Tests: none practical (Compose + window); manual check on phone: Dark pref with light system and Light pref with dark system, both orientations, 3-button and gesture nav. Size S-M, risk LOW-MED (visual only, device check recommended).

## P3 — CONFIRMED — startForegroundService after suspensions can throw ForegroundServiceStartNotAllowedException -> uncaught in viewModelScope = crash. Window is small but the failure is a crash. HIGH-DEVICE
Verified: `StudyViewModel.startSession` suspends (getDeck, prefs.first) then `sessionHolder.register` -> `ContextCompat.startForegroundService`; same in `restartWith` after `awaitIdle()`+`getDeck`. If the app went to background in that window (user taps Study and instantly locks screen) API 31+ throws `ForegroundServiceStartNotAllowedException` (extends IllegalStateException; API<=30 also throws IllegalStateException for background starts). Related unhandled race: if the app goes background while `uiState` is still Loading, `onAppBackground` returns early (`as? Active ?: return`) and is never re-applied; ProcessLifecycleOwner.addObserver does NOT replay ON_STOP to a late observer (only brings it to CREATED). So the session then plays "in foreground mode" while the app is in background.
Fix:
1. `StudySessionHolder.register(...)`: return Boolean; wrap `startForegroundService` in `try { ...; true } catch (e: IllegalStateException) { Log.w("StudySessionHolder", "Cannot start study service", e); false }` (IllegalStateException is enough; it is also the supertype on API 26-30). Return true when `!runsInBackground` (nothing to start).
2. StudyViewModel: after `register` in `startSession` and `restartWith`: `if (!started || !ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) onAppBackground()` — i.e. if the process is already not visible, apply the same transition a normal background would (bg-capable + service started OK -> `setBackground(true)`; otherwise pause with `pausedByAppBackground = true`, resumed on foreground). Careful: when service failed to start but runsInBackground is true, `onAppBackground` would call `setBackground(true)` and the session would continue unserviced; in that case force the pause branch: add a VM flag `serviceStartFailed` or pass `runsInBackground = runsInBackground && started` into the stored field and uiState (`this.runsInBackground = ...` is set before register today; reorder so register's result updates it, and `_uiState` Active copy via `copy(runsInBackground = ...)`).
Tests: StudyRestartTest — extend `StudySessionHolder` construction with an injectable starter lambda (default `ContextCompat::startForegroundService`) so a test can throw `IllegalStateException`; assert no crash and `register` returns false; assert with Robolectric `ProcessLifecycleOwner` driven to CREATED (via `(ProcessLifecycleOwner.get().lifecycle as LifecycleRegistry).handleLifecycleEvent(ON_STOP)`) the engine ends up paused.
Size M, risk HIGH-DEVICE (the catch path and the API-31 restriction cannot be reproduced in Robolectric; happy path unchanged). Not CROSS-LAYER (holder + VM are both this area).

## P4 — EDGE — onCreate always startForeground / sticky restart shows then stops
`startForeground` immediately in onCreate is REQUIRED by the `startForegroundService` 5-second contract (it cannot wait to learn whether a session exists), so that part is correct. The sticky-restart case needs process death while the FGS is alive (low-memory kill/crash); result is a notification flash then `stopSelf()` (collector sees `session == null`). Cosmetic, ~never. Do not touch; any `START_NOT_STICKY` override would need a device test for no visible gain.

## P5 — PARTIAL — 110-line `listen()` is long, but extracting the whole listener is risk without test coverage
SttRecognizer has no unit tests and runs on the mic path verified on the phone. Do only the safe, testable extractions: (a) `private fun recognizerIntent(lang: String): Intent`; (b) pure `internal fun listenResultForError(error: Int, heard: String): ListenResult` (the `when` in `onError`: non-blank text -> COMPLETED(text); NO_MATCH/SPEECH_TIMEOUT -> COMPLETED(""); else ERROR). Leave the continuation + `RecognitionListener` inline (its cleanup/cancel ordering is subtle, see P7). Do with P6/P8/P9 in one edit.
Tests: new `SttErrorMappingTest` (plain JUnit): heard text wins over any error code; NO_MATCH and SPEECH_TIMEOUT -> empty COMPLETED; ERROR_NETWORK etc. -> ERROR.
Size S, risk LOW (pure extraction; HIGH-DEVICE only if someone restructures the listener, which this spec says not to).

## P6 — CONFIRMED — `withTimeout` + `catch TimeoutCancellationException` -> `withTimeoutOrNull`
Replace with `withTimeoutOrNull(timeoutMs) { suspendCancellableCoroutine<ListenResult> { ... } } ?: ListenResult(accumulator.combined(), ListenStatus.COMPLETED)`. Same behaviour (invokeOnCancellation cleanup still runs on timeout), and fixes a latent quirk: the current catch also swallows a TimeoutCancellationException raised by an OUTER `withTimeout` (it would return a result instead of propagating). Keep the explicit `suspendCancellableCoroutine<ListenResult>` type arg.
Tests: none cheap (needs a real recogniser); rely on P5's mapping test + phone smoke test of a listen step timeout. Size S, risk LOW (HIGH-DEVICE is not needed: semantics identical; smoke-test listen timeout once).

## P7 — INTENTIONAL — AtomicBoolean + Looper check + Handler.post cleanup
`SpeechRecognizer.cancel()/destroy()` must run on the main thread, while `invokeOnCancellation` runs on whichever thread cancels (parent scope may cancel from non-main), hence the thread check + post; AtomicBoolean makes cleanup idempotent across the listener callbacks and the cancel handler. AudioTurn's KDoc relies on the cleanup finishing before the next turn, so deferring everything to `Handler.post` unconditionally would be worse. Keep.

## P8 — PARTIAL — dead null branch is real; "swallows CancellationException" is wrong
`SpeechRecognizer.createSpeechRecognizer` never returns null (platform type); delete the `if (recognizer == null)` branch (S). `catch (_: Exception)` wraps a plain non-suspending call inside the `suspendCancellableCoroutine` block, which cannot throw CancellationException, so no cancellation is swallowed. Useful small add: `Log.w(TAG, "...", e)` in the two catch blocks (`createSpeechRecognizer`, `startListening`) — today both silently become `ListenStatus.ERROR` with no trace. Do with P5/P6. Size S, risk LOW.

## P9 — PARTIAL — only `val run` shadowing is worth touching
Rename `val run` -> `val destroy`/`teardown` (free during P5). `EXTRA_MAX_RESULTS=3` is harmless (n-best list; code takes the top hypothesis) and changing it alters recogniser behaviour verified on the phone — leave (NOT_WORTH for that half). Size S, risk LOW.

## P10 — NOT_WORTH — missing mic permission returns ERROR like an engine failure
Deliberate flow: StudyScreen requests RECORD_AUDIO up front when `needsMicrophone`; a failed listen sets `listeningFailed` and `onMicrophoneGranted()` restarts the card. Distinguishing needs a new `ListenStatus` (domain, CROSS-LAYER), new strings in 5 locales and VM branching, for a path that is covered by the existing snackbar + auto-restart. Skip.

## P11 — INTENTIONAL — combine re-runs onSessionPlaying/silence.start/holdCpuAwake on every state emission
All three are idempotent by design and rely on it: `AudioFocusRequester.request` KDoc ("Ponowne wywołanie przy trzymanym fokusie nic nie zmienia"), SilentPlaybackTest (`silence.start()` twice -> one play), `holdCpuAwake` guards on `wakeLock == null`. The repeated call is also how a refused/lost focus gets re-evaluated. `Triple` destructuring is cosmetic: leave.

## P12 — CONFIRMED — `engine.autoPausedInBackground` collector is redundant with the combine collector; delete the SharedFlow. CROSS-LAYER, HIGH-DEVICE
Both emit sites in SessionEngine (ShowRatings in background; `onTtsFailure` in background) also change `state`/`isPaused`: ShowRatings -> status REVEALED, `pause()` -> `isPaused=true`. `combine(state, inBackground, isPaused)` then gives `sessionAudioIdle==true`, which already does `player.update(playing=false, paused=true)` and `holdCpuAwake(false)` (= `releaseWakeLock`). The extra collector produces the identical `SessionPlayback` (deduped by `SessionPlayer.update` equality) and the same wake-lock release.
Fix: delete the first `launch { engine.autoPausedInBackground.collect {...} }` in `StudySessionService.followSession`; delete `_autoPausedInBackground`, `autoPausedInBackground` and the two `tryEmit` calls in `domain/session/SessionEngine.kt` (CROSS-LAYER: domain fixer); update the two tests that collect it (`SessionEngineBackgroundTest` ~line 222, `SessionEngineSemanticsTest` ~line 413) to assert `eng.state.value.status == REVEALED` / `eng.isPaused.value` instead; drop the now-stale KDoc.
Do P13 in the same edit. Size S, risk HIGH-DEVICE (background session auto-pause + notification text only verifiable on phone; reasoning above says behaviour-neutral). Verify on phone: bg session reaching a manual-rating step, and bg TTS failure (e.g. voice data removed) -> notification shows "paused", CPU lock released.

## P13 — PARTIAL — redundant `playing` param is real; "paused" really means idle; notification text for non-"needs app" pauses is slightly off
Confirmed: both call sites pass `playing == !paused`. Fix (S): `playbackOf(state, deckName, idle: Boolean)` with `playing = !idle`; rename local `paused` -> `idle`. Optional text tweak (visible, default design): show `study_background_paused` ("Paused - open the app to continue") only when `state.status == REVEALED || ERROR` (the user really has to go to the app); for manual pause / focus loss / foreground tap gate show the normal `n/m` progress, since the play button works and in the gate case the app is already in front. Extract to a pure `internal fun sessionPlayback(state, deckName, idle, pausedText)` so it is unit-testable (SessionPlayerTest-style). Risk LOW for the rename; the text tweak is device-visible (lock screen/notification), mark HIGH-DEVICE for that part only. Do with P12.

## P14 — CONFIRMED — wake lock handling: extract `TimedWakeLock`
Finding says 3 nullable fields; it is 2 (`wakeLock`, `wakeLockRenewal`) plus the `lock` closure, but the intent holds: acquire/renew/release logic is spread across `holdCpuAwake` + `releaseWakeLock` + onDestroy. Extract `playback/TimedWakeLock.kt`: `class TimedWakeLock(context, scope, tag, timeoutMs)` with `fun hold()` / `fun release()` (idempotent), renewal at timeout/2 as today; service keeps one val and calls `wakeLock.hold(inBackground && !idle)` style. Behaviour must be identical (non-reference-counted, 10-minute timeout, renew every 5 min).
Tests: new `TimedWakeLockTest` with Robolectric `ShadowPowerManager.getLatestWakeLock()`: hold -> held; second hold no new lock; idleFor(timeout/2) re-acquires; release -> not held and renewal cancelled. Size S-M, risk HIGH-DEVICE (wake lock / Doze behaviour only truly verifiable on phone; unit test covers the logic). Do after P12/P13.

## P15 — CONFIRMED — SDK_INT>=Q branch is unnecessary
`ServiceCompat.startForeground(service, id, notification, type)` ignores `type` below API 29 and uses the typed call above (core 1.19.1). `ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK` is a compile-time constant (inlined, safe on API 26). Replace with the single call passing the constant; remove `Build` import (and the oddly wrapped `if` that ktlint produced). Size S, risk LOW (library-guaranteed; sanity-check service start on the phone as part of normal background smoke test).

## P16 — CONFIRMED — shared notification id/channel undocumented
Add a short Polish comment at `startInForeground`/`setMediaNotificationProvider`: the bootstrap notification (needed within 5 s of `startForegroundService`) uses the same `NOTIFICATION_ID` + `CHANNEL_ID` as the Media3 provider, so Media3's media notification replaces it in place instead of posting a second one. Comment-only, S, risk none.

## P17 — NOT_WORTH — injects concrete AudioFocusManager
It is a `@Singleton` with `@Inject` constructor; injecting the interface would require a `@Binds` module just to satisfy an Android Service that is not unit-tested (tests use `FakeFocus` against `SessionAudioFocus`, which already takes the interface). No benefit.

## P18 — NOT_WORTH — controls.stop() + stopSelf() "redundant"
`controls.stop()` -> `holder.release()` -> `session=null` -> the collector's `stopSelf()` runs asynchronously (non-immediate Main dispatcher), after `onTaskRemoved` may already have returned and the process been reaped. The explicit `stopSelf()` is deterministic. Keep.

## P19 — CONFIRMED — startSession does everything / repeated casts
Verified `current as? StudyUiState.Active` is evaluated 3x in the `_uiState.update` block and `(current as StudyUiState.Active)` at line 160 is only safe because `endedEarly` derives from the same cast. Fix (S): `val previous = current as? StudyUiState.Active` once; `endedEarly = previous?.endedEarly == true && FINISHED`; `completedCardCount = if (endedEarly) previous!!.completedCardCount ...` -> `previous?.takeIf { endedEarly }?.completedCardCount ?: session.cards.size`. Extract the update lambda body to a private `activeState(deck, engine, runsInBackground, session, paused, previous): StudyUiState.Active` and the FINISHED refresh to a private suspend `refreshResultCards(session)`. Pure refactor under existing StudyRestartTest coverage (7 tests incl. endedEarly/completedCardCount). Size S-M, risk LOW. Combine with P21/P1/P25/P3.

## P20 — INTENTIONAL — reviewWrites.state combined only as a re-emission trigger
Deliberate and tested: `failed reviews block restart and retry refreshes the finished summary` (StudyRestartTest) relies on queue-state changes re-running the FINISHED block (cancel the old awaitIdle via collectLatest, re-read fresh cards after retry). No change to behaviour; one-line Polish comment above the `combine` stating why `reviewWrites.state` is included would help (S, comment-only).

## P21 — CONFIRMED — `?: return _uiState.update { }` idiom
Replace with explicit statements:
```
val deck = deckRepository.getDeck(deckId)
if (deck == null) { _uiState.value = StudyUiState.DeckMissing; return }
...
if (cards.isEmpty()) { _uiState.value = StudyUiState.NoDueCards(deck.name); return }
```
(`update {}` with a constant needs no CAS loop.) Size S, risk LOW. With P19.

## P22 — NOT_WORTH — getDueCards/filter on Main, getDeck loads whole deck
`getDueCards` is a filter+sort over the deck (single-digit ms even for thousands of cards); `getDeck` is a suspend Room read off-main. A narrower "cards by ids" DAO query (data layer, CROSS-LAYER) buys nothing measurable at this scale. Skip.

## P23 — NOT_WORTH — observer added inside async startSession, removed in onCleared
Not a bug: if the VM is cleared before startSession finishes, the coroutine is cancelled at its next suspension (prompt cancellation) before `addObserver`, and `removeObserver` of a non-added observer is a no-op. Adding it earlier would be equivalent because the callbacks early-return while `engine == null`. (Note: `addObserver` replays only CREATED for an already-stopped process; relevant to P3, handled there.)

## P24 — NOT_WORTH — runsInBackground stored twice
VM field is read in `restartWith` where `_uiState` may not hold Active yet / hold a stale copy; deriving from uiState is more fragile than the duplicate boolean. Skip.

## P25 — CONFIRMED — restartSession/restartFailed double tap starts two launches
Consequences are mild (two `engine.start` + two `register` back-to-back, brief TTS restart stutter, or two ReviewSaveFailed snackbars when the write queue is failed), but the guard is 3 lines. Fix: `private var restartJob: Job? = null`; in `restartWith`: `if (restartJob?.isActive == true) return; restartJob = viewModelScope.launch { ... }`.
Tests: StudyRestartTest: call `vm.restartSession()` twice synchronously, then `idle()`; assert a single additional service start (`startedServices().size == 1`) and one restart. Size S, risk LOW.

## P26 — NOT_WORTH — two ProcessLifecycleOwner observers
They serve independent consumers (sync in Application, session in VM). A shared `AppForeground` StateFlow would be a new singleton in data/di (CROSS-LAYER) plus rewiring SyncLifecycle (untested, Firebase-coupled, known deliberate debt) for no behavioural gain.

## P27 — CONFIRMED — `this@SharedTransitionLayout` passed 11 times
Provide it once: wrap the NavHost in `CompositionLocalProvider(LocalSharedTransitionScope provides this@SharedTransitionLayout) { NavHost(...) }` and drop the `sharedScope` parameter from `screen(...)`; `screen` keeps providing only `LocalNavAnimatedVisibilityScope provides this`. Locals flow into destination composables through NavHost.
Risk: if the provision is wrong, `sharedDeckCard`/`sharedDeckTitle` silently return `this` (no animation, no crash) — so verify the deck-card fly-in/out transition Dashboard->Study/Browse/DeckSettings on the phone. No unit test possible. Size S, risk LOW-MED (visual regression only).

## P28 — NOT_WORTH — inline nav lambdas / Dashboard 8 callbacks
Wiring navigation callbacks in the NavHost is the standard Compose-navigation place; a navigator object or per-screen `NavGraphBuilder` extensions is a larger redesign. The "ModeDetailRoute from 3 screens" duplication is a one-liner each. Skip.

## P29 — CONFIRMED — 0.92f / 0.9f magic numbers
Name them (private consts in NavGraph.kt): `BEHIND_SCALE = 0.92f` (screen going behind / returning: `shrinkAway`, `growBack`), `LEAVING_SCALE = 0.9f` (`slideAway`, `shrinkInPlace`), with a one-line comment on the intended difference (screen going behind vs leaving). No value change. Size S, risk none (visual identical).

## P30 — PARTIAL — vague `up()` name and duplicated background
Rename `NavHostController.up()` -> `popIfResumed()` (its KDoc already says what it does) at the 9 call sites (S). The NavHost `.background(MaterialTheme.colorScheme.background)` duplicates `Surface(color = background)` in MainActivity directly above it, so it is invisible redundancy: remove the NavHost one (keep Surface, which also hosts MigrationDialog) and drop the unused `background`/`MaterialTheme` imports if no longer needed. Verify on phone that transitions (scale-out showing what is behind) look unchanged. Size S, risk LOW.

## P31 — CONFIRMED — `deckBoundsTransform()` is @Composable with a keyless remember around a constant
`RayCollMotion.spatial<Rect>()` is a plain function (NavGraph already uses it in top-level vals). Replace with a top-level `private val deckBoundsTransform = BoundsTransform { _, _ -> RayCollMotion.spatial<Rect>() }` and delete the `@Composable` function and the `remember` import; update the two call sites. Size S, risk LOW (verify the shared transition on the phone together with P27).

## P32 — PARTIAL — scope lookup duplication; string keys; opt-in check
Merge the identical two-line `LocalSharedTransitionScope.current ?: return this` / `LocalNavAnimatedVisibilityScope.current ?: return this` lookups into one private helper (e.g. `@Composable private fun Modifier.withSharedScopes(block: SharedTransitionScope.(AnimatedVisibilityScope) -> Modifier): Modifier`) — S, optional. String keys ("deck-card-$id", "deck-title-$id") appear once each: NOT_WORTH. "Is `ExperimentalSharedTransitionApi` still needed": cannot be answered from source; fixer should try removing the `@OptIn` on a scratch compile with the current BOM (2026.09.00) and keep it if the build errors. Size S, risk LOW.

## P33 — NOT_WORTH — `?: return@setContent`, splash has no timeout
The early return is a valid way to skip the first composition and the splash condition already holds until `preferences != null`. A splash timeout would only matter if the prefs flow never emits; P50 (catch -> default prefs) removes that scenario. Skip.

## P34 — INTENTIONAL — later `request()` callbacks ignored
Documented in `AudioFocusRequester.request` KDoc ("Ponowne wywołanie przy trzymanym fokusie nic nie zmienia"); the only caller passes the same bound `onFocusChange` of one `SessionAudioFocus`, and `release()` abandons on service destroy so a new instance registers afresh. Platform-type `getSystemService` is idiomatic; no change.

## P35 — INTENTIONAL — duck/delayed-focus comment
The comment is accurate: without `setWillPauseWhenDucked`, the system ducks automatically for apps targeting 8.0+, and without `setAcceptsDelayedFocusGain` `requestAudioFocus` never returns DELAYED, so `granted` is correctly binary. No change.

## P36 — INTENTIONAL — onSessionPlaying re-requests on every emission
Same as P11: `request()` is a no-op when focus is held (tested with `FakeFocus.held`), and the repeat is what lets a refused focus pause the session. No change.

## P37 — EDGE — failed SoundPool load stays "pending"
Raw resources are packaged with the APK, a load failure basically cannot occur, and the cost is one Int in a set. Skip.

## P38 — CONFIRMED — vibration durations are magic numbers; `else` hides GOOD
Add private consts (`TICK_MS = 20L`, `GOOD_MS` etc. or one `private val DURATION_BY_RATING`) and make GOOD explicit: `Ratings.GOOD -> 20L` with `else -> 20L` only as a defensive default; reuse the same consts in `playSuccess` (20) / `playError` (50). Do NOT change values (feel verified on phone). Test (optional): extend HapticAndSoundPlayerTest with `ShadowVibrator` asserting last duration per rating. Size S, risk LOW.

## P39 — NOT_WORTH — use VibrationEffect.createPredefined on API 29+
Changes the haptic feel the owner already verified, differs per device, and needs a phone to judge; no objective gain. HIGH-DEVICE for zero demonstrated benefit. Skip.

## P40 — CONFIRMED — SilentPlayback.start elvis/runCatching/also chain; Log.w drops throwable
Rewrite for readability with a private helper:
```
private fun ensureTrack(): SilentTrack? = track ?: runCatching(createTrack)
    .onFailure { Log.w(TAG, "Cannot create silent track", it) }
    .getOrNull()
    ?.also { track = it }
```
and `start()`: `val current = ensureTrack() ?: return`; pass the throwable (`Log.w(TAG, "...", it)`) in both failure logs so stack traces survive. Behaviour identical; SilentPlaybackTest (3 tests) covers it. Size S, risk LOW.

## P41 — EDGE — AudioTrack leaked if write/setLoopPoints throws in `also{}`
`AudioTrack.write(ByteArray,int,int)` and `setLoopPoints` return error codes rather than throwing for our valid arguments; a throw would also be caught by `runCatching(createTrack)` and the process keeps going. Practically never. (`@MainThread` is not required by AudioTrack.) Skip. If anyone touches it: wrap in try/catch that releases the track and rethrows.

## P42 — NOT_WORTH — MediaMetadata set twice; getState rebuilds each call
Setting metadata on both the MediaItem and MediaItemData is what notification/controller consumers read (controller metadata vs item metadata); removing one cannot be verified without the lock screen/notification on a phone (HIGH-DEVICE) for a 2-line saving. `getState()` allocation per invalidate is negligible. Skip.

## P43 — CONFIRMED — `withCardNavigation(controls)` takes controls the constructor already holds
Make it a member: `fun withCardNavigation(): Player = object : ForwardingPlayer(this) { override fun seekToNext() = controls.next() ... }` (inside the class the `private val controls` is reachable; keep `@UnstableApi`). Update call sites: `StudySessionService.onCreate` (`player.withCardNavigation()`), `SessionPlayerTest` (3 uses), `SessionMediaControllerTest` (1 use). Behaviour identical (tests assert each nav command fires exactly once). Size S, risk LOW.

## P44 — NOT_WORTH — TtsPlayer.speak mixes concerns
28 lines, linear and well-commented (guard -> engine -> language -> chunk loop with per-chunk timeout -> reset on error); TtsRecoveryTest/TtsPlayerTest pin its behaviour. Splitting adds indirection on a path verified on the phone.

## P45 — NOT_WORTH — TtsResult.Error(reason: String); nanoTime utterance id
`reason` is only logged (`SessionDepsAdapter`), a sealed hierarchy would be over-design; `System.nanoTime()` ids cannot collide for sequential utterances and the id filter exists precisely to drop stale callbacks. Skip.

## P46 — NOT_WORTH — tts.language result ignored; no setAudioAttributes
`isLanguageAvailable` is checked right before `language =`, so the setter result adds nothing (EDGE at best). Explicit `AudioAttributes(USAGE_MEDIA, CONTENT_TYPE_SPEECH)` would be consistent with AudioFocusManager/SilentPlayback but changes audio routing/ducking behaviour that is verified on the phone: HIGH-DEVICE with no demonstrated bug. Skip unless a routing issue is reported.

## P47 — CONFIRMED — SessionDepsAdapter mapping inside lambda; magic log tag
Add `private const val TAG = "SessionDeps"` and extract `private fun TtsResult.toSpeakResult(): SpeakResult` (with the two `Log.w` calls) so `speak` becomes `audioTurn.withTurn { ttsPlayer.speak(text, lang, rate).toSpeakResult() }` (after P48). Size S, risk LOW (no test exists; mapping is 3 trivial branches — add a tiny test if desired).

## P48 — CONFIRMED — `AudioTurn.take` name
`take` collides mentally with `Iterable.take(n)`. Rename to `withTurn` (reads like `withLock`). Call sites: `SessionDepsAdapter` (2), `AudioTurnTest`, any other test usage (grep `\.take {`). Do together with P47. Size S, risk LOW.

## P49 — PARTIAL — StudySessionHolder: undocumented equality dedup is the real gap
The two `resume` paths are already documented in the KDoc ("tryb bez serwisu wznawia sam silnik") and `controls` is only touched on the main thread (service lifecycle + VM): INTENTIONAL. The real, worth-fixing gap: `ActiveSession` is a data class with the engine compared by identity, so `register(sameEngine, sameDeckName)` is a no-op for `MutableStateFlow` (no re-emission). The restart flow relies on that: a still-alive service keeps following the same run, while a service that stopped at FINISHED is started anew by `startForegroundService` and picks the value up in onCreate. Add a Polish comment on `register` saying so (and that all access is main-thread). Comment-only, S, risk none.

## P50 — CONFIRMED — preferences flow throwing crashes the app on launch; fix belongs in the repository. CROSS-LAYER
`MainViewModel.preferences` = `prefsRepository.preferences.stateIn(viewModelScope, Eagerly, null)`; DataStore `data` throws IOException (read error) / CorruptionException (no handler configured: `preferencesDataStore(name = "raycoll_settings")` has no `corruptionHandler`), so the exception is uncaught in viewModelScope -> crash on every launch (user stuck until clearing data; real data on the phone). Fixing only MainViewModel would leave `StudyViewModel` (`preferences.first()`), AppSettings etc. exposed, so fix at the source:
1. `UserPreferencesRepository.kt` (data layer, CROSS-LAYER): `preferencesDataStore(name = "raycoll_settings", corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() })`, and `preferences = context.dataStore.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map { ... }` (defaults apply through the existing `?:` fallbacks).
2. MainViewModel needs no change after that (optionally none).
Tests (data fixer): UserPreferencesRepositoryTest: write garbage bytes to the `raycoll_settings` datastore file before first read -> `preferences.first()` returns defaults instead of throwing. Size S, risk LOW (happy path unchanged).

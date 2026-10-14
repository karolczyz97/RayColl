# Red-team instructions (shared)

You are red-teaming code-review findings for RayColl, an Android Kotlin/Compose (Material 3) app: flashcards + FSRS spaced repetition, Room, Firestore sync, TTS/STT study sessions in a Media3 foreground service.
Repo: C:\Users\Karol\.gemini\antigravity\scratch\RayColl (git, main). READ-ONLY: never edit repo files.
Review dir: C:\Users\Karol\AppData\Local\Temp\claude\C--Users-Karol--gemini-antigravity-scratch-RayColl\3dcb1293-b81c-4b3b-88f3-9cfde2526c53\scratchpad\review\

The reviewers may be wrong, may misread code, or may flag deliberate design. For EVERY finding ID in your findings file: open the actual code; read surrounding comments (Polish); check related tests in app/src/test (tests often encode intended behaviour); check git history (`git log -S<text> --oneline`, `git log -L`, commit messages) for intent. Verdicts:
- CONFIRMED — real and worth fixing.
- PARTIAL — partly right; say which part.
- WRONG — factually incorrect (explain).
- INTENTIONAL — deliberate (cite comment/test/commit); say if it should still change.
- EDGE — real but practically never happens (~0.01%); skip.
- NOT_WORTH — pure churn adding risk without benefit, or a speculative big redesign. Use sparingly: the owner wants even small improvements fixed; cheap readability wins = CONFIRMED.

Project facts:
- The Android app is the ONLY client (no web version; never justify anything by "web parity").
- Format constraints only for data already in Firestore and in users' JSON backups: serialized names/shapes must stay readable.
- App is installed on the owner's phone (Pixel 10 Pro, sometimes de-DE locale, sometimes landscape) with real data. Room DB version 1 is shipped → schema changes need a real migration; weigh cost/benefit.
- Firestore rules: owner-only on users/{uid}/decks/{deckId}/cards, studyModes, activity.
- minSdk 26, targetSdk 36, compileSdk 37. ~435 unit tests (Robolectric), ktlint via :app:ktlintCheck.
- Polish code comments are intentional and stay.
- Known deliberate debt: import rollback on cloud failure skipped (local-first, scheduler retries); SyncLifecycle/BootstrapLoader/AppSettingsViewModel untested (Firebase-coupled).
- Known pitfalls: runCurrentCard Job LAZY inserted into map BEFORE start() (immediate dispatcher); SyncScheduler.flush() must not cancel its own timer job; coroutines-test backgroundScope isn't pumped by advanceUntilIdle; normalizeHeaderToken: NFD doesn't decompose "ł" (manual replace needed).
- Verified on phone: background study with headset media buttons works thanks to SilentPlayback (silent AudioTrack so the system picks RayColl's media session); session survives rotation via ProcessLifecycleOwner; ratings+navigation pinned at bottom of study screen; landscape compact card (LocalCompactCard, font x0.72) with side arrows; ScreenScaffold max width 640dp (has a test); "Restore defaults" disabled for unmodified modes; snackbar after "Sync now"; deck delete requires typing a token.
- Strings in 5 locales (en, pl, de, es, it); I18nConsistencyTest checks key parity.

For each CONFIRMED/PARTIAL: concrete fix spec — what to change, which files, flag CROSS-LAYER if it touches another fixer's area (areas: domain+core | data+di+build | playback+StudyViewModel+MainViewModel+navigation+MainActivity | ui-study/dashboard/browse/components/theme/archive/stats | ui-settings/decksettings/imports/modedetail/studymodes+res), size S/M/L, risk, tests to add/update. For visible UX changes propose ONE concrete default design consistent with Material 3 and the app's current look.

Output: write the full result to `verdict-<area>.md` in the review dir, one section per ID: `ID — VERDICT — one-line reason`, then the fix spec. Return to the orchestrator ONLY: counts per verdict; IDs that are WRONG/INTENTIONAL/EDGE/NOT_WORTH with one-line reasons; the CROSS-LAYER IDs.

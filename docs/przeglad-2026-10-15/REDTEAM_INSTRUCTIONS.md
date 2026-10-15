# Red-team instructions (15.10.2026, shared)

You are red-teaming code-review findings for RayColl, an Android Kotlin/Compose (Material 3) app: flashcards + FSRS spaced repetition, Room, Firestore sync, TTS/STT study sessions in a Media3 foreground service.
Repo: C:\Users\Karol\.gemini\antigravity\scratch\RayColl, branch `przeglad-modernizacja`. READ-ONLY for code: never edit repo files except your own verdict file.
Findings and verdicts live in `docs/przeglad-2026-10-15/` (findings-*.md = today's second scan; IDs are N1..Nn per file; prefix them with the file's area code in your output: DOM, DAT, AUD, NAU, EKR, TB). Yesterday's verdicts are in `docs/przeglad-2026-10-14/verdict-*.md` (context only).

The reviewers may be wrong, may misread code, or may flag deliberate design. For EVERY finding ID in your findings file: open the actual code; read surrounding comments (Polish); check related tests in app/src/test (tests often encode intended behaviour); check git history (`git log -S<text> --oneline`, commit messages, yesterday's verdicts) for intent. If a finding claims library/system behaviour (Media3, Android audio focus, M3 snackbar defaults, aapt2 escaping, Navigation lifecycle), verify it as far as possible (library sources/bytecode in the Gradle cache `C:/Users/Karol/.gradle/caches`, documented defaults) and say how sure you are.

Verdicts:
- CONFIRMED — real and worth fixing.
- PARTIAL — partly right; say which part.
- WRONG — factually incorrect (explain).
- INTENTIONAL — deliberate (cite comment/test/commit/decision); say if it should still change.
- EDGE — real but practically never happens (~0.01%); skip.
- NOT_WORTH — pure churn adding risk without benefit, or a speculative big redesign. Use sparingly: the owner explicitly wants even the smallest improvements fixed; cheap readability wins = CONFIRMED.
- DECISION — real, but fixing it changes intended app behaviour/look and there is a genuine product choice (the owner decides UX/behaviour). Give the recommended default and alternatives. Pure bug fixes that restore obviously intended behaviour are NOT DECISION.

Project facts:
- The Android app is the ONLY client (no web version; never justify anything by "web parity").
- Format constraints only for data already in Firestore and in users' JSON backups: serialized names/shapes must stay readable.
- App is installed on the owner's phone (Pixel 10 Pro, sometimes de-DE locale, sometimes landscape) with real data. Room DB is shipped → schema changes need a real migration; weigh cost/benefit.
- minSdk 26, targetSdk/compileSdk 37. 533 unit tests (Robolectric), ktlint via :app:ktlintCheck.
- Polish code comments are intentional and stay.
- Owner decisions already made (docs/przeglad-2026-10-14/USTALENIA_Z_ROZMOW.md): landscape study = card scrolls, controls pinned, arrows at sides; "Restore defaults" disabled for unmodified modes; manual sync shows result; search field HAS a clear button (approved earlier); Polish term for a deck is "talia"; owner's experimental modes (test/sada/Klik) are not auto-repaired.
- The speaker/mic indicator design (StudyCard "U0") is pending an owner choice between variants — do not judge its design, only bugs in it.
- Known deliberate debt: import rollback on cloud failure skipped (local-first, scheduler retries).
- Known pitfalls: runCurrentCard Job LAZY inserted into map BEFORE start() (immediate dispatcher); SyncScheduler.flush() must not cancel its own timer job; coroutines-test backgroundScope isn't pumped by advanceUntilIdle; normalizeHeaderToken: NFD doesn't decompose "ł".
- Strings in 5 locales (en, pl, de, es, it); I18nConsistencyTest checks key parity.

For each CONFIRMED/PARTIAL: concrete fix spec — what to change, which files, flag CROSS-LAYER if it touches another area (areas: DOM = domain+core | DAT = data+di | AUD = playback+MainActivity+Application+manifest+res/xml | NAU = ui/study, dashboard, components, theme, navigation, main | EKR = ui/browse, archive, stats, decksettings, imports, modedetail, appsettings, studymodes | TB = tests infra, build files, README, repo root, strings translations), size S/M/L, risk, tests to add/update. For visible UX changes that are not DECISION, propose ONE concrete default design consistent with Material 3 and the app's current look.

Output: write the full result to `docs/przeglad-2026-10-15/verdict-<area>.md` (area = file suffix of your findings file), one section per ID: `### <PREFIX>-N<k> — VERDICT — one-line reason`, then the fix spec. Return to the orchestrator ONLY: counts per verdict; one line per ID that is WRONG/INTENTIONAL/EDGE/NOT_WORTH/DECISION with the reason; the CROSS-LAYER IDs with the other area.

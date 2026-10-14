# Verdict: domain/ + core/ (paths relative to app/src/main/java/com/raycoll/app/)

Counts: CONFIRMED 18, PARTIAL 26, INTENTIONAL 7, EDGE 2, NOT_WORTH 10, WRONG 0 (whole-finding). Sub-claims found WRONG are noted inline (D43 "dead ?: null", D55 "Ł replace dead").

## Serialization/wire-format summary for cross-cutting type refactors
All three Json instances that touch persisted data (data/local/mappers/EntityMappers.kt, data/remote/firestore/FirestoreSchema.kt, data/repository/BackupRepository.kt) use `ignoreUnknownKeys = true, encodeDefaults = true`. Room has NO schema change in any recommendation below.
- CardState enum (D5): NOT recommended. If done anyway: byte-compatible ONLY with an Int-based custom KSerializer (encode `code`, decode unknown -> NEW) plus mapper conversion in EntityMappers (Room column stays `state INTEGER`) and FirestoreSchema (`"state"` stays a number). Recommended alternative (named Int constants) changes no serialized byte.
- Rating enum (D40): NOT recommended. Ratings are persisted as plain ints in `AutoRateFixedStep.rating`, `CompoundBranch.fixedRating`, `AutoPassNext.rating` (steps JSON in Room/Firestore/backup). An enum would need the same Int-serializer + coercion; not byte-compatible without it.
- AtomicStep sealed split (D4): NOT recommended. Leaf `@SerialName`s ("show_page", ...) and the default "type" discriminator must stay identical. Nested sealed interfaces in kotlinx.serialization may change the polymorphic descriptor shape; I could not verify this by compiling (read-only), so byte-compat would have to be proven by AtomicStepModelTest + BackupValidatorTest (it reads `ModeStep.serializer().descriptor.getElementDescriptor(1)` element names) + FirestoreSchemaTest before trusting it.
- Sealed answer types (D14/D15): LastAnswerResult, ListenResult, SessionState are never serialized (no @Serializable, not in Room/Firestore/backup/SavedState) -> no format impact. Only compile-time cross-layer churn.
- `dummy: Int` -> data object (D35): NOT byte-identical on WRITE (key `"dummy":0` disappears from `show_all_grade` / `fail_next` params), but READ-compatible both ways (existing stored `{"kind":"show_all_grade","dummy":0}` still decodes because all three Json have ignoreUnknownKeys=true; BackupValidator accepts any content for those two kinds). Room study_modes.steps JSON, Firestore steps and old backups all stay readable; no migration.
- isBuiltIn redundancy (D11): `isBuiltIn` is a required Firestore field (`FirestoreSchema.modeFromMap` throws if missing) and a Room column -> must stay in the model.

---

D1 — NOT_WORTH — God-class split of a 412-line engine that has several documented concurrency pitfalls; zero user value, high regression risk.
No change. (Individual cheap cleanups from D10/D16/D18/D20/D23-D26 are specified under their own IDs and are enough.)

D2 — NOT_WORTH — Resume-after-gate state is spread over 4 places but works, is pinned by `tap-gate resume preserves the previous-card window` and reducer tests (`CompleteInteractionGate sets pending step`); moving it into runSteps(card, startIndex) just relocates the same state into the engine.
No change.

D3 — NOT_WORTH — The "invalid" combination (kind=NONE + continueStepIndex) is never constructed; `TapRevealMode.NONE` is legitimate (WaitForTapStep). Nullable-gate redesign touches reducer, engine, StudySessionService.kt:274 (playback, other area) and SessionAudioIdleTest for no behaviour gain.
No change.

D4 — NOT_WORTH — The 3 "dead" branches (`is CompoundStep -> Unit` in StepExecutor, `withCondition` CompoundStep->this, `pageIndex` else->null) are single commented lines; the refactor would touch ModeStep/expander/executor/validator/normalizer/ui-modedetail and risks the persisted polymorphic shape (see wire summary).
No change. Wire: untouched.

D5 — PARTIAL — Magic ints 0..3 in 5 production spots are a real readability smell, but an enum drags ~25 test sites + mappers + custom serializer.
Fix (S, domain only, no wire change): in model/SrsState.kt add
`object SrsStates { const val NEW = 0; const val LEARNING = 1; const val REVIEW = 2; const val RELEARNING = 3; val VALID = NEW..RELEARNING }`
and use it in selectors/DueCards.kt:12 (`state == SrsStates.NEW`), srs/SrsEngine.kt:21,23-34 (newState = LEARNING/REVIEW/RELEARNING), srs/SrsCardCategory.kt (when branches), normalize/DataNormalizer.kt:38,58 (VALID_SRS_STATES -> SrsStates.VALID, `state = SrsStates.NEW`). Fix KDoc on SrsState to point at it. Tests: none required (existing SrsEngineTest/SelectorsTest cover); optional: one test asserting getCardCategory for each constant. Risk: nil.

D6 — CONFIRMED — mergeCards/mergeGroups/mergeStudyModes repeat the same union-by-id loop with `!!`.
Fix (S-M, domain only): add `private inline fun <T> mergeById(local: List<T>, cloud: List<T>, id: (T) -> String, both: (T, T) -> T, single: (T) -> T): List<T>` that iterates `(local.map(id) + cloud.map(id)).toSet()` (ORDER MUST BE PRESERVED: local ids first then cloud-only; card order in a deck is rowid-based) and per id does `if (l != null && c != null) both(l, c) else single((l ?: c)!!)` -> no `!!` outside one line (or use `requireNotNull`). Keep the per-type "both" bodies exactly as they are (tombstone/winner rules differ per type; do not unify them). Tests: existing MergeEngineTest + TwoDeviceSyncTest must pass unchanged; add one assertion on result ORDER for mergeCards/mergeGroups (local first, then cloud-only). Risk: low-medium (sync-critical) — hence tests first.

D7 — NOT_WORTH — The Map-tree validator is also the strict guard for Firestore steps (`FirestoreSchema.modeFromMap` calls `assertStudyModeStep`) and for whole-file backup rejection; it enforces things decode does not (doc-id rules, duplicate ids, threshold<=100, non-negative numbers). Replacing with strict decode + domain checks is a large redesign that changes error semantics; covered by 15 BackupValidatorTest cases.
No change.

D8 — PARTIAL — (a) `kind ?: type` in assertCompoundPause is dead/misleading but harmless: the decoder only knows `kind`, and BackupRepository.importFrom already catches SerializationException -> INVALID_BACKUP (same user outcome, no crash). (b) "requires fields that have defaults" is deliberate strictness: FirestoreSchema documents STRICT loading (corrupt doc is skipped), backup policy is "reject whole file", and every file the app ever exports has all fields (encodeDefaults=true). Real nit: the comment at model/ModeStep.kt:108-109 ("Wartości domyślne działają ... przy dekodowaniu backupu bez danego pola") is wrong for backups because the validator rejects first.
Fix (S, domain): normalize/BackupValidator.kt:74 -> `val kind = value["kind"]`; correct the ModeStep.kt comment to "defaults serve the editor and Kotlin construction; backups/Firestore are validated strictly". Do NOT relax required fields. Tests: add `compound pause with "type" instead of "kind" is rejected` to BackupValidatorTest. Wire: none.

D9 — PARTIAL — Asymmetry is real (speakPage wraps `deps.speak`, listenAndCheck does not) but SttRecognizer.listen already converts every failure into ListenResult(ERROR) (permission, unavailable, createSpeechRecognizer, startListening, timeout), so no current crash path; hardening is 6 lines.
Fix (S, domain): in StepExecutor.listenAndCheck wrap the call inside the runSkippable block:
`listenResult = try { deps.listen(...) } catch (e: CancellationException) { throw e } catch (_: Exception) { ListenResult("", ListenStatus.ERROR) }`. (Rethrow CancellationException — tap-to-skip cancels this block.) Tests: add to SessionEngineSemanticsTest using FakeSessionDeps with a throwing listen -> status ERROR answer + `SessionError.STT` + session continues to the manual-rating fallback. Risk: nil.

D10 — EDGE — `rate()`/`skipToNextCard()` first call interruptCurrentCard() which sets holding=false, and the engine scope is viewModelScope (Main.immediate), so `holding.first { !it }` never suspends and the launched advance runs synchronously inside the call; no window for stop() or a second skip to interleave in production (only reachable with a non-immediate test dispatcher).
Optional one-line guard if touching the file anyway: in advanceToNextCard after `holding.first { !it }` add `if (_state.value.status == SessionStatus.FINISHED) return`. Not required.

D11 — NOT_WORTH — `isBuiltIn` is persisted (Room column, required Firestore field, backup JSON) and read by FirestoreSchema validation; dropping/deriving it would change the stored shape. normalizeStudyMode already keeps the two consistent. No change. Wire: must stay.

D12 — PARTIAL — The "opposite fallbacks" are by design (different questions: "does the user own this data" vs "can it be reset to factory"), and unknown-source built-ins are normalised to non-built-in anyway; the only real duplication is the `name != seed.name || steps != seed.steps` comparison.
Fix (S, domain): merge/MigrationRules.kt: `fun isUserAuthoredMode(mode: StudyMode) = !mode.isBuiltIn || seedModeFor(mode.builtInSourceId) == null || mode.isModifiedBuiltIn()` (import from domain.operations). Tests: MigrationRulesTest already pins all branches; no new tests.

D13 — PARTIAL — Real UX papercut with the most common path: every fresh install seeds the guest namespace (random ids, `updatedAt = 0`), so the first sign-in on a new account always shows "Local data found: transfer or start fresh" even if the user never touched the two phrase decks. Code treats untouched seed MODES as "no data" (test `nietknięte tryby wbudowane (seed) → brak danych`) but not decks; LocalStore.seedIfEmpty already uses `updatedAt == 0L` as the "untouched seed" marker for modes.
Fix (S, domain only; caller BootstrapLoader.kt:67 unchanged): in merge/MigrationRules.kt define `private fun FlashcardGroup.isUntouchedSeed() = updatedAt == 0L && cards.isNotEmpty() && cards.all { it.contentUpdatedAt == 0L && it.srsUpdatedAt == 0L }` and use `groups.any { it.deletedAt == null && !it.isUntouchedSeed() }`. Any edit (rename, page count, archive, card add/edit via newCard(now)) or any review (srsUpdatedAt) bumps a timestamp, so real user data still triggers the dialog. Visible UX change: dialog no longer appears when guest data is just the two seed decks (default design: skip dialog, account gets its own deterministic seed via seedIfEmpty). Tests: update MigrationRulesTest (the live-deck fixture at line 28 must get `updatedAt = 1L`); add cases: seed-shaped guest (updatedAt 0, cards ts 0) -> false; same with one reviewed card -> true; same with `updatedAt = 5` -> true. Risk: low; worst case (heuristic misses) is today's dialog.

D14 — PARTIAL — Four near-identical LastAnswerResult constructions in listenAndCheck are noisy; a sealed Answer type is not worth it (AnswerPanel.kt, StudyCard.kt, StudyCardTest consume the flat data class; not serialized).
Fix (S, domain): local helper in listenAndCheck: `fun answer(status: AnswerStatus, text: String = "", percent: Int? = null, suggested: Int? = null) = LastAnswerResult(status, text, percent, step.successThreshold, step.pageIndex, suggested)` and use it in the 4 blocks (blank expected, skipped, error, scored). Tests: existing semantics tests cover all 4. No wire impact.

D15 — NOT_WORTH — ListenResult(text, status): sealed redesign forces changes in SttRecognizer.kt, SessionDepsAdapter.kt (playback), FakeSessionDeps, AudioTurnTest, StudyRestartTest for a cosmetic gain; text is documented as "what was heard so far" and is used for COMPLETED and ERROR alike (ERROR keeps the transcript in AnswerPanel). Not serialized.

D16 — CONFIRMED — runSkippable returns Boolean only so listenAndCheck can capture `var listenResult`; callers elsewhere ignore the Boolean (SessionEngine.kt:232,239,244; StepExecutor.kt:71,77,90,95).
Fix (S, domain): `internal suspend fun <T> runSkippable(block: suspend () -> T): T? = coroutineScope { var result: T? = null; val inner = launch { result = block() }; skippableJob = inner; try { inner.join() } finally { if (skippableJob === inner) skippableJob = null }; if (inner.isCancelled) null else result }`. In listenAndCheck: `val result = engine.runSkippable { deps.listen(...) }` and drop `tapped`/`var listenResult` (null == tapped; keep the `result == null ||` branch). Keep the launch-based implementation (do not switch to async: cancellation of the inner job must not be mistaken for outer cancellation). Tests: existing `tap during listening stops stt with skipped result without rating`, `tap during wait skips the step`, `foreground tap interrupts a substituted background pause`. Risk: low.

D17 — NOT_WORTH — `replaceFromBase: SrsState?` is documented in KDoc and mirrors PendingReview/ReviewWriteQueue (data layer) and DeckRepository; replacing with a sealed/enum is cross-layer churn. No change.

D18 — CONFIRMED — `reviewedCardIds` and `baseSrsStates.keys` are always mutated together (SessionEngine.kt:88-89, 171-172) and cleared together.
Fix (S, domain): delete `reviewedCardIds`; `reviewedCardCount = baseSrsStates.size`; `if (card.id in baseSrsStates)` in submitReview; drop `reviewedCardIds +=` / `.clear()`. Tests: existing `rate persists review...`, `re-rate after goToPreviousCard overwrites review from base srs`. Risk: nil.

D19 — INTENTIONAL — Pinned by `ClearError clears message but not status` (reducer) and `clearError clears message only` (engine). `error` is a one-shot event consumed immediately by StudyViewModel (`engine.clearError()` right after sending the snackbar event) while `status == ERROR` is state that StudySessionService.sessionAudioIdle uses to release audio focus until the next step moves status on. No change.

D20 — CONFIRMED — (a) StartSession lists every field and equals `SessionState(cards = ...)`; adding a field later and forgetting to reset it is a trap. (b) FinishSession leaves audioPageIndex/error (stop() mid-TTS leaves a stale audioPageIndex; not visible today because FINISHED shows the results screen, but cheap to make consistent).
Fix (S, domain): fold with D26: make `StartSession(val cards: List<Flashcard>)` produce `SessionState(cards = action.cards)`; remove `SetCards`; in FinishSession add `audioPageIndex = null, error = null`. Tests: SessionReducerTest (3 uses of `SessionAction.StartSession` -> `StartSession(emptyList())`), add test that FinishSession clears audioPageIndex. Risk: low.

D21 — PARTIAL — The `takeUnless` simplification is a free readability win; `revealedPages: Set<Int>` is not worth it: it ripples into ui-study (StudyCard.kt:83,110,122,230,308,330, StudyCardTest) and tests assert `listOf(0,1,2)` ordering.
Fix (S, domain): SessionReducer RevealPages: `peekedPageIndex = state.peekedPageIndex.takeUnless { it in newRevealed }`. Keep List<Int>. Tests: existing `RevealPages clears peek if the page is revealed`.

D22 — INTENTIONAL — Wiping lastAnswerResult at a gate is pinned by `action phase transitions clear lastAnswerResult` and its consequence (a conditional step after a post-listen gate never matches) is exactly what ModeIssue.ConditionAfterTapGate warns about. Optional S: add a one-line Polish comment in the reducer's StartTapRevealGate branch pointing to ModeIssue.ConditionAfterTapGate.

D23 — CONFIRMED — pressDown picks `nextHidden` at press time, fires PeekSet 500 ms later; a page revealed meanwhile (step reveal, auto-reveal) makes it peek an already-revealed page (UI ignores revealed pages at StudyCard.kt:330, so the user sees nothing even though another page is still hidden).
Fix (S, domain): `peekTimerJob = scope.launch { delay(StudyConstants.PEEK_HOLD_THRESHOLD_MS); hiddenPages().firstOrNull()?.let { dispatch(SessionAction.PeekSet(it)) } }` and drop the early `?: return`. Tests: in SessionEngineSemanticsTest next to `press held past threshold peeks next hidden page`: press, reveal page 1 through the engine during the delay, advance time, assert peekedPageIndex == 2 (or null when nothing hidden). Risk: nil.

D24 — PARTIAL — Only cosmetic parts are valid; bg "reveal all after one wait" vs fg "one per tap" is intentional in effect (nothing is visible/audible between reveals in the background, and substituted waits would only lengthen hands-free playback).
Fix (S, domain): in substituteInBackground compute `waitMs` lazily (`val waitMs by lazy` or a local fun used only in the 3 wait branches) and replace the forEach loop with `hiddenPages().takeIf { it.isNotEmpty() }?.let { dispatch(SessionAction.RevealPages(it)) }`. Tests: existing background tests. No behaviour change.

D25 — CONFIRMED — Comment "koniec nasłuchu = odpowiedź pominięta" sits above the IDLE/SPEAKING/ERROR branch but describes the LISTENING branch; the awkwardly wrapped nested if (ktlint-formatted) is hard to read.
Fix (S, domain): move comment to `SessionStatus.LISTENING -> skippableJob?.cancel()`; extract `private fun handleTapWhileIdle() { if (_state.value.interactionGate.kind == InteractionGateKind.TAP_TO_REVEAL) handleGateTap() else skippableJob?.cancel() }` and call it from the IDLE/SPEAKING/ERROR branch. Tests: existing.

D26 — PARTIAL — (a) `private val ttsRate` is only forwarded -> plain constructor parameter. (b) `needsMicrophone`/`canRunHandsFree` are re-scanned on every access; StudyViewModel reads `engine.needsMicrophone` inside the state collector on every emission -> compute once (`expandedSteps` is immutable). (c) start() double dispatch (SetCards then StartSession) is folded into D20. Not a bug.
Fix (S, domain): `ttsRate: Float` without `private val`; `val needsMicrophone: Boolean = expandedSteps.any { it is ListenAndCheckStep }` and `val canRunHandsFree: Boolean = BackgroundSubstitution.isModeHandsFreeCapable(expandedSteps)` declared after `expandedSteps`. Tests: existing.

D27 — PARTIAL — `* 3` is an unnamed magic factor (CONFIRMED part). Stale lastTtsDurationMs on a card that has no TTS before its listen is real but EDGE: only matters for a custom mode that speaks on some cards but not others, and it can only lengthen the hard timeout (the recognizer ends on silence anyway).
Fix (S, domain): add `StudyConstants.STT_TIMEOUT_TTS_FACTOR = 3` (or private const in StepExecutor) and use it at StepExecutor.kt:148. Optional: reset by keying duration to card id (`lastTtsCardId`) — skip unless touching the file. Tests: none new.

D28 — CONFIRMED — DynamicPauseStep and WaitStep both do PeekClear + runSkippable{delay}.
Fix (S, domain): `private suspend fun pause(ms: Long) { engine.dispatch(SessionAction.PeekClear); engine.runSkippable { delay(ms) } }`; DynamicPause -> `pause(pauseMs.toLong())`, Wait -> `pause(step.ms)`. Optionally extract SpeakPage/SpeakAll into `speak...()` helpers to shorten `execute`. Tests: existing.

D29 — CONFIRMED — ListenGrade and GradeAfterListen duplicate onCorrect/onWrong/manualFallback expansion.
Fix (S, domain): `private fun gradeSteps(onCorrect: CompoundBranch, onWrong: CompoundBranch, manualFallback: Boolean): List<ModeStep> = buildList { addAll(expandBranch(onCorrect, CORRECT)); addAll(expandBranch(onWrong, WRONG)); if (manualFallback) { add(ShowAllPagesStep()); add(ShowRatingsStep()) } }`; ListenGrade = `listOf(ListenAndCheckStep(...)) + gradeSteps(...)`, GradeAfterListen = `gradeSteps(...)`. "Odd feedback selection" (`if (condition == CORRECT) Success else Error`) is fine; can become `when (condition)`. Tests: CompoundExpanderTest unchanged (output identical; it is the safety net).

D30 — INTENTIONAL — Two different use cases: SpeakPauseNext = shadowing pause sized by the upcoming page; AutoFlip = think-time sized by the question page. The field name `nextPageIndex` is serialized in persisted steps (Room/Firestore/backup), so a rename is a format change. Optional S: add KDoc on DynamicPauseStep stating "pause = chars of page [nextPageIndex]". If ever renamed, keep `@SerialName("nextPageIndex")`.

D31 — CONFIRMED — validate() expands every step 3 times and is ~55 lines (runs on every editor change).
Fix (S, domain): `val perStep = steps.map(CompoundExpander::expandStep)` once; `ownedSteps = perStep.flatMapIndexed { i, atoms -> atoms.map { i to it } }`; page-range check uses `perStep[index]`; extract `pageRangeIssues`, `terminalAndRateIssues`, `fallbackIssues`, `tapGateIssues` private helpers returning lists. Tests: ModeStepValidatorTest (output order of issues must stay: keep the same append order; `issues.distinct()` unchanged).

D32 — CONFIRMED (low) — 7 identical `override val blocking`.
Fix (S, domain): `sealed class ModeIssue(val blocking: Boolean)`; `data object NameRequired : ModeIssue(blocking = true)`, `data class PageOutOfRange(...) : ModeIssue(true)` etc. UI only uses `is ModeIssue.X` and `.blocking` (verify with a compile). Data-class equality unaffected. Tests: ModeStepValidatorTest.

D33 — CONFIRMED — KDoc claim is false for AUDIO: ListenGrade() expands to a single attempt, the built-in LISTEN_SPEAK seed has two (retry after WRONG). CLASSIC template does equal the built-in. Making the template match is not expressible with one compound; a simple single-attempt template is a fine product choice.
Fix (S, domain): reword the KDoc of ModeTemplate: "CLASSIC = built-in Classic; AUDIO = simplified Audio (one listening attempt, no retry); BLANK = empty". No code change. Tests: ModeStepValidatorTest line 57 already validates all templates.

D34 — CONFIRMED — 2000/2200/800/500 are repeated in ModeStep.kt (DEFAULT_CORRECT/WRONG, Fixed.ms default) and SeedModes.kt.
Fix (S, domain): add to StudyConstants `PAUSE_AFTER_CORRECT_MS = 2000L`, `PAUSE_AFTER_WRONG_MS = 2200L`, `PAUSE_BEFORE_RETRY_MS = 800L`; use `STEP_DEFAULT_WAIT_MS` for `CompoundPause.Fixed.ms`. VALUES MUST NOT CHANGE: SEED_MODES steps are compared to stored built-ins (`isModifiedBuiltIn`, `refreshUntouchedBuiltInModes`), so any change would flip every stored built-in to "modified". Tests: existing + SeedDataTest. Wire: none.

D35 — CONFIRMED — `dummy: Int = 0` exists only because a `data class` needs a property.
Fix (S, CROSS-LAYER minor): `@Serializable @SerialName("show_all_grade") data object ShowAllGrade : CompoundParams` and same for `fail_next`; update the two UI constructions `CompoundParams.ShowAllGrade()` / `FailNext()` in ui/modedetail/AddStepDialog.kt:87,97 (drop the parens) and tests BackupValidatorTest.kt:87,93, CompoundExpanderTest.kt:77. `is ShowAllGrade` branches elsewhere keep compiling. Wire: write side loses `"dummy":0`; read side unchanged (see wire summary). Tests to add: decode legacy JSON `{"kind":"show_all_grade","dummy":0}` and `{"kind":"fail_next","dummy":0}` through ModeStep serializer and via FirestoreSchema.modeFromMap; round-trip equality (data object identity). Risk: low; verify the polymorphic data-object encoding under `@JsonClassDiscriminator("kind")` by running the existing round-trip tests.

D36 — PARTIAL — The extension `ModeStep.pageIndex` shadows member `pageIndex` of ShowPageStep/SpeakPageStep/ListenAndCheckStep (works only through smart-cast resolution) and `else -> null` hides new step types. `withCondition`'s 17 branches are unavoidable without a base-class `copy` (NOT_WORTH).
Fix (S, domain only: SessionEngine.kt:62, ModeStepValidator.kt:59, AtomicStepModelTest): rename extension to `ModeStep.referencedPage` and list the null cases explicitly (`is ShowAllPagesStep, ..., is CompoundStep -> null`). Tests: AtomicStepModelTest (update import/name). Wire: none.

D37 — INTENTIONAL — A StepCondition with no safe fallback: mapping an unknown condition to null would silently run a conditional step (e.g. MarkFailed, AutoRate) unconditionally; throwing makes backup import reject and Room `decodeOrEmpty` log. KeyedEnum fallbacks exist for harmless presentation/ordering enums. Wire values ("correct"/"wrong") identical either way. No change.

D38 — PARTIAL — calculateFsrs is ~40 lines and follows the published FSRS-4.5 formulation; `w[i]` indexing is the community convention, renaming 17 indices risks typos that tests may not catch. Only the loose numbers deserve names.
Fix (S, domain): see D39; add a short KDoc to SrsConstants.W naming what each used index means (w[4]/w[5] init difficulty, w[6]/w[7] difficulty update, w[8..10] recall stability, w[11..14] forget stability, w[15]/w[16] hard/easy). Tests: SrsEngineTest unchanged.

D39 — PARTIAL — `clamp` mapping NaN to min is deliberate and commented (SrsEngine.kt:360). Remaining magic numbers: difficulty bounds 1.0/10.0 (x3), `11 - d`, `rating - 3`, `0.1` in initStability.
Fix (S, domain): `SrsConstants.DIFFICULTY_MIN = 1.0`, `DIFFICULTY_MAX = 10.0`; `initStability` min -> `SrsConstants.MIN_INTERVAL_DAYS` (same 0.1) ; leave `11 - d` and `rating - 3` (FSRS formula constants; comment them). Tests: SrsEngineTest.

D40 — PARTIAL — (a) `SrsConstants.W` is a public mutable DoubleArray that nobody mutates: make it immutable. (b) Ratings Int -> enum Rating: NOT_WORTH (ints flow through deps.haptic, onCardReviewed, PendingReview/ReviewWriteQueue in data, study UI rating buttons, and persisted step JSON; see wire summary).
Fix (S, domain): `val W: List<Double> = listOf(...)` and change the private helper signatures in SrsEngine.kt from DoubleArray to List<Double> (only usage: SrsEngine.kt:11). Tests: SrsEngineTest (numbers must be bit-identical). No wire impact.

D41 — CONFIRMED — The threshold==null branch (MATCH_EASY/GOOD/HARD) is dead in production: the only caller without a stored `suggestedRating` is AutoRateFromAnswerStep, and CORRECT/INCORRECT results always carry suggestedRating (set together with the status in listenAndCheck). `if (threshold >= 100) 100 else threshold + ceil(0)` is redundant (formula already yields 100).
Fix (S-M, domain): `fun mapMatchToRating(matchPercent: Int, successThreshold: Int): Int` using `easy = threshold + ceil((100 - threshold) / 2.0).toInt()`; delete MatchConstants.MATCH_EASY/GOOD/HARD; StepExecutor.kt:109 -> `result.suggestedRating ?: mapMatchToRating(result.percent ?: 0, result.threshold ?: StudyConstants.STEP_DEFAULT_THRESHOLD)`; rewrite the 4 threshold-less tests in SrsEngineTest (`90/70/50/20 percent`) to the threshold form (e.g. threshold 70) or delete them. Behaviour for real sessions identical. Risk: low.

D42 — PARTIAL — Confirmed cleanups: 5 unused imports (SrsConstants, SrsState, abs, max, min), Regexes recompiled per call (`normalizeText`), unreachable `maxLen > 0` checks (both operands are non-empty by construction), `normalizeText`/`levenshteinDistance` public but used only here. `wordSimilarity` being order-insensitive is a lenient-grading design choice (greedy per-word best match) — leave.
Fix (S, domain): remove imports; hoist `private val NON_WORD = Regex(...)`, `private val SPACES = Regex("\\s+")`; remove the dead conditionals (`1.0 - dist.toDouble() / maxLen`, `(1 - lev/maxLen) * 100`); make helpers `private`/`internal`. Tests: SrsEngineTest matchSpeech cases (exact values 100/50/100/100).

D43 — PARTIAL — Tombstone predicate difference (`!= null` vs `> 0`) is benign: deletedAt is only ever written as clock.now() or absent, never 0. One-sided card resurrection after an edit newer than the delete is pinned by test `edit newer than deletion revives card`; groups/modes not doing it is a harmless asymmetry (deletes never bump updatedAt, so a single-sided group cannot have edit > delete). Sub-claim "dead `?: null`" is WRONG (no such expression in MergeEngine; the `if (deletedAt > 0) deletedAt else null` branches are meaningful).
Fix (optional S, domain): introduce one helper (e.g. `private fun Long?.isTombstone() = (this ?: 0L) > 0L`) so merge and live()/countDueCards read the same; keep behaviour. Skip if D6 is done and not otherwise touching.

D44 — INTENTIONAL — "srs never-regress" is the documented design and tests pin it (`mergeCards content last-write-wins srs never-regress`, `srs prefers higher repetitions then newer srsUpdatedAt`, `uses srs side as content tiebreaker`). The app has no "reset card progress" feature (grep), so the claimed failure mode does not exist. No change.

D45 — NOT_WORTH — Renaming getCardCategory/getDueCards/getGuestHasData/getStoredPageCount touches StudyViewModel, SessionSummary (ui) and BootstrapLoader (data) for style only. mergeActivityHeatmap is already 4 clear lines. No change.

D46 — PARTIAL — Two parts. (1) No truncation beyond 20 entries: EDGE (only corrupt data; storedPageCount caps at MAX_STORED_PAGE_COUNT but oversize name/language lists survive). (2) REAL: padPageMetadata persists the English literal "Page N", so raising the page count in deck settings (DeckRepository.setActivePageCount -> withActivePageCount) stores "Page 3" in the user's data even in pl/de/es/it UI, while the import flow uses localized `import_page_label`. Tests pin the English fallback (DataNormalizerTest:113, OperationsTest:92) so keep it as the domain-level fallback for loading corrupt/legacy data.
Fix (M, CROSS-LAYER: ui-settings/decksettings + data): let the caller supply names: `FlashcardGroup.withActivePageCount(count, now, defaultName: (Int) -> String = { "Page ${it + 1}" })` and `padPageMetadata(..., defaultName)`; `DeckRepository.setActivePageCount(deckId, count, defaultName)`; DeckSettingsScreen passes `{ context.getString(R.string.import_page_label, it + 1) }` (the screen already uses that string for the field label). Existing stored "Page N" values stay as they are. Optional S in domain: truncate lists to `storedPageCount` in normalizeGroup. Tests: OperationsTest: custom default name is used; DeckSettingsViewModelTest. Risk: low.

D47 — PARTIAL — The `0..100` literal appears in asThreshold and the validator (>100); worth a constant. NaN/negative difficulty/stability repair is EDGE: JSON cannot carry NaN, BackupValidator and FirestoreSchema require finite numbers, and calculateFsrs re-clamps every output and treats stability <= 0 as "new".
Fix (S, domain): `StudyConstants.MAX_THRESHOLD_PERCENT = 100` (or `PERCENT_RANGE = 0..100`) used by DataNormalizer.asThreshold and the two BackupValidator checks (lines 139, 186). Tests: existing.

D48 — PARTIAL — "updatedAt bump only by convention" is a design observation (soft deletes deliberately do NOT bump updatedAt: tombstone must be newer than every edit, and merge's `isAlive` relies on `deletedAt > latestEdit`) -> NOT_WORTH. Real nit: newStudyMode/edited() do not trim the name while newGroup does (a mode named "Foo " is stored with the space; validator only checks isBlank).
Fix (S, domain): `newStudyMode`: `name = name.trim()`; `StudyMode.edited`: `copy(name = name.trim(), updatedAt = now)`. Do NOT trim in normalizeStudyMode (runs on every load/merge and would silently rewrite existing data). Tests: OperationsTest for both.

D49 — PARTIAL — `newGroup(languages, ...)` should be `pageLanguages` (param is only called positionally at DeckRepository.kt:91, so a rename is safe). `withPagesSwapped` pad-then-discard is just confusing code; from == to bump is unreachable from the UI (EDGE).
Fix (S, domain): rename param to `pageLanguages` (update OperationsTest:49 only if named); in withPagesSwapped replace the padPageMetadata call with `val languages = pageLanguages + List((pageNames.size - pageLanguages.size).coerceAtLeast(0)) { DEFAULT_PAGE_LANGUAGE }` or add a `padLanguages()` helper used by both; early `if (from == to) return this`. Tests: OperationsTest swap cases + one for from == to returning the same instance.

D50 — INTENTIONAL — archiveDaysLeft is documented as calendar days ("Dni kalendarzowe do usunięcia") and the UI shows "Deletes today" for 0 / "Deletes tomorrow" for 1; the last day showing "today" while the exact cut-off is a few hours later is correct for a calendar-day label. SelectorsTest pins 0/1/7. Zone injection is already available via LocalDates.localDate(zone). No change.

D51 — CONFIRMED — `SEED_MODES.first()` silently depends on list order.
Fix (S, domain): in resolveStudyMode final fallback use `SEED_MODES.first { it.id == DEFAULT_MODE_ID }`. Tests: OperationsTest resolveStudyMode fallback (add if missing).

D52 — INTENTIONAL — BuiltInMode.defaultName ("Klasyczny", "Audio") is the STORED name of built-in modes and is compared with stored data (`isModifiedBuiltIn`, `isUserAuthoredMode`, `refreshUntouchedBuiltInModes`); the KDoc says the UI shows the translation until the user renames. Changing/localizing it would flip existing built-ins to "modified" and break the "Restore defaults disabled for unmodified modes" behaviour. isBuiltInModeSourceId wrapper and enum/list split are harmless. No change; do not alter the names.

D53 — PARTIAL — Confirmed: redundant same-package import `com.raycoll.app.domain.seed.SEED_MODES` in SeedData.kt:8; `languageNames` rebuilds a 5-entry map on each access. NOT valid: replacing the table with Locale.getDisplayLanguage — names are persisted and synced, and must be identical across devices/ICU versions (that is the point of idScope-deterministic seeds). deckKey "null" for guests is harmless (seedId ignores key when idScope == null).
Fix (S, domain): delete the import; turn `languageNames` into `private val` via `by lazy` (or a companion map built once). Tests: SeedDataTest.

D54 — NOT_WORTH — The 3 tables have different jobs (header-token matching, header->language detection, localized seed names); consolidating them couples unrelated code. The "en-US" in LanguageDetector is the English tag, not DEFAULT_PAGE_LANGUAGE by meaning. No change.

D55 — PARTIAL — Per-call Regex compilation is real (looksLikeHeaderLabel/normalizeHeaderToken per token). Sub-claim "Ł replace dead after lowercase" is WRONG: the replaces run BEFORE lowercase and are required (NFD does not decompose ł/Ł; project memory + code comment). Double score with rounding is a readability nit.
Fix (S, domain): hoist `private val COMBINING_MARKS = "\\p{InCombiningDiacriticalMarks}+".toRegex()` and `private val WHITESPACE = Regex("\\s+")`; leave the ł handling untouched; optional: express weights as integer points (65/45/15/10, penalty min(35, n*15)) and compare `>= 50`, dropping the rounding. Tests: ImportParserTest header cases (must include a "Słowo" case — add one if absent, it guards the ł handling).

D56 — PARTIAL — Valid: unused import DEFAULT_PAGE_LANGUAGE in ImportPayloadValidator.kt; nested elvis/if is hard to read. `ImportPayload` as data class is harmless. Blank pageNames: not an issue (ImportScreen substitutes localized defaults before building the payload, ImportScreen.kt:123). LANG_CODE_REGEX rejecting 3-subtag tags such as zh-Hans-CN is EDGE for a flashcard-language field.
Fix (S, domain): remove import; rewrite validateImportPayload as a flat `when` (deckName blank -> pageCount range/languages size -> invalid language -> no cards -> empty row) with a small `firstInvalidLanguage` local. Tests: ImportParserTest/ImportViewModelTest unchanged.

D57 — CONFIRMED — detectSeparator calls parseRows over the whole text for 4 separators then keeps 5 rows; it runs on every edit of the pasted text (ImportViewModel.parseSource), so large pastes pay 4 full parses.
Fix (S, domain): add an optional `maxRows: Int = Int.MAX_VALUE` to parseRows that stops once `rows.size == maxRows` after an `endRow()` (count non-blank rows exactly like take(5) does now) and call `parseRows(text, separator, ImportConstants.SEP_DETECT_ROWS)`. Tests: ImportParserTest `detectSeparator ...` cases (incl. the quoted `"a;b"` case) must stay green; add a 10k-row perf-sanity-free assertion that detection result is unchanged for a long text.

D58 — PARTIAL — encodeCell not quoting `\r` is real, but the cells reaching it come from parseRows (already CR-normalised) or the preview text fields (Enter yields \n), so impact is EDGE; the fix is one condition. Regex per call in normalizeCsvText: hoist.
Fix (S, domain): `cell.contains('\r')` in encodeCell's quoting condition; `private val LINE_BREAKS = Regex("\r\n?")` for normalizeCsvText. Tests: add "cell with \\r" to the round-trip test at ImportParserTest.kt:90 (note: parse normalises it to \n, so assert no row split rather than equality).

D59 — CONFIRMED — dayKey uses pattern "yyyy-MM-dd" (year-of-era) while computeStreak builds keys with LocalDate.toString(); two formats for the same persisted key.
Fix (S, core+domain): `fun dayKey(...) = localDate(epochMillis, zone).toString()` (ISO_LOCAL_DATE; drop `formatter`); in computeStreak use `LocalDates.dayKey` helper on the date (or keep toString) so both go through one definition. Wire: byte-identical for all years 1000-9999 (existing heatmap keys in Room/Firestore/backup unchanged). Tests: LocalDatesTest already exists; add one asserting `dayKey(x) == localDate(x).toString()` and a non-default zone case.

D60 — PARTIAL — Valid cosmetics: `Clock.minuteTicks()` extension declared before the interface; 60_000L unnamed. Ids global randomness is not a problem (tests use IdsTest; ids are random UUIDs by design).
Fix (S, core): reorder (interface, SystemClock, then extension) and `private const val TICK_INTERVAL_MS = 60_000L`. No tests.

D61 — PARTIAL — live() lives in DueCards.kt although used by BrowseFilter/CardStats; countDueCards re-implements the same filter inline. Cheap tidy only.
Fix (S, domain): add `val Flashcard.isLive get() = deletedAt == null` next to the model (Flashcard.kt) and use it in `live()`, `countDueCards`, and FlashcardGroup-level checks; move `live()` to selectors/Flashcards.kt or keep and add a one-line KDoc. Tests: SelectorsTest.

D62 — CONFIRMED — BrowseFilter lowercases (allocates) every page of every card on each keystroke.
Fix (S, domain): `card.pages.take(activePageCount).any { it.contains(query, ignoreCase = true) }` with `val query = search.trim()` (no lowercase). Behaviour: equal for ASCII/Latin/Polish; slightly better for Greek final sigma. Tests: BrowseViewModelTest + SelectorsTest; add a Polish-diacritics ("Ł"/"ł") and de-DE "ß"/"ä" case.

D63 — EDGE — Int overflow needs ~18M characters of card text. "object with pure functions" is fine as is. Optional one-token hardening: `sumOf { it.length.toLong() }`. No change required.

---
CROSS-LAYER IDs: D35 (ui-modedetail: AddStepDialog.kt:87,97), D46 (ui-decksettings + data DeckRepository.setActivePageCount).

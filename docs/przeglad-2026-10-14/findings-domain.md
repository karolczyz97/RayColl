# Findings: domain/ + core/ (paths relative to app/src/main/java/com/raycoll/app/)

D1 domain/session/SessionEngine.kt:38-412 — god class (~12 mutable fields, 3 StateFlows, peek, review bookkeeping, bg substitution, pause). StepExecutor calls back via internal methods; `this` passed in init. Suggest: extract ReviewTracker, PeekController, narrow StepHost interface.
D2 SessionEngine.kt:100-149 + SessionReducer.kt:105-113 + SessionState.kt:26,43 — resume-after-tap-gate spread over pendingStepIndexToRun, continueStepIndex, ConsumePendingStepIndex, lastExecutedCardIndex. Suggest runSteps(card, startIndex).
D3 SessionState.kt:20-27 — InteractionGate has two NONE sentinels; kind=NONE with non-null continueStepIndex representable. Suggest nullable gate / TapGate.
D4 model/ModeStep.kt:9-100,228-257 (+SessionEngine.kt:61, StepExecutor.kt:121-122) — CompoundStep shares type with atomic steps; executor/withCondition/pageIndex carry dead branches. Suggest sealed AtomicStep; expander returns List<AtomicStep>.
D5 model/SrsState.kt:11 (+DueCards.kt:12, SrsCardCategory.kt:8-13, SrsEngine.kt:21-33, DataNormalizer.kt:38,58) — FSRS state is Int 0..3 compared with literals. Suggest enum CardState with int serializer.
D6 merge/MergeEngine.kt:29-169 — mergeCards/mergeGroups/mergeStudyModes same skeleton, `!!`. Suggest generic mergeById.
D7 normalize/BackupValidator.kt (330 lines) hand-validates Map tree, then decoded anyway. Suggest strict decode + domain checks.
D8 BUG BackupValidator.kt:74,102-157,162-163,186,259-275 — accepts `kind ?: type` for CompoundPause but decoder uses discriminator "kind" → passes validation then SerializationException; requires fields that have model defaults (speak, pageIndex, successThreshold, pageLanguages, pageNames) → rejects valid backups.
D9 BUG session/StepExecutor.kt:149-154,201-205 — deps.speak wrapped in try/catch; deps.listen not → STT exception propagates through card job, may crash.
D10 BUG SessionEngine.kt:151-156,179-194 — rate()/skipToNextCard() launch advanceToNextCard not tracked in cardJobs; stop() between launch and resumption lets AdvanceCard flip FINISHED back; two rapid skips can skip two cards.
D11 model/StudyMode.kt:10-11 (+DataNormalizer.kt:155-163) — isBuiltIn redundant with builtInSourceId.
D12 merge/MigrationRules.kt:14-24 vs operations/StudyModeOperations.kt:27-30 — isUserAuthoredMode and isModifiedBuiltIn same rule with opposite fallbacks.
D13 BUG? MigrationRules.kt:14-17 (caller data/sync/BootstrapLoader.kt:67) — getGuestHasData counts any non-deleted group; guest with only auto-seeded decks gets migration dialog on first login.
D14 SessionState.kt:11-18 / StepExecutor.kt:105-186 — LastAnswerResult = status + 4 nullable fields, built in 4 near-identical blocks. Suggest sealed Answer or factories.
D15 SessionDeps.kt:19-21 — ListenResult(text,status) text meaningless for SKIPPED/ERROR. Suggest sealed.
D16 SessionEngine.kt:289-298 + StepExecutor.kt:149-155 — runSkippable returns Boolean so caller captures var. Suggest runSkippable<T>(): T?.
D17 SessionDeps.kt:14 — replaceFromBase: SrsState? encodes re-review as nullable.
D18 SessionEngine.kt:69-79,162-177 — reviewedCardIds == baseSrsStates.keys, redundant set.
D19 SessionState.kt:5,44 + SessionReducer.kt:87-89 — status ERROR and error field can disagree; ClearError leaves status ERROR.
D20 SessionReducer.kt:4-17,63-85 — StartSession lists all fields to reset (== SessionState(cards=...)); FinishSession doesn't clear audioPageIndex/error (stale audio highlight after stop mid-TTS).
D21 SessionReducer.kt:32-52 — if/else → takeIf/takeUnless; revealedPages should be Set<Int>.
D22 SessionReducer.kt:95-103 — StartTapRevealGate wipes lastAnswerResult (hidden coupling with validator ConditionAfterTapGate).
D23 BUG SessionEngine.kt:339-350 — pressDown computes nextHidden before 500ms delay; may peek a page revealed meanwhile.
D24 SessionEngine.kt:228-259 — calculateBgWaitMs evaluated for steps that don't need it; reveal loop could be one RevealPages; bg reveals all pages after one wait vs fg one per tap (intentional?).
D25 SessionEngine.kt:301-319 — misplaced comment; nested if → handleTapWhileIdle().
D26 SessionEngine.kt:38,66-67 — ttsRate only forwarded; needsMicrophone/canRunHandsFree getters rescan; start() dispatches SetCards then StartSession.
D27 StepExecutor.kt:41,147-148 — `* 3` magic; lastTtsDurationMs not reset per card → card with no TTS inherits previous duration for listen timeout.
D28 StepExecutor.kt:86-96 — DynamicPauseStep and WaitStep duplicate PeekClear+runSkippable{delay}; execute when ~70 lines.
D29 steps/CompoundExpander.kt:77-94,109-139 — manualFallback and onCorrect/onWrong duplicated between ListenGrade and GradeAfterListen; odd feedback selection.
D30 CompoundExpander.kt:69-75 + ModeStep.kt:53-59 — DynamicPauseStep.nextPageIndex: SpeakPauseNext passes next page, AutoFlip passes question page. Intentional? rename timingPageIndex.
D31 steps/ModeStepValidator.kt:48-102 — validate ~55 lines, expands steps 3 times.
D32 ModeStepValidator.kt:17-41 — each ModeIssue repeats `override val blocking`.
D33 steps/ModeTemplate.kt:7-11 — KDoc says templates = built-in modes, but AUDIO template lacks second attempt of LISTEN_SPEAK seed.
D34 ModeStep.kt:176-182,212 + seed/SeedModes.kt:61-77 — pause lengths 2000/2200/800/500 repeated.
D35 ModeStep.kt:120,160 — ShowAllGrade/FailNext have `dummy: Int = 0`; use data object.
D36 ModeStep.kt:228-257 — `pageIndex` extension shares name with member; else->null non-exhaustive; withCondition 17 identical branches.
D37 ModeStep.kt:14-21 vs KeyedEnum.kt — StepCondition uses SerialName and throws on unknown; others use KeyedEnumSerializer with fallback; inconsistent.
D38 srs/SrsEngine.kt:10-49 — calculateFsrs long; w[i] unnamed.
D39 SrsEngine.kt:51-78 — magic 1.0/10.0/11-d/rating-3/0.1; `clamp` maps NaN to min.
D40 model/Constants.kt:21-40 — SrsConstants.W public mutable DoubleArray; Ratings are Int constants → enum Rating.
D41 srs/SpeechMatcher.kt:77-90 — mapMatchToRating two functions in one; redundant `if threshold>=100`.
D42 SpeechMatcher.kt:3-16,38-75 — unused imports, Regex compiled per call, unreachable maxLen>0 checks, public helpers; wordSimilarity order-insensitive.
D43 model/Flashcard.kt:12, FlashcardGroup.kt:17-18, MergeEngine.kt:10-114 — tombstone is `deletedAt != null` in live()/countDueCards/isArchiveExpired but `> 0` in merge; one-sided cards resurrect when edited after delete but groups/modes don't; dead `?: null`.
D44 MergeEngine.kt:16-27,64-69 — localSrsWins prefers more repetitions (reset can't propagate); contentSide tie-break falls back to SRS preference.
D45 MergeEngine.kt etc — Java-style getX functions; mergeActivityHeatmap could be one-liner.
D46 BUG normalize/DataNormalizer.kt:41-55 — padPageMetadata pads but never truncates pageNames/pageLanguages beyond count; hard-coded English "Page N" in domain.
D47 DataNormalizer.kt:57-78,168 — 0..100 repeated; normalizeCard doesn't repair NaN/negative difficulty/stability.
D48 operations/*.kt + data/local/db/Daos.kt:33-39,119-124 — updatedAt bump invariant only by convention; deletes/archives raw SQL; newStudyMode doesn't trim name while newGroup does.
D49 operations/GroupOperations.kt:10-56 — newGroup(languages,...) naming; withPagesSwapped pads then discards names, bumps updatedAt when from==to; parallel lists pageNames/pageLanguages.
D50 GroupOperations.kt:48-51 + selectors/ArchiveCountdown.kt:9-13 — isArchiveExpired uses elapsed ms, archiveDaysLeft calendar days in default zone → "0 days left" while alive; zone not injectable.
D51 operations/StudyModeOperations.kt:21-24 — SEED_MODES.first() fallback relies on order.
D52 seed/SeedModes.kt:23-86 — BuiltInMode enum and SEED_MODES separate; isBuiltInModeSourceId wrapper; defaultName hard-coded Polish in domain.
D53 seed/SeedData.kt:8,66-84 — redundant same-package import; languageNames map rebuilt per access; 5x5 translation table could use Locale.getDisplayLanguage; deckKey interpolates null as "null".
D54 imports/HeaderDetector.kt:12-18 + LanguageDetector.kt:15-43 + SeedData.kt:58-64 — language data in 3 tables; "en-US" literal.
D55 HeaderDetector.kt:20-85 — regexes compiled per call; Ł replace dead after lowercase? (NB: memory says NFD doesn't decompose ł — check); double weights + rounding hack.
D56 imports/ImportPayloadValidator.kt:3-44 — unused import; nested elvis/if chains; ImportPayload not data class; blank pageNames not rejected; LANG_CODE_REGEX rejects zh-Hans-CN.
D57 imports/CsvParser.kt:64-73 — detectSeparator parses whole file 4 times.
D58 BUG CsvParser.kt:76-81 — encodeCell doesn't quote cells containing \r; normalizeCsvText regex per call.
D59 core/LocalDates.kt:9-16 + selectors/CardStats.kt:34-36 — dayKey "yyyy-MM-dd" (year-of-era) vs LocalDate.toString(); use ISO_LOCAL_DATE.
D60 core/Clock.kt:7-22 — ordering, 60_000L unnamed; Ids.newId global randomness.
D61 selectors/DueCards.kt:9-42 — live() placement; countDueCards re-implements filter.
D62 selectors/BrowseFilter.kt:14-18 — lowercases each page per keystroke; use contains(ignoreCase).
D63 session/BackgroundSubstitution.kt:8-21 — object with pure fns; Int overflow on huge pages.

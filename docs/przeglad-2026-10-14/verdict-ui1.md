# Verdict ui1 — ui/study (except VM), ui/dashboard, ui/browse, ui/components, ui/theme, ui/archive, ui/stats

Paths relative to `app/src/main/java/com/raycoll/app/` unless prefixed `res/` (= `app/src/main/res/`).
Facts checked while verifying (reused below):
- Kotlin 2.4.21 + Compose BOM 2026.09 => strong skipping is on by default (`@Immutable` is irrelevant).
- MainActivity wraps everything in `Surface(color = colorScheme.background)` and NavGraph.kt:69 also paints `.background(colorScheme.background)`.
- Contrast measured (WCAG ratio) for the SRS pastel `*Bg` colours used by the bars: light, vs track `surfaceContainerHighest` E6E0E9 = 1.00 / 1.00 / 1.00 / 1.05 (new / learning / review / mastered), vs card `surfaceContainerLow` F8F2FA = 1.12-1.18. Dark, vs track 36343B = 1.04 / 1.24 / 1.15 / 1.00. The matching `*Fg` colours give 6.0-8.4 (light) and 7.2-9.5 (dark) vs the track.
- Contrast of the audio-tint colours vs the study card (`surfaceContainerHigh`): light `primaryContainer` 1.05, `tertiaryContainer` 1.05; dark 1.48 / 1.54. => in light mode the tint alone is almost invisible, so the icon and a 1dp border must carry the signal (important for U0).

Counts are at the bottom.

---

## U0 — CONFIRMED (owner-requested) — AudioBadge is an in-flow child of the page Column, so every TTS/STT step shifts all text by ~32dp

Evidence: `StudyCard.kt:350-353` `CardPage` = `Column { if (audioActive) AudioBadge(listening); Text }`. `AudioBadge` = Surface(extraSmall) + Row(16dp icon + labelSmall) with `padding(h = s, v = xs)` => ~ 16 + 8 + Spacing.s(8) gap ≈ 32dp inserted above the text of the active page and removed again on `EndSpeaking`. Because `audioPageIndex` changes page0 -> null -> page1 (or 0 -> 1 directly for `SpeakAllPagesStep`), text jumps down/up on each step. Not animated (plain `if`). Two more facts that shape the design:
1. `ListenAndCheckStep` usually listens on a page that is NOT revealed yet (answer page). Then `AnimatedVisibility(visible = index in revealedPages)` hides that page and today NO indicator is shown on the card at all; the only listening cue is `AnswerPanel`'s `GraphicEq` icon. Keep that: the new indicator lives on revealed pages only. Do not reveal pages just to show the mic.
2. `audioPageIndex` may point at a page `>= activePageCount` (hidden deck page) — `face.pages` is already `take(activePageCount)`, so no page matches; behaviour unchanged.

### Design (default, = orchestrator option "B")
The page that is currently being spoken or listened to gets a rounded tinted background (`primaryContainer` when speaking, `tertiaryContainer` when listening) plus a 1dp hairline border in the matching `on*Container` colour (alpha 0.35, same pattern as `ResultChip`). A small icon (speaker / mic, 16dp) sits in a constant left gutter inside the tinted box. The text label is dropped from the screen and survives only as the icon's `contentDescription` for TalkBack. Everything geometric (gutter, vertical inset, border width) is ALWAYS present on every page; only colours/alpha and the icon's visibility change. Nothing changes layout size when the audio state toggles.

### Composables that change (all in `ui/study/StudyCard.kt`; no other file, no res/ change, no new strings)
1. `CardPages` — new outer padding/arrangement (below), derives a per-page `AudioMark`, uses a small hold helper.
2. `CardPage` — becomes the tinted Box (below). Signature changes `audioActive: Boolean, listening: Boolean` -> `mark: AudioMark`.
3. `AudioBadge` — DELETE (and the now-unused `Surface`/`Row` imports stay only if the peek Surface still uses `Surface`; `Row` import may become unused -> ktlint).
4. Add `private enum class AudioMark { NONE, SPEAKING, LISTENING }`, `private data class AudioPos(val page: Int?, val listening: Boolean)`, `heldAudio()`, constants.
5. `CardFace` unchanged (still carries `audioPage`/`listening`). `StudyCardContent` unchanged.
Unchanged on purpose: the `FlashcardView` `pointerInput`, `AnimatedContent(contentKey = card.id)` (so an audio change on the same card recomposes in place, it is not a card transition), `StudyLayout`, `AnswerPanel`.

### Constants (top of file, next to LONG_TEXT etc.)
```kotlin
private const val AUDIO_RELEASE_MS = 250L          // how long the mark is held after audio stops (anti-flicker)
private val AudioGutter = 20.dp                    // reserved left AND right (symmetric => centred text stays centred)
private val AudioGutterCompact = 16.dp
private val AudioIcon = 16.dp
private val AudioIconCompact = 14.dp
```

### CardPages — reserve the geometry (exact)
```kotlin
@Composable
private fun CardPages(face: CardFace) {
    val effects = remember { RayCollMotion.effects<Float>() }
    val spatialSize = remember { RayCollMotion.spatial<IntSize>() }
    val compact = LocalCompactCard.current
    val gap = if (compact) Spacing.s else Spacing.l          // existing: 8 / 16
    val inset = if (compact) Spacing.xs else Spacing.s       // NEW: vertical padding inside every page box: 4 / 8
    val audio = heldAudio(AudioPos(face.audioPage, face.listening))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = inset, vertical = gap - inset),   // 8/8 (portrait), 4/4 (landscape compact)
        horizontalAlignment = Alignment.CenterHorizontally
        // NO verticalArrangement = spacedBy(gap): page boxes carry gap/2 each (see arithmetic below)
    ) {
        face.pages.forEachIndexed { index, page ->
            AnimatedVisibility(
                visible = index in face.revealedPages,
                enter = fadeIn(effects) + expandVertically(spatialSize, expandFrom = Alignment.Top),
                exit = fadeOut(effects) + shrinkVertically(spatialSize, shrinkTowards = Alignment.Top)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (index > 0) {                                        // also fixes the U58 artefact
                        HorizontalDivider(Modifier.padding(vertical = Spacing.xs), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    CardPage(
                        page = page,
                        isFirst = index == 0,
                        mark = when {
                            audio.page != index -> AudioMark.NONE
                            audio.listening -> AudioMark.LISTENING
                            else -> AudioMark.SPEAKING
                        }
                    )
                }
            }
        }
        // peek block: keep the old gap above it, but INSIDE the animated content (an invisible AnimatedVisibility must not reserve space)
        val peeked = face.peekedPage?.takeIf { it !in face.revealedPages }?.let(face.pages::getOrNull)
        AnimatedVisibility(visible = peeked != null, enter = fadeIn(effects) + expandVertically(spatialSize), exit = fadeOut(effects) + shrinkVertically(spatialSize)) {
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.padding(top = inset)) { /* Text as today */ }
        }
    }
}
```
Spacing arithmetic (text-to-text distance between two revealed pages): today = `spacedBy(gap)` + divider(4+1+4) = gap + 9 = 25dp (portrait) / 17dp (compact). New = page0 bottom inset + divider 9 + page1 top inset = 2*inset + 9 = 25dp / 17dp. IDENTICAL. Top of first text from card top: today `gap` (16/8); new `(gap - inset) + inset` = 16/8. IDENTICAL. Only deliberate visual deltas: (a) bottom whitespace under the last text is `gap` (16/8) instead of today's 32-48dp, because `Arrangement.spacedBy(gap)` also spaced the zero-height invisible `AnimatedVisibility` children (known gotcha: hidden pages + the peek slot each added a phantom 16dp) — card is up to ~32dp shorter while pages are hidden; (b) text column is 24dp narrower (portrait: horizontal inset 8 + gutter 20 = 28 per side vs 16 today; compact: 4 + 16 = 20 vs 8) because of the reserved gutters. Check on the phone with a 20-25 char single-word term at 28sp (the longest "short" case); if it now wraps where it did not, reduce `AudioGutter` to 18dp.

### CardPage (exact)
```kotlin
@Composable
private fun CardPage(page: String, isFirst: Boolean, mark: AudioMark) {
    val compact = LocalCompactCard.current
    val colors = MaterialTheme.colorScheme
    val effects = remember { RayCollMotion.effects<Color>() }
    val gutter = if (compact) AudioGutterCompact else AudioGutter
    val iconSize = if (compact) AudioIconCompact else AudioIcon
    val inset = if (compact) Spacing.xs else Spacing.s
    val shape = if (compact) MaterialTheme.shapes.extraSmall else MaterialTheme.shapes.medium   // 8dp / 16dp

    // last non-NONE mark: icon + hue keep their value while the tint fades out (no flash to the wrong colour)
    var lastMark by remember { mutableStateOf(AudioMark.SPEAKING) }
    LaunchedEffect(mark) { if (mark != AudioMark.NONE) lastMark = mark }
    val shown = if (mark != AudioMark.NONE) mark else lastMark
    val listening = shown == AudioMark.LISTENING
    val container = if (listening) colors.tertiaryContainer else colors.primaryContainer
    val onContainer = if (listening) colors.onTertiaryContainer else colors.onPrimaryContainer
    // inactive = SAME hue at alpha 0 (not Color.Transparent, which would sweep through black in Oklab)
    val tint by animateColorAsState(
        targetValue = if (mark == AudioMark.NONE) container.copy(alpha = 0f) else container,
        animationSpec = effects,
        label = "audio_tint"
    )

    // text size + line height (U6 fix is applied here too — same Text)
    val size = pageFontSize(page, compact)                 // TextUnit, see U6
    val lineHeight = size * 1.3f
    val firstLine = with(LocalDensity.current) { lineHeight.toDp() }

    Box(
        Modifier
            .fillMaxWidth()
            .background(tint, shape)
            .border(1.dp, onContainer.copy(alpha = 0.35f * tint.alpha), shape)   // border is ALWAYS laid out (draw-only), alpha 0 when idle
    ) {
        // absolutely positioned inside the left gutter; contributes NO size (its bottom edge is always above the text's bottom edge)
        AnimatedVisibility(
            visible = mark != AudioMark.NONE,
            enter = fadeIn(effects), exit = fadeOut(effects),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = (gutter - iconSize) / 2, top = inset + (firstLine - iconSize) / 2)
        ) {
            Icon(
                imageVector = if (listening) Icons.Filled.Mic else Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = stringResource(if (listening) R.string.study_badge_listening else R.string.study_badge_speaking),
                tint = onContainer,
                modifier = Modifier.size(iconSize)
            )
        }
        Text(
            text = page,
            style = if (isFirst) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
            fontSize = size,
            lineHeight = lineHeight,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = gutter, vertical = inset)     // CONSTANT — this is what "reserves" the space
        )
    }
}

@Composable
private fun heldAudio(now: AudioPos): AudioPos {
    var held by remember { mutableStateOf(now) }
    LaunchedEffect(now) {
        if (now.page == null) delay(AUDIO_RELEASE_MS)   // hold the previous mark through short gaps (EndSpeaking -> StartSpeaking)
        held = now
    }
    return if (now.page != null) now else held         // appearing is immediate, only disappearing is delayed
}
```
Notes on the modifiers: `background(tint, shape)` + `border(...)` + `AnimatedVisibility` icon + `Text` padding are the ONLY layout-relevant parts, and none of them depends on `mark`. `tint` is animated per frame during ~150ms (spring, stiffness 1600) -> only this tiny composable recomposes. Reading order for TalkBack follows layout (Text starts above the icon, so "<page text>, Speaking"); if the owner wants "Speaking" first add `Modifier.semantics { traversalIndex = -1f }` to the icon and `isTraversalGroup = true` to the Box (not needed by default).

### Semantics / TalkBack
- Icon `contentDescription` = existing strings `study_badge_speaking` / `study_badge_listening` (all 5 locales already exist; I18nConsistencyTest unaffected, no res change).
- The tinted Box and the Text have NO extra semantics; the Icon is only in the tree while `mark != NONE` (so "Speaking" disappears from the a11y tree 250ms+fade after audio stops).
- NO `liveRegion` on the indicator on purpose: a polite live region would make TalkBack speak "Speaking" over the card's own TTS, and "Listening" through the speaker exactly when the mic opens (TalkBack's voice would be picked up by STT). Announcement of results is handled by U14 (and only when not listening).
- Colour is not the only cue: speaker vs mic icon differ in shape; border adds a non-colour edge.

### Landscape compact card (`LocalCompactCard == true`, font x0.72)
Same structure, compact constants: `gutter 16dp`, `icon 14dp`, `inset 4dp`, `shape = shapes.extraSmall (8dp)`, outer padding `horizontal 4 / vertical 4`. Heights: text >= 18sp*0.72*1.3 = 16.8sp + 2*4 = ~25dp; icon box = 4 + (16.8-14)/2 + 14 = 19.4dp < 25 => the icon can never grow the Box. Single-page card height identical to today (outer 4 + inset 4 = 8 = old `gap`). The side prev/next arrows and `StudyActions` row are untouched. Scroll behaviour unchanged (the `LaunchedEffect` in `StudyCardContent` is keyed on `revealedPages.size`, not on audio state, so audio changes cannot trigger a scroll).

### Tests (app/src/test/.../ui/study/StudyCardTest.kt — extend, it already has `active()` and `noActions`)
1. `audio indicator never moves page text` — render `SessionState(cards = listOf(card), revealedPages = listOf(0, 1), status = IDLE)`; capture `onNodeWithText("front", useUnmergedTree = true).getUnclippedBoundsInRoot()` and the same for "back"; then in a loop set `status = SPEAKING, audioPageIndex = 0` / `SPEAKING, 1` / `LISTENING, 1`, `waitForIdle()`, assert both DpRects equal to the idle ones (exact). (Do NOT use page 1 hidden here; listening on a hidden page shows nothing.)
2. `audio indicator is exposed to TalkBack` — `onNodeWithContentDescription("Speaking", useUnmergedTree = true).assertExists()` for SPEAKING, `"Listening"` for LISTENING (`en` default locale in Robolectric).
3. `audio indicator is held briefly and then removed` — `mainClock.autoAdvance = false`; go SPEAKING -> IDLE; `advanceTimeBy(100)` -> still exists; `advanceTimeBy(600)` -> `assertDoesNotExist()`.
4. `@Config(qualifiers = "w800dp-h360dp-land")` variant of test 1 (compact card). Verify `LocalCompactCard` is active by also asserting the text top offset == 8dp from card top is NOT required; the equality-with-idle assertion is enough.
5. Existing tests (`reveal button...`, `long page text ... 15 lines`, `answer of the previous card...`) must stay green: they query text and layout lines only; width of the text shrinks by 24dp but the 15 explicit `\n` lines do not wrap.

Size M (one file, ~80 changed lines, no other layer). Risk: low-medium — only a visual regression risk (text 24dp narrower; card ~16-32dp shorter at the bottom); no logic touched. CROSS-LAYER: no. Combine with U6, U58 (same lines).

---

## U1 — CONFIRMED — SessionSummary is not scrollable; in landscape the buttons are clipped
Evidence: `SessionSummary.kt:132` `Box(modifier.padding(xl), center) { RayCollCard { Column(padding xl) } }`, no scroll. Height estimate in landscape (Pixel 10 Pro, ~360dp - 64 top bar): headline 36 + desc 24-48 + bar 8 + 2 stat lines 40 + 2-3 buttons (40 each) + 7 gaps x12 + padding 2x24 + 2x24 (outer) ≈ 400-450dp > ~300dp available. Failed-cards button makes it worse. Bug is real (owner uses landscape).
Fix (S): replace the root with a scroll-safe centred layout:
```kotlin
BoxWithConstraints(modifier) {
    Column(
        Modifier.fillMaxWidth().heightIn(min = maxHeight).verticalScroll(rememberScrollState()).padding(Spacing.xl),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
    ) { RayCollCard(...) { Column(Modifier.padding(Spacing.xl)...) { ...unchanged } } }
}
```
Optionally use `Spacing.l` padding in landscape (`LocalConfiguration`). Test (new `SessionSummaryTest` or in StudyCardTest): `@Config(qualifiers = "w800dp-h360dp-land")`, render finished state with failed cards, `onNodeWithText("Back to dashboard").performScrollTo().assertIsDisplayed()`. Size S, risk low. Not CROSS-LAYER.

## U2 — CONFIRMED — PL rating labels "Powtórz (Again)" etc. are English-suffixed and ellipsised; fixed 68dp height clips at large font
Evidence (verified): `res/values-pl/strings.xml:42-45` = "Powtórz (Again)", "Trudne (Hard)", "Dobrze (Good)", "Łatwe (Easy)"; de/es/it have clean one-word labels. 4 buttons on 360dp: (360-32-3x8)/4 = 76dp, minus 2x4dp padding = 68dp for ~15 chars at 12sp (≈93dp) => `maxLines = 1` ellipsis ("Powtórz (A…"). Same labels leak into `AnswerPanel` badge via `ratingLabel()` ("Powtórz (Again) (+1)"). `.height(68.dp)` clips at font scale 2.0 (16 + 22 + 4 + 2x~16sp).
Fix (S, CROSS-LAYER res for the PL strings): PL: "Powtórz" / "Trudne" / "Dobrze" / "Łatwe". Code in `RatingButtons.kt`: `Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), spacedBy(s))`, each Button `Modifier.weight(1f).fillMaxHeight().heightIn(min = RatingButtonHeight)` (replace `.height(68.dp)`), label `maxLines = 2`, `textAlign = Center`, `overflow = Ellipsis`; export `internal val RatingButtonHeight = 68.dp` (also used by U9). Test: add to StudyCardTest a RatingButtons render with `@Config(fontScale = 2.0f)` asserting all four labels `assertIsDisplayed()`.

## U3 — CONFIRMED (duplicate of U0) — see U0.

## U4 — CONFIRMED — pastel `*Bg` colours for bars are ~1.0-1.3:1 against track/card; future segments (alpha .25) vanish; no a11y description
Evidence: measured ratios in the header (e.g. light "new" D4E3FF vs track E6E0E9 = 1.00). `SegmentedBar` is used for deck stats (Dashboard, Stats, Browse, Summary) and session progress. Bars carry meaning (card categories) but are effectively invisible in light mode and dim in dark.
Fix (S-M): bars use the `*Fg` colours (they are the "strong" variant of each category and already 6-9:1): `CardStatsBar` -> `srs.newFg/learningFg/reviewFg/masteredFg`; `SessionSegments`/`categoryColor` -> same `*Fg`. Alphas in `SessionSegments`: current 1.0, completed 0.6, upcoming 0.22 (see U15). Chips/badges keep `*Bg` + `*Fg` text. Add semantics in `SegmentedBar` (see U21). Tests: update `StudyCardTest."progress bar retains visible card segments with 150 cards"` — it compares pixels to `LocalSrsColors.current.newBg`; change to `newFg`. Look change: bars become more saturated (visible design delta; owner should eyeball on phone). Size S-M. Not CROSS-LAYER.

## U5 — CONFIRMED — CollectEvents handler suspends on showSnackbar, later events wait
Evidence: `UiEvents.kt:55` `events.collect { handler.value(it) }` is sequential; `showSnackbar` suspends until dismissed (Short 4s, Long 10s for `ReviewSaveFailed`). Concrete cases: BrowseScreen — `SaveFailed` snackbar, user fixes and presses Save again -> `Saved` (which closes the dialog) waits up to 4s; StudyScreen — a 10s `ReviewSaveFailed` snackbar blocks a following `SpeechError(TTS_MISSING_VOICE)` dialog. `SnackbarHostState.showSnackbar` already queues internally (Mutex), so concurrent handlers are safe.
Fix (S): in `CollectEvents`:
```kotlin
lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
    val scope = this
    events.collect { event -> scope.launch(start = CoroutineStart.UNDISPATCHED) { handler.value(event) } }
}
```
UNDISPATCHED keeps ordering for non-suspending handlers (`editTarget = null`), suspending ones continue in their own child coroutine; children are cancelled on STOPPED exactly like before. Test (new `UiEventsTest` Robolectric compose): handler 1 suspends on a `CompletableDeferred`, send event 2, assert event 2's side effect happened. Size S, risk low-medium (ordering semantic) — run all ViewModel/Screen tests (`SaveFailureTest`, `DialogStateRestorationTest`). Not CROSS-LAYER (UiEvents is ui/components).

## U6 — CONFIRMED — fontSize overridden to 28/24/20/18sp but lineHeight stays from titleLarge/bodyLarge
Evidence: `Type.kt` `sized()` scales lineHeight with the *style's* size: titleLarge = 20sp/25.45sp, bodyLarge = 16sp/24sp. `StudyCard.kt:356` overrides only `fontSize`. Result line-height/font ratios: 28sp on 25.45 = 0.91, 24sp on 24 = 1.0 (multi-line titles overlap/cramped); 18sp on 24 = 1.33 (fine). Real for any wrapped first page >1 line.
Fix (S, inside the U0 edit of `CardPage`): extract
```kotlin
private fun pageFontSize(page: String, compact: Boolean): TextUnit {
    val base = when { page.length > LONGEST_TEXT -> 18.sp; page.length > LONGER_TEXT -> 20.sp; page.length > LONG_TEXT -> 24.sp; else -> 28.sp }
    return if (compact) base * COMPACT_FONT_SCALE else base
}
```
and pass `fontSize = size, lineHeight = size * 1.3f` (shown in U0). Test: add `pageFontSize` unit test (thresholds 70/140/260 and compact scale) + keep `long page text ... 15 lines`. Also covers U54 (named function instead of tokens).

## U7 — CONFIRMED — animateScrollTo(maxValue) runs before the newly revealed page has expanded
Evidence: `StudyCard.kt:110-112` `LaunchedEffect(revealedPages.size, currentCardIndex) { scrollState.animateScrollTo(scrollState.maxValue) }` — target is `maxValue` at launch time (before the `expandVertically` of the page / AnswerPanel runs, ~400ms spring), so for a card taller than the viewport it scrolls to the OLD bottom. In landscape (viewport ~150-180dp) the 3rd page is routinely below the fold. On a card change the same effect scrolls the new card to its bottom, also unintended (a long first page should start at the top). Short cards that fit are unaffected, which is why it went unnoticed. (Not device-verified; follows from Compose semantics.)
Fix (S):
```kotlin
LaunchedEffect(session.revealedPages.size, session.currentCardIndex) {
    if (session.revealedPages.size <= 1) {
        scrollState.scrollTo(0)                                  // fresh card starts at the top
    } else {
        withTimeoutOrNull(REVEAL_FOLLOW_MS /* 700 */) {          // follow the growth of the animating page
            snapshotFlow { scrollState.maxValue }.collectLatest { scrollState.animateScrollTo(it) }
        }
    }
}
```
Risk: fights a user who scrolls manually during the 700ms — acceptable. Test: with `mainClock.autoAdvance = false`, reveal the 3rd page of a tall card in a small viewport (`@Config(qualifiers = "w800dp-h360dp-land")`), advance 800ms, assert the last page `assertIsDisplayed()` without `performScrollTo`. Size S.

## U8 — CONFIRMED — paused banner is a plain Row inserted above the progress bar; the whole card jumps ~48dp on pause/resume
Evidence: `StudyScreen.kt:147-155` `if (state.paused) Row { Text; TextButton }` inside the Column -> TextButton min height 48dp shifts progress bar + card. Pause is triggered often (headset button, focus loss).
Fix (S) — zero layout change: move it into the app bar actions (ScreenScaffold already has `actions: RowScope`):
```kotlin
actions = {
    AnimatedVisibility(active?.paused == true, enter = fadeIn(), exit = fadeOut()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.study_paused), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = viewModel::resume) {
                Icon(Icons.Filled.PlayArrow, null, Modifier.size(ButtonDefaults.IconSize)); Spacer(Modifier.width(Spacing.xs)); Text(stringResource(R.string.study_resume))
            }
        }
    }
}
```
Title (deck name) already has `maxLines = 1, Ellipsis`. No new strings (`study_paused`, `study_resume` exist). Remove the old Row. Tests: `StudyRestartTest` tests the ViewModel `paused` flag only -> unaffected; optionally add a compose test that Resume exists when `paused = true`. Size S.

## U9 — CONFIRMED — controls slot toggles 68 / 48 / Spacer 48 -> card viewport resizes by 20dp
Evidence: `StudyCard.kt:229-241` + `RatingButtons.kt:70`. Cards are top-anchored so text does not jump, but the viewport edge (and `maxValue`) moves 20dp on every reveal -> rating, and `AnimatedContent`'s size animation visibly resizes the slot.
Fix (S): give the `AnimatedContent` modifier a constant minimum height: `modifier = modifier.heightIn(min = RatingButtonHeight)` (shared constant from U2), and render `TextButton`/`Spacer` centred in it (`contentAlignment = Center` already). Landscape: same constant (68dp slot, side arrows centred) — costs ~20dp of viewport permanently but removes the jump; if too tight on the phone use 56dp only for landscape via `if (landscape) 56.dp else 68.dp` (then RatingButtons min height must follow). Size S.

## U10 — PARTIAL — rating colours reuse card-category colours; interval line / haptics suggested
Real: scale order is red / yellow / orange / green (Hard=`learningBg` yellow, Good=`reviewBg` orange) — not monotone, and Good looks like "warning". Cheap fix (S): Again = `errorContainer`, Hard = `learningBg/Fg` (amber, unchanged), Good = `primaryContainer/onPrimaryContainer`, Easy = `masteredBg/Fg`. Haptic on tap (S): `LocalHapticFeedback.current.performHapticFeedback(HapticFeedbackType.ContextClick)` in each onClick. NOT_WORTH: "interval under label" (needs FSRS preview from domain, CROSS-LAYER domain+StudyViewModel, speculative feature).

## U11 — PARTIAL — 12sp rating labels (real, fix with U2); 11sp badges are M3 default (leave)
After U2 shortens PL labels, switch the rating label style to `labelLarge` (14sp): "Powtórz" ≈ 52dp fits 68dp; at font scale > 1.3 it falls back to the 2-line/ellipsis rules of U2. `labelSmall` badges stay (decorative / M3 default). Size S, same edit as U2.

## U12 — CONFIRMED — study area `clickable(indication = null)` has no role/onClickLabel; AnswerPanel clickable no role
Evidence: `StudyCard.kt:163-169`; `AnswerPanel.kt:291`. TalkBack announces an unlabeled full-screen "double-tap to activate".
Fix (S): `clickable(interactionSource, indication = null, onClickLabel = stringResource(R.string.study_a11y_tap_card), role = Role.Button, onClick = actions.onTap)`; AnswerPanel `clickable(role = Role.Button, onClick = onTap)`. New string `study_a11y_tap_card` ("Continue" / PL "Dalej" / de "Weiter" / es "Continuar" / it "Continua") x5 locales => CROSS-LAYER (res/ fixer). Hold-to-peek has no a11y equivalent by design (peek is a convenience; tapping reveals the same content). I18nConsistencyTest will check parity.

## U13 — CONFIRMED — mic permission denied: only a generic STT-failure snackbar, no path to settings
Evidence: `StudyScreen.kt:214-229` requests RECORD_AUDIO; denial is ignored; first listen step then fails -> `SessionError.STT` -> snackbar "Speech recognition failed. Check microphone access." with no action (and permanently-denied users can never get the system prompt again).
Fix (S, no new strings): in the `SessionError.STT` branch, if `checkSelfPermission(RECORD_AUDIO) != GRANTED` show the snackbar with `actionLabel = resources.getString(R.string.app_settings_title)` and on `ActionPerformed` `runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())) }`. Works properly only with U5 (otherwise it queues behind other snackbars). Size S.

## U14 — CONFIRMED — answer result not announced; no liveRegion anywhere
Fix (S): on the `Surface` in `AnswerPanel` add `Modifier.semantics { if (!listening) liveRegion = LiveRegionMode.Polite }` — NOT while `listening` (partial STT text updates several times/sec would be read out by TalkBack and picked up by the mic). Also `role = Role.Button` (U12). Test: compose test asserting `SemanticsProperties.LiveRegion` present when `listening = false`, absent when `true`. Size S.

## U15 — PARTIAL — current segment alpha 1.0 vs completed 0.85 indistinguishable; gap 0 above 30 cards
Real but minor (counter "n/N" next to the bar shows the position). Fold into U4: current 1.0, completed 0.6, upcoming 0.22. Leave `gap = 0` above 30 cards (otherwise segments get sub-pixel).

## U16 — CONFIRMED (cheap) — SessionSegments rebuilds the segment list + getCardCategory for every card on every state emission
Evidence: `SessionSummary.kt:227-243`; `SessionProgressBar(state.session)` recomposes on every `SessionState` change, incl. `UpdatePartialStt` (several/sec while listening). 150 allocations per emission is cheap, but it is trivially avoidable.
Fix (S): `val segments = remember(session.cards, session.currentCardIndex, srs) { ... }`; pass to `SegmentedBar`. Keep `SessionProgressBar(session: SessionState)` signature (test uses it). Size S.

## U17 — PARTIAL — computeCardStats(it) in composition
Negligible cost (summary recomposes rarely) but one-line fix: `val stats = remember(state.resultCards) { state.resultCards?.let(::computeCardStats) }`. Do it while editing U1. Size S.

## U18 — CONFIRMED — `height(40.dp)` on Browse/Study buttons => 40dp touch targets
Evidence: `DashboardScreen.kt:207-209,226-228` and `StudySplitButton.kt` (Row heights from the parent's 40dp, arrow `widthIn(min = 40.dp)`). `.height(40.dp)` on a Material Button defeats `minimumInteractiveComponentSize` (constraints clamp it back to 40).
Fix (S): replace both `.height(40.dp)` with `.heightIn(min = 48.dp)` (buttons stay vertically centred, visual height 48, consistent with each other); `StudySplitButton`: `widthIn(min = 48.dp)` for the arrow. Visual delta: deck card +8dp taller. Size S.

## U19 — CONFIRMED — Study button is primary even when dueCount == 0 (leads to NoDueCards dead end)
Evidence: `DashboardScreen.kt:220-229`, `DeckSummary.dueCount` available. Fix (S): pass `hasDue: Boolean` to `StudySplitButton`; when false use `colors.secondaryContainer / onSecondaryContainer` (still clickable — NoDueCards explains why; no hard disable). Size S. Not CROSS-LAYER.

## U20 — CONFIRMED — empty dashboard text says "Click + to add one"; FAB only imports; button redundant
Evidence: FAB `onClick = onImport` (DashboardScreen:101-110); en "No decks found. Click + to add one."; PL "Kliknij +", IT "Clicca su +" (de/es already "Tippe"/"Toca"). "Click" is wrong for touch and "+" does not "add a deck", it opens Import.
Fix (S, CROSS-LAYER res): `dashboard_no_groups` en "No decks yet. Import flashcards to get started." / pl "Brak zestawów. Zaimportuj fiszki, aby zacząć." / de "Noch keine Decks. Importiere Karteikarten, um zu starten." / es "Aún no hay mazos. Importa tarjetas para empezar." / it "Nessun mazzo. Importa le carte per iniziare." Keep the "Import Flashcards" button in the empty state (primary CTA); FAB stays (also hidden duplication is fine). No code change.

## U21 — PARTIAL — per-deck bars have no numbers/legend/contentDescription
Real for a11y and for Stats per-deck cards. Fix (S): in `SegmentedBar` add optional `description: String? = null` -> `Modifier.semantics { contentDescription = description }`; `CardStatsBar` builds it from existing strings: `listOf(srs_badge_new to newCount, ...).joinToString(", ") { stringResource(R.string.filter_chip_count, stringResource(label), count) }` (no new strings). Stats screen per-deck `SectionCard`: render `CardStatsBar` + the existing `SrsDistribution` row (reuse, makes numbers visible); Dashboard keeps the bare bar (glanceable; legend lives in Stats). NOT_WORTH: adding a legend on every Dashboard card.

## U22 — PARTIAL — permanent edit/delete row per card
Real: the 48dp row is ~35% of each card and delete sits next to edit (mitigated: delete asks for confirmation). Swipe-to-delete + Undo is a speculative redesign -> NOT_WORTH. Default design (M): make the card body tappable = edit; remove the Edit icon; keep one Delete `IconButton` at the top-end of the card in a Row with the page texts (`Row { Column(weight 1f) { pages }; IconButton(delete) }`), keeping `ConfirmDialog(destructive)`. Needs `RayCollCard` onClick (U53). A11y: card `onClickLabel = browse_edit_card`. Size M, risk low-medium. Not CROSS-LAYER.

## U23 — PARTIAL — Browse header is pinned (~130-160dp); landscape list is tiny; stats bar duplicates chip counts
Real in landscape (header ≈ 24 + 56 + 48 = 128dp + 64 top bar of ~360dp). Fix (M): when the list is non-empty move stats bar + search field + chips into the `LazyColumn` as `item(key = "header")`; keep the current `Column` layout for the empty-result branch (EmptyState uses `fillMaxSize`, cannot live in a LazyColumn item). `BrowseSearchFieldTest` finds `hasSetTextAction` at list top, so it stays valid; verify after change. Alternative minimal (S): hide `CardStatsBar` in `Browse` (duplicates chips). Pick the first.

## U24 — CONFIRMED — same "No flashcards found." for an empty deck and for no filter results
Fix (S, CROSS-LAYER res): if `state.counts.total == 0` show `EmptyState(stringResource(R.string.browse_empty_deck), action = { Button(onClick = onAdd) { Text(stringResource(R.string.browse_add_card)) } })` else keep `browse_no_cards`. New string `browse_empty_deck` ("This deck has no flashcards yet." / "Ten zestaw nie ma jeszcze fiszek." / de "Dieses Deck hat noch keine Karten." / es "Este mazo aún no tiene tarjetas." / it "Questo mazzo non ha ancora carte.") x5. Needs an `onAdd` callback into `BrowseContent`.

## U25 — PARTIAL — no per-card SRS info; no maxLines on page text
Add `maxLines = 4, overflow = Ellipsis` to the Browse page `Text`s (S; full text in the editor on tap). Per-card SRS badge is a feature request -> NOT_WORTH.

## U26 — CONFIRMED — card editor: scrim tap discards the draft; Save silently disabled; no imeAction/autofocus
Evidence: `CardEditorDialog.kt` `AlertDialog(onDismissRequest = onDismiss)`; draft lives in `rememberSaveable` inside the dialog => removed with the dialog (tests only check rotation + Cancel). Up to 20 fields in an AlertDialog is OK (it scrolls) — full-screen editor redesign is NOT_WORTH.
Fix (S): `properties = DialogProperties(dismissOnClickOutside = false)` (Back/Cancel still dismiss); every field `keyboardOptions = KeyboardOptions(capitalization = Sentences, imeAction = if (last) Done else Next)`; autofocus first field via `FocusRequester` + `LaunchedEffect(Unit)` only for the NEW-card dialog. Disabled-Save hint needs a new string (skip; if wanted, reuse nothing -> CROSS-LAYER). Test: `DialogStateRestorationTest` unchanged.

## U27 — PARTIAL — PersistedTextField blur flicker, isError without text, dead KeyboardOptions
Evidence: `PersistedTextField.kt:32-34` on blur `text = storedValue` immediately; the per-keystroke write echo trails by one write, so the last char can flash back. Real but ~tens of ms. `isError = isError(text)` shows a red outline with no explanation (DeckSettings deck name blank). `KeyboardOptions.Default` is a no-op.
Fix (S): blur effect `if (!focused) { delay(200); text = storedValue }`; add `errorText: String? = null` -> `supportingText = { if (error && errorText != null) Text(errorText) }`; delete the `keyboardOptions` line + import. CROSS-LAYER: `DeckSettingsScreen.kt:123` (ui-settings/decksettings fixer) passes `errorText = stringResource(R.string.import_error_name_required)` (string already exists in 5 locales).

## U28 — WRONG — "side strips use window colour; Box has no background"
Evidence: `MainActivity.kt` wraps `RayCollNavHost()` in `Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background)` and `NavGraph.kt:69` paints `.background(colorScheme.background)`, so the strips outside the 640dp Scaffold are the theme background (also for in-app forced light/dark). 640dp max width incl. the top bar is the verified/tested design (`ScreenScaffoldWidthTest`). A `scrollBehavior` on the TopAppBar is a nicety, not a defect. No change; `ScreenScaffoldWidthTest` stays as is.

## U29 — PARTIAL — dynamic colour: fixed SRS hues; scheme rebuilt on recomposition
Fixed SRS hues are deliberate semantic colours (INTENTIONAL, comment + `LocalSrsColors`). The scheme is rebuilt only when `RayCollTheme` recomposes (pref changes) — EDGE perf, but a one-liner readability fix: `val colorScheme = remember(theme, isDark, useSystemColors, context) { ... }` (S). Note: with dynamic colour on (Pixel 10 Pro default) the audio tint (U0) uses the dynamic containers, still guaranteed on*Container pairs.

## U30 — CONFIRMED — "Good (+3)" looks like points
Evidence: `study_suggested_rating` = "%1$s (+%2$d)" where %2$d is the rating number (Again=1..Easy=4).
Fix (S, CROSS-LAYER res): format -> "Suggested: %1$s" / pl "Sugestia: %1$s" / de "Vorschlag: %1$s" / es "Sugerencia: %1$s" / it "Suggerito: %1$s"; in `AnswerPanel.kt:92,116-120` pass only the label (drop the rating argument). Test: none needed (string); I18nConsistencyTest still passes (same key).

## U31 — PARTIAL — Loading title ""; exit confirm vague
Exit dialog: real — `study_exit_confirm_message` doesn't say ratings are saved while `study_session_ended_desc` does. Fix (S, CROSS-LAYER res): en "Your ratings so far are saved. Leave this study session?" / pl "Dotychczasowe oceny są zapisane. Opuścić sesję nauki?" (+ de/es/it). Empty title during Loading: only visible for a frame and interacts with the shared-title transition; fixing needs the deck name from `StudyViewModel` (other fixer's area) -> skip.

## U32 — CONFIRMED — PL terminology/wording (verified)
Evidence: `values-pl/strings.xml`: "zestaw*" 17 occurrences vs "talia/talie/talii" 3 (lines 16, 244, 247); `dashboard_due_count` "Pozostało: %1$d" (en "Due", i.e. due/do powtórki, not remaining); `study_finished_desc` "…zostały opanowane" (cards were reviewed, not mastered); `study_session_ended_desc` "Wyszedłeś" gendered.
Fix (S, res only, CROSS-LAYER): use "zestaw" everywhere (L16 "Masz lokalne zestawy…", L244 "…usunąć zestawu.", L247 "Wszystkie zestawy, postępy i tryby…"); `dashboard_due_count` "Do powtórki: %1$d"; `study_finished_desc` "Wszystkie zaplanowane na dziś fiszki zostały powtórzone."; `study_session_ended_desc` "Sesja przerwana przed ukończeniem. Twój postęp jest zapisany."

## U33 — NOT_WORTH — mixed Title Case (en), duplicate string, emoji
Title Case is a stylistic choice across dozens of strings (churn). `study_answer_retry` is NOT a duplicate of `errors_retry` in PL ("Spróbuj jeszcze raz" vs "Spróbuj ponownie") — different meaning (retry answer vs retry operation). Emoji in `study_bravo` is deliberate (all locales).

## U34 — CONFIRMED — MigrationDialog: consequences unclear
Evidence: `MigrationController` — MIGRATE copies guest data to the account (guest copy stays), FRESH starts the account from sample decks (guest data stays untouched on device), CANCEL signs out. The dialog never says guest data is kept.
Fix (S, CROSS-LAYER res): `auth_migration_desc` en: "You have local decks from a previous session. Transfer them to your account, or start the account with the sample decks. Your local decks stay on this device either way." (+ pl/de/es/it); relabel `auth_migration_fresh_btn` "Start with sample decks". No code change.

## U35 — INTENTIONAL — peek text uses `outline` (3.45:1)
It is a deliberately dim "ghost preview" of an unrevealed page (`StudyCard.kt` peek Surface, `surfaceContainerHighest`). Discoverability of hold-to-peek is an onboarding feature, not a defect. Leave.

## U36 — NOT_WORTH — short card sticks to the top
Top anchoring is what keeps the card stable when pages / AnswerPanel appear below it (and matches the U0 "nothing moves" goal). Centering vertically would make the card jump up on every reveal.

## U37 — NOT_WORTH — Next skips without undo; swipe gestures
"Previous card" button already is the undo (`goToPreviousCard`); swipe gestures are a feature.

## U38 — CONFIRMED (cosmetic) — predictive-back progress snaps back instantly on cancel
Evidence: `StudyScreen.kt:92-104` `finally { backProgress = 0f }`. Fix (S): hold progress in an `Animatable(0f)`; in `collect { anim.snapTo(event.progress) }`; in `finally { withContext(NonCancellable) { anim.animateTo(0f, RayCollMotion.spatial()) } }`; `graphicsLayer` reads `anim.value`. Low priority.

## U39 — CONFIRMED (dead code) — imePadding in StudyScreen
No text field on the study screen. Remove `.imePadding()` at `StudyScreen.kt:129` (+ import). S.

## U40 — CONFIRMED — missing-voice AlertDialog duplicates ConfirmDialog
`StudyScreen.kt:192-209` equals `ConfirmDialog(title, text, confirmLabel = study_missing_voice_btn, onConfirm = {startActivity}, onDismiss)` (cancel button text = btn_cancel in both). Replace; S, -12 lines.

## U41 — PARTIAL — StudySplitButton a11y
Real: arrow `Icon(contentDescription = study_modes_title)` + `onClickLabel = study_modes_title` => TalkBack reads "Study modes, button, double-tap to Study modes". Fix (S): drop `onClickLabel`, keep the icon description. Menu items: add `Modifier.semantics { selected = mode.id == activeModeId; role = Role.RadioButton }` on each `DropdownMenuItem` (the `RadioButton(onClick = null)` exposes no state). "Disabled arrow no cue": `modes.isNotEmpty()` is always true (seeded built-in modes) -> EDGE, skip.

## U42 — PARTIAL — deck card not tappable; same "Deck options" description for every deck
Tappable card = redesign, NOT_WORTH (two explicit buttons exist). A11y part real: ten identical "Deck options" buttons. Fix (S, CROSS-LAYER res): new `a11y_deck_menu_named` "Options for %1$s" x5 and `DeckMenu(deckName, ...)`.

## U43 — PARTIAL — FAB pops without animation; sync state only in Settings
FAB: wrap in `AnimatedVisibility(visible = uiState is Ready, enter = scaleIn(spatial) + fadeIn, exit = scaleOut + fadeOut)` (S). Sync state on the dashboard is a feature (NOT_WORTH).

## U44 — PARTIAL — Archive: restore feedback, alarming red, explainer
Colour: use `error` only when `daysLeft <= 3`, else `onSurfaceVariant` (S). Restore: the card vanishing is the feedback; a snackbar with Undo needs new strings (skip). Explainer: the archive confirm dialog already states the retention (INTENTIONAL).

## U45 — EDGE — `archiveDaysLeft(...) ?: 0` shows "Deletes today" when the date is missing
`DeckDao.archivedDecks` selects `archivedAt IS NOT NULL`, so null cannot occur; `?: 0` is only a type fallback.

## U46 — PARTIAL — SrsDistribution SpaceBetween overlaps at large font
Real at fontScale >= 1.5 / long DE words (4 fixed-width chips in ~296dp). Fix (S): `FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = spacedBy(Spacing.s))` instead of `Row(SpaceBetween)`. "Rendered 3 ways" (bar, chips, filter chips) is a consolidation idea -> NOT_WORTH.

## U47 — NOT_WORTH — heatmap labels/legend/scroll affordance
Feature additions (localised month/weekday text drawn in Canvas, legend); the recomputation cost is negligible (`values.sum()` over ≤ ~140 entries). Heatmap already has a contentDescription and starts scrolled to the newest week.

## U48 — NOT_WORTH — Stats empty state
App seeds sample decks; a "new user" does see zeros only until the first review. Not worth new strings/composables.

## U49 — PARTIAL — States.kt
`ErrorState.onRetry` is never passed (verified: 3 call sites, none pass it) -> remove the parameter and `retryAction` (S, dead code). Deck-missing screens have the top-bar back arrow as the exit (INTENTIONAL). Bare spinner: fine.

## U50 — PARTIAL — SyncStatusBanner
Add `Modifier.semantics { liveRegion = LiveRegionMode.Polite }` on the Surface (S). Rotating sync icon = cosmetic -> NOT_WORTH. (Used only in SettingsSections.kt — no cross-layer change needed.)

## U51 — CONFIRMED — LanguagePicker: "EN" chip, current language not marked, no description
Fix (S, no new strings): AssistChip `modifier = Modifier.semantics { contentDescription = Locale.forLanguageTag(language).displayName }`; menu items `leadingIcon = { if (option == language) Icon(Icons.Filled.Check, null) else Spacer(Modifier.size(24.dp)) }` and `Modifier.semantics { selected = option == language }`. Component API unchanged (used by decksettings/PageRow).

## U52 — INTENTIONAL — ModeListItem restore disabled / delete next to edit
"Restore defaults disabled for unmodified modes" is listed as verified-on-phone behaviour and has a comment (`ModeListItem.kt:356`); delete goes through a confirm dialog. No change.

## U53 — PARTIAL — RayCollCard
Shadow style is subjective (app-wide look change) -> NOT_WORTH. Add `onClick: (() -> Unit)? = null` that picks `ElevatedCard(onClick = ...)` only if U22 is implemented (S).

## U54 — PARTIAL — study text sizes not tokens
Do NOT add theme tokens; the `pageFontSize()` function from U6 is the cleanup.

## U55 — NOT_WORTH — Spacing tokens for 48/button height/icon/max width
Wholesale tokenisation is churn. Introduce only the 2-3 constants touched by U2/U9/U18 (`RatingButtonHeight`, min touch 48dp) locally.

## U56 — NOT_WORTH — repeated `remember { RayCollMotion.effects() }`
A `spring(...)` spec is a trivial allocation; `MotionScheme` factories are internal in M3 1.4.0 (documented in `RayCollMotion.kt`).

## U57 — PARTIAL — repeated tinted Surface pattern; Previous/Next buttons
Merge `PreviousCardButton`/`NextCardButton` into one private `CardNavButton(icon, description, onClick)` (S, readability). The 7 tinted surfaces differ in padding/shape/border -> abstraction is NOT_WORTH.

## U58 — CONFIRMED — formatter artefacts
Verified at `StudyCard.kt:313-315, 334-335`, `BrowseScreen.kt:273-286`, `ArchiveScreen.kt:63-65`, `StudyScreen.kt:234-236`; NOT at `SessionSummary.kt:89-91` (that location is clean). Cause: ktlint `android_studio` wrapping of short conditions. Fix (S): introduce locals so the lines are short and stable (`val isFirst = index == 0` in `CardItem`, `val showDivider = index > 0`, `val key = { deck: ArchivedDeck -> deck.id }` / named arg lambda, `val needsPermission = runsInBackground && needsNotificationPermission`), then `:app:ktlintFormat` + `:app:ktlintCheck`. Do it in the same edits as U0/U40/U22.

## U59 — NOT_WORTH (CROSS-LAYER if done) — domain logic in UI
`canReveal`, `readingQuestion`, summary message selection are small pure expressions already covered by compose tests; moving them into `StudyUiState` touches StudyViewModel.kt (another fixer) for no behaviour change.

## U60 — NOT_WORTH — no @Preview
Tooling nicety across ~30 composables; no functional benefit for this project.

## U61 — NOT_WORTH — @Immutable / Browse per-keystroke recompute
Part 1 WRONG: Kotlin 2.4.21 enables strong skipping by default, `@Immutable` adds nothing. Part 2: Browse already filters on `Dispatchers.Default` via `flowOn(filterContext)` precisely so typing never blocks (`BrowseSearchFieldTest`, commit "A4"); a debounce would add latency for no visible gain.

## U62 — PARTIAL — StudyScreen ~150 lines
Reasonable readability win done as a by-product of U5/U8/U13/U38/U39/U40: extract `StudyBackGesture(...)` (the `PredictiveBackHandler` block + `Animatable`) and `StudyDialogs(...)`. The `remember(viewModel) { CardActions }` hoist already exists. No separate task.

## U63 — CONFIRMED — inconsistent button icon sizes
Dashboard uses `size(16.dp)` (Style, PlayArrow), Archive `ButtonDefaults.IconSize` (18dp). Use `ButtonDefaults.IconSize` + `ButtonDefaults.IconSpacing` in DashboardScreen/StudySplitButton while editing U18. S.

## U64 — CONFIRMED — file/name mismatches
`components/SegmentedProgressBar.kt` holds `SegmentedBar`/`CardStatsBar` -> rename to `SegmentedBar.kt` (`git mv`); `SessionSummary.kt` also holds `SessionProgressBar` and `NoDueCards` -> move into `SessionProgressBar.kt` / `NoDueCards.kt` (or `StudyScreen.kt`). Pure move, same package (no import changes). S; do after the other study edits to avoid merge conflicts.

## U65 — CONFIRMED — Browse FAB uses `btn_add` ("Add") instead of `browse_add_card`; lambda
`BrowseScreen.kt:110` contentDescription -> `R.string.browse_add_card`; `onClick = { editTarget = CardEditTarget.New }` is fine as a lambda (it is not a method reference candidate: it mutates local state) — only the description changes. S.

---

## Counts (U0..U65 = 66 IDs; U3 counted as CONFIRMED duplicate of U0)
- CONFIRMED: 30 — U0, U1, U2, U3, U4, U5, U6, U7, U8, U9, U12, U13, U14, U16, U18, U19, U20, U24, U26, U30, U32, U34, U38, U39, U40, U51, U58, U63, U64, U65
- PARTIAL: 22 — U10, U11, U15, U17, U21, U22, U23, U25, U27, U29, U31, U41, U42, U43, U44, U46, U49, U50, U53, U54, U57, U62
- WRONG: 1 — U28
- INTENTIONAL: 2 — U35, U52
- EDGE: 1 — U45
- NOT_WORTH: 10 — U33, U36, U37, U47, U48, U55, U56, U59, U60, U61

(30 + 22 + 1 + 2 + 1 + 10 = 66.)

CROSS-LAYER (res and other-area files): U2 (PL strings), U12 (new string), U20 (strings x5), U24 (new string), U27 (DeckSettingsScreen.kt call site), U30 (strings x5), U31 (strings), U32 (PL strings), U34 (strings), U42 (new string). If U59 were done: StudyViewModel.kt.

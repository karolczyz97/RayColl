# Verdict ui2 (appsettings, decksettings, imports, modedetail, studymodes, res/)

Counts: CONFIRMED 23, PARTIAL 18, WRONG 1, INTENTIONAL 2, EDGE 3, NOT_WORTH 13 (total 60).
Paths relative to app/src/main/java/com/raycoll/app/ unless noted. res = app/src/main/res.
Suggested order of work (value/cost): V1, V3, V2, V4+V5+V6+V43+V7 (one ModeDetailScreen pass), V11/V12/V34 (FlowRow pass), V14/V15 (a11y pass), V29, V28, V16, V19, rest.

---

## V1 — CONFIRMED (reproduced by test) — second tap on Import while a snackbar is visible imports a duplicate deck
Evidence: I copied the repo to scratchpad/ui2copy (NOT the repo) and wrote a Robolectric Compose test (V1ReproTest) on the real ImportScreen + ImportViewModel + Room harness:
1. type deck name + CSV; make a snackbar appear via `ImportEvent.ColumnSettingsReset` (set a name on column 3, then replace the text with a 2-column text);
2. tap Import (while snackbar visible): deck count = 1, `finished=false`, Import button ENABLED (runImport's `finally` set importing=false; `Imported` sits in the channel because `CollectEvents` is suspended inside `showSnackbar`, up to ~4 s);
3. tap Import again: deck count = 2; after the snackbar times out `onFinished` fires -> final state 2 identical decks. Test assertion failed with "DUPLICATE expected:<1> but was:<2>".
Reachable in the real app with any snackbar-producing event up to 4 s before/at the tap: ColumnSettingsReset (edit a column name, then paste/edit text with fewer columns), FileUnreadable, SaveFailed (retry succeeded), Invalid. The user sees nothing happen after the first tap (screen stays, button live), so a second tap is natural. `up()` in NavGraph guards double navigation (RESUMED check) so only the data duplication is the damage.
The same ordering exists in DeckSettings (Closed after SaveFailed) and ModeDetail (Closed) but is harmless there: delete/archive idempotent (test "deleting a missing deck is an idempotent success"), ModeDetail sets `existingMode` after the first save so a second save is a no-op ("double save creates one new mode" test).
Fix spec (S, low risk, mine):
- ImportViewModel.runImport: do not re-enable after a successful import. Use `var succeeded = false`; set it right after `addDeckWithCards`; in `finally` do `if (!succeeded) _uiState.update { it.copy(importing = false) }`. The `importing` flag then stays true until the VM dies with the screen. (Optionally also guard `if (_uiState.value.importing) return` already there.)
- ImportScreen: with `importing=true` the button already disables; add progress (see V16).
- Belt and braces (CROSS-LAYER, ui1 area: ui/components/UiEvents.kt): in `CollectEvents` use `events.collectLatest { handler.value(it) }` so a newer event pre-empts a still-showing snackbar (showSnackbar is cancelled -> snackbar dismissed) and `Imported`/`Closed` navigate immediately. Alternative without touching components: in ImportScreen handler, `snackbarHostState.currentSnackbarData?.dismiss()` is not enough (collector is already blocked), so use `rememberCoroutineScope().launch { showSnackbar }` for non-navigation events.
Tests: turn V1ReproTest into a regression test in ImportScreenTest (assert 1 deck after two taps, expecting `importing` stays true); add ImportViewModelTest "import success keeps importing=true".

## V2 — CONFIRMED — .tsv greyed out in the picker
`IMPORT_MIME_TYPES` = csv/plain/markdown/comma-separated-values. Android's MimeTypeMap maps .tsv -> `text/tab-separated-values`, Windows-created csv in Drive often `application/vnd.ms-excel`/`application/csv`. The screen even says "CSV / TSV". Anki exports are .txt (works) but plain .tsv is greyed.
Fix (S): `private val IMPORT_MIME_TYPES = arrayOf("text/*", "application/csv", "application/vnd.ms-excel")` (`text/*` covers csv/tsv/plain/markdown/x-markdown). Update `import_upload_file` text in 5 locales to ".csv, .tsv, .txt, .md" (see V18 wording). Test: none practical (picker); trivial review.

## V3 — CONFIRMED (CROSS-LAYER: MainActivity.kt = playback/MainActivity fixer; theme/Theme.kt = ui1) — in-app Light/Dark does not drive system bars
`enableEdgeToEdge()` with default styles uses the *system* night flag for status/nav bar icon colour. With system Dark + app Light the Compose background is light but status-bar icons are white (invisible); reverse for system Light + app Dark. `windowBackground` = `?android:attr/colorBackground` also follows system (visible on rotation/IME resize/transitions).
Fix (S): in MainActivity, after `val prefs`, compute `val dark = prefs.theme.isDark(isSystemInDarkTheme())` (extract `fun ThemePref.isDark(systemDark: Boolean)` from RayCollTheme's `when`, reuse in both) and
`DisposableEffect(dark) { enableEdgeToEdge(statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }, navigationBarStyle = SystemBarStyle.auto(lightScrim, darkScrim) { dark }); onDispose {} }` (must come after the `?: return@setContent`). Do NOT use `AppCompatDelegate.setDefaultNightMode` (recreates the Activity on every start when pref != system -> flash + state churn). Optionally `window.setBackgroundDrawable(ColorDrawable(colorScheme.background))` in the same effect.
Tests: unit test for `ThemePref.isDark`; the rest is visual (check on phone: system dark + app light, and reverse).

## V4 — CONFIRMED — back discards edits without asking
No BackHandler anywhere in modedetail (only StudyScreen uses one). Editor can hold a long step list; the top-bar back arrow and system back both drop it.
Fix (S-M): ModeDetailViewModel: keep `baseline` (name + steps from load/chooseTemplate); `Editing.dirty = name != baselineName || steps.map{it.step} != baselineSteps`. Screen: `BackHandler(enabled = editing?.dirty == true) { showDiscard = true }` + top-bar `onBack` goes through the same guard; the `ModeDetailEvent.Closed` handler must call the raw `onBack` (no prompt after Save). `ConfirmDialog` "Discard changes?" (new strings x5: title, text, confirm "Discard"; rememberSaveable flag like the other dialogs). Tests: VM `dirty` true after edit/false after revert; extend DialogStateRestorationTest with discard dialog surviving recreation.

## V5 — CONFIRMED — issues / disabled Save reason live only in the scrolling header
Save is a top-bar action (always visible, disabled) but the reason is an item scrolled away on a long list.
Fix (S, do together with V43/V6): render blocking issues in a pinned strip: `Column { AnimatedVisibility(blockingIssues.isNotEmpty()) { IssuesBanner(blocking) } ; LazyColumn(...) }` (weight(1f) on the list) instead of inside the `header` item; non-blocking tips stay in the header item (V43). Keep text to blocking issues only so the pinned strip stays short (max 3 lines: NameRequired, NoSteps, PageOutOfRange per step). Test: StepCard-level n/a; add a ModeDetail screen test that the banner node exists after scrolling the list to the last step.

## V6 — CONFIRMED — new mode shows a red error before the user typed anything
Fix (S): in ModeEditor keep `var nameTouched by rememberSaveable { mutableStateOf(false) }` (set true on first name change or blur). Filter `ModeIssue.NameRequired` out of the displayed issues while `state.isNew && !nameTouched`; name field gets `isError = nameTouched && blank` and `supportingText` = `mode_issue_name_required` when error. Save stays disabled. Also `FocusRequester` on the name field for `isNew` (optional). Tests: screen test: new mode shows no banner; after typing+clearing shows error.

## V7 — CONFIRMED — Add step icon at the top, new step appended at the bottom with no scroll/highlight
Fix (S-M): add a trailing LazyColumn `item(key="add_step") { OutlinedButton(onClick = onAddStep, Modifier.fillMaxWidth()) { Icon(Add); Text(mode_editor_add_step) } }` (same look as StudyModesScreen's "New study mode" button) and drop the header AddCircle icon (one entry point, no scrolling to the top with 15 steps). On add: `val listState = rememberLazyListState()` + `LaunchedEffect(state.steps.size)` with `var prev by remember { mutableIntStateOf(state.steps.size) }`: if size > prev -> `listState.animateScrollToItem(state.steps.size)` (header = index 0, so the new step index = size); update prev. Not on first composition/delete. Tests: screen test adding a step scrolls the new card into view.

## V8 — PARTIAL — Add-step dialog
Right: 20+ identical TextButtons, ready-made blocks (compound) are the last group though they are what most users want; "TTS/STT" jargon. Wrong/overkill: per-step descriptions (25 strings x 5 locales).
Fix (S): move the `step_category_compound` group first and rename it "Ready-made blocks" (+4 locales); keep low-level groups after. Rename `step_speak_all_pages`/`step_speak_page` catalog labels via V49 (no abbreviations). No descriptions. Tests: none beyond I18nConsistencyTest.

## V9 — NOT_WORTH — template picker "bare buttons"
Labels already explain the choice ("Classic (flip & rate)", "Audio (listen & speak)", "Empty mode"); back leaving the screen is right (nothing created yet). Adding 3 description strings x5 locales is churn.

## V10 — PARTIAL — StepCard move/delete; drag-to-reorder
Real problems: (a) each arrow tap moves the card by a neighbour's height (cards are 150-250dp tall), so after one tap the card slides away from the finger/viewport and the user must hunt it for the next tap; (b) delete has no undo and sits right next to the arrows. "No drag" itself is not a defect.
Fixes (S-M, recommended): (1) after `moveStep`, keep the moved card under the finger: in ModeEditor, on move call `listState.animateScrollBy(delta)` where delta = the swapped neighbour's height (`listState.layoutInfo.visibleItemsInfo` by key), or simply `animateScrollToItem(newIndex+1)` — pick scrollBy; (2) delete undo: VM stores `lastRemoved = (index, EditableStep)`; screen shows snackbar "Step deleted" + action "Undo" (`showSnackbar(actionLabel)`; use the same SnackbarHostState; strings x5) calling `viewModel.undoRemove()` (re-insert at index, keep key). Tests: VM undo test; moveStep scroll is visual.
Drag-and-drop answer: LIBRARY if ever done, not custom. Custom needs LazyListState item-offset maths, auto-scroll at edges, interplay with `animateItem()` and stable keys, haptics, and a11y custom actions: ~150-250 lines and a source of subtle bugs. `sh.calvin.reorderable:reorderable` (LazyColumn `ReorderableItem` + `Modifier.draggableHandle()`, maintained, small, no transitive deps) does it in ~20 lines plus a version-catalog entry (data+di+build area: CROSS-LAYER if adopted). BUT it is NOT worth doing now: cards are tall so a drag across 3+ steps needs edge auto-scroll anyway (the library has it, but UX is still clumsy; a good drag UX would also want collapsed cards while dragging = redesign), lists are short (4-12 steps), edits are rare, and arrows are the accessible fallback you would have to keep anyway. Keep arrows, apply (1)+(2). Revisit drag only if the owner explicitly wants it; then: library + handle icon in the title row, keep arrows for TalkBack.

## V11 — CONFIRMED — ConditionSelector Row does not wrap
Three `FilterChip`s in a plain `Row`. es "Siempre / Si es correcto / Si es incorrecto" is ~400dp of chips inside a ~355dp card on a 412dp phone even at default font; de/large font worse. Clipped, not wrapped.
Fix (S): `FlowRow(horizontalArrangement = spacedBy(Spacing.s), verticalArrangement = spacedBy(Spacing.xs))` (FlowRow already imported in StepCard). The "full row on every card is clutter" part is NOT_WORTH. Tests: StepCardTest: render with fontScale 1.5 / es locale, assert all three chips displayed (`assertIsDisplayed`).

## V12 — CONFIRMED — FIELD_WIDTH 112dp truncates labels
Field is always filled so the label is always floated: ~80dp usable; "Dynamic pause multiplier" (en), "Mnożnik pauzy dynamicznej" (pl) truncate (de already abbreviated to "Faktor dyn. Pause" — a sign of the same problem). Hard dp ignores font scale.
Fix (S): in `FieldRow` items use `Modifier.widthIn(min = 112.dp)` + weight in the FlowRow (`Modifier.weight(1f, fill = true).widthIn(min = 112.dp)` — IntField/MultiplierField take a modifier param) so fields share the row and labels get more room; single fields (WaitStep, AutoRateFixed) keep 112-140dp. Shorten `compound_field_multiplier` to "Pause multiplier" / pl "Mnożnik pauzy" / de "Pausenfaktor" / es "Factor de pausa" / it "Fattore pausa". Tests: StepCardTest with large font: the label node text is not truncated (`TextLayoutResult.hasVisualOverflow == false` via `SemanticsActions.GetTextLayoutResult`).

## V13 — PARTIAL — units / ratings / multiplier hints
- wait in ms: fine for a tuning field (range 0..600000); skip seconds conversion (NOT_WORTH).
- threshold bare "%": label already "Threshold (%)" — fine.
- rating "1–4" without names: CONFIRMED, cheap: reuse existing `study_rating_1..4` as supportingText ("3 – Good") under RatingField (NumberField gets optional `hint: (T) -> String?` shown when valid; error text still wins).
- multiplier no hint: real (pause = 60 ms x page length in chars x multiplier, StepExecutor). Hint via supportingText "x60 ms per character" is jargon; lowest value, skip.
Fix size S for rating hint (rating names must not hard-code order: use `Ratings.MIN..MAX` -> string by index). Tests: NumberFieldTest/StepCardTest shows "Good" for rating 3.

## V14 — CONFIRMED — label not associated with Switch/Checkbox/RadioButton
TalkBack announces an unlabeled "Switch, on"; tapping the text does nothing (touch target = control only).
Fix (S): wrap each row `Modifier.toggleable(value, role = Role.Switch/Checkbox, onValueChange = ...)` (or `selectable(role = Role.RadioButton)` for the DeckSettings mode row) and give the control `onCheckedChange = null`. Spots: StepCard.LabeledSwitch, SettingsSections.SwitchRow (title+description in one merged row), ImportScreen header checkbox row, DeckSettings ModesSection radio row (also fixes V40 part). Tests: StepCardTest: `onNodeWithText("Speak").performClick()` toggles the value; assert `isToggleable`.

## V15 — CONFIRMED (CROSS-LAYER for ModeListItem.kt = ui1 components) — identical contentDescriptions per row
TalkBack lists "Delete, Delete, Delete…". Zero-new-string fix (S): `contentDescription = "${stringResource(R.string.btn_delete)}: ${stepTitle(step)}"` (StepCard: up/down/delete; stepTitle includes page/number), `"...: ${mode.displayName()}"` in ModeActions (edit/reset/delete), PageRow: `"${a11y_move_page_up}: ${name.ifBlank { Page N }}"`, ImportScreen preview row: `"Delete: ${rowIndex+1}"`. Tests: StepCardTest asserts distinct descriptions between two cards.

## V16 — CONFIRMED — Import button is the last item of a long LazyColumn; no progress
With a few hundred preview rows the user must scroll to the end. `importing` is set but unused visually.
Fix (M): restructure content as `Column(Modifier.fillMaxSize().padding(padding).imePadding()) { LazyColumn(Modifier.weight(1f)) {…without "submit" item}; Surface(tonalElevation) { Button(...) } }`; inside the button: `if (state.importing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(...)`. No ScreenScaffold change needed. Success feedback: screen closes and the new deck appears on the dashboard (acceptable; do not add a snackbar that dies with the screen). Update ImportScreenTest (`performScrollToKey("submit")` -> button is always visible). Combine with V1 and V19.

## V17 — PARTIAL — preview rows have no column headers
Config card above lists pages in the same order but with 3-5 narrow unlabeled fields + trash per row it is hard to tell which is which.
Fix (S-M): `stickyHeader { Row { pageNames.forEach { Text(name.ifBlank{"Page N"}, labelSmall, maxLines=1, ellipsis, Modifier.weight(1f)) }; Spacer(48.dp) } }` just above the rows (same weights/spacing as PreviewRow). Skip changing the trash icon. Tests: assert header text node present when source non-blank.

## V18 — PARTIAL (size cap is CROSS-LAYER: data/files/DocumentFiles.kt) — format guidance / whole file in the text field / "Upload"
- Wording: "Upload file" is wrong (nothing is uploaded) -> "Choose file (.csv, .tsv, .txt, .md)" x5 (CONFIRMED, S).
- Guidance: add `supportingText` under the paste field: "One card per line. Separate pages with tab, ; , or |. First row may be a header." (new string x5) (CONFIRMED, S).
- Whole file dumped in the text field: real risk, not just taste: `DocumentFiles.readText` has no size limit (`readBytes()` -> OOM on a huge file, and a multi-MB text in an OutlinedTextField freezes layout). Add a cap (e.g. 2 MB): `readNBytes(MAX + 1)`, over limit -> null -> existing `FileUnreadable` snackbar (or a dedicated "File too large" string). CROSS-LAYER (data fixer). Tests: DocumentFiles cap test.

## V19 — PARTIAL — canImport vs validation messages
`ImportViewModelTest` ("canImport requires name and nonempty rows", "missing deck name emits a validation problem") encodes: the VM check is a safety net, the button is disabled. Real part: disabled with no reason. The "Preview(N) counts blank rows" part only happens after the user blanks cells (parseRows drops blank lines) = EDGE.
Fix (S): under/above the Import button show `Text(R.string.import_error_name_required)` when `deckName.isBlank()` else `import_error_no_cards` when no non-blank row (strings exist in 5 locales); `color = onSurfaceVariant`, labelMedium. Or deck-name field `supportingText` when blank and source non-blank. Tests: ImportScreenTest: with CSV and no name the hint is shown.

## V20 — EDGE — draft lost on process death during the file picker
Process death while DocumentsUI is foreground is rare; saving the whole draft in SavedStateHandle risks TransactionTooLargeException for big pasted text (Bundle ~1 MB). Not worth it.

## V21 — NOT_WORTH — refactor ImportViewModel into a draft reducer
Working code with ~30 targeted tests around subtle races (debounce, rowsEdited, overrides); the rewrite is pure risk for readability.

## V22 — NOT_WORTH — duplicated page name+language row
They differ in behaviour (plain TextField in a draft vs PersistedTextField + arrows). Extraction would need flags; marginal.

## V23 — CONFIRMED (CROSS-LAYER: new shared composable in ui/components, ui1 area) — 3 copies of AssistChip+DropdownMenu
Extract `ChipDropdown<T>(label: String, options: List<T>, optionLabel: @Composable (T) -> String, onSelect: (T) -> Unit)` into ui/components/ChipDropdown.kt; use in SeparatorPicker, AppLanguagePicker, LanguagePicker. Pure refactor, existing screen tests cover. Size S.

## V24 — CONFIRMED (CROSS-LAYER, same file as V23's neighbour) — section card copy-pasted
Extract `SectionCard(title: String? = null, modifier, content: ColumnScope.() -> Unit)` (RayCollCard + Column(padding l, spacedBy m) + optional titleMedium heading with `semantics { heading() }` — also fixes V52 headings) into ui/components; replace SettingsSection, DeckSettings.Section, ImportScreen cards (title = null). Note DeckSettings ModesSection uses spacedBy(s) not m: either accept m or add a param. Size S.

## V25 — PARTIAL (CROSS-LAYER for shared dialogs in ui/components) — mode reset/delete dialogs triplicated
Dialogs: CONFIRMED, S: add `ModeResetDialog(onConfirm, onDismiss)` and `ModeDeleteDialog(...)` in ModeListItem.kt (or ModeDialogs.kt) using the existing ConfirmDialog + same strings; use in DeckSettingsScreen, StudyModesScreen, ModeDetailScreen. The VM `launch/save` helper repetition (5 VMs) is NOT_WORTH (each is a 2-line wrapper with a different event type).

## V26 — NOT_WORTH — DeckSettingsContent takes the whole ViewModel
Screen-private composable, no previews/tests need it; replacing with 10 lambdas adds noise.

## V27 — PARTIAL — delete-deck confirmation
Wrong: it is not "no text"; `isDeleteConfirmed` is case-insensitive and trimmed (comment + `dialog_state` test), `TextFieldValue.Saver` is needed so cursor/selection survive recreation (harmless). Right: nothing says "type X to confirm" — the token is only the floating label; and diacritics are exact (pl "USUŃ" needs Ń).
Fix (S): add supporting text `delete_confirm_hint` = "Type %1$s to confirm" (x5) under the field (`supportingText = { Text(stringResource(R.string.delete_confirm_hint, token)) }`); compare after stripping diacritics: `Normalizer.normalize(s, NFD).replace(Regex("\\p{M}+"), "")` on both sides (token Ń->N; do NOT rely on this for Ł: `Ł` has no decomposition — handle by also `.replace('Ł','L')` or just keep exact for Ł; pl token has only Ń, ok). Tests: `isDeleteConfirmed("usun","USUŃ")` true, existing DialogStateRestorationTest delete dialog test.

## V28 — CONFIRMED — factory reset
It is a destructive confirm dialog (not one tap), but weaker than deleting one deck (typing token) though it wipes ALL decks and, when signed in, replaces cloud data too (`localStore.replaceAll` + sync; the import dialog already says so, the reset dialog does not). No success feedback.
Fix (S): (1) reuse the import wording: append "When you are signed in, this is also synced to the cloud." and "Export a backup first." to `app_settings_factory_reset_desc` x5; (2) success snackbar: new `app_settings_factory_reset_done` x5, emitted as an `AppSettingsEvent` after `replaceAll` (like BackupImported); (3) optional M: generalize `DeleteDeckDialog` into `TypeToConfirmDialog(title, text, token, confirmLabel)` (decksettings package, imported by appsettings) and use it for reset with the same `delete_confirm_token`. Default recommendation: (1)+(2) now, (3) if the owner wants more friction. Tests: VM emits event after reset (AppSettingsViewModel is Firebase-coupled/untested -> do a pure-ConfirmDialog-text test only).

## V29 — CONFIRMED — Export/Import card
Two-button `Row` clips in de ("Zustand nach JSON exportieren" + "Aus Datei importieren" ≈ 420dp of buttons in a 348dp card) and large fonts; the factory-reset button sits in the same card as safe actions; "Export State to JSON" is jargon.
Fix (S): `FlowRow` for the two buttons (also the Account row Sync now/Sign out); rename: en "Save backup to file" / "Restore from backup" (pl "Zapisz kopię do pliku" / "Przywróć z kopii", de "Backup in Datei speichern" / "Aus Backup wiederherstellen", es "Guardar copia en archivo" / "Restaurar desde copia", it "Salva backup su file" / "Ripristina da backup") — keys unchanged; the import confirm dialog confirm label `app_settings_import_file_btn` becomes "Restore from backup" which fits. Move factory reset to its own small `SettingsSection` ("Danger zone"? new string) or a plain error-coloured OutlinedButton below the card; default: separate card without title, error-container Button. Tests: layout check with fontScale 1.5 and de locale that both buttons are displayed.

## V30 — PARTIAL — account section
- sign out without confirm: NOT_WORTH (reversible: signing in again restores the account namespace; offline sign-out keeps unsynced changes locally).
- Sync now enabled during SYNCING: CONFIRMED, S: `enabled = state.syncStatus != SyncStatus.SYNCING`.
- no last-synced time: NOT_WORTH (needs data-layer state).
- "Local Mode" with no benefit line: CONFIRMED-lite, S: one extra sentence "Sign in to back up your decks and sync them between devices." (x5), bodySmall, above the sign-in button; keep `auth_local` as is.
Tests: SettingsSections has no screen test; assert via a small Compose test or leave.

## V31 — PARTIAL — TTS rate slider
Test voice: NOT_WORTH (needs a TTS engine instance in settings = playback area, big). Semantics: CONFIRMED S: `Slider(modifier = Modifier.semantics { contentDescription = label; stateDescription = "%.1f×".format(rate) })` — TalkBack otherwise reads a meaningless percentage.

## V32 — CONFIRMED — POST_NOTIFICATIONS denial ignored
On denial the toggle stays on; study still runs in the foreground service but the notification with controls is invisible; the user is not told. Headset buttons still work (SilentPlayback), so do NOT switch the toggle off.
Fix (S): `rememberLauncherForActivityResult(RequestPermission()) { granted -> if (!granted) scope.launch { snackbar.showSnackbar(msg, actionLabel = open settings) } }` with new strings x5 ("Notifications are off: study controls won't appear in the notification." + "Settings") and `Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)`. Careful with V1-style blocking: use a separate coroutine, not the CollectEvents collector. Tests: none (permission flow).

## V33 — PARTIAL — language picker
`AppCompatDelegate.getApplicationLocales()` read in composition is fine (cheap; changing the locale recreates the Activity) -> not an issue. Real but small: `APP_LANGUAGES` is a manual copy of the `values-*` dirs (the locale config is generated from them by AGP). Make `APP_LANGUAGES` `internal` and add to I18nConsistencyTest: `listOf("pl","en","de","es","it").toSet() == locales.map{ it.removePrefix("values-") ... }`. Size S.

## V34 — CONFIRMED — FilterChip single choice / horizontal scroll hides options
DeckSettings.ChoiceChips uses `horizontalScroll`: the 4-option Study scope row ("New + due for review", "New", "Review", "All") overflows, "All" is hidden off-screen with no affordance. Chips for single choice also announce as checkboxes to TalkBack.
Fix (S): `FlowRow` in ChoiceChips (replace horizontalScroll), Theme row, RevealStyleSelector, ConditionSelector (V11). For true single-choice semantics add `Modifier.semantics { role = Role.RadioButton }` to the chip (FilterChip merges selected state); or use `SingleChoiceSegmentedButtonRow` only for Theme (3 short labels) — default: FlowRow everywhere, no segmented. Tests: DeckSettings screen with fontScale 1.5 shows all 4 options.

## V35 — PARTIAL — Study scope hierarchy
True: the first chip row has no label while "Card order" gets a `titleMedium` equal to the card title. Fix (S): add `settings_study_filter` ("Cards to study" / pl "Które karty" / de "Welche Karten" / es "Qué tarjetas" / it "Quali carte") as `labelLarge` text above the first chips, and make "Card order" `labelLarge` too (card title stays titleMedium). No further redesign.

## V36 — INTENTIONAL — writes per keystroke
`PersistedTextField` documents "zapisywane na bieżąco" on purpose; Room write is cheap and cloud upload is DEBOUNCED (`CloudWriteMode.DEBOUNCED`, SyncScheduler `PERSISTENCE_DEBOUNCE_MS`), so "every keystroke writes cloud" is wrong. Blank name: field shows `isError` and reverts to the stored name on blur — acceptable. Optional S nicety: `supportingText` "Name can't be empty" — skip.

## V37 — INTENTIONAL — PageRow
Name field has ~170dp on a 412dp phone (not 110); swapping pages is self-inverse (press the opposite arrow) — "no undo" holds in spirit. Last commit (641e3057) just made swap crash-free. Side note for the data fixer: a swap rewrites and dirties every card (sync cost on big decks) — out of scope here.

## V38 — WRONG — lowering page count loses data
`withActivePageCount` only changes `activePageCount` and pads metadata; card `pages` beyond it are kept, raising the count brings them back. Non-destructive, no warning needed. Re-using `import_pages_count` ("Pages:") is fine.

## V39 — NOT_WORTH — Archive/Delete stacked, bare "Delete"
Delete opens a typed-token dialog titled "Delete permanently?" with the deck name; a longer button label adds 5 strings for no safety gain.

## V40 — PARTIAL — active mode radio
Only the dot selecting is real (fixed by V14: `selectable` row). No step summary, duplication of StudyModesScreen and the alpha highlight are subjective -> NOT_WORTH.

## V41 — NOT_WORTH — StudyModesScreen polish
Mode delete has a confirm dialog; clickable card + edit icon is standard; the info banner is one static line; two "+" entry points (top bar + list end) are harmless. Subjective.

## V42 — CONFIRMED (CROSS-LAYER: ModeListItem.kt) — built-in badge colour inconsistent
ModeDetail header uses `tertiaryContainer` for "Built-in mode"; the lists use `secondaryContainer` for built-in and `tertiaryContainer` for "Modified" — so the detail's built-in badge looks like the "Modified" one. Fix (S): make `ModeBadge` public in ModeListItem.kt as `BuiltInBadge()` (secondary colours) and use it in ModeDetailScreen instead of the inline Surface. Test: none (visual).

## V43 — CONFIRMED — blocking errors and tips share one red banner
`ModeIssue.blocking=false` (NoTerminalStep, AutoRateWithoutStt, NoFallbackAfterListen, ConditionAfterTapGate) shown under red "Issues in mode configuration:" next to an enabled Save.
Fix (S): split into two banners: blocking -> errorContainer, title `mode_editor_issues_title` (V5 pinned); non-blocking -> `secondaryContainer` (or tertiaryContainer), `Icons.Filled.Info`, new title `mode_editor_tips_title` ("Tips:" x5). Tests: ModeDetail screen test with a non-blocking issue shows tips banner and Save enabled.

## V44 — PARTIAL — Refresh icon / reset closes screen
Icon: CONFIRMED S: `Icons.Filled.RestartAlt` (or `Restore`; extended icons already a dependency) instead of `Refresh` in ModeDetailScreen and ModeListItem (CROSS-LAYER for the latter). Reset closing the screen: INTENTIONAL (`persist{}` then `Closed`, same as Save; test "reset built-in restores").

## V45 — EDGE — missing mode id -> template picker
Only when a mode is deleted elsewhere (sync) between list and tap. Cheap fix if ever touched: if `modeId != null && mode == null` -> new `ModeDetailUiState.Missing` rendered with `ErrorState`. Skip.

## V46 — NOT_WORTH — canReset getter per recomposition
Compares ≤ ~20 steps once per recomposition of the top bar; unmeasurable.

## V47 — EDGE — invalidFields design
Self-healing: `NumberField` text is `rememberSaveable`, `LaunchedEffect(valid)` re-registers after recreation; keys include stable `item.key`. Only gap: an off-screen (disposed) invalid field is removed from the set and Save would persist the last valid model value — requires typing an out-of-range value, scrolling away without blur, then Save. Skip.

## V48 — NOT_WORTH — bottom padding vs FAB
Conditional on a FAB that V7 does not introduce (trailing button instead).

## V49 — CONFIRMED — step title abbreviations
en `step_speak_page` "TTS page %1$d", `step_speak_all_pages` "TTS all pages", `step_wait` "Wait %1$dms", `step_listen_and_check` "Check speech p. %1$d (threshold %2$d%%)" vs the catalog labels "Speak page (TTS)", "Check speech (STT)".
Fix (S, copy only x5): "Speak page %1$d", "Speak all pages", "Wait %1$d ms", "Check speech, page %1$d (min. %2$d%%)". `step_speak_all_pages` is also the catalog label, so the new text serves both. Check the other locales for the same abbreviations (de "Warte %1$dms" etc.). I18nConsistencyTest guards placeholders.

## V50 — NOT_WORTH — stacked cards / narrowed top bar
Cards are the app's consistent look; ScreenScaffold max-width 640 (including top bar) is covered by a test and verified on the phone.

## V51 — CONFIRMED — version text contrast
`outline` #787680 on #FEF7FF = ~4.25:1 (< 4.5 AA for bodySmall) in light theme. Fix (S): `MaterialTheme.colorScheme.onSurfaceVariant`.

## V52 — PARTIAL — StudyModesLink / headings
CONFIRMED S: `clickable(role = Role.Button, onClick)` on the row; section titles `semantics { heading() }` (comes free with V24's SectionCard). The "…" in "Manage modes…" and hardcoded 40/16dp: NOT_WORTH.

## V53 — NOT_WORTH — VM tidy-ups
`lastReady` is mutated in a combine lambda that runs single-threaded on viewModelScope (Main); StudyModesViewModel has no lastReady. A shared constant for 5_000 touches several areas for nothing.

## V54 — NOT_WORTH — chooseGoogleAccount signingIn resets
Firebase-coupled, tested in AppSettingsSignInTest (cancel/failed/closing paths); restructuring risks the sign-in flow. `appVersion` in state is harmless.

## V55 — PARTIAL — copy consistency
Capitalization: CONFIRMED (en only, S): sentence case for "Deck Name", "Study Scope", "Pages Configuration", "Archive Deck", "Restart Session", "Restart Failed Only", "Edit Flashcard", "Delete Flashcard", "App Settings", "Study Statistics", "TTS Speech Rate", "Background Audio", "Export / Import Data", "Local Mode", "Import Flashcards". Tests that look up by English text must change in the same commit: ImportScreenTest ("Deck Name", "Import Flashcards"), DialogStateRestorationTest ("Edit Flashcard"), others via grep `hasText(`/`onNodeWithText`. Other locales are already sentence case. "mode" overloaded / tone: NOT_WORTH.

## V56 — CONFIRMED — es/pl copy
res/values-es/strings.xml:66 `import_csv_label` "Pegue el texto…" (usted) -> "Pega el texto CSV / TSV aquí" (tú like the rest). res/values-pl/strings.xml:16 (auth_migration_desc "lokalne talie"), :244 (settings_delete_error "talii"), :247 (factory_reset_desc "Wszystkie talie") -> "zestawy"/"zestawu" (the rest of the file says zestaw). S.

## V57 — PARTIAL — strings
Duplicate keys (dashboard_name_label vs settings_rename_label, study_modes_title vs app_settings_study_modes, errors_retry vs study_answer_retry, mode_list_new) are separate on purpose (can diverge per screen/locale; pl already differs for retry): NOT_WORTH to merge across 5 files. CONFIRMED S: `import_error_page_count` hardcodes "2 to 20" -> `%1$d to %2$d` fed from `PageLimits.MIN_PAGE_COUNT`/`MAX_STORED_PAGE_COUNT` (`problemArgs` for `InvalidPageCount`; the `Imported` path stays). x5 locales.

## V58 — PARTIAL — monochrome icon layer
Verified: `ic_launcher.xml`/`ic_launcher_round.xml` use the full-colour foreground PNG (512x512 RGBA, blue gradient chevron with soft drop shadow) as `<monochrome>`. Android uses only the alpha channel, so the chevron stays recognisable, but the shadow adds a soft halo and the gradient is lost — not a "blob", but not clean. Fix (S, owner should eyeball): add `drawable/ic_launcher_monochrome.xml` (108x108 vector, solid chevron path, centred in the 66dp safe zone, approx. points from the PNG: left foot (34,70), apex (54,32), right foot (74,70), inner apex (54,46), rounded joins) and point both `<monochrome>` tags at it. Test: none; check on the phone with themed icons on.

## V59 — NOT_WORTH — values-night / splash
`Theme.RayColl` is `Theme.AppCompat.DayNight`, so the system night mode is already handled; the splash must show before DataStore is read, so it can only follow the system. A flash only occurs when the in-app theme differs from the system theme. Fixing needs a synchronous theme mirror (SharedPreferences) = churn.

## V60 — NOT_WORTH — ErrorState retry / DeckMissing
DeckMissing sits inside `ScreenScaffold` with a back arrow; "deck no longer exists" has nothing to retry.

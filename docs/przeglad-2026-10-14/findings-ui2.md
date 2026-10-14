# Findings: ui/appsettings, ui/decksettings, ui/imports, ui/modedetail, ui/studymodes, res/ (paths relative to app/src/main/java/com/raycoll/app/)

V1 BUG ui/imports/ImportScreen.kt:66-75 + UiEvents CollectEvents — snackbar suspends collector; Imported event waits; runImport finally re-enables button → second tap imports duplicate deck. Same for DeckSettingsEvent.Closed after SaveFailed.
V2 BUG ImportScreen.kt:55 — IMPORT_MIME_TYPES lacks text/tab-separated-values, application/vnd.ms-excel, application/csv → .tsv greyed out.
V3 BUG ui/theme/Theme.kt:94-115 + MainActivity.kt:29 + res/values/themes.xml:4 — in-app light/dark only drives Compose; system bars and window background follow system; no setDefaultNightMode.
V4 ui/modedetail/ModeDetailScreen.kt:70-92 — no BackHandler/dirty check; back discards edits.
V5 ModeDetailScreen.kt:156-200 — issues & disabled Save only visible in scrolling header.
V6 ui/modedetail/ModeDetailViewModel.kt:154 + ModeDetailScreen.kt:184 — new mode shows red errors immediately.
V7 ModeDetailScreen.kt:191-197 + ModeDetailViewModel.kt:103 — Add step icon at top, step appended at bottom w/o scroll/highlight.
V8 ui/modedetail/AddStepDialog.kt:44-130 — 20+ TextButtons, jargon, ready-made blocks last.
V9 ModeDetailScreen.kt:219-241 — template picker three bare buttons, no description; back leaves screen.
V10 ui/modedetail/StepCard.kt:67-100 — Up/Down/Delete per step; no drag; delete no undo.
V11 StepCard.kt:96,104-119 — ConditionSelector full row each card; Row doesn't wrap (DE/large font clip).
V12 StepCard.kt:47-48,250-276 — FIELD_WIDTH=112dp fixed; labels truncate.
V13 StepCard.kt:132,239,247,264 — wait in ms, threshold bare %, rating "1–4" without names, multiplier no hint.
V14 StepCard.kt:279-283, ui/appsettings/SettingsSections.kt:265-277, ImportScreen.kt:194-197, ui/decksettings/DeckSettingsScreen.kt:337 — label not linked to Switch/Checkbox/Radio; use toggleable/selectable row.
V15 StepCard.kt:76-94, ui/decksettings/PageRow.kt:45-50, ui/components/ModeListItem.kt:55-72, ImportScreen.kt:254-259 — identical contentDescriptions per row ("Delete").
V16 ImportScreen.kt:122-132 — Import button last LazyColumn item; no progress; no success feedback.
V17 ImportScreen.kt:243-262 — preview rows N unlabeled fields side by side, no headers; trash per row.
V18 ImportScreen.kt:146-171 — no format guidance; whole file dumped into text field; "Upload file" wording.
V19 ui/imports/ImportViewModel.kt:54-55,198 + ImportScreen.kt:98,110 — canImport requires name so DeckNameRequired/NoCards never shown; disabled without reason; Preview(N) counts blank rows.
V20 ImportViewModel.kt:99-121 — draft not in SavedStateHandle; lost on process death during file picker.
V21 ImportViewModel.kt:93-360 — 6 mutable non-state fields + 2 jobs; extract ImportDraft reducer.
V22 ImportScreen.kt:204-213 vs PageRow.kt:38-44 — duplicated page name+language row.
V23 SettingsSections.kt:175-195, ImportScreen.kt:219-240, ui/components/LanguagePicker.kt:22-43 — 3 copies AssistChip+DropdownMenu.
V24 SettingsSections.kt:57-65, DeckSettingsScreen.kt:250-257, ImportScreen.kt:145,185, DeckSettingsScreen.kt:307 — section card copy-pasted 5x.
V25 DeckSettingsScreen.kt:222-246, ui/studymodes/StudyModesScreen.kt:110-134, ModeDetailScreen.kt:126-137 — reset/delete mode dialogs triplicated; VMs repeat launch/save helpers.
V26 DeckSettingsScreen.kt:102-106,375 — DeckSettingsContent takes whole ViewModel.
V27 ui/decksettings/DeleteDeckDialog.kt:36-44 — no "type X to confirm" text; diacritics exact (USUN fails); needless TextFieldValue.Saver.
V28 ui/appsettings/AppSettingsScreen.kt:160-172 — factory reset one tap; doesn't mention cloud wipe; no success feedback.
V29 SettingsSections.kt:242-262 — danger button inside Export/Import card; jargon labels; two-button Row clips DE/large font (also 76-84).
V30 SettingsSections.kt:67-99 + ui/appsettings/AppSettingsViewModel.kt — sign out no confirm; Sync now enabled during SYNCING; no last synced time; "Local Mode" no benefit line.
V31 SettingsSections.kt:145-165 — TTS rate slider no test voice; no semantics.
V32 AppSettingsScreen.kt:124-130 — background audio permission denied ignored; toggle stays on.
V33 AppSettingsScreen.kt:111-122 — AppCompatDelegate read in composition; APP_LANGUAGES hardcoded separately from locale config.
V34 SettingsSections.kt:113-125, DeckSettingsScreen.kt:148-167,282-295, StepCard.kt:206-221 — FilterChip used for single choice; 4-option row horizontal scroll hides options.
V35 DeckSettingsScreen.kt:147-168 — Study Scope: filter unlabeled; hierarchy.
V36 DeckSettingsScreen.kt:122-129, ui/decksettings/DeckSettingsViewModel.kt:78-83, PersistedTextField.kt:37-40 — every keystroke writes Room + cloud; blank name silently dropped.
V37 PageRow.kt:33-51 — name field ~110dp; page swap rewrites all cards without confirm/undo.
V38 DeckSettingsScreen.kt:260-279 — page count stepper reuses import string; no warning when lowering with data.
V39 DeckSettingsScreen.kt:177-193 — Archive/Delete stacked, bare "Delete".
V40 DeckSettingsScreen.kt:298-352 — active mode radio: only dot selects; no step summary; duplicate of StudyModesScreen list; alpha highlight.
V41 StudyModesScreen.kt:84-105,137-179 — "Steps: N" only; card clickable + Edit icon redundant; delete no undo; permanent info banner; "+" twice.
V42 ModeDetailScreen.kt:172-183 vs ModeListItem.kt:29-47 — built-in badge color inconsistent; reimplemented.
V43 ModeDetailScreen.kt:243-263 — blocking errors and tips share one red banner.
V44 ModeDetailScreen.kt:84, ModeListItem.kt:56 — Refresh icon for "Restore defaults"; reset closes screen.
V45 BUG ModeDetailViewModel.kt:76-91 — missing mode id → ChoosingTemplate silently "create new".
V46 ModeDetailViewModel.kt:50-52 — canReset getter compares per recomposition.
V47 ModeDetailScreen.kt:59,109-111 + StepCard.kt:51,259,273 — invalidFields keyed by "${step.key}/${labelRes}"; not saveable; Save gate depends on DisposableEffect.
V48 ModeDetailScreen.kt:153 — bottom padding Spacing.xl; use fabClearance if FAB added.
V49 ui/modedetail/StepTexts.kt:50-69 + strings.xml:129-131 — step titles abbreviations ("TTS page 1", "Wait 1000ms").
V50 AppSettingsScreen.kt:84-145 / SettingsSections.kt — stacked elevated cards dated; ScreenScaffold 640 max width narrows top bar.
V51 AppSettingsScreen.kt:138-143 — version text outline color low contrast.
V52 SettingsSections.kt:206-239 — StudyModesLink clickable Row no role; hardcoded dp; ellipsis in "Manage modes…"; no heading() semantics.
V53 ui/studymodes/StudyModesViewModel.kt:17-46, DeckSettingsViewModel.kt:55-72 — lastReady mutated inside combine; repeated UiState patterns; 5_000 repeated.
V54 AppSettingsViewModel.kt:217-301 — chooseGoogleAccount signingIn resets in 4 paths; appVersion constant in state.
V55 res/values/strings.xml — mixed capitalization; "mode" overloaded; tone.
V56 res/values-es/strings.xml:66 — "Pegue" (usted) vs tú elsewhere; values-pl:244 "talii" vs "zestaw".
V57 strings.xml — duplicate keys (dashboard_name_label=settings_rename_label; app_settings_study_modes=study_modes_title; errors_retry=study_answer_retry; import_pages_count reused; mode_list_new dual use); import_error_page_count hardcodes "2 to 20".
V58 res/mipmap-anydpi/ic_launcher.xml:5 — monochrome layer reuses full-color foreground PNG → themed icon blob. (VERIFIED)
V59 res/values/themes.xml — no values-night; splash colors follow system.
V60 ScreenScaffold.kt:42 / ui/components/States.kt:57-93 — ErrorState retry only if passed; DeckMissing no Go back.

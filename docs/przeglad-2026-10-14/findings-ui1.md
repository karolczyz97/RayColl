# Findings: ui/study (except VM), ui/dashboard, ui/browse, ui/components, ui/theme, ui/archive, ui/stats, strings (paths relative to app/src/main/java/com/raycoll/app/)

U0 ICON SHIFT ui/study/StudyCard.kt:351-352 — AudioBadge is in-flow child above page text in CardPage Column → ~32dp shift on every TTS/STT step; when audio moves page0→page1 both pages jump. User wants indicator still in the card but not moving text. Chosen design (orchestrator recommendation "B"): active page gets tinted rounded background (primaryContainer when speaking, tertiaryContainer when listening) with constant padding always reserved; small speaker/mic icon absolutely positioned in a constant left gutter of the page; text never moves; label text kept only for TalkBack (semantics/liveRegion). Hold last value briefly to avoid flicker.
U1 BUG ui/study/SessionSummary.kt:45-105 — summary not scrollable; landscape clips buttons.
U2 BUG ui/study/RatingButtons.kt:70,77-82 + res/values-pl/strings.xml:42-45 — "Powtórz (Again)" etc: English in PL, 12sp maxLines=1 on ~68dp → ellipsis; fixed height 68dp clips large fonts. (VERIFIED strings)
U3 = U0.
U4 ui/components/SegmentedProgressBar.kt:49-59, SessionSummary.kt:139-156, DashboardScreen.kt:203 — stats/progress bars use pastel *Bg colors ~1.1-1.3:1 contrast; future segments alpha 0.25 invisible; dark mode vanish; no a11y description/legend.
U5 BUG ui/components/UiEvents.kt:49-57 — CollectEvents handler suspends on showSnackbar; later events (Saved, navigation) wait until snackbar times out. (VERIFIED)
U6 BUG StudyCard.kt:356-361 — fontSize overridden to 28/24/20/18sp but lineHeight stays from titleLarge/bodyLarge (~25/24sp) → cramped multi-line.
U7 StudyCard.kt:110-112 — animateScrollTo(maxValue) launched before expand finishes; never scrolls to AnswerPanel.
U8 ui/study/StudyScreen.kt:147-155 — paused banner plain Row inserted → card jumps ~48dp on pause/resume; unstyled.
U9 StudyCard.kt:229-241 + RatingButtons.kt:70 — controls slot toggles 68/48/Spacer48 → card viewport resizes 20dp.
U10 RatingButtons.kt:50-53 — rating colors reuse card-category colors (Good=orange). Suggest dedicated colors, interval under label, haptics.
U11 RatingButtons.kt:79 / ui/theme/Type.kt:27-28 — labelMedium 12sp for rating labels; labelSmall 11sp for badges.
U12 StudyCard.kt:163-169 — whole study area clickable(indication=null) without role/onClickLabel; FlashcardView pointerInput no semantics.
U13 StudyScreen.kt:212-229 — mic permission denied: nothing shown until STT fails; no rationale/settings path.
U14 StudyCard.kt:140-143 / ui/study/AnswerPanel.kt:72-78 — answer result not announced; no liveRegion anywhere.
U15 SessionSummary.kt:146-149 — current segment alpha 1.0 vs completed 0.85 indistinguishable; >30 cards gap 0.
U16 SessionSummary.kt:139-156 — SessionSegments rebuilds segments + getCardCategory every state emission; unstable list.
U17 SessionSummary.kt:69 — computeCardStats in composition each recomposition.
U18 ui/dashboard/DashboardScreen.kt:208-209,227-228 + StudySplitButton.kt:62-95 — height(40.dp) touch targets <48.
U19 DashboardScreen.kt:220-229 — Study button prominent when dueCount==0 → NoDueCards screen.
U20 DashboardScreen.kt:101-110 + values/strings.xml:35 — FAB "+" only imports; empty text "Click + to add one" + redundant Import button; "Click" wrong for touch.
U21 DashboardScreen.kt:203 + ui/stats/StatsScreen.kt:70 — per-deck bars no numbers/legend/contentDescription.
U22 ui/browse/BrowseScreen.kt:288-309 — permanent edit/delete row per card; delete next to edit. Suggest tap=edit, delete via swipe/overflow + Undo.
U23 BrowseScreen.kt:198-229 — header ~160dp pinned; landscape tiny list; bar duplicates chip counts.
U24 BrowseScreen.kt:212-214 — same empty text for empty deck vs no results.
U25 BrowseScreen.kt:261-311 — no per-card SRS info; no maxLines on page text.
U26 ui/browse/CardEditorDialog.kt:57-93 — up to 20 fields in AlertDialog; scrim tap discards draft; Save silently disabled under MIN_FILLED_PAGES; no autofocus/imeAction.
U27 BUG ui/components/PersistedTextField.kt:30-34 — on blur LaunchedEffect resets text to storedValue before write echoes → flicker; isError without supportingText; KeyboardOptions.Default dead.
U28 ui/components/ScreenScaffold.kt:40-63 — Box no background; side strips use window color on wide screens; top bar not full width; no scrollBehavior.
U29 ui/theme/Theme.kt:103-107 + SrsColors.kt — dynamic color on: fixed SRS hues clash; dynamic scheme recomputed each recomposition.
U30 AnswerPanel.kt:92,116-120 + strings.xml:277 — "Good (+3)" looks like points.
U31 StudyScreen.kt:113 — Loading title ""; exit confirm vague, doesn't say progress saved.
U32 res/values-pl/strings.xml — "zestaw" vs "talia" inconsistent; dashboard_due_count "Pozostało: %d" ≠ due; study_finished_desc "opanowane"; study_session_ended_desc gendered "Wyszedłeś".
U33 res/values/strings.xml — mixed Title Case; study_answer_retry dup of errors_retry; emoji in study_bravo.
U34 ui/components/MigrationDialog.kt:23-35 — "Start fresh"/"Don't sign in" consequences unclear.
U35 StudyCard.kt:337-344 — peek Surface outline text low contrast; hold-to-peek undiscoverable.
U36 StudyCard.kt:172-181 — short card sticks to top; center vertically.
U37 StudyCard.kt:185-207 — Next skips without undo; swipe gestures.
U38 StudyScreen.kt:92-104 — predictive back progress snaps back instantly.
U39 StudyScreen.kt:129 — imePadding dead.
U40 StudyScreen.kt:192-209 — missing-voice AlertDialog duplicates ConfirmDialog.
U41 ui/dashboard/StudySplitButton.kt:98 — contentDescription and onClickLabel both "Study modes"; disabled no cue; menu items no selected semantics.
U42 DashboardScreen.kt:187-202 — deck card not tappable; a11y_deck_menu same for every deck.
U43 DashboardScreen.kt:113-116 — FAB pops without animation; sync state only in Settings.
U44 ui/archive/ArchiveScreen.kt:91-98 — restore no feedback/undo; red "Deletes in N days" alarming; no retention explainer.
U45 ui/archive/ArchiveViewModel.kt:48 — archiveDaysLeft ?: 0 shows "Deletes today" when date missing.
U46 StatsScreen.kt:127-148 — SrsDistribution SpaceBetween wraps/overlaps large font; distribution rendered 3 ways.
U47 ui/stats/ActivityHeatmap.kt:44-98 — no month/weekday labels, legend, scroll affordance; thresholds hard-coded; recompute.
U48 StatsScreen.kt:45-73 — new user all zeros, no empty state.
U49 ui/components/States.kt:30-55 — bare spinner; ErrorState onRetry never passed (dead); deck missing no CTA.
U50 ui/components/SyncStatusBanner.kt:43-48 — static sync icon; no liveRegion.
U51 ui/components/LanguagePicker.kt:19-43 — cryptic "EN"; current not marked; no description.
U52 ui/components/ModeListItem.kt:55 — Restore disabled without explanation; delete adjacent edit.
U53 ui/components/RayCollCard.kt:19-25 — ElevatedCard 2dp shadow dated; no onClick overload.
U54 ui/theme/Type.kt — study text sizes not tokens.
U55 ui/theme/Spacing.kt — no tokens for 48 touch, button height, icon size, max width; hard-coded dp across files.
U56 StudyCard.kt:106-107,215-216,267-268,296-297 — remember{RayCollMotion.effects()} repeated in 4 composables; check MaterialTheme.motionScheme availability.
U57 shared — "tinted Surface with icon+text" pattern 7x (AudioBadge, ResultChip, ModeBadge, due badge, SyncStatusBanner, SrsStatItem, peek); Previous/NextCardButton near-identical.
U58 formatter artefacts — StudyCard.kt:313-315,334-335, BrowseScreen.kt:273-286, ArchiveScreen.kt:63-65, SessionSummary.kt:89-91, StudyScreen.kt:234-236 `if (index >\n0\n)`.
U59 StudyCard.kt:132-143,229-232, SessionSummary.kt:56-68 — domain logic in UI → derived fields in StudyUiState.
U60 whole app — no @Preview anywhere.
U61 UI state classes not @Immutable; BrowseViewModel recomputes stats/filter per keystroke without debounce.
U62 StudyScreen.kt:57-210 — ~150-line composable; extract parts; hoist actions remember.
U63 DashboardScreen.kt:212 vs ArchiveScreen.kt:96 — inconsistent button icon sizes and shapes.
U64 SegmentedProgressBar.kt name mismatch; SessionSummary.kt contains SessionProgressBar & NoDueCards.
U65 BrowseScreen.kt:110 — FAB contentDescription btn_add instead of browse_add_card; lambda → method ref.

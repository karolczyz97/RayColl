# Skan 15.10.2026 — UI: nauka, pulpit, komponenty, motyw, nawigacja

Pełna lista agenta (tylko odczyt kodu, gałąź `przeglad-modernizacja`). Zbiorczy raport: [RAPORT.md](RAPORT.md).

## Ocena ogólna

Obszar jest w dobrym stanie po wczorajszych poprawkach. Nie znalazłem w nim komentarzy o „TS”, „parytecie” ani Expo. Naprawy U5, U8, U9, U12, U14, U16, U18, U19 i U30 działają zgodnie z werdyktem. Najpoważniejsze nowe znaleziska to błąd w poprawce U13 (komunikat o mikrofonie nie znika), komunikaty błędów powtarzane przy każdej karcie oraz dwa miejsca, w których układ ekranu się przesuwa lub ucina animację: podsumowanie sesji i przycisk „Dotknij, aby odkryć”. Motyw i nawigacja są spójne. Animacje są zgodne z RayCollMotion, z wyjątkiem kilku domyślnych (opisane niżej). Nie uruchamiałem Gradle ani aplikacji, więc wszystko poniżej wynika z czytania kodu.

## Znaleziska (od najważniejszych)

**N1. [bug] [S] StudyScreen.kt:93-97** (poprawka U13 niekompletna) — Gdy brakuje zgody na mikrofon, komunikat ma przycisk akcji i nie podano czasu trwania. Material 3 ustawia wtedy `Indefinite`, więc komunikat wisi bez końca nad dolnymi przyciskami (poprzednia/następna karta) i blokuje kolejne komunikaty. Po przyznaniu zgody w systemowym oknie komunikat „Sprawdź dostęp do mikrofonu” dalej wisi, bo nikt go nie zamyka. → Dać `duration = SnackbarDuration.Long`. W `onMicrophoneGranted` wywołać `snackbarHostState.currentSnackbarData?.dismiss()`. Zmienia zachowanie: tak.

**N2. [bug/UX] [S/M] StudyViewModel.kt:152-155, StudyScreen.kt:85-99** — Każdy błąd mowy trafia na ekran jako osobne zdarzenie, bez deduplikacji. Brak głosu TTS to okno „Missing TTS voice”, które wraca przy każdej następnej karcie po zamknięciu. Błędy TTS i STT układają się w kolejkę identycznych komunikatów, po 4 s każdy. → W ViewModelu pamiętać zgłoszone typy błędów na sesję (okno braku głosu pokazać raz). W ekranie przed kolejnym komunikatem wywołać `currentSnackbarData?.dismiss()`. Zmienia zachowanie: tak.

**N3. [UX] [S] SessionSummary.kt:73, StudyViewModel.kt:185 i 192-199** — Pasek rozkładu kart w podsumowaniu pojawia się dopiero po zapisie ocen i odczycie z bazy (`resultCards` jest przez chwilę `null`). Karta jest wyśrodkowana pionowo, więc cała treść przeskakuje o ok. 28 dp po ułamku sekundy. To ta sama klasa problemu co ikonki w U0. → Od razu pokazać pusty tor (`SegmentedBar(emptyList())`) i wypełnić go animacją albo zawinąć w `AnimatedVisibility`. Zmienia wygląd: tak.

**N4. [UX] [S] StudyCard.kt:249-274** — `AnimatedContent` ma stan `Boolean` (oceny / nie oceny), a warunek `canReveal` jest czytany spoza stanu. Wychodząca zawartość widzi najnowsze wartości, więc przy ostatnim odsłonięciu „Dotknij, aby odkryć” znika natychmiast zamiast wyjechać. Przejścia „odkryj ↔ pusto” (start nasłuchu, nadejście odpowiedzi głosowej) nie mają animacji w ogóle. → Zrobić stan 3-wartościowy (`enum { RATINGS, REVEAL, NONE }`) liczony przed `AnimatedContent`. Zmienia wygląd: tak (drobny).

**N5. [bug] [S] StudyScreen.kt:196-207, StudyViewModel.kt:281-287** — Okno „Opuścić sesję?” nie zamyka się, gdy sesja skończy się samodzielnie. Tryb audio sam przechodzi po kartach i nie jest wstrzymywany. Jeśli użytkownik potem kliknie „Wyjdź”, `endSessionEarly()` bez sprawdzenia `finished` oznacza zakończoną sesję jako „przerwaną” i liczy `completedCardCount` od bieżącego indeksu. → Dodać `if (finished) return` w ViewModelu i zamykać okno, gdy `inProgress` zmieni się na false. Zmienia zachowanie: nie (naprawa poprawności). Pytanie do decyzji: czy okno ma też wstrzymywać audio (zob. pytania na końcu).

**N6. [UX] [S] StudyScreen.kt:155-193** — Przejścia Ładowanie → Nauka → Podsumowanie → Restart to twarde cięcia (zwykłe `when`). Podsumowanie z „Brawo! 🎉” pojawia się bez animacji. → Owinąć `when` w `Crossfade` albo `AnimatedContent` z `RayCollMotion.effects()` i `contentKey = { it::class }`. Zmienia wygląd: tak.

**N7. [UX] [S] DashboardScreen.kt:275-295** — Liczba kart do powtórki, najważniejsza liczba na pulpicie, jest w najmniejszej czcionce (`labelSmall`, 11 sp) w pastylce obok nazwy trybu. → Powiększyć do `labelMedium` albo dopisać liczbę do przycisku „Ucz się” (np. „Ucz się · 12”). Zmienia wygląd: tak.

**N8. [dostępność] [S] AnswerPanel.kt:66-69** — Napis „Pominięto” ma kolor `outline` na `surfaceContainerHighest`. Kontrast wynosi 3,45:1 (jasny) i 3,86:1 (ciemny), czyli poniżej 4,5:1 dla zwykłego tekstu 16 sp. → Użyć `onSurfaceVariant` (7,2:1). Zmienia wygląd: lekko.

**N9. [UX] [S] RatingButtons.kt:60-63** (nowy argument do U10) — „Dobrze” używa `primaryContainer`, czyli koloru z motywu dynamicznego (domyślnie włączony na Pixelu). Pozostałe trzy oceny mają stałe barwy. Przy zielonej lub żółtej tapecie „Dobrze” może wyglądać jak „Łatwe” albo „Trudne”. → Dodać stały kolor „good” do `SrsColors` (np. niebieski) w obu motywach. Zmienia wygląd: tak.

**N10. [UX] [M] RatingButtons.kt:55, AnswerPanel.kt:90-101** — Po odpowiedzi głosowej panel pisze „Sugestia: Dobrze”, ale przyciski nie pokazują, który jest sugerowany. → Przekazać `suggested: Int?` do `RatingButtons` i dać sugerowanemu przyciskowi 2 dp ramkę. Zmienia wygląd: tak (do akceptacji).

**N11. [UX/tekst] [S] AnswerPanel.kt:112-117, `study_answer_incorrect_threshold`** — „Źle • 45% / 80%” nie mówi, że 80% to wymagany próg. → Zmienić tekst na „Źle • 45% (wymagane 80%)” w 5 językach (strings.xml × 5). Zmienia zachowanie: nie, tylko tekst.

**N12. [dostępność] [S] SessionProgressBar.kt:38-42** — TalkBack czyta „3/20” niejednoznacznie (może jak datę). → Dodać `semantics { contentDescription = "Karta 3 z 20" }` i nowy tekst `study_progress_a11y` w 5 językach. Zmienia zachowanie: tylko dla czytnika ekranu.

**N13. [UX] [S] DashboardScreen.kt:149-160, 186-200** — Lista talii nie ma `Modifier.animateItem()`. Po archiwizacji lub imporcie karta znika albo pojawia się skokowo, a reszta przeskakuje. → Dodać parametr `modifier` do `DeckCard` i przekazać `Modifier.animateItem()` z `items`. Zmienia wygląd: tak.

**N14. [UX] [S] StudyScreen.kt:124-139** (skutek uboczny U8) — Po pauzie na pasku tytułu pojawiają się „Wstrzymano” i przycisk „Wznów”, razem ok. 170 dp. Tytuł talii dostaje wtedy ok. 130–170 dp i skraca się do „Angielski – s…”. → Zostawić tylko przycisk „Wznów” z ikoną albo przenieść komunikat do paska postępu. Zmienia wygląd: tak.

**N15. [modernizacja] [M] Theme.kt:116-122, RayCollMotion.kt, NavGraph.kt:36-38** — Komponenty biblioteki (menu, okna, przyciski) używają standardowego schematu ruchu, a własne animacje to ręczna kopia „expressive”. W AAR material3 1.4.0 z cache Gradle jest publiczne `MaterialExpressiveTheme` (eksperymentalne) i `MaterialTheme.motionScheme`; fabryki `MotionScheme.expressive()` są internal. → Zamienić `MaterialTheme(` na `MaterialExpressiveTheme(` bez podawania `motionScheme` (do potwierdzenia w buildzie, że domyślnie jest expressive). Potem `RayCollMotion` zastąpić `MaterialTheme.motionScheme.defaultSpatialSpec/defaultEffectsSpec`. Wymaga `@OptIn` i oględzin na telefonie. Zmienia wygląd: tak (drobne różnice w ruchu).

**N16. [czytelność] [M] DashboardScreen.kt:137 + ok. 20 miejsc w ui/\*\*** — Konwencja to `shape = MaterialTheme.shapes.small` na każdym przycisku, wpisywane ręcznie. Przycisk „Importuj” w pustym pulpicie jest jedynym bez tego i ma domyślny kształt pigułki. → Natychmiast: dodać `shape` w linii 137. Docelowo: wspólne `RayCollButton` i `RayCollOutlinedButton` w `ui/components`. Zmienia wygląd: tak (tylko ten jeden przycisk).

**N17. [bug] [S] StudyCard.kt:136-143** (poprawka U7 niekompletna) — Werdykt przewidywał `withTimeoutOrNull(700)`. W kodzie jest `snapshotFlow{maxValue}.collectLatest{animateScrollTo}` bez końca. Gdy panel odpowiedzi rośnie podczas nasłuchu (częściowy tekst), każda zmiana wysokości wciąga widok na dół, nawet jeśli użytkownik przewinął w górę. → Owinąć pętlę w `withTimeoutOrNull(700)`. Zmienia zachowanie: tak (drobne).

**N18. [dostępność] [S] States.kt:59-86** — `MessageState` (EmptyState, ErrorState, NoDueCards) nie przewija się. W poziomie przy dużej czcionce (ok. 2×) ikona, tekst i przycisk (~350 dp) nie mieszczą się w ok. 270 dp i przycisk jest ucięty. → Dodać `verticalScroll(rememberScrollState())` na kolumnie. Zmienia wygląd: nie.

**N19. [UX] [S] SessionSummary.kt:61-77** — Tytuł (28 sp) i opisy nie mają `textAlign = Center`. „Session ended early” (EN) i „Sitzung vorzeitig beendet” (DE) zawijają się w ok. 264 dp, a ich linie są wyrównane do lewej w wyśrodkowanej kolumnie. → Dodać `textAlign = TextAlign.Center`. Zmienia wygląd: tak.

**N20. [UX] [S] AnswerPanel.kt:40-48** — `clickable` jest na `modifier` powierzchni, więc leży na zewnątrz przycinania do zaokrąglenia. Rozbłysk dotyku ma prostokątne rogi poza łukiem 16 dp. → Użyć `Surface(onClick = onTap, …)` albo dać `.clip(shape)` przed `clickable`. Zmienia wygląd: lekko.

**N21. [dostępność] [S] AnswerPanel.kt:149-165** — `ResultChip` to jeden `Row` bez zawijania. Przy czcionce ≥ 1,3× i polskich tekstach plakietka „Sugestia: Powtórz” się ściska. → Zamienić na `FlowRow`. Zmienia wygląd: tylko przy dużej czcionce.

**N22. [dostępność] [S] DashboardScreen.kt:218-249, StudySplitButton.kt:72-90** — Przyciski „Przeglądaj” i „Ucz się” na każdej karcie mają identyczne etykiety dla TalkBack. U42 naprawiło tylko menu „⋮”. → Dodać `semantics { contentDescription = "Ucz się: <nazwa talii>" }` i analogicznie dla „Przeglądaj” (nowe teksty ×5). Zmienia zachowanie: tylko dla czytnika ekranu.

**N23. [UX] [S] MigrationDialog.kt:25-37** — Trzy `TextButton` w kolumnie mają tę samą wagę wizualną. „Nie loguj się” (które wylogowuje) wygląda jak „Przenieś na konto”. → `Button` dla „Przenieś”, `TextButton` dla pozostałych albo przesunąć „Nie loguj się” do `dismissButton`. Zmienia wygląd: tak.

**N24. [modernizacja] [S] StudyCard.kt:146** — Zwarty układ karty zależy od orientacji, nie od wysokości okna. Na tablecie lub w rozłożonym foldzie w poziomie czcionka karty jest bez potrzeby mniejsza o 28%, a w trybie wielu okien w pionie układ nie przechodzi na zwarty. → Warunek `LocalConfiguration.current.screenHeightDp < ~480`. Zmienia zachowanie: tylko na dużych ekranach.

**N25. [UX] [S] DashboardScreen.kt:126-128, 140-148** — Padding Scaffolda (z dolną krawędzią systemową) jest nałożony na cały `Crossfade`. Lista kończy się nad paskiem nawigacji i ucina karty zamiast przewijać się pod przezroczystym paskiem. → Dolny padding przenieść do `contentPadding` listy: `padding.calculateBottomPadding() + Spacing.fabClearance`. Zmienia wygląd: lekko.

**N26. [modernizacja] [S] DashboardScreen.kt:77, 127-130** — `updateTransition(...).Crossfade(...)` wymaga `@OptIn(ExperimentalAnimationApi)` (sprawdziłem: w animation 1.12.1 to API jest nadal eksperymentalne) i używa domyślnego `tween(300)` zamiast RayCollMotion. → Zwykły, stabilny `Crossfade(targetState = uiState, animationSpec = RayCollMotion.effects(), contentKey = { it::class })`; usunąć OptIn. Zmienia wygląd: lekko.

**N27. [czytelność] [S] StudyScreen.kt:71-220** — `StudyScreen` ma ok. 150 linii. Obsługa zdarzeń (30 linii), dwa dialogi i `when` stanów są wymieszane. → Wyciągnąć `StudyEventsEffect(...)`, `StudyDialogs(...)` i `StudyContent(...)`. `StudyBackGesture` jest już wydzielony. Zmienia wygląd: nie.

**N28. [czytelność] [M] StudyCard.kt (482 linie)** — Jeden plik zawiera układ ekranu, sterowanie, kartę, strony i logikę znacznika dźwięku. → Po zakończeniu pracy nad U0 podzielić na `StudyLayout.kt`/`StudyActions.kt` (układ i oceny) oraz `CardPages.kt` (karta, strony, `AudioMark`, `heldAudio`). To czysty podział plików. Zmienia wygląd: nie.

**N29. [czytelność] [S] DashboardScreen.kt:276-295 vs ModeListItem.kt:100-109** — Pastylka z liczbą do powtórki powiela prywatny `ModeBadge` (te same: `Surface(extraSmall)`, `labelSmall`, padding `s`/`xxs`). → Udostępnić `ModeBadge` jako publiczny `PillBadge` w `ui/components` i użyć w `DeckSubtitle`. Zmienia wygląd: nie.

**N30. [martwy-kod/czytelność] [S]** — Drobiazgi bez zmiany zachowania:
- `StudyCard.kt:194-200` — `remember { MutableInteractionSource() }` jest zbędne; w obecnym Compose można dać `interactionSource = null`.
- `StudyCard.kt:272` — `Spacer(Modifier.height(48.dp))` jest zbędny, bo `heightIn(min = 68.dp)` już rezerwuje miejsce.
- `DashboardViewModel.kt:27` — `@Immutable` nic nie daje przy strong skipping (Kotlin 2.4).
- `DashboardScreen.kt:75` — własne `MinTouchTarget` dubluje `minimumInteractiveComponentSize()`.

**N31. [UX/tekst] [S] values-pl/strings.xml:50-51, 143, 294** — Ten sam obiekt raz jest „kartą” („Poprzednia karta”, „Przerobione karty”), a raz „fiszką” (przeglądanie, import, `study_finished_desc`). → Ujednolicić nazewnictwo (do decyzji, patrz pytania). Zmienia zachowanie: tylko teksty.

## TOP 5

1. **N1 + N2** — komunikaty błędów: dać `Long` zamiast `Indefinite`, zamykać po przyznaniu zgody, nie powtarzać okna „brak głosu” przy każdej karcie.
2. **N3** — pasek w podsumowaniu pojawia się z opóźnieniem i przesuwa treść; zarezerwować miejsce.
3. **N4** — „Dotknij, aby odkryć” znika skokowo przy każdej karcie; stan 3-wartościowy.
4. **N7** — liczba kart do powtórki w 11 sp; powiększyć lub dać do przycisku „Ucz się”.
5. **N5** — poprawka poprawności: okno wyjścia po końcu sesji nie powinno oznaczać jej jako przerwanej.

## Pytania do decyzji

- **N10:** czy podświetlać na przyciskach ocen ocenę sugerowaną po odpowiedzi głosowej?
- **N5:** czy okno „Opuścić sesję?” ma wstrzymywać audio, dopóki jest otwarte?
- **N7:** powiększyć pastylkę czy dopisać liczbę do przycisku „Ucz się”?
- **N9:** jaki stały kolor dla oceny „Dobrze”?
- **N31:** „karta” czy „fiszka” w interfejsie?

**Model do poprawek:** Sonnet wystarczy do N1, N3, N4, N8, N11–N14, N16, N18–N23, N25, N26, N29, N30. Do N2, N5, N15 i N28 (zmiany w ViewModelu, motywie i strukturze plików) lepszy będzie Opus.

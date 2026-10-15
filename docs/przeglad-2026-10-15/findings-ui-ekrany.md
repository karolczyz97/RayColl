# Skan 15.10.2026 — UI: przeglądanie, import, ustawienia, edytor trybów, archiwum, statystyki

Pełna lista agenta (tylko odczyt kodu, gałąź `przeglad-modernizacja`). Zbiorczy raport: [RAPORT.md](RAPORT.md).

## Zakres i ścieżki

Ścieżki są względem `C:\Users\Karol\.gemini\antigravity\scratch\RayColl\app\src\main\java\com\raycoll\app\ui\`. Pliki tekstów to `C:\Users\Karol\.gemini\antigravity\scratch\RayColl\app\src\main\res\values*\strings.xml`. Numery linii pochodzą z aktualnej gałęzi `przeglad-modernizacja`.

Niczego nie uruchamiałem na telefonie ani w Gradle. Kontrasty policzyłem skryptem w node. Nie czytałem `docs/przeglad-2026-10-15/` (nieśledzony katalog raportów innych agentów).

## Ocena ogólna

Obszar jest w dobrej kondycji po wczorajszych poprawkach. Na ekranach nie ma zaszytych kolorów, więc tryb ciemny działa. W komentarzach nie ma już odwołań do TS, Expo ani parytetu. Nie znalazłem nieużywanych stringów ani nieużywanych funkcji na najwyższym poziomie plików.

Zostały trzy rodzaje problemów:
1. **Luki w przepływach.** Import kasuje szkic przy „Wstecz”. Kilka akcji kończy się bez żadnej informacji zwrotnej, np. dodanie karty i import.
2. **Niedokończone poprawki.**
   - Wspólne komponenty (SectionCard, chipy wyboru) nie są używane wszędzie.
   - Opisy dla czytników ekranu brakują w kilku miejscach.
   - Żargon STT/TTS został w tekstach trybów.
3. **Zbyt długie pliki.** ModeDetailScreen, StepCard, ImportViewModel i DeckSettingsScreen mają 340–390 linii.

## Znaleziska (od najważniejszych)

N1. [bug] [M] `imports/ImportScreen.kt:84-87` — Strzałka „Wstecz” i gest cofania zamykają import i kasują wszystko: nazwę talii, wklejony tekst, poprawione komórki podglądu i ustawienia kolumn. Edytor trybów pyta o to od V4, import nie.
→ Policzyć `dirty` (nazwa lub tekst niepuste) i dodać `BackHandler(enabled = dirty && !importing)`. Strzałka w pasku ma iść tą samą ścieżką. Dodać `ConfirmDialog` z flagą `rememberSaveable`, jak w `modedetail/ModeDetailScreen.kt:78,96-97,171-183`. Obecne `mode_editor_discard_*` mówią o „trybie”, więc trzeba ogólnych tekstów ×5 języków.
Zmienia zachowanie: tak.

N2. [bug] [S] `components/UiEvents.kt:56`, `navigation/NavGraph.kt:130-134`, `imports/ImportScreen.kt:73`, `decksettings/DeckSettingsScreen.kt:86`, `modedetail/ModeDetailScreen.kt:89` — Zdarzenie zamykające ekran (`Imported`, `Closed`) może zostać odebrane i zignorowane.
- Handler startuje od razu, gdy ekran osiąga STARTED, a `popIfResumed` wymaga RESUMED. Dzieje się tak po powrocie z tła albo po obrocie.
- Zdarzenie jest wtedy zużyte, a ekran zostaje. W imporcie zostaje wieczny spinner i zablokowane pole (`importing` zostaje `true` po sukcesie, `ImportViewModel.kt:223-227`), choć talia już istnieje. W ustawieniach talii po archiwizacji zostaje stary widok.
- To wynika z kodu i standardowego zachowania Navigation, ale warto potwierdzić testem.
→ Dla zdarzeń zamykających użyć strażnika akceptującego STARTED (np. „to jest nadal bieżący wpis stosu i nie jest DESTROYED”). Strażnik RESUMED zostawić dla tapnięć.
Zmienia zachowanie: tak (naprawa).

N3. [UX] [S–M] `browse/BrowseScreen.kt:90-93`, `browse/BrowseViewModel.kt:102-105`, `data/local/db/Daos.kt:102` — Po „Zapisz” nowej karty okno znika bez żadnej informacji. Lista jest sortowana po `rowid`, więc nowa karta jest na samym końcu. Przy aktywnym wyszukiwaniu lub filtrze jest w ogóle niewidoczna.
→ `Saved(isNew)` ma wywołać snackbar „Dodano fiszkę” (nowy string ×5). Dodatkowo wyczyścić filtr i wyszukiwanie, a listę przewinąć do ostatniego elementu (`LazyListState` do wyniesienia z `BrowseContent`).
Zmienia zachowanie: tak.

N4. [UX] [S] `browse/BrowseScreen.kt:215-225` i `:229-241` — Pole wyszukiwania nie ma przycisku „X” ani akcji Search na klawiaturze. W USTALENIACH wyszukiwarka z przyciskiem czyszczenia jest zapisana jako zatwierdzona, a w wersji Kotlin jej brak. Pusty wynik wyszukiwania nie ma żadnej akcji, więc trzeba ręcznie kasować tekst i klikać „Wszystkie”.
→ `trailingIcon` z ikoną Clear, gdy `query` niepusty (opis ×5). `KeyboardOptions(imeAction = Search)` zamykające klawiaturę. W pustym wyniku przycisk „Wyczyść filtry”.
Zmienia zachowanie/wygląd: tak.

N5. [UX] [L] `imports/ImportScreen.kt:299-322`, `domain/imports/HeaderDetector.kt:52-56` — Wiersz podglądu to N jednowierszowych pól o równej szerokości. Na telefonie 360 dp przy 3 kolumnach to ok. 90 dp na pole. Przy 4–5 kolumnach tekst jest nieczytelny.
- `detectPageCount` nie ma górnej granicy. Błąd dopiero przy imporcie (`import_error_page_count`, 2–20).
- Dodatkowa kolumna (np. przecinek w tekście, gdy wykryto przecinek jako separator) dodaje kolejne pole.
→ Gdy kolumn > 2, pokazywać wiersz jako kompaktową kartę z polami w pionie i etykietami = nazwy stron. Albo wiersz tylko do odczytu, a dotknięcie otwiera istniejący `CardEditorDialog`. Do tego ostrzeżenie, gdy kolumn > `MAX_VISIBLE_PAGE_COUNT`.
Zmienia wygląd: tak.

N6. [UX] [S] `imports/ImportScreen.kt:89-94,137,144-180` — Pasek z przyciskiem Import jest zawsze na dole, a całość ma `imePadding()`. Pasek zajmuje co najmniej 72 dp (12+48+12), plus linię podpowiedzi, gdy przycisk jest wyłączony. W poziomie z klawiaturą zostaje na listę resztka po odjęciu klawiatury, paska tytułu (64 dp) i tego paska. Wymaga sprawdzenia na urządzeniu.
→ Ukrywać `SubmitBar`, gdy klawiatura jest widoczna (`WindowInsets.isImeVisible`). Alternatywnie dać „Importuj” jako akcję w górnym pasku w poziomie.
Zmienia wygląd: tak.

N7. [UX] [S] `modedetail/StepTexts.kt:28-47`, wszystkie `values*/strings.xml` (`mode_issue_no_terminal`, `mode_issue_auto_rate_without_stt`) — Komunikaty o błędach trybu cytują nazwy kroków, których nie ma na liście.
- pl: komunikat mówi „Oceń z odpowiedzi” i „Sprawdź mowę (STT)”, a kroki nazywają się „Oceń automatycznie z wyniku” i „Sprawdź wymowę (STT)”.
- pl: „Pokaż przyciski ocen” kontra „Pokaż przyciski oceny”.
- de/es/it mają to samo; po angielsku tylko „Next card” kontra „Go to next card (no rating)”.
→ Wstawić `%1$s` w komunikatach, a `issueText` ma podawać `stringResource(R.string.step_*)`. Nazwy zawsze się zgodzą.
Zmienia zachowanie: nie (tylko tekst).

N8. [UX] [S] `values*/strings.xml` (en: 144, 147, 154-155, 218, 128; pl: 150, 153, 160-161, 228, 134, 137-138, 173) — V8/V49 poprawiły tylko tytuły kroków. Został żargon w pierwszej grupie okna „Dodaj krok”: „Grade STT”, „Oceń po STT”, „Speak page (TTS)”, „Check speech (STT)”.
- Karta kroku nadal nazywa się „Compound: …”, a grupa „Ready-made blocks”. W pl: „Złożony: …”.
- Anglicyzmy w pl: „Pauza dyn. (str. …)”, „Fallback ręczny”, „feedback sukcesu”, „tapnięcie”.
- Tytuł kroku „×1.3” zaokrągla mnożnik, który w polu ma np. 1.25 (`%2$.1f`).
→ Zamienić STT/TTS na „rozpoznawanie mowy”/„odczyt”, „Złożony: X” na samo „X”, rozwinąć skróty, pokazać pełny mnożnik.
Zmienia zachowanie: nie (tylko tekst).

N9. [dostępność] [S] `browse/BrowseScreen.kt:333`, `imports/ImportScreen.kt:302-308`, `:119-122`, `:254`, `modedetail/ModeDetailScreen.kt:283-286` — Czytnik ekranu czyta „Usuń fiszkę” identycznie przy każdej karcie. V15 poprawiło to wszędzie indziej, tu nie. Pola komórek podglądu nie mają żadnej etykiety. „Podgląd (N)”, „Konfiguracja stron” i „Kroki trybu” nie są nagłówkami.
→ `a11y_item_action` z początkiem pierwszej strony karty. Pola komórek: `semantics { contentDescription = "<nazwa strony>, wiersz N" }`. Do nagłówków `heading()`.
Zmienia zachowanie: nie.

N10. [dostępność] [S] `modedetail/StepCard.kt:332-341`, `appsettings/SettingsSections.kt:279-296`, `studymodes/StudyModesScreen.kt:155-159` — Cele dotyku i role.
- `LabeledSwitch` ma wysokość ok. 32 dp (Switch bez `onCheckedChange` nie dostaje minimalnego celu dotyku), `SwitchRow` ok. 40 dp.
- Karta trybu używa `Modifier.clickable(onClick)` bez roli i bez `onClickLabel`, w przeciwieństwie do `StudyModesLink` i `CardItem`. `RayCollCard` nie ma wersji z `onClick`, więc efekt dotyku może nie być przycięty do zaokrąglonych rogów (do obejrzenia na telefonie).
→ `heightIn(min = 48.dp)` w obu wierszach przełączników. W `RayCollCard` dodać wariant `onClick` oparty na `ElevatedCard(onClick)`, z rolą i etykietą.
Zmienia wygląd: lekko.

N11. [UX] [M] `modedetail/ModeDetailViewModel.kt:112`, `modedetail/ModeDetailScreen.kt:303-312` — Nowy krok zawsze ląduje na końcu listy. Wstawienie go na 3. miejsce przy 12 krokach to ok. 9 tapnięć strzałką.
→ Parametr `insertAfter`. Na karcie przycisk „Dodaj poniżej”, albo okno „Dodaj krok” wstawia po aktualnie zaznaczonym kroku.
Zmienia zachowanie: tak.

N12. [UX/czytelność] [S] `browse/BrowseScreen.kt:266-281`; ten sam wzorzec w `decksettings/DeckSettingsScreen.kt:279-295`, `appsettings/SettingsSections.kt:117-134`, `modedetail/StepCard.kt:128-147,234-254` — Chipy wyboru pojedynczego (FilterChip + rola RadioButton) są skopiowane w 4 miejscach. W przeglądaniu kart ten wzorzec ma poziome przewijanie i brak roli RadioButton. Pięć chipów „Wszystkie (n) … Opanowane (n)” nie mieści się w 360 dp, ostatnie są ukryte bez wskazówki — to samo, co V34 naprawiło w ustawieniach talii.
→ Wspólny `ChoiceChipRow(options, selected, onSelect)` w `components/` (FlowRow + rola). Użyć w 5 miejscach.
Zmienia wygląd: tak (Browse w 2 liniach).

N13. [UX] [S] `decksettings/DeckSettingsScreen.kt:298-359`, `components/ModeConfirmDialogs.kt:19-30`, `values*/strings.xml` (`mode_list_delete_desc`) — Tryby są wspólne dla wszystkich talii, ale w ustawieniach talii nic o tym nie mówi. Ołówek zmienia tryb dla wszystkich talii, które go używają. Dialog usuwania mówi tylko „zostanie trwale usunięty”, a talie z usuniętym trybem cicho przechodzą na domyślny (komentarz w `DeckSettingsViewModel.kt:34`).
→ Dopisać w dialogu „Talie używające tego trybu przełączą się na domyślny” oraz jednolinijkową podpowiedź pod nagłówkiem „Tryby nauki”. Teksty ×5.
Zmienia zachowanie: nie.

N14. [bug] [S] `main/MainViewModel.kt:28-30`, `components/MigrationDialog.kt:27-35` — `resolveMigration` jest wywoływane przez `viewModelScope.launch` bez żadnej ochrony. Błąd w `migrationController.execute` zabija aplikację przy pierwszym logowaniu (wyjątek trafia przez `await()`). Przyciski dialogu nie są blokowane po tapnięciu, więc trwający transfer można zlecić drugi raz, także inną opcją.
→ Stan `migrating` w ViewModelu: blokada przycisków i spinner. Błąd przez `launchSaving` z komunikatem.
Zmienia zachowanie: tak.

N15. [UX] [S] `values*/strings.xml` (en: 100, 103-105; pl: 104, 107-109) — Słownictwo kopii zapasowej jest niespójne. Sekcja „Export / import data”, przyciski „Save backup / Restore from backup”, a wyniki „Import completed!” / „Import failed.” To samo słowo „Import” oznacza też import fiszek z CSV.
→ Nazwać sekcję „Kopia zapasowa” i wynik „Przywrócono dane z kopii” / „Nie udało się przywrócić kopii”. Teksty ×5.
Zmienia zachowanie: nie.

N16. [UX] [S–M] `appsettings/AppSettingsViewModel.kt:144-158`, `appsettings/SettingsSections.kt:246-276` — Eksport, przywracanie z kopii i reset do danych fabrycznych nie mają stanu „w trakcie”. Brak spinnera, a przyciski zostają aktywne. Można uruchomić przywracanie, gdy reset jeszcze trwa. `BackupRepository` nie ma blokady (sprawdziłem: brak Mutex).
→ Flaga `busy` w stanie UI. Gdy aktywna, przyciski wyłączone i `LinearProgressIndicator`.
Zmienia wygląd/zachowanie: tak.

N17. [UX] [M] `imports/ImportViewModel.kt:224`, `decksettings/DeckSettingsViewModel.kt:115-116,126-127`, `data/local/db/Daos.kt:11` — Po imporcie, archiwizacji i usunięciu ekran się zamyka bez potwierdzenia. Nowa talia trafia na koniec listy pulpitu (sort po `rowid`), przy wielu taliach poza ekranem. V16 odrzuciło snackbar „który ginie z ekranem”; tu chodzi o komunikat na ekranie docelowym.
→ Jednorazowa wiadomość dla pulpitu (wynik nawigacji lub singleton): „Zaimportowano „X” (N fiszek)”, „Zarchiwizowano „X” — Cofnij”. Plus przewinięcie do nowej talii.
Zmienia zachowanie: tak. Dotyka też Dashboard i NavGraph.

N18. [UX] [S] `browse/BrowseScreen.kt:94,98`, `decksettings/DeckSettingsScreen.kt:87-88`, `imports/ImportScreen.kt:79`, `appsettings/AppSettingsScreen.kt:99`, `archive/ArchiveScreen.kt:44`, `studymodes/StudyModesScreen.kt:61` — Komunikaty snackbar ustawiają się w kolejkę. Tylko `ModeDetailScreen.kt:140` zamyka poprzedni. Zapisy ustawień talii idą przy każdym znaku (`DeckSettingsViewModel.kt:82-87`), więc przy awarii zapisu 10 znaków daje 10 kolejnych snackbarów po 4 s.
→ Pomocnik `SnackbarHostState.showLatest()` (zamknąć bieżący, pokazać nowy) w `components/` i użyć we wszystkich ekranach.
Zmienia zachowanie: drobnie.

N19. [UX] [S] `modedetail/ModeDetailScreen.kt:331-352`, `imports/ImportScreen.kt:168-170` — Stałe `height(48.dp)` na przyciskach (Import, 3 przyciski szablonów) ucinają etykiety przy dużej czcionce i w tłumaczeniach, które się zawijają. Ekran wyboru szablonu nie ma przewijania, więc w poziomie z dużą czcionką przyciski mogą wypaść.
→ `heightIn(min = 48.dp)` oraz `verticalScroll` w `TemplatePicker`.
Zmienia wygląd: tylko przy dużej czcionce.

N20. [bug] [S] `data/files/DocumentFiles.kt:14`, `imports/ImportViewModel.kt:132-143` — Limit pliku to 20 MB, bo ta stała jest wspólna z przywracaniem kopii. V18 zalecało ok. 2 MB dla importu CSV, bo cały tekst trafia do pola tekstowego i jest przetwarzany po każdym znaku (`rows.toMutableList()` przy każdej edycji, `ImportViewModel.kt:296-299`). Wybranie pliku podmienia też wpisany tekst bez pytania.
→ Osobny limit dla CSV (ok. 2–3 MB) i komunikat „Plik jest za duży”. Przy niepustym tekście zapytać przed nadpisaniem.
Zmienia zachowanie: tak.

N21. [UX] [S] `appsettings/AppSettingsViewModel.kt:72-83` — Stan początkowy to domyślne `UserPreferences()`. Przy otwarciu ekranu przełączniki i chipy motywu mogą jedną klatkę pokazywać wartości domyślne, a potem przeskoczyć. `MainViewModel` rozwiązuje to przez `null` do pierwszego odczytu.
→ Ten sam wzorzec: `preferences: UserPreferences?` i `LoadingState` do pierwszej emisji.
Zmienia wygląd: nie (usuwa migotanie).

N22. [UX] [S] `imports/ImportScreen.kt:190-197`, `decksettings/DeleteDeckDialog.kt:38-46`, `browse/BrowseScreen.kt:215` — Klawiatura i fokus.
- Pole nazwy w imporcie nie ma „Dalej”, więc po nazwie trzeba dotknąć drugiego pola.
- Dialog usunięcia talii nie ma autofokusu na polu potwierdzenia ani akcji „Gotowe”.
- Wyszukiwarka nie ma akcji Search.
→ `imeAction = Next` + `FocusRequester` w imporcie. `FocusRequester` + `Done` w dialogu usuwania.
Zmienia zachowanie: drobnie.

N23. [UX] [S–M] `appsettings/SettingsSections.kt:116,148,157,288`, `decksettings/DeckSettingsScreen.kt:161,172,261`, `imports/ImportScreen.kt:232,254` — Etykiety pól mają 4 różne style (bodyMedium, bodyLarge, labelLarge, titleMedium). Ta sama rola wygląda inaczej w ustawieniach aplikacji, ustawieniach talii i imporcie.
→ Dwa style: etykieta wiersza = bodyLarge, etykieta grupy = labelLarge. Wspólny `FieldLabel` w `components/`.
Zmienia wygląd: tak.

N24. [UX] [S] `browse/BrowseScreen.kt:332`, `modedetail/StepCard.kt:89,109`, `decksettings/PageRow.kt:47,57`, `components/ModeListItem.kt:76` — Te same akcje mają różne ikony: kosz `Delete` kontra `DeleteOutline` (tylko w `ModeActions`), strzałki `ArrowUpward` (strony) kontra `KeyboardArrowUp` (kroki). Nazwa talii: `ArchiveScreen.kt:78` titleLarge, `StatsScreen.kt:125` titleMedium+SemiBold, `StudyModesScreen.kt:162` titleMedium.
→ Jedna ikona na akcję i jeden styl tytułu karty.
Zmienia wygląd: tak.

N25. [modernizacja] [S] `browse/BrowseScreen.kt:243`, `archive/ArchiveScreen.kt:65`, `studymodes/StudyModesScreen.kt:85` — `Modifier.animateItem()` jest tylko w edytorze trybu. Usunięcie karty w przeglądaniu, przywrócenie talii z archiwum i usunięcie trybu kończą się nagłym skokiem listy.
→ `.animateItem()` w tych trzech listach (klucze już są).
Zmienia wygląd: tak (animacja).

N26. [modernizacja] [S] `archive/ArchiveScreen.kt:98-105`, `decksettings/DeckSettingsScreen.kt:196-207,315-318`, `imports/ImportScreen.kt:209-216`, `studymodes/StudyModesScreen.kt:94-105`, `modedetail/ModeDetailScreen.kt:304-311` — Przyciski z ikoną robią to samo ręcznie (Icon + Text z `padding(start=…)`).
- Odstęp bywa 4 dp (`DeckSettingsScreen.kt:317`) i 8 dp.
- Przyciski nie używają `ButtonDefaults.ButtonWithIconContentPadding`, więc ikona jest 24 dp od krawędzi.
→ `contentPadding = ButtonWithIconContentPadding` plus `Spacer(Modifier.size(ButtonDefaults.IconSpacing))`. Albo mały wspólny `IconLabel`.
Zmienia wygląd: lekko.

N27. [czytelność] [S] `stats/StatsScreen.kt:121-129` — Prywatny `SectionCard` dubluje `components/SectionCard.kt`. Brakuje nagłówka dla czytnika ekranu (`heading()`), jest ręczne `SemiBold` i odstęp 8 dp zamiast 12 dp. V24 miało to zastąpić.
→ Usunąć prywatną wersję i użyć wspólnej (`spacing = Spacing.s`, jeśli odstęp ma zostać).
Zmienia wygląd: minimalnie.

N28. [czytelność] [S] `modedetail/ModeDetailScreen.kt` (391 l.), `modedetail/StepCard.kt` (341 l.) — Za długo.
- `ModeEditor` ma 129 linii (`:187-315`) i zawiera logikę pola nazwy (`:240-272`).
- W `ModeDetailScreen.kt` mieszkają też `TemplatePicker`, `NoticeBanner` i `ScrollToAddedStep`.
- `StepCard.kt` łączy kartę (`:63-147`) z całą mapą pól parametrów (`:149-341`).
→ Przenieść `TemplatePicker` i `NoticeBanner` do `ModeEditorParts.kt` oraz wyciągnąć `ModeNameField`. Pola parametrów (`StepParams`, `CompoundParams`, `IntField` itd.) do `StepParamFields.kt`. Czysty ruch kodu.
Zmienia zachowanie: nie.

N29. [czytelność] [S] `imports/ImportViewModel.kt` (364 l.), `imports/ImportScreen.kt` (347 l.) — Czyste funkcje i klasy pomocnicze siedzą w ViewModelu: `ParseInput`, `ParsedSource`, `ColumnOverride`, `parseSource`, `buildSource` (`:71-90`, `:328-364`). Ekran ma `ColumnHeaders`, `PreviewRow` i mapowanie problemów (`:274-347`).
→ `ImportParsing.kt` (ruch bez zmian logiki, ViewModel spada do ok. 230 linii) i `ImportPreview.kt`. To nie jest przebudowa odrzucona w V21, tylko przeniesienie.
Zmienia zachowanie: nie.

N30. [czytelność] [S] `decksettings/DeckSettingsScreen.kt` (360 l.) — `DeckSettingsContent` ma 144 linie (`:111-254`): cztery sekcje plus sześć stanów dialogów.
→ Wyciągnąć `DeckDialogs(...)` (`:210-253`) i `ModesSection` (`:297-359`) do osobnych plików. Opcjonalnie jedna sealed klasa stanu dialogu zamiast czterech flag.
Zmienia zachowanie: nie.

N31. [czytelność] [S] `stats/StatsViewModel.kt:43,57`, `decksettings/DeckSettingsViewModel.kt:75`, `browse/BrowseViewModel.kt:57-64,91`, `archive/ArchiveViewModel.kt:41` — Cztery sposoby na to samo: wstrzyknięty `@DefaultDispatcher` (Archive, Import), `Dispatchers.Default` wprost (DeckSettings), dodatkowy parametr konstruktora (Browse) i w Stats `flowOn(Dispatchers.Main.immediate)` na tickerze (`:43`). To ostatnie jest zabiegiem pod test (testy w `StatsViewModelTest` ustawiają `setMain`) i niczego nie tłumaczy w kodzie produkcyjnym.
→ Wszędzie wstrzykiwać `@DefaultDispatcher`. W Stats usunąć `Main.immediate`.
Zmienia zachowanie: nie.

N32. [czytelność] [S] `decksettings/DeckSettingsScreen.kt:191-195`, `modedetail/ModeDetailScreen.kt:245-255,337-351`, `modedetail/ModeDetailViewModel.kt:188-193`, `appsettings/AppSettingsScreen.kt:130-140`, `browse/BrowseViewModel.kt:75-79`, `archive/ArchiveViewModel.kt:43-47`, `stats/StatsViewModel.kt:45-48`, `stats/ActivityHeatmap.kt:61-66` — Zostały artefakty formatowania z U58 (`deck,\n query,\n selected\n ->`, `height =\n …`, klamry w nowych liniach). Utrudniają czytanie diffów.
→ Lokalne wartości lub nazwane lambdy, potem `ktlintFormat`.
Zmienia zachowanie: nie.

N33. [martwy-kod] [S] `values*/strings.xml` (`<!-- ui1 -->`, `<!-- ui2 -->`: en 290,294, pl 300,304, de 290,294, es 295,299, it 295,299), `decksettings/DeckSettingsViewModel.kt:95`, `ui/component/` — Resztki po podziale pracy.
- Znaczniki „ui1/ui2” w plikach tekstów, które nic nie znaczą w repozytorium.
- Domyślna lambda `{ "Page ${it + 1}" }` w ViewModelu po angielsku. Produkcyjny kod zawsze podaje własną (`DeckSettingsScreen.kt:157`), więc domyślna może tylko przypadkiem zapisać angielską nazwę.
- Pusty katalog `ui/component/` obok `ui/components/` (nie jest w git).
→ Usunąć wszystkie trzy.
Zmienia zachowanie: nie.

N34. [UX] [S] `modedetail/ModeDetailScreen.kt:222-231,274-282` — Przy znikaniu banera błędów lub wskazówek animacja wyjścia pokazuje już pustą listę, bo `AnimatedVisibility` komponuje treść z najnowszym stanem. Punkty znikają od razu, a sam tytuł banera kurczy się przez ok. 300 ms.
→ Zapamiętać ostatnią niepustą listę na czas wyjścia (`remember` + `AnimatedContent`), albo pokazać baner bez animacji wyjścia.
Zmienia wygląd: drobnie.

## TOP 5 zmian

1. **N1 + N2** — Import: pytanie przy „Wstecz” oraz naprawa „wiecznego spinnera” po sukcesie. Chroni dane użytkownika i usuwa jedyny znaleziony błąd, który wygląda jak zawieszenie.
2. **N3 + N4** — Przeglądanie kart: potwierdzenie dodania, przewinięcie do nowej karty, przycisk „X” w wyszukiwarce, akcja w pustym wyniku. To ekran używany najczęściej z tych w moim obszarze.
3. **N5 + N6** — Import: czytelny podgląd przy więcej niż 2 kolumnach i pasek przycisku nieprzeszkadzający klawiaturze. Wymaga decyzji o układzie.
4. **N7 + N8 + N13** — Teksty edytora trybów: nazwy kroków zgodne w komunikatach, bez STT/TTS/„Compound”, informacja że tryby są wspólne. Same stringi (×5 języków), małe ryzyko.
5. **N9 + N10 + N19** — Pakiet dostępności: opisy przycisków, etykiety pól podglądu, cele dotyku przełączników, role kart, wysokości przycisków przy dużej czcionce. Wszystko S, bez zmiany układu.

## Decyzje dla Ciebie (zmieniają zachowanie)

- Import: czy pytać „Odrzucić wpisane dane?” przy „Wstecz”? (N1)
- Przeglądanie kart: po dodaniu karty czyścić filtr i przewijać do nowej karty, czy tylko pokazać snackbar? (N3)
- Podgląd importu: układ pionowy dla więcej niż 2 kolumn, czy wiersze tylko do odczytu z edycją w oknie karty? (N5)
- Edytor trybów: przycisk „Dodaj poniżej” na każdej karcie kroku? (N11)
- Czy po imporcie, archiwizacji i usunięciu pokazywać komunikat na pulpicie (z „Cofnij” przy archiwizacji)? (N17)

## Model do wdrożenia

Sonnet 5.5 wystarczy dla większości pozycji (głównie teksty, drobne zmiany UI i przenoszenie kodu). Opus warto wziąć tylko do N5 (przebudowa podglądu) i N2 (nawigacja plus zdarzenia, wymaga testu cyklu życia).

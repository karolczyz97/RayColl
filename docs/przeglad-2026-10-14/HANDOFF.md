# Przegląd modernizacji RayColl: stan prac i przekazanie (14.10.2026)

## Aktualizacja po kontynuacji w Codex (14.10.2026)

**Obie gałęzie UI zostały scalone do `przeglad-modernizacja`, konflikty rozwiązane, a przekazania między warstwami domknięte.** Przeczytano wcześniej historię rozmów Claude i raporty jego agentów. Weryfikacja całości: **533 testy, 0 błędów i 0 pominiętych**, ktlint zielony, Android Lint bez błędów (3 ostrzeżenia), buildy debug i release udane.

Szczegóły aktualnego wyniku: [WERYFIKACJA.md](WERYFIKACJA.md). Odzyskane ustalenia użytkownika: [USTALENIA_Z_ROZMOW.md](USTALENIA_Z_ROZMOW.md).

**Pozostało:** test na fizycznym telefonie (ADB nie wykrywa urządzenia), następnie zatwierdzenie scalenia do `main` i push. Robocze kopie pozostają do zakończenia tego etapu. Poniższe sekcje opisują historyczny stan przy przekazaniu z Claude; punkty 1–4 listy prac są już zakończone.

## Skąd to jest
1. Pięciu recenzentów przeczytało całą apkę i zebrało ~300 uwag (`findings-*.md`, ID: D = domain, R/B = dane i build, P = dźwięk i serwis, U = UI nauki/dashboard/browse, V = UI ustawień/importu/trybów).
2. Redteam sprawdził każdą uwagę w kodzie, testach i historii gita (`verdict-*.md`, werdykt plus specyfikacja poprawki dla każdego ID).
3. Pięciu „fixerów” naprawia CONFIRMED/PARTIAL, każdy we własnym git worktree (`FIX_INSTRUCTIONS.md`, Gradle przez `gradle-locked.sh`, jeden build naraz).

## Wynik redteamu (300 uwag)
| Obszar | Potwierdzone/częściowe | Błędne | Celowe | Skrajne | Nie warto |
|---|---|---|---|---|---|
| domain (D) | 44 | 0 | 7 | 2 | 10 |
| dane+build (R/B) | 24 | 2 | 1 | 9 | 25 |
| dźwięk (P) | 27 | 0 | 6 | 3 | 14 |
| UI 1 (U) | 52 | 1 | 2 | 1 | 10 |
| UI 2 (V) | 41 | 1 | 2 | 3 | 13 |
| **Razem** | **188** | **4** | **18** | **18** | **72** |

„Nie warto” to głównie duże przebudowy: rozbicie SessionEngine, typy AtomicStep/CardState/Rating, przepisanie walidatora kopii, kolejki synchronizacji na kanałach, zmiana klucza głównego w Room, zapytania collectionGroup, podglądy @Preview. Mają ryzyko dla formatu danych albo dla przetestowanego kodu, a nie dają zysku dla użytkownika.

## Stan gałęzi
Gałąź integracyjna: **`przeglad-modernizacja`** (od `main` 641e3057), nic nie jest wypchnięte, `main` jest nietknięty.

| Warstwa | Gałąź | Stan |
|---|---|---|
| domain + core | worktree-agent-a9b94762caee28b78 (e7cf3fd7) | ✅ scalona |
| dźwięk, serwis, StudyViewModel, nawigacja, MainActivity | worktree-agent-a3b598400c9c2a9ac (5ceb1abb) | ✅ scalona |
| dane, di, build | worktree-agent-a21e9cf516693cf85 (faa0de2d, 54d2c4da, ab61f0b4) | ✅ scalona |
| przekazania (auto-pauza z SessionEngine, stałe SyncScheduler) | db619c52 na gałęzi integracyjnej | ✅ |
| **UI 1**: nauka (ikonki U0!), dashboard, browse, components, theme, archive, stats, część stringów | worktree `.claude/worktrees/agent-a4fc7ab74e04a3d10` | ⏳ **w trakcie, NIEZACOMMITOWANE** (~25 plików) |
| **UI 2**: ustawienia, talia, import, edytor trybów, ModeListItem, res | worktree `.claude/worktrees/agent-aa5168433a7cabbc5` | ⏳ **w trakcie, NIEZACOMMITOWANE** (~28 plików) |

Gałąź integracyjna po scaleniu 3 warstw: **490 testów zielonych** (baza 435), ktlint czysty.

## Co zostało do zrobienia (dla następnego agenta)
1. **Dokończyć UI 1 i UI 2.** W każdym worktree sprawdź `git status` / `git diff` i porównaj z `verdict-ui1.md` / `verdict-ui2.md` (CONFIRMED/PARTIAL), dokończ brakujące pozycje według `FIX_INSTRUCTIONS.md` (kopiuj `local.properties` do worktree, Gradle tylko przez `gradle-locked.sh`), na końcu `:app:testDebugUnitTest :app:ktlintCheck` i commit.
   - Priorytet UI 1: **U0** (ikonka głośnika/mikrofonu nie może przesuwać tekstu: podświetlenie aktywnej strony plus ikona w stałym lewym marginesie, spec w verdict-ui1.md), U5 (snackbary blokują zdarzenia), U1 (podsumowanie przewijane), U2/U32 (polskie nazwy ocen bez „(Again)”, jedno słowo „talia”).
   - Priorytet UI 2: **V1** (podwójny import, odtworzony testem: `importing` ma zostać true po udanym imporcie), V2 (typy plików .tsv/.csv), V27 (podpis „wpisz USUŃ” i tolerancja ogonków), V28 (reset fabryczny), V58 (jednokolorowa warstwa ikony).
2. **Scalić obie gałęzie UI do `przeglad-modernizacja`.** Obie wyszły z 51095cf9, czyli BEZ ostatniego commita usera 641e3057 (zmienił `BrowseScreen.kt`, `BrowseViewModel.kt`, `CsvParser.kt`, `GroupOperations.kt`). Spodziewaj się konfliktów w Browse* i w `strings.xml` (oba UI dopisują klucze na końcu plików: zostaw oba bloki). Po scaleniu sprawdź, że nie ma dosłownych znaków BOM w kodzie (`git grep -lI $'\xEF\xBB\xBF' -- 'app/src/*.kt'`); agent domeny raz zamienił `﻿` na niewidoczny znak.
3. **Przekazania, które czekają na gałęzie UI:**
   - R6: `.flowOn(Dispatchers.Default)` przed `.stateIn` w `ArchiveViewModel` i `DeckSettingsViewModel` (wzór: DashboardViewModel).
   - D46: nowe strony talii dostają angielskie „Page N”. Dodaj parametr `defaultName: (Int) -> String` w `FlashcardGroup.withActivePageCount` i `padPageMetadata`, przepuść przez `DeckRepository.setActivePageCount`, a w `DeckSettingsScreen` podaj `context.getString(R.string.import_page_label, it + 1)`.
   - R17: `ImportViewModel.import` też ma przycinać nazwy stron (trim).
   - Wszystkie przekazania z raportów obu agentów UI (np. LanguagePicker → wspólny DropdownChip, SettingsSections → SectionCard).
   - Opcjonalnie R20: wydzielić z `SyncLifecycle.start` blok „koniec sesji” do klasy w `playback/` (np. `SessionEndSync`), żeby warstwa danych nie importowała playback. Wymaga zmiany konstruktora w `TwoDeviceSyncTest`.
4. **Pełna weryfikacja:** `:app:testDebugUnitTest :app:ktlintCheck :app:lintDebug :app:assembleRelease` (JDK 23 czasem losowo wywala C2 przy R8, wtedy powtórz).
5. **Test na telefonie** (lista niżej), potem merge `przeglad-modernizacja` do `main` i push **tylko za zgodą usera**.
6. Sprzątanie: `git worktree remove` dla 5 worktree agentów (są „locked”, użyj `--force` po scaleniu) i usunięcie gałęzi `worktree-agent-*`.

## Do sprawdzenia na telefonie
- Ikonki głośnika i mikrofonu: tekst karty stoi w miejscu, w pionie i w poziomie (po scaleniu UI 1).
- Motyw: ciemny w apce przy jasnym systemie i odwrotnie. Ikony pasków mają pasować do motywu apki.
- Ręczna pauza, wyjście z apki, powrót: sesja NIE wznawia się sama.
- „Ucz się” i od razu blokada ekranu: brak crasha.
- Sesja w tle: dojście do ręcznej oceny i awaria TTS. Powiadomienie pokazuje „wstrzymano”, wake lock się zwalnia, jest jedno powiadomienie.
- Nasłuch: zakończony timeoutem i zwykły.
- Animacja karty talii między Dashboardem a Study/Browse/DeckSettings, tło przy przejściach.
- Kolejność talii i kart (tryb SEQUENTIAL) na drugim urządzeniu lub po reinstalacji taka sama jak na telefonie. Pierwsza wysyłka po aktualizacji jednorazowo przepisze wszystkie dokumenty, bo dodaje pole `position`.
- Świeża instalacja i logowanie: nie pojawia się „Znaleziono dane lokalne”, gdy są tylko przykładowe talie.
- Eksport kopii na Dysk Google nadpisujący dłuższy plik: plik nie ma śmieci na końcu.
- Import (po scaleniu UI 2): podwójne tapnięcie nie tworzy dwóch talii, pliki .tsv można wybrać.

## Najważniejsze zmiany zachowania (już na gałęzi)
- Pauza ręczna nie jest kasowana przez wyjście z apki (P1).
- Kolory pasków systemowych zgodne z motywem apki (P2/V3).
- Brak crasha przy starcie serwisu nauki w tle z zablokowanym ekranem (P3).
- Kolejność talii i kart przenosi się przez chmurę (R9, nowe opcjonalne pole `position` w Firestore, stare dokumenty czytelne).
- Brak pytania o migrację, gdy gość ma tylko przykładowe talie (D13).
- Błąd rozpoznawania mowy nie zamyka apki (D9).
- Kopia zapasowa: walidator czyta tylko `kind` (D8). Eksport w trybie „wt” (R3), import z limitem 20 MB i bez BOM (R4).
- Uszkodzony plik ustawień nie zamyka apki (P50).
- Brak klucza Firebase psuje już tylko build release (B1).
- `mapMatchToRating` wymaga progu (D41); `StartSession(cards)` zastąpiło `SetCards` (D20).

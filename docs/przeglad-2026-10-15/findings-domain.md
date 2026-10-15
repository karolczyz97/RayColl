# Skan 15.10.2026 — Domena + core

Pełna lista agenta (tylko odczyt kodu, gałąź `przeglad-modernizacja`). Zbiorczy raport: [RAPORT.md](RAPORT.md).

## Ocena ogólna

Domena jest w dobrym stanie. Poprawki z wczoraj (D5, D6, D9, D12, D13, D16, D18, D20, D23, D28, D29, D31, D35, D41, D57 i kolejne) są wprowadzone poprawnie i niczego nie psują. Komentarze są czyste: w `domain/**` i `core/**` nie ma odwołań do TS, parytetu, T10 ani Expo. Logika FSRS, scalanie (`MergeEngine`) i silnik sesji są spójne. Nie znalazłem żadnego błędu w obliczeniach FSRS ani w kolejności kroków sesji.

Najsłabsze miejsca to dwa elementy importu (`detectSeparator` i liczenie liczby stron), jedna niespójność w regule migracji gościa i jedna decyzja produktowa w trybie „Audio” (N4). Do tego `BackupValidator`, w którym komunikaty błędów powtarzają się wielokrotnie, i kilka drobnych duplikacji w `SessionEngine`.

Wydajność: nic wartego zgłoszenia. Najcięższe operacje (`normalizeStoreData`, `jsonElementToPlain`, `browseFilter`) są liniowe i na realnych rozmiarach talii niezauważalne.

Znane ograniczenie, którego nie ruszać (wymaga zmiany formatu): `mergeActivityHeatmap` bierze maksimum z dwóch urządzeń, więc gdy tego samego dnia uczyłeś się na obu, licznik zaniża sumę.

## Znaleziska

**N1. [bug] [S] `merge/MigrationRules.kt:16-19`**
`getGuestHasData` filtruje usunięte talie (`deletedAt == null`), ale nie filtruje usuniętych trybów (`modes.any(::isUserAuthoredMode)`). Migawka gościa zawiera tombstony (`LocalStore.kt:41`, komentarz „RAZEM z tombstonami”), a `deleteMode` robi soft-delete (`StudyModeRepository.kt:58`).
→ Zmienić na `modes.any { it.deletedAt == null && isUserAuthoredMode(it) }` i dodać przypadek „usunięty własny tryb → false” w `MigrationRulesTest`.
Wpływ na użytkownika: tak. Gość, który stworzył i usunął własny tryb, przy pierwszym logowaniu dostaje dialog „Znaleziono dane lokalne”, choć nic nie ma.

**N2. [bug] [M] `imports/CsvParser.kt:66-76` (`detectSeparator`)**
Wynik to suma dodatkowych kolumn razy odsetek podzielonych wierszy, więc im więcej kawałków, tym lepiej. Przykład policzony z kodu: 5 wierszy typu `Wenn ich Zeit habe, gehe ich<TAB>If I have time, I go`. TAB daje 1 dodatkową kolumnę na wiersz (wynik 5), a PRZECINEK daje 2 (wynik 10), więc wygrywa przecinek. To typowy wklej ze Sheets/Excela z przykładowymi zdaniami. Test `ImportParserTest.kt:21` nazywa się „most consistent”, ale kod tego nie sprawdza.
→ Dodać priorytet: separatory rzadkie w zwykłym tekście (TAB, PIPE, SEMICOLON) wygrywają, jeśli dzielą co najmniej połowę wierszy. PRZECINEK wybierany tylko wtedy, gdy żaden z nich nie dzieli. Istniejące testy z linii 22-27 zostają zielone.
Wpływ na użytkownika: tak. Podgląd importu jest rozjechany, a użytkownik musi ręcznie przełączyć separator.

**N3. [bug] [S] `imports/HeaderDetector.kt:53-56` (`detectPageCount`)**
Liczy `it.size` wiersza, więc puste komórki na końcu (`hello;cześć;` albo skopiowana pusta ostatnia kolumna) tworzą fantomową trzecią stronę. W `ImportViewModel.kt:355,362` nic tego nie przycina, a w UI nie ma pola do zmiany liczby stron. Powstaje talia z pustą stroną 3.
→ Liczyć do ostatniej niepustej komórki: `dataRows.maxOfOrNull { row -> row.indexOfLast { it.isNotBlank() } + 1 }`. Dodać test `a;b;\nc;d;` → 2.
Wpływ na użytkownika: tak, przy plikach z pustą końcową kolumną.

**N4. [bug?] [M] `seed/SeedModes.kt:67-72` + `session/StepExecutor.kt:99-110` (decyzja produktowa)**
W wbudowanym trybie „Audio” druga próba po błędzie kończy się `AutoRateFromAnswerStep(CORRECT)`, czyli oceną z procentu dopasowania (`mapMatchToRating`: Dobre albo Łatwe). Karta, której nie umiałeś za pierwszym razem, może więc dostać „Łatwe” (4) i bardzo długi odstęp. Pierwsza pomyłka nie jest nigdzie zapamiętana.
→ Do Twojej decyzji (pytanie na końcu). Jeśli ma być zmiana: w drugiej próbie `AutoRateFixed(GOOD)` albo `HARD` zamiast oceny z dopasowania. Zmiana kroków wbudowanego trybu wymaga sprawdzenia `refreshUntouchedBuiltInModes` (wersje w bazie) i `SeedDataTest`.
Wpływ na użytkownika: tak, jakość harmonogramu powtórek.

**N5. [bug-niski] [S] `selectors/CardStats.kt:32-38` + `model/Constants.kt:63`**
`computeStreak` ucina serię do `STREAK_MAX_DAYS = 365`. Heatmapa jest zapisana w całości i nigdzie nie jest czyszczona (`Daos.kt`, `ActivityDao`), więc seria dłuższa niż rok pokaże „365”. KDoc o limicie milczy.
→ `generateSequence(start) { it.minusDays(1) }.takeWhile { (heatmap[LocalDates.dayKey(it)] ?: 0) > 0 }.count()` i usunąć stałą. Dodać test na 400 dni.
Wpływ na użytkownika: tak, ale tylko przy serii dłuższej niż rok.

**N6. [martwy-kod] [S] `session/StepExecutor.kt:99-110`**
Gałąź `result.suggestedRating ?: mapMatchToRating(result.percent ?: 0, result.threshold ?: ...)` nigdy się nie wykona. Każdy wynik CORRECT/INCORRECT dostaje `suggestedRating` w `StepExecutor.kt:172-176`, a `answered` jest równoważne „`suggestedRating != null`”. Wczorajsze D41 zostawiło ten fallback jako zalecany, ale po usunięciu wariantu bez progu jest już zbędny.
→ `state.lastAnswerResult.suggestedRating?.takeIf { !state.cardReviewed }?.let(engine::submitReview)`.
Wpływ na użytkownika: nie.

**N7. [martwy-kod] [S] `session/SessionEngine.kt:156-162`**
W `baseSrsStates[card.id] ?: card.srsState` elvis jest martwy, bo chwilę wcześniej sprawdzono `card.id in baseSrsStates`. Do tego dwa wyszukiwania w mapie.
→ `val base = baseSrsStates[card.id]; if (base != null) {…onCardReviewed(…, base)} else {…}`.
Wpływ na użytkownika: nie.

**N8. [czytelność] [S] `normalize/BackupValidator.kt` (cały plik) + `DataNormalizer.kt:57-63`**
- `assertCompoundBranch` (88-101) wywołuje 9 razy `stepMessage(modeId, index, "has invalid compound branch")`. `assertCompoundParams` robi to samo z „has invalid compound params”.
- Formatter połamał warunki na nieczytelne kawałki: 114-116, 169-171, 195-197, 242-246, 291-300. To samo jest w `normalizeCard` (`if (… in\n SrsStates.VALID\n) {`).
- Linia 19: nieużywana zmienna `e`.
- Linie 75-76: `val kind = …; when (kind)`.
→ Na początku funkcji `val invalid = stepMessage(…)`. Warunki skrócić przez helper `isOptional(value) { … }` albo `rs !in setOf("all","next")`. `normalizeCard` zapisać jako `if (…state in SrsStates.VALID) card else card.copy(…)`.
Wpływ na użytkownika: nie.

**N9. [czytelność] [S] `session/SessionEngine.kt:206-211, 343-347, 355-359, 366-370`**
Cztery razy ten sam blok „puść palec: `holding=false; peekTimerJob?.cancel(); peekTimerJob = null`”. W `pause()` brakuje zerowania zadania, więc zachowanie jest niespójne. Dodatkowo `handleGateTap` (313) powtarza sprawdzenie, które zrobił już `handleTapWhileIdle`.
→ Prywatna `releaseHold()` użyta w czterech miejscach, usunąć zbędny guard.
Wpływ na użytkownika: nie.

**N10. [czytelność] [S] domyślna nazwa strony w 4 kopiach**
Lambda `{ "Page ${it + 1}" }` jest wklejona w `DataNormalizer.kt:44`, `GroupOperations.kt:31`, `DeckRepository.kt:129` i `DeckSettingsViewModel.kt:95`.
→ Jedna `fun defaultPageName(index: Int) = "Page ${index + 1}"` w domenie, reszta używa `::defaultPageName`.
Wpływ na użytkownika: nie.

**N11. [czytelność] [S] „aktywne strony” liczone w kilku miejscach**
`(0 until group.activePageCount)` występuje w `SessionEngine.kt:250`, `StepExecutor.kt:60` i `StepExecutor.kt:130-131`. Ten ostatni to ponowna implementacja prywatnego `hiddenPages()`. `card.pages.take(...activePageCount)` występuje w `SessionEngine.kt:219` i `BrowseFilter.kt:17`.
→ `val FlashcardGroup.activePages: IntRange`, `hiddenPages()` jako `internal` i użyć go w `startRevealGate`. Opcjonalnie `Flashcard.visiblePages(count)`.
Wpływ na użytkownika: nie.

**N12. [czytelność] [S] `operations/StudyModeOperations.kt:27-30`**
`seedModeFor(builtInSourceId)?.takeIf { isBuiltIn }` wygląda, jakby `isBuiltIn` dotyczyło seeda (`it`). W rzeczywistości dotyczy sprawdzanego trybu (receiver rozszerzenia), więc łatwo to źle przeczytać.
→ `if (!isBuiltIn) return false; val seed = seedModeFor(builtInSourceId) ?: return false`.
Wpływ na użytkownika: nie.

**N13. [modernizacja] [S] `merge/MergeEngine.kt:16-21, 61, 92, 121`**
- `localSrsWins` to `when` zwracające `true`/`false`. Wystarczy `localReps > cloudReps || (localReps == cloudReps && localSrsAt > cloudSrsAt)`.
- Trzy kopie `if (deletedAt > 0) deletedAt else null` → `deletedAt.takeIf { it > 0 }`. Zachowanie identyczne.
Wpływ na użytkownika: nie. Zmiana w kodzie synchronizacji, więc najpierw uruchomić `MergeEngineTest` i `TwoDeviceSyncTest`.

**N14. [czytelność] [S] zbyt szeroka widoczność**
Publiczne, ale używane tylko we własnym pliku: `HeaderDetector.kt:12,29,36,43` (`HEADER_TOKENS`, `looksLike*`), `BackupValidator.kt:160,211,231,251,280,304`, `DataNormalizer.kt:51` (`getStoredPageCount`), `MergeEngine.kt:23` (`mergeActivityHeatmap`), `CsvParser.kt:21` (`normalizeCsvText`).
→ `private` albo `internal`. `BackupValidatorTest` korzysta z części walidatorów, więc `internal` wystarczy.
Wpływ na użytkownika: nie.

**N15. [czytelność] [S] porządki wokół `live()` i `dayKey`**
`live()` jest w `DueCards.kt:11`, a używają go też `BrowseFilter` i `CardStats`. Wczoraj (D61) dodano `isLive`, ale `live()` nie przeniesiono do `Flashcard.kt` obok niego. `ui/stats/ActivityHeatmap.kt:77` buduje klucz przez `date.toString()` zamiast `LocalDates.dayKey(date)`, więc wciąż są dwie definicje tego samego klucza (D59).
→ Przenieść `live()` obok `isLive`, w UI użyć `LocalDates.dayKey`.
Wpływ na użytkownika: nie.

**N16. [modernizacja] [S] `srs/SrsEngine.kt:15-36` i `srs/SpeechMatcher.kt:37-62`**
- `calculateFsrs` deklaruje `val newD/newS/newState` bez wartości i uzupełnia je w gałęziach. Czytelniej: `val (newD, newS, newState) = if … else …` (mały `data class` albo `Triple`).
- `elapsedMs = now - (if (last != 0L) last else now)` to po prostu `if (last == 0L) 0 else now - last`.
- `wordSimilarity` używa ręcznych `bestScore/bestIdx/used`. Da się zapisać jako `maxByOrNull` po wolnych indeksach.
Wpływ na użytkownika: nie. Przed zmianą mieć pod ręką `SrsEngineTest` (wartości muszą wyjść co do bitu takie same).

**N17. [czytelność] [S] `imports/HeaderDetector.kt:36-49, 73-86`**
Magiczne liczby: 24 znaki, 3 i 5 słów, wagi 0.65/0.45/0.15/0.1/0.35/0.15 i zaokrąglanie do setnych.
→ Nazwane stałe w `ImportConstants`. Opcjonalnie wagi jako punkty całkowite i próg `>= 50` (D55 to już wspominało jako opcję).
Wpływ na użytkownika: nie.

**N18. [czytelność] [S] `session/SessionEngine.kt:103, 108`**
`runSteps` jest rozszerzeniem `CoroutineScope` tylko po to, by wywołać `ensureActive()`. Idiomatycznie to zwykłe `suspend fun` z `currentCoroutineContext().ensureActive()`. Karta jest pobierana z `_state.value.cards[cardIndex]` dopiero w ciele zadania, choć `val card = s.cards[cardIndex]` jest znane przed `launch`.
→ Pobrać kartę przed `launch` i usunąć receiver.
Wpływ na użytkownika: nie.

**N19. [czytelność] [S] `model/Constants.kt:16-17, 37, 91`**
- `candidate.coerceAtLeast(0)` w `clampActivePageCount` jest zbędne (zewnętrzne `maxOf(MIN_PAGE_COUNT, …)` i tak daje minimum 2).
- `DAY_MS` mieszka w `SrsConstants`, a używa go `ArchiveConstants`.
→ Uprościć do `candidate.coerceAtMost(upperBound).coerceAtLeast(MIN_PAGE_COUNT)` i przenieść `DAY_MS` do wspólnego obiektu czasu.
Wpływ na użytkownika: nie.

**N20. [czytelność] [S] `core/Clock.kt:12`**
`object SystemClock` ma tę samą nazwę co `android.os.SystemClock` (zegar monotoniczny), a jest zegarem ściennym. Łatwo o zły import.
→ Zmienić nazwę na `WallClock` (dotyka `AppModule.kt` i testów).
Wpływ na użytkownika: nie.

## TOP 5 zmian

1. **N4** – podjąć decyzję produktową o ocenie po drugiej próbie w trybie „Audio”. To jedyne znalezisko, które wpływa na jakość samych powtórek.
2. **N2** – lepsze wykrywanie separatora przy wklejaniu z arkusza ze zdaniami zawierającymi przecinki.
3. **N3** – koniec z fantomową, pustą trzecią stroną przy imporcie.
4. **N1** – dialog migracji nie pojawia się z powodu usuniętego trybu.
5. **N8** – porządek w `BackupValidator`. Największy plik domeny jest dziś najtrudniejszy do czytania, a poprawka jest mechaniczna i ma pokrycie testami.

## Pytanie do Ciebie

Czy karta, której nie umiałeś za pierwszym razem w trybie „Audio”, a za drugim powiedziałeś poprawnie, ma dostawać ocenę z dopasowania (może wyjść „Łatwe”), czy najwyżej „Dobre”/„Trudne”? Od tego zależy, czy robimy N4.

# Verdict: domain/ + core/ (skan 15.10.2026, prefiks DOM)

Ścieżki względem `app/src/main/java/com/raycoll/app/`. Wszystkie znaleziska sprawdzone w kodzie, testach i historii git.

Liczby: CONFIRMED 15, PARTIAL 4, DECISION 1, WRONG 0, INTENTIONAL 0, EDGE 0, NOT_WORTH 0 (łącznie 20).
Podznaczone fragmenty NOT_WORTH wewnątrz PARTIAL: N16 (wordSimilarity, newD/newS/newState), N11 (`Flashcard.visiblePages`), N13 (`localSrsWins`).

Żadna zmiana nie dotyka formatu Firestore/backupu ani schematu Room. Nie trzeba migracji.

---

### DOM-N1 — CONFIRMED — `getGuestHasData` nie pomija usuniętych trybów, więc gość z usuniętym własnym trybem dostaje zbędny dialog migracji

Sprawdzone: `LocalStore.snapshotOf` (`data/repository/LocalStore.kt:38-47`) zwraca `modeDao.allModes(ns)` razem z tombstonami (komentarz „RAZEM z tombstonami”), a `StudyModeRepository.deleteMode` robi `softDeleteMode`. `BootstrapLoader.bootstrapFreshAccount` (linia 67) przekazuje ten snapshot wprost do `getGuestHasData`. Talie są już filtrowane po `deletedAt == null`, tryby nie. Test `MigrationRulesTest` nie ma przypadku z usuniętym trybem.

Fix (S, DOM):
- `domain/merge/MigrationRules.kt:19`: `modes.any { it.deletedAt == null && isUserAuthoredMode(it) }`.
- Zostawić `isUserAuthoredMode` bez zmian (nie jest to jej rola; używa jej tylko ta funkcja).
- Test: w `MigrationRulesTest.getGuestHasData...` dodać `assertFalse(getGuestHasData(emptyList(), emptyMap(), listOf(customMode.copy(deletedAt = 1L))))` oraz usunięty zmodyfikowany wbudowany (`SEED_MODES[0].copy(name = "X", deletedAt = 1L)` -> false).
- Ryzyko: znikome. Uwaga uboczna (bez zmiany): `hasUserLocalCache` w `BootstrapLoader.kt:65` też liczy tombstony w `local.studyModes`, ale to konserwatywny kierunek (dialog się nie pokaże), więc zostaje.

### DOM-N2 — CONFIRMED — `detectSeparator` premiuje przecinek w zdaniach, więc wklejka TAB ze zdaniami z przecinkiem wybiera przecinek

Sprawdzone ręcznie z kodu: dla wierszy `Wenn ich Zeit habe, gehe ich<TAB>If I have time, I go` TAB daje 2 komórki (wynik 5 * 1.0 = 5), przecinek 3 komórki (wynik 10 * 1.0 = 10), więc wygrywa przecinek. Test `ImportParserTest` „picks the most consistent candidate” nie sprawdza takiego przypadku. Realne: wklejka z Arkuszy i eksport Anki to TAB, zdania z przecinkami są w niemieckim/włoskim/hiszpańskim wręcz normą. Użytkownik może ręcznie przełączyć separator, ale domyślny podgląd jest błędny.

Fix (M, DOM `domain/imports/CsvParser.kt`, funkcja `detectSeparator`):
- Zostawić obecną funkcję punktującą jako `score(rows)`.
- Nowa reguła: kandydaci „rzadcy w zwykłym tekście” = `TAB, PIPE, SEMICOLON`. Spośród nich ci, którzy dzielą co najmniej połowę wierszy (`splitRows.size * 2 >= rows.size`, przy niepustych `rows`), konkurują między sobą po `score`; zwycięzca bierze wszystko, o ile istnieje.
- Dopiero gdy żaden rzadki separator nie dzieli >= połowy wierszy: przecinek, jeśli dzieli cokolwiek (`rows.any { it.size > 1 }`), inaczej `CsvSeparator.DEFAULT`.
- Nie zmieniać kolejności enum `CsvSeparator` (używana w UI do wyświetlania).
- Testy: dopisać do `ImportParserTest` (a) 5 wierszy zdań z przecinkami rozdzielonych TAB -> TAB, (b) to samo ze średnikiem -> SEMICOLON, (c) CSV z przecinkiem i pojedynczym średnikiem w jednym wierszu z 5 -> COMMA, (d) wiersz z TAB tylko w 1 z 5 wierszy + przecinki -> COMMA. Istniejące asercje z linii 22-27 przechodzą (sprawdzone: `a;b;c\nd,e,f\ng;h;i` -> semicolon dzieli 2/3 >= połowy; `"a;b"\n1;2\n3;4` -> semicolon 2/3).
- Ryzyko: niskie; zmienia tylko wybór domyślny w niejednoznacznych przypadkach.

### DOM-N3 — CONFIRMED — `detectPageCount` liczy puste komórki końcowe, więc `hello;cześć;` tworzy pustą stronę 3

Sprawdzone: `parseRows` dodaje pustą komórkę po końcowym separatorze (`endCell()` przy `endRow()`), `detectPageCount` bierze `it.size`, a `ImportViewModel.parseSource` (linie 355-362) dopełnia wszystkie wiersze do tego `pageCount`. W UI nie ma kontrolki zmiany liczby stron w imporcie (`pageNames` pochodzą z `parsed.pageCount`), więc fantomowa strona trafia do talii. Realne przy eksporcie z Excela z końcowym `;` i Anki z pustą kolumną tagów.

Fix (S, DOM `domain/imports/HeaderDetector.kt:53-56`):
- `maxOf(PageLimits.MIN_PAGE_COUNT, dataRows.maxOfOrNull { row -> row.indexOfLast { it.isNotBlank() } + 1 } ?: 0)`.
- Puste komórki w środku (`a;;c`) nadal dają 3 strony, bo liczy się ostatnia niepusta.
- Test: `a;b;\nc;d;` -> 2; `a;b;;\nc;d;e;` -> 3; zachować istniejące asercje (linie 63-67).
- Ryzyko: znikome. Wiersze nie są obcinane, tylko dopełniane do `pageCount`; nadmiarowa pusta komórka w wierszu z `pageCount=2` jest już dziś ignorowana przez `List(pageCount) { row.getOrElse(it) { "" } }`.

### DOM-N4 — DECISION — druga próba w trybie „Audio” kończy się oceną z dopasowania, więc po pierwszej pomyłce karta może dostać „Łatwe”

Sprawdzone: `SeedModes.kt:67-72` (druga grupa `CORRECT` ma `AutoRateFromAnswerStep`), `StepExecutor.listenAndCheck` wylicza `suggestedRating = mapMatchToRating(percent, threshold)` -> Dobre albo Łatwe po przekroczeniu progu. Pierwsza pomyłka nie jest zapamiętana nigdzie poza przebiegiem kroków. `CompoundExpanderTest` (linie 148-190) przypina kształt tego trybu: 10 kroków CORRECT i indeks 7 jako `AutoRateFromAnswerStep`. Brak śladu w `USTALENIA_Z_ROZMOW.md`, że to zamierzone; jest to pozostałość po pierwotnym projekcie trybu, nie świadoma decyzja.

Dlaczego DECISION, a nie bug: zmiana zmienia harmonogram powtórek (UX/algorytm nauki) i jest wyborem produktowym.

Rekomendowany domyślny wariant: druga próba udana -> stała ocena `HARD` (`AutoRateFixedStep(condition = CORRECT, rating = Ratings.HARD)`). Powód: karta, której nie umiałeś za pierwszym razem, nie powinna dostać dłuższego odstępu niż karta zaliczona od razu; w FSRS „Hard” nadal liczy się jako przypomnienie, ale z karą stabilności.
Alternatywy: (b) `GOOD` (łagodniej), (c) zostawić jak jest (ocena z dopasowania).

Jeśli wybrane (a) albo (b), spec (S-M, DOM):
- `domain/seed/SeedModes.kt:67-72`: w drugiej grupie zamienić `AutoRateFromAnswerStep(condition = CORRECT)` na `AutoRateFixedStep(condition = CORRECT, rating = Ratings.HARD)`.
- `refreshUntouchedBuiltInModes` (`LocalStore.kt:108-123`) sam podmieni tryb u wszystkich z `updatedAt == 0` (nieedytowany) i `steps != seed.steps`; tryby edytowane przez użytkownika zostają. Nie trzeba wersjonowania ani migracji.
- Testy: `CompoundExpanderTest.audio mode pacing...` (indeks 7 z `correctSteps` -> `AutoRateFixedStep` z oceną 2), `SeedDataTest`; sprawdzić `SessionEngine*Test` używające trybu listen-speak (grep wykazał tylko te dwa pliki z id `listen-speak`).
- Uwaga: `CompoundParams.ListenGrade` (kreator kompozytowy) ma własną konfigurację gałęzi; nie jest powiązany z seedem i nie wymaga zmiany.

### DOM-N5 — CONFIRMED — `computeStreak` ucina serię do 365 dni, a heatmapa jest przechowywana bez limitu

Sprawdzone: `CardStats.kt:32-38` iteruje `0 until STREAK_MAX_DAYS`; stała `StudyConstants.STREAK_MAX_DAYS = 365` używana tylko tam. `ActivityDao` nie czyści starych dni (jedyny `DELETE` kasuje całą przestrzeń nazw). Seria > 365 dni pokaże „365”. Cap nie jest zabezpieczeniem pętli: mapa jest skończona, a `takeWhile` zatrzyma się na pierwszym dniu bez wpisu. Historia git: stała pochodzi z pierwszego portu (`06697f31`), bez uzasadnienia w komentarzu. Skutek tylko przy serii dłuższej niż rok, ale poprawka jest tania.

Fix (S, DOM):
- `CardStats.kt`: `generateSequence(start) { it.minusDays(1) }.takeWhile { (heatmap[LocalDates.dayKey(it)] ?: 0) > 0 }.count()`.
- Usunąć `STREAK_MAX_DAYS` z `domain/model/Constants.kt:63`.
- Test w `SelectorsTest`: heatmapa z 400 kolejnych dni kończąca się dziś -> 400; zachować istniejące (linie 110, 178, 201).
- Ryzyko: znikome (pętla ograniczona rozmiarem mapy).

### DOM-N6 — CONFIRMED — fallback `mapMatchToRating(percent ?: 0, ...)` w `AutoRateFromAnswerStep` nigdy się nie wykona

Sprawdzone: jedyne miejsce tworzące `LastAnswerResult` ze statusem CORRECT/INCORRECT to `listenAndCheck` (`StepExecutor.kt:172-176`), który zawsze ustawia `suggestedRating`. Testy tworzące `LastAnswerResult(status = CORRECT, ...)` bez oceny (`SessionReducerTest.kt:122`, `StudyCardTest`) nie przechodzą przez `AutoRateFromAnswerStep`. Pole `threshold` nadal potrzebne w UI (`AnswerPanel.kt:108`), więc zostaje.

Fix (S, DOM `session/StepExecutor.kt:99-110`):
- `is AutoRateFromAnswerStep -> state.lastAnswerResult.suggestedRating?.takeIf { !state.cardReviewed }?.let(engine::submitReview)`.
- Usunąć importy `mapMatchToRating` jeśli niewykorzystane w pliku (jest jeszcze w `listenAndCheck`, więc zostaje) i `StudyConstants.STEP_DEFAULT_THRESHOLD` z tego miejsca (stała zostaje, jeśli używana gdzie indziej; ktlint wykryje nieużywany import).
- Zachowanie: INCORRECT/CORRECT bez oceny (niemożliwe) dawałoby teraz brak oceny zamiast zgadniętej; akceptowalne, bo niemożliwe.
- Testy: istniejące testy silnika powinny przejść bez zmian.

### DOM-N7 — CONFIRMED — martwy elvis i podwójne wyszukiwanie w `baseSrsStates`

Sprawdzone: `SessionEngine.kt:156-162` sprawdza `card.id in baseSrsStates`, po czym czyta `baseSrsStates[card.id] ?: card.srsState`. Elvis nigdy nie zadziała.

Fix (S, DOM): `val base = baseSrsStates[card.id]; if (base != null) { dispatch(RemoveFailedCard(card.id)); deps.onCardReviewed(group.id, card.id, rating, base) } else { baseSrsStates[card.id] = card.srsState; deps.onCardReviewed(group.id, card.id, rating, null) }`. Zachowanie identyczne. Testy sesji bez zmian.

### DOM-N8 — CONFIRMED — `BackupValidator.kt` ma 9 powtórzeń komunikatu, połamane warunki i drobne niedoróbki

Sprawdzone: `assertCompoundBranch` (88-101) 9x `stepMessage(.., "has invalid compound branch")`, `assertCompoundPause` 4x, `assertCompoundParams` kilka razy; połamania: linie 114-116, 169-171, 195-197, 242-246, 291-300 (efekt ktlint przy długich warunkach); linia 19 `catch (e: Exception)` z nieużywanym `e` (w kodzie wszędzie indziej `_`); `DataNormalizer.normalizeCard` (57-63) połamany tak samo.

Fix (S-M, DOM, czysto mechaniczny):
- Na początku `assertCompoundPause`, `assertCompoundBranch`, `assertCompoundParams`: `val invalid = stepMessage(modeId, index, "has invalid compound ...")` i używać `invalid`.
- `catch (_: Exception)`, `when (value["kind"])` bez zmiennej pośredniej.
- Warunki: `if (step["pauseMultiplier"] != null)` zmieścić w jednej linii przez lokalną `val multiplier = step["pauseMultiplier"]`; `rs !in setOf("all", "next")` jako `private val REVEAL_STYLES`; `pages.any { it !is String }` w jednej linii; w `validateStudyMode` małe `private fun isOptional(value: Any?, check: (Any) -> Boolean)` albo lokalne `val builtIn = mode["isBuiltIn"]`.
- `normalizeCard`: `if (card.srsState.state in SrsStates.VALID) card else card.copy(srsState = card.srsState.copy(state = SrsStates.NEW))`.
- Komunikaty błędów muszą pozostać bajt w bajt (testy `BackupValidatorTest` i `FirestoreSchemaTest` je sprawdzają). Uruchomić ktlint, bo to on narzucił połamania.
- Ryzyko: niskie przy pokryciu testami.

### DOM-N9 — CONFIRMED — czterokrotnie zduplikowane „puść palec” i zbędny guard w `handleGateTap`

Sprawdzone: `holding.value = false; peekTimerJob?.cancel()` (+ `peekTimerJob = null` w trzech z czterech) w `interruptCurrentCard`, `pressUp`, `cancelPress`, `pause`. W `pause()` brak zerowania jest bez skutku (anulowany Job), więc „niespójność” jest tylko kosmetyczna. `handleGateTap` (linia ~313) jest wołany wyłącznie z `handleTapWhileIdle`, który już sprawdził `kind == TAP_TO_REVEAL`.

Fix (S, DOM `session/SessionEngine.kt`):
- `private fun releaseHold() { holding.value = false; peekTimerJob?.cancel(); peekTimerJob = null }` i użyć w czterech miejscach.
- Usunąć zbędny guard w `handleGateTap` (zostawić odczyt `gate`).
- Testy sesji bez zmian.

### DOM-N10 — CONFIRMED — domyślna nazwa strony `"Page ${it + 1}"` w 4 kopiach

Sprawdzone: `DataNormalizer.kt:44`, `GroupOperations.kt:31`, `DeckRepository.kt:129`, `DeckSettingsViewModel.kt:95`. Jedyne wywołanie produkcyjne (`DeckSettingsScreen.kt:157`) przekazuje zlokalizowaną lambdę z zasobów; domyślne wartości służą testom i `normalizeGroup`.

Fix (S, DOM + CROSS-LAYER DAT `data/repository/DeckRepository.kt` i EKR `ui/decksettings/DeckSettingsViewModel.kt`):
- `fun defaultPageName(index: Int): String = "Page ${index + 1}"` w `domain/model/Constants.kt` obok `PageLimits` (albo w `DataNormalizer.kt`).
- Cztery miejsca: `defaultName: (Int) -> String = ::defaultPageName`.
- Alternatywa lepsza dla ViewModelu i repozytorium: usunąć wartość domyślną i wymagać lambdy tam, gdzie jedyny produkcyjny wołający ją przekazuje; wtedy trzeba zaktualizować `DeckRepositoryTest` (trzy wywołania `setActivePageCount(id, n)`) i `DeckSettingsViewModelTest` jest już zgodny. Zalecany wariant minimalny: wspólna funkcja i `::defaultPageName`, bez zmiany sygnatur.
- Uwaga (bez zmiany zachowania): `normalizeGroup` zapisuje angielskie „Page N” w danych (nie ma dostępu do zasobów); jest to stare i zamierzone, nie dotyczy tej poprawki.

### DOM-N11 — PARTIAL — „aktywne strony” liczone w kilku miejscach: sensowne `hiddenPages()` w bramce, reszta marginalna

Potwierdzone: `StepExecutor.startRevealGate` (130-131) reimplementuje prywatne `SessionEngine.hiddenPages()`. Dodatkowo `(0 until group.activePageCount)` występuje w `StepExecutor.kt:60` i `:78`, `SessionEngine.kt:250`. Niepotwierdzone jako wartościowe: `Flashcard.visiblePages(count)` dla dwóch użyć w różnych warstwach (`SessionEngine.kt:219` i `BrowseFilter.kt:17`) to wprowadzanie API dla dwóch miejsc.

Fix (S, DOM):
- `val FlashcardGroup.activePages: IntRange get() = 0 until activePageCount` w `domain/model/FlashcardGroup.kt` i użyć w czterech miejscach.
- `hiddenPages()` zmienić na `internal` w `SessionEngine`; w `StepExecutor.startRevealGate`: `if (engine.hiddenPages().isNotEmpty()) dispatch(...)`.
- Pominąć `Flashcard.visiblePages`.
- Testy sesji bez zmian.

### DOM-N12 — CONFIRMED — `seedModeFor(...)?.takeIf { isBuiltIn }` łatwo odczytać jako flagę seeda

Sprawdzone: w lambdzie `takeIf` brak odbiorcy, więc `isBuiltIn` rozwiązuje się do `this` (sprawdzanego trybu). Poprawne, ale mylące; seed zawsze ma `isBuiltIn = true`.

Fix (S, DOM `operations/StudyModeOperations.kt:27-30`): `if (!isBuiltIn) return false; val seed = seedModeFor(builtInSourceId) ?: return false; return name != seed.name || steps != seed.steps`. `MigrationRulesTest` pokrywa funkcję pośrednio.

### DOM-N13 — PARTIAL — `takeIf` ma sens, przepisanie `localSrsWins` to remis czytelności

Potwierdzone: trzy kopie `if (deletedAt > 0) deletedAt else null` (MergeEngine ok. linii 61, 92, 121). `deletedAt.takeIf { it > 0 }` jest równoważne (typ `Long`). Opcjonalnie lokalny `private fun Long.asTombstone(): Long? = takeIf { it > 0 }`.
Niepotwierdzone jako ulepszenie: `localSrsWins` jako `when` odzwierciedla drabinkę priorytetów (więcej powtórzeń, potem nowszy znacznik) i jest czytelne; jednolinijkowy `||`/`&&` nie jest jaśniejszy. Zostawić.

Fix (S, DOM `merge/MergeEngine.kt`): trzy podmiany na `takeIf`. Przed zmianą uruchomić `MergeEngineTest` i `TwoDeviceSyncTest`. Ryzyko: znikome.

### DOM-N14 — CONFIRMED — zbyt szeroka widoczność symboli używanych tylko w jednym pliku

Sprawdzone grepem po `app/src`:
- `HeaderDetector.HEADER_TOKENS`, `looksLikeHeaderLabel`, `looksLikeShortLabel`, `looksLikeDataValue`: tylko `HeaderDetector.kt` -> `private`.
- `getStoredPageCount`: tylko `DataNormalizer.kt` -> `private`.
- `normalizeCsvText`: tylko `CsvParser.kt` -> `private`.
- `mergeActivityHeatmap`: `MergeEngine.kt` + `MergeEngineTest` -> `internal`.
- `BackupValidator.kt`: `validateSrsState`, `validateCard`, `validateGroup`, `validateStudyMode` używane tylko w pliku -> `private`; `validateBackupData` używane w pliku i `BackupValidatorTest` -> `internal`; `assertStudyModeStep` używane też przez `data/remote/firestore/FirestoreSchema.kt` -> `internal` (nie `private`).
- `normalizeHeaderToken` jest już `internal` i faktycznie używane przez `LanguageDetector` i test; bez zmian.

Fix (S, DOM). Nie wymaga edycji plików w innych obszarach (`internal` dostępne w całym module). Uwaga: `private` w obiekcie `HeaderDetector` + test w `ImportParserTest` nie używa tych funkcji (sprawdzone). Zmiana na `private` przy publicznym `looksLikeHeaderRow` nie wymaga nic więcej.

### DOM-N15 — CONFIRMED — `live()` z dala od `isLive` i dwa miejsca budujące klucz dnia

Sprawdzone: `live()` w `selectors/DueCards.kt:11`, użycia w `BrowseFilter`, `CardStats`, `DueCards` i `ui/archive/ArchiveViewModel.kt:52`; `isLive` w `model/Flashcard.kt:16`. `ui/stats/ActivityHeatmap.kt:77` używa `date.toString()` zamiast `LocalDates.dayKey(date)`; dziś wynik identyczny (`dayKey` = `date.toString()`), ale to dwie definicje klucza.

Fix (S, DOM + CROSS-LAYER EKR dla `ui/stats/ActivityHeatmap.kt`):
- Przenieść `fun List<Flashcard>.live()` do `domain/model/Flashcard.kt` obok `isLive` (zmienić pakiet importów w czterech plikach, w tym `ArchiveViewModel`; zasięg tego importu to EKR).
- `heatmap[LocalDates.dayKey(date)]` w `ActivityHeatmap.kt`.
- Tańsza alternatywa dla `live()`: zostawić w `selectors` (używa go wyłącznie logika selektorów + jedno miejsce UI); przeniesienie jest czysto estetyczne. Jeśli ma być robione, to razem z poprawką klucza dnia.

### DOM-N16 — PARTIAL — tylko uproszczenie `elapsedMs` jest wartościowe

- `elapsedMs = now - (if (last != 0L) last else now)` -> `if (last == 0L) 0L else now - last` (równoważne, czytelniejsze). Potwierdzone, S, `SrsEngineTest` musi dać wyniki co do bitu takie same (nie zmienia się arytmetyka na `Double`).
- `newD/newS/newState` jako `val` z odroczonym przypisaniem: to idiomatyczne definite assignment w Kotlinie. `Triple` zamieniłby nazwane wartości na pozycyjne i dodałby alokację na ścieżce oceny. Zostawić.
- `wordSimilarity`: ręczne `bestScore/bestIdx/used` mają subtelną semantykę (zero nie jest nigdy wybierane, remisy do pierwszego, próg `> WORD_MATCH_MIN`). Przepisanie na `maxByOrNull` jest równoważne tylko przy `WORD_MATCH_MIN >= 0`, daje niewielki zysk i ryzykuje regresję w ocenie mowy bez korzyści. Zostawić.

Fix (S, DOM `srs/SrsEngine.kt:15`): tylko `elapsedMs`. Test: istniejący `SrsEngineTest`.

### DOM-N17 — CONFIRMED — magiczne liczby w `HeaderDetector`

Sprawdzone: `24`, `3`, `5` w `looksLikeShortLabel`/`looksLikeDataValue` oraz wagi `0.65/0.45/0.15/0.1/0.35/0.15` i zaokrąglenie do setnych w `looksLikeHeaderRow`. `HEADER_CONFIDENCE_MIN = 0.5` już jest w `ImportConstants`.

Fix (S, DOM): prywatne stałe w `HeaderDetector` (np. `SHORT_LABEL_MAX_CHARS = 24`, `SHORT_LABEL_MAX_WORDS = 3`, `SENTENCE_MIN_WORDS = 5`, `SCORE_KEYWORDS_MANY = 0.65` itd.) albo w `ImportConstants`. Nie przechodzić na punkty całkowite i próg `>= 50`: zmiana reprezentacji liczb bez korzyści, a obecne zaokrąglenie `roundToInt() / 100.0` jest celowe (obrona przed błędami typu 0.7000000000000001). Wynik detekcji musi pozostać bez zmian; testy `ImportParserTest` (nagłówki) wystarczą.

### DOM-N18 — CONFIRMED — `runSteps` jako rozszerzenie `CoroutineScope` tylko dla `ensureActive()`

Sprawdzone: `private suspend fun CoroutineScope.runSteps(card)` (SessionEngine ~103-108) wołany z `scope.launch(start = LAZY) { runSteps(_state.value.cards[cardIndex]) }`. `s.cards[cardIndex]` jest znane przed `launch` (lista kart zmienia się tylko przez `StartSession`, a `start()` najpierw anuluje wszystkie zadania kart).

Fix (S, DOM `session/SessionEngine.kt`):
- `val card = s.cards[cardIndex]` przed `launch`; `scope.launch(start = LAZY) { runSteps(card) }`.
- `private suspend fun runSteps(card: Flashcard)` z `currentCoroutineContext().ensureActive()` (import `kotlinx.coroutines.currentCoroutineContext`).
- Zachować wstawienie `job` do `cardJobs` PRZED `job.start()` (znana pułapka z LAZY i natychmiastowym dispatcherem); nie ruszać tej kolejności.
- Testy: `SessionEngine*Test` bez zmian.

### DOM-N19 — PARTIAL — zbędne `coerceAtLeast(0)` potwierdzone; przeniesienie `DAY_MS` opcjonalne

- `PageLimits.clampActivePageCount`: wewnętrzne `candidate.coerceAtLeast(0)` jest zbędne, bo zewnętrzne `maxOf(MIN_PAGE_COUNT, ...)` i tak daje minimum 2. Fix (S): `candidate.coerceAtMost(upperBound).coerceAtLeast(MIN_PAGE_COUNT)` (nie `coerceIn`, bo rzuca przy `upper < lower`). Testy `OperationsTest` (linie 63-64) i `DataNormalizerTest` potwierdzą równoważność.
- `DAY_MS` w `SrsConstants` używany przez `ArchiveConstants`: prawda, ale dotyka `SrsEngine.kt` (2 użycia) i trzech testów (`SrsEngineTest`, `ArchiveViewModelTest`). Wartość czysto porządkowa; jeśli robić, nowy `object TimeConstants { const val DAY_MS = 86_400_000L }` i zamiana importów (testy w obszarze TB/EKR poprawić razem). Dopuszczalne, ale najniższy priorytet.

### DOM-N20 — CONFIRMED — `core.SystemClock` ma nazwę zegara monotonicznego z Androida, a jest zegarem ściennym

Sprawdzone: `core/Clock.kt:12` `object SystemClock : Clock { now() = System.currentTimeMillis() }`; `android.os.SystemClock` to realny kłopotliwy kolizyjny import. Wbrew opisowi findingu żaden test nie odwołuje się do `SystemClock` (grep po `app/src/test` pusty); jedyny użytkownik poza definicją to `di/AppModule.kt:5,45`.

Fix (S, DOM + CROSS-LAYER DAT dla `di/AppModule.kt`): zmienić nazwę na `WallClock` (definicja + import i `provideClock(): Clock = WallClock`). Brak zmian testów. Ryzyko: znikome.

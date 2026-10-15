# Red team Codex: DOM (20 uwag)

Stan: `39f0690b`, 15.10.2026. Ścieżki Kotlin względem `app/src/main/java/com/raycoll/app/`.
To ponowna ocena także istniejącego `verdict-domain.md`. CONFIRMED przy porządkach oznacza zasadną drobną poprawę, nie błąd działania. PARTIAL obejmuje wyłącznie opisany niżej zakres. Nie wdrożono zmian.

### DOM-N1 — CONFIRMED — usunięty własny tryb wywołuje pytanie o migrację

`domain/merge/MigrationRules.kt:19` sprawdza autorstwo bez `deletedAt`; `LocalStore.snapshotOf` jawnie zawiera tombstony, a `StudyModeRepository.deleteMode` robi soft delete.
Naprawa: filtrować żywe tryby przed `isUserAuthoredMode`. S, niskie ryzyko. `MigrationRulesTest`: usunięty własny i zmodyfikowany wbudowany tryb nie oznaczają danych do przeniesienia.

### DOM-N2 — PARTIAL — błąd separatora realny, proponowana heurystyka powoduje regresję

`domain/imports/CsvParser.kt:66`: rzeczywisty kod wybiera COMMA dla pięciu wierszy `Wenn ich Zeit habe, gehe ich<TAB>If I have time, I go`. Odtworzone na skompilowanej aplikacji.
Jednak reguła „TAB/PIPE/SEMICOLON przed przecinkiem, jeśli dzielą połowę wierszy” zmieni poprawny trzykolumnowy CSV `word; hint,translation,example` z COMMA na SEMICOLON. To również sprawdzono. Nie kopiować specyfikacji z wcześniejszego werdyktu.
Naprawa M: preferować spójny TAB w danych arkuszowych; dla pozostałych kandydatów oceniać stałą liczbę kolumn w próbce, nie samą sumę fragmentów. Zachować ręczny wybór separatora i nie wprowadzać bezwarunkowego pierwszeństwa średnika/pipe. Testy obu powyższych przypadków, cudzysłowów i istniejące `ImportParserTest`; średnie ryzyko heurystyki. CROSS-LAYER EKR: regresja ręcznego wyboru w `ImportViewModelTest`.

### DOM-N3 — PARTIAL — puste końcowe kolumny są rzeczywiste, lecz nie zawsze zbędne

`HeaderDetector.detectPageCount` liczy szerokość, więc `a;b;\nc;d;` daje 3 strony; odtworzone. Samo `indexOfLast(isNotBlank)` traci jednak informację o nazwanej kolumnie nagłówka, w której wszystkie dane są jeszcze puste. Pusta strona może też być zamierzona.
Naprawa S/M po uzgodnieniu reguły importu: pomijać wyłącznie końcowe kolumny puste w całym źródle i bez niepustej nazwy w nagłówku; nie usuwać pustych kolumn wewnętrznych. CROSS-LAYER EKR: przekazać nagłówek do obliczenia szerokości, testy `a;b;`, `a;;c` oraz `front;back;notes` z pustymi notes. Średnie ryzyko utraty struktury; wymaga zatwierdzenia zachowania przy imporcie.

### DOM-N4 — DECISION — ocena za drugą próbę jest elementem zasad nauki

`domain/seed/SeedModes.kt:67–72` ponownie stosuje ocenę z dopasowania. `CompoundExpanderTest.audio mode pacing...` wprost przypina tę sekwencję; komentarz mówi o drugiej próbie, ale nie obiecuje kary. Nie wykazano błędu implementacji FSRS.
Rekomendacja do zatwierdzenia: druga udana próba = stałe HARD, alternatywa GOOD lub obecna ocena. Nie zmieniać własnych trybów. Po decyzji: seed + testy sekwencji + test aktualizacji nieedytowanych trybów w `LocalStore`. Automatyczna aktualizacja dotyczy tylko `updatedAt == 0`; nie potrzeba nowego systemu wersjonowania. CROSS-LAYER DAT.

### DOM-N5 — CONFIRMED — seria naprawdę zatrzymuje się na 365

`domain/selectors/CardStats.kt:35` ma limit; baza nie usuwa starszych dni. Wykonanie z mapą 400 dni zwróciło 365.
Naprawa S: iterować wstecz do pierwszego pustego dnia; usunąć `STREAK_MAX_DAYS`. Test 400 dni, brak dzisiejszej aktywności i luka w środku. Niskie ryzyko, pętla ograniczona skończoną mapą.

### DOM-N6 — CONFIRMED — fallback automatycznej oceny jest obecnie nieosiągalny

`StepExecutor.listenAndCheck` zawsze ustawia `suggestedRating` dla CORRECT/INCORRECT; pozostałe wyniki nie są oceniane przez tę gałąź.
Naprawa S: uprościć `AutoRateFromAnswerStep`, zachowując warunek statusu odpowiedzi i `!cardReviewed`; nie traktować dowolnego przyszłego `suggestedRating` jako odpowiedzi. Nie usuwać pól wyniku potrzebnych UI. Istniejące testy sesji i przypadek pominięcia/błędu; niskie ryzyko.

### DOM-N7 — CONFIRMED — zbędny fallback i dwa odczyty mapy

`SessionEngine.submitReview`: po sprawdzeniu obecności klucza wartość `SrsState` nie może być null. Naprawa S: jeden odczyt `val base = baseSrsStates[card.id]`, dalej dotychczasowe gałęzie pierwszej/powtórnej oceny. Testy sesji, bez nowego testu dla samej składni; niskie ryzyko.

### DOM-N8 — PARTIAL — powtórzone komunikaty warto usunąć, nowy miniwalidator zbędny

`BackupValidator` powtarza `stepMessage`; nieużywany parametr wyjątku i zbędna zmienna `kind` są drobnymi porządkami. Łamanie warunków narzuca ktlint, nie jest samo w sobie defektem.
Naprawa S: lokalna nazwa komunikatu w danej funkcji, `_` w catch i prostszy warunek `normalizeCard`; bez nowego DSL `isOptional` i bez zmiany tekstu błędów. `BackupValidatorTest`, `FirestoreSchemaTest`, ktlint; niskie ryzyko. W walidacji wejścia nie zmieniać znaczenia kontroli.

### DOM-N9 — CONFIRMED — cztery kopie zwalniania przytrzymania

W `interruptCurrentCard`, `pressUp`, `cancelPress`, `pause` powtarza się ten sam reset. Brak wyzerowania anulowanego joba w `pause` nie powoduje samodzielnego błędu.
Naprawa S: prywatne `releaseHold()`; zachować osobną semantykę `clearPeek` i rozróżnienie krótkiego/długiego dotknięcia. Zbędny guard `handleGateTap` można usunąć, bo jedyny caller sprawdza rodzaj bramki. Testy gestów i pauzy; niskie ryzyko.

### DOM-N10 — CONFIRMED — domyślna nazwa strony ma cztery kopie

`DataNormalizer`, `GroupOperations`, `DeckRepository`, `DeckSettingsViewModel`. Produkcyjny ekran podaje tłumaczenie; fallback domeny jest celowo niezależny od Androida.
Naprawa S: jedna funkcja `defaultPageName`, użyta jako fallback w czterech miejscach; bez usuwania nazw zapisanych w istniejących danych. CROSS-LAYER DAT + EKR; testy operacji/repozytorium i ktlint. Nie wykonywać drugi raz DAT-N19/EKR-N33.

### DOM-N11 — PARTIAL — wspólne obliczenie ukrytych stron, bez nowego API dla każdej listy

`startRevealGate` powiela regułę `hiddenPages`. Samo `0until activePageCount` jest idiomatyczne i nie stanowi istotnej duplikacji.
Naprawa S: współdzielić obliczenie ukrytych stron w silniku i executorze, zachować poprawny zbiór/porządek. Pominąć `Flashcard.visiblePages` i publiczne rozszerzenia wprowadzone wyłącznie dla krótkiego wyrażenia. Testy sesji; niskie ryzyko.

### DOM-N12 — CONFIRMED — niejawny receiver w `takeIf` utrudnia odczyt

`StudyModeOperations.isModifiedBuiltIn` działa poprawnie; `isBuiltIn` dotyczy badanego trybu, nie seeda.
Naprawa S: jawny wczesny return przy `!isBuiltIn`, następnie odczyt seeda. Testy trybów i migracji, ktlint; bez zmiany działania.

### DOM-N13 — PARTIAL — drobne `takeIf` dopuszczalne, logika priorytetów zostaje

Trzy `if (deletedAt > 0) deletedAt else null` można skrócić lokalnie. `localSrsWins` jako drabinka warunków czytelnie opisuje regułę i nie wymaga jednoliniowego `&&/||` ani nowego helpera.
Naprawa S: tylko lokalne `takeIf`; `MergeEngineTest`, `TwoDeviceSyncTest`. Nie łączyć kosmetyki z naprawą kolejności DAT-N1; niskie ryzyko.

### DOM-N14 — CONFIRMED — można zawęzić widoczność pomocników

Wyszukanie użyć potwierdza: lokalne pomocniki `HeaderDetector`, `getStoredPageCount`, `normalizeCsvText` i cztery walidatory mogą być private. `mergeActivityHeatmap` i `validateBackupData` wymagają internal dla testów. `assertStudyModeStep` używa też `FirestoreSchema`, więc nie może być private.
Naprawa S: dokładnie ten zakres. Kompilacja main/test + testy walidacji; niskie ryzyko. Nie zmieniać współdzielonego `normalizeHeaderToken`/`WHITESPACE`.

### DOM-N15 — PARTIAL — wspólny klucz dnia tak, przeniesienie `live()` opcjonalne

`ActivityHeatmap` wywołuje `date.toString()` zamiast istniejącego `LocalDates.dayKey`; wynik jest dziś równoważny. Obecne miejsce `live()` w selektorach jest rozsądne; zmiana pakietu tylko dla sąsiedztwa z `isLive` nie poprawia działania.
Naprawa S: użyć `LocalDates.dayKey` w UI. CROSS-LAYER EKR; `LocalDatesTest`, testy statystyk. Pominąć mechaniczne przenoszenie `live()`.

### DOM-N16 — PARTIAL — uprościć czas, nie przepisywać poprawnej arytmetyki

`elapsedMs` można czytelniej zapisać przez `if (lastReview == 0L) 0L else now-lastReview`. Odroczone przypisanie `val newD/newS/newState` jest poprawnym Kotlinem; `Triple` pogarsza nazwy. Dopasowanie słów ma semantykę remisów i progu, której nie warto zmieniać dla innej składni.
Naprawa S: tylko `elapsedMs`, `SrsEngineTest`; bez nowych testów samej równoważnej składni. Niskie ryzyko.

### DOM-N17 — CONFIRMED — nazwać progi heurystyki nagłówka

`HeaderDetector`: długość i liczba słów mają znaczenie domenowe, podobnie wagi heurystyki.
Naprawa S: prywatne stałe opisujące znaczenie, bez zmiany wartości, reprezentacji Double ani zaokrągleń. Istniejące `ImportParserTest`, ktlint; niskie ryzyko. Nie rozszerzać wspólnego `Constants.kt` o lokalne szczegóły parsera.

### DOM-N18 — CONFIRMED — jawna karta i kontekst anulowania upraszczają `runSteps`

`runCurrentCard` ma już kartę w odczytanym stanie. Naprawa S: przechwycić ją przed `launch`, zwykła prywatna `suspend fun runSteps`, `currentCoroutineContext().ensureActive()`.
Koniecznie zachować LAZY → wpis do `cardJobs` → start; to świadoma poprawka dla natychmiastowego dispatchera. Testy sesji z dispatcherem natychmiastowym; niskie ryzyko.

### DOM-N19 — PARTIAL — zbędny clamp tak, nowy obiekt czasu niepotrzebny

Wewnętrzne `coerceAtLeast(0)` nie zmienia końcowego minimum 2. Naprawa S: równoważne `candidate.coerceAtMost(upperBound).coerceAtLeast(MIN_PAGE_COUNT)`; nie `coerceIn`, gdy upperBound < minimum.
Testy normalizacji/operacji. Samo przenoszenie `DAY_MS` do nowego obiektu ma znikomy zysk; zostawić poza tą poprawką. Niskie ryzyko.

### DOM-N20 — CONFIRMED — `WallClock` lepiej wyraża kontrakt czasu

`core.SystemClock` zwraca czas ścienny, podczas gdy Android ma klasę o tej nazwie dla innego zegara. To poprawa nazwy, nie istniejący błąd importu.
Naprawa S: nazwa obiektu + jedyny użytkownik `di/AppModule.kt`; plik może nadal nazywać się `Clock.kt`. CROSS-LAYER DAT; kompilacja i testy, bez nowego testu nazwy.

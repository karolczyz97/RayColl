# Red‑team audytu portu RayColl (Kotlin) vs oryginał TensorDeck (TypeScript)

Data: 2026‑07‑14. Metoda: adwersaryjne porównanie **linia po linii** logiki domenowej
i ViewModeli portu Kotlin z oryginałem TypeScript w `../TensorDeck` (źródło prawdy
zachowania). Cel: wykryć odstępstwa, których „build zielony + testy z tego samego
rozumienia" nie łapią. Poprzedni samo‑audyt (`REGRESSION_SELFCHECK.md`) deklarował
„nie znaleziono błędów" — ten pass znalazł i naprawił **11 realnych** + usunął martwy kod.

Suita po naprawach: **206 testów zielonych** (było 195), `assembleDebug` i
`assembleRelease` (R8) zielone. Każda naprawa ma test regresyjny; dla dwóch
najtrudniejszych (bramka/tap‑gate) zweryfikowano, że test **pada bez** swojej naprawy.

## Naprawione błędy

| # | Plik | Błąd | Skutek dla użytkownika |
|---|---|---|---|
| 1 | `selectors/DueCards.kt` | Fisher–Yates gubił `i=n‑1` (`indices.reversed().drop(1)`) | W kolejności **losowej** ostatnia karta talii **nigdy** się nie tasowała — zawsze była na końcu sesji |
| 2 | `selectors/DueCards.kt` | `getGroupProgress` `toInt()` zamiast `roundToInt()` | Pasek postępu talii zaniżony o 1 pp (2/3 → 66% zamiast 67%) |
| 3 | `selectors/CardStats.kt` | `computeStreak` iterował od systemowego `LocalDate.now()`, a `hasToday` z wstrzykniętego `now` | Zły streak, gdy `now` ≠ data systemowa; łamało regułę „czas zawsze przez Clock" (i testowalność) |
| 4 | `session/SessionEngine.kt` | `cardStartedAt` resetowany też przy wznowieniu po tap‑gate | Okno „poprzednia = cofnij vs restart" (3 s) liczyło się od tapnięcia w bramkę, nie od startu karty |
| 5 | `session/SessionReducer.kt` | `AdvanceCard` nie resetował `status→IDLE` (TS = INITIAL) | Po ocenie karty status `REVEALED` **wyciekał** na następną; w trybie „cichym" (reveal bez speak) **bramka drugiej karty była nieklikalna → użytkownik utykał**. Dodatkowo parytet: `StartTapRevealGate→idle`, `StartListening` czyści `lastAnswerResult`, `SetCurrentStep`/`ShowRatings` czyszczą peek, `FinishSession` czyści peek/gate/pending |
| 6 | `normalize/DataNormalizer.kt` | Gałęzie compound (`listen_grade`/`grade_after_listen`) nie normalizowane | `speakPage<0`, `rating>4`, zły `rate`, pauza spoza zakresu przechodziły z uszkodzonego/importowanego backupu do ekspansji kroków |
| 7 | `srs/SrsEngine.kt` | clamp `stability` przez `coerceIn` przepuszczał NaN | Teoretyczny NaN → `nextReview` = teraz + `NaN.toLong()` = teraz; parytet z `clamp` (NaN→min) jak w gałęzi difficulty |
| 8 | `operations/GroupOperations.kt` | `setVisiblePageCount` dopełniał metadane do **bieżącego** storedPageCount zamiast do nowej liczby | Zwiększenie liczby stron ponad obecny stored (np. 2→4) się **nie utrwalało** — `normalizeGroup` cofał `activePageCount` |
| 9 | `ui/decksettings/DeckSettingsViewModel.kt` | `setVisiblePageCount` nie odświeżał `_deck` (inaczej niż `movePage`) | Zwiększenie stron pokazywało nowy numer w stepperze, ale **nie dodawało wierszy** nazw/języków nowych stron do konfiguracji (spec 3.4) |
| 10 | `steps/ModeStepValidator.kt` + VM | `NoTerminalStep`/`AutoRateWithoutStt` **blokowały** zapis, a w `validateModeSteps.ts` to **ostrzeżenia** | Nie dało się zapisać trybu, który oryginał dopuszcza (dodano `ModeIssue.blocking`, parytet z `hasBlockingStepIssue`) |
| 11 | `imports/ImportPayloadValidator.kt` | Martwy, rozjechany duplikat `detectFirstRowHeader`/`detectPageCount` (substring match, `length>50`, inny zestaw słów) | Nieużywany (ImportViewModel używa `HeaderDetector`), ale ryzyko przypadkowego podpięcia — **usunięty** |

## Znane, świadomie zaakceptowane rozbieżności (nie‑błędy)

- **Nagłówek/język importu**: Kotlin normalizuje `ł→l` (`normalizeHeaderToken`), TS nie (NFD nie rozkłada `ł`). Efekt: polskie nagłówki „słowo/tłumaczenie" **są** wykrywane w Kotlinie, a w TS **nie** — to poprawka latentnego buga oryginału, zgodna z „chyba że na lepsze".
- **`revealedPages` start = `[0]`** (Kotlin) vs `[]` (TS INITIAL). Kotlin trzyma się spec „strona 0 zawsze widoczna od startu karty"; TS wymagał tapa na odsłonięcie strony 0. Kotlin bliżej spec.
- **Import out‑of‑range**: pipeline Kotlin to `validate(raw)→decode→normalize→replace`; TS to `normalize→validate`. Backup z `threshold=250`/`rating=99` (tylko z korupcji/ręcznej edycji) Kotlin **odrzuca** z jasnym błędem, TS by sklampował i przyjął. Backupy eksportowane przez samą apkę zawsze mają wartości w zakresie → nie odrzuca legalnych. Fail bezpieczny (stan nietknięty).
- **Nieznany `type` kroku w backupie**: kotlinx rzuca na nieznanym dyskryminatorze; `validateBackupJson` łapie to jako „unsupported type" (odrzucenie) — TS by taki krok wyciął i przyjął resztę. Dotyczy tylko backupów z nowszej wersji/ręcznie zepsutych. Fail bezpieczny.
- **`lastTtsDurationMs`** liczony per‑karta (nowy `StepExecutor` na kartę), w TS per‑sesja. Różni się tylko soft‑timeout STT w trybie „listen bez wcześniejszego speak w tej karcie" — pomijalne.
- **`ModeStepValidator`** waliduje indeksy stron względem `MAX_PAGES=20` (tryby są niezależne od talii), nie względem `pageCount` konkretnej talii jak TS. Nie sprawdza indeksów **wewnątrz** kroków compound (runtime i tak filtruje `pageIndex >= activePageCount` przy ekspansji). Marginalne.

## Drugi przebieg — skan „całej apki" pod kątem uproszczeń (2026‑07‑14)

Pełny skan warstwy danych/DI/playback/UI pod kątem uproszczeń i martwego kodu. Znalazł
**2 kolejne realne błędy** (poprawność) + sprzątanie:

| Plik | Znalezisko | Skutek |
|---|---|---|
| `data/local/db/Daos.kt` + `DeckRepository.reviewCard` | `incrementActivity` = UPDATE count+1, ale wiersz dnia nigdy nie tworzony | **KRYTYCZNE**: pierwsza (i każda) ocena nowego dnia była no‑opem → heatmapa i streak **nigdy** by się nie zapisywały na urządzeniu. Test DAO maskował to, ręcznie tworząc wiersz. Fix: `ensureActivityRow` (INSERT OR IGNORE) |
| `data/repository/DeckRepository.kt` | Zero transakcji w całej warstwie danych | reviewCard (karta+heatmapa), addDeckWithCards, updateDeck, movePage, purgeExpiredArchives, seedIfEmpty, replaceAll robiły po kilka zapisów bez `db.withTransaction` → kill apki w połowie = sieroty/częściowy stan (spec 13.8, T5). Owinięte transakcyjnie |
| `DeckRepository` | 4 bliźniacze settery metadanych | Uproszczone do jednego `mutateDeckMeta` (uproszczenie, nie błąd) |
| `di/DispatchersModule.kt` | `@IoDispatcher`/`@DefaultDispatcher`/`@MainDispatcher` + providery | Martwy kod — nic ich nie wstrzykiwało. Usunięte |
| `data/repository/ArchivePurger.kt` | wstrzykiwany `Clock` nieużywany | Usunięty |
| `StatsViewModelTest` | asercja `heatmap is Map<*,*>` zawsze prawdziwa | → `heatmap.isEmpty()` |

Reszta (domenowa logika, ViewModele, playback‑bridge, DI, core) potwierdzona jako czysta.
Kompilator zgłasza tylko nieszkodliwe deprecacje wstecznej kompatybilności
(VIBRATOR_SERVICE dla API<31, coroutine `resume`, ikony kierunkowe — apka jest LTR‑only,
a AutoMirrored nie rozwiązuje się w tym BOM). Uznane za świadomie zostawione.

## Świadomie NIE ruszone: warstwa sync/Firestore (nie do zweryfikowania)

Sync jest zależny od Firebase (`google-services.json` NIE ma w repo) i oznaczony N‑A —
przepisywanie nietestowalnego kodu chmury na ślepo to duże ryzyko. Znane rozjazdy vs plan T10
(udokumentowane, NIE naprawiane):

- **SyncEngine.saveUserData**: batch bez dzielenia — Firestore limit ~500 operacji/batch;
  talia z >~500 dokumentami (karty+talie+tryby+dni) wywali `batch.commit()`. Plan wymagał
  chunkowania (400) i diff per‑dokument + czyszczenie osieroconych. Obecnie: pełny zapis
  wszystkiego za każdym razem (działa dla małych zbiorów, nieoptymalne/łamie się dla dużych).
- **Scheduler**: brak osobnego `SyncScheduler` — jest prosty debounce 1200 ms w `SyncLifecycle`.
  Brakuje: licznika „flush co 10 ocen" w study, `immediate+await` dla importów/usunięć,
  flush na `ProcessLifecycle onStop`, rollbacku importu przy błędzie chmury (talia‑widmo).
  Efekt: prostszy, local‑first (Room zawsze zapisany od razu; chmura best‑effort). Funkcjonalnie
  rozsądne dla v1 jednego urządzenia, ale nie 1:1 z polityką planu.

To są rzeczy, których się „obawiam", ale których nie da się bezpiecznie naprawić bez
konfiguracji Firebase i realnego urządzenia/emulatora.

## Dług techniczny / nietknięte (świadomie)

- **Room `exportSchema`**: build ostrzega, że schemat nie jest eksportowany (brak wtyczki `androidx.room` + `schemaDirectory`). Przy greenfieldzie, `fallbackToDestructiveMigration` i schemacie v1 (zero migracji) wartość eksportu jest znikoma; dodanie wtyczki to zmiana infrastruktury build z ryzykiem — zostawione świadomie.
- **`AppSettingsViewModel`** bez testu VM — zależy od Firebase‑coupled `AuthRepository`/`SyncLifecycle`/`MigrationController`; logika backupu pokryta przez `BackupRepository`/DAO. Dług testowy, nie funkcjonalny (jak w README).
- **Warstwy N‑A** (playback TTS/STT/Media3, sync Firestore, nawigacja/predictive back, Auto Backup) wymagają urządzenia — poza zakresem audytu kodowego.

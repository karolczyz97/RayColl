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

## Trzeci przebieg — pełny skan każdego pliku + domknięcie sync T10 (2026-07-14)

Kolejny adwersaryjny pass po CAŁEJ apce (każdy plik main + porównania z TS).
Znalazł i naprawił kolejne realne błędy oraz domknął największy dług — warstwę
sync wg spec T10. Suita po naprawach: **242 testy zielone**, debug + release (R8) zielone.

### Naprawione błędy (poprawność)

| # | Plik | Błąd | Skutek dla użytkownika |
|---|---|---|---|
| 1 | `MainActivity.kt` | `RayCollTheme` wołane z DEFAULTAMI — prefs motywu/dynamic colors nigdy nie trafiały do theme | Przełącznik motywu (jasny/ciemny/system) i Material You w ustawieniach **nie robiły nic** |
| 2 | `MigrationController.kt` | MIGRATE robił `getSnapshot()` PO przełączeniu namespace na uid → kopiował pusty snapshot | „Przenieś na konto" **przenosiło nic** — świeże konto zostawało puste |
| 3 | `HapticAndSoundPlayer.kt` | `loadSounds()` nie było wołane nigdzie (id=0) | Dźwięki feedback_success/feedback_error **nigdy nie grały**; do tego TS gra też haptykę przy feedbacku — dodana |
| 4 | `data/remote/firestore/FirestoreSchema.kt` | Format dokumentów niezgodny z TS: karta z płaskimi polami SRS (TS: zagnieżdżony `srsState`), tryb ze `stepsJson` stringiem (TS: strukturalne `steps`) | Web/Expo i Android **nie czytałyby nawzajem swoich danych** z tego samego konta Firestore |
| 5 | `SyncEngine.kt` | Jeden batch bez chunkowania (limit Firestore 500 op) | Talia >~500 dokumentów = **sync trwale zepsuty** (commit zawsze odrzucany) |
| 6 | `SyncEngine.kt` | Zero czyszczenia osieroconych dokumentów | Po factory-reset/imporcie backupu stare talie **wracały z chmury** przy następnym bootstrapie |
| 7 | `ModeStep.kt` | Defaulty CompoundParams ≠ TS (`speak/speakQuestion/manualFallback=false`, `nextPage=0`) | „Dodaj krok" w edytorze tworzył kroki inne niż oryginał (np. speak_pause_next odsłaniał stronę 0, która już jest widoczna) |
| 8 | `CompoundExpander.kt` | Warunek z kroku compound propagowany na listen+gałęzie (TS: ignorowany) + odwrócony fallback flip_reveal | Pominięty warunkiem listen zostawiał stary wynik → gałęzie correct/wrong **oceniały od nieaktualnej odpowiedzi** |
| 9 | `BootstrapLoader.kt` + `DashboardViewModel.kt` | Bootstrap nie seedował trybów nauki; Dashboard seedował też namespace zalogowanego przy błędzie chmury | Świeże konto bez trybów do restartu apki; seed przy niedostępnej chmurze → **duplikaty seedów** po merge |
| 10 | `DeckRepository/StudyModeRepository.seedIfEmpty` | check-then-insert bez transakcji, wołane równolegle | Wyścig przy starcie mógł **podwoić talie seedowe** |
| 11 | `StatsViewModel.kt` | `System.currentTimeMillis()` zamiast `Clock` | Łamanie reguły projektu (ta sama klasa co #3 z drugiego passu); nietestowalny streak |
| 12 | `SttRecognizer.kt` | Ścieżka NO_MATCH zwracała tylko finalne segmenty (bez partiali) | Rozpoznane, ale niesfinalizowane słowa **gubione** przy braku końcowego wyniku (TS: getText() = finals+interim) |
| 13 | `TtsPlayer.kt` | Brak filtra utteranceId + LANG_MISSING_DATA traktowane jak dostępny głos | Spóźniony callback przerwanej wypowiedzi mógł przedwcześnie zakończyć następną; brak dialogu instalacji głosu przy niepobranych danych |
| 14 | `AppSettingsScreen.kt` | Brak prośby o POST_NOTIFICATIONS | Notyfikacja nauki w tle **nie pokazywała się** na Androidzie 13+ |

### Domknięty dług T10 (wcześniej „świadomie nie ruszone")

- **SyncScheduler** (nowy, testowany na wirtualnym czasie): debounce 1200 ms
  latest-wins, licznik ocen study z flushem co 10, immediate dla importu/
  usunięcia/archiwizacji/restore, single-in-flight + retry nieudanego pusha.
- **SyncLifecycle**: flush na granicy sesji study (FINISHED/wyjście z ekranu),
  flush na ProcessLifecycle ON_STOP, best-effort flush przed wylogowaniem.
- **SyncEngine**: diff per dokument względem cache'u chmury (cache tylko po
  pełnym sukcesie), pełny zapis z czyszczeniem osieroconych, chunkowanie 400,
  corrupt dokument pomijany per-doc z warningiem (jak firestorePersistence.ts).
- **DataMutations**: sygnały z trybem cloud (debounced/study/immediate).
- **SyncStatusBanner** podpięty w ustawieniach (był napisany, nieużywany).
- Testy: `FirestoreSchemaTest` (round-trip + strict), `SyncDiffTest`,
  `SyncSchedulerTest`.

### Nadal świadomie pominięte

- **Rollback importu przy błędzie chmury** (plan T10): import/usunięcie sygnalizuje
  immediate push, ale wywołujący nie czeka na wynik chmury i nie przywraca
  poprzedniego snapshotu. Local-first: Room zawsze spójny; przy błędzie chmury
  scheduler ponawia. Talia-widmo w chmurze nie powstaje (diff dopisze/skasuje
  przy następnym udanym pushu).
- **Seed „tylko w pamięci" przy błędzie chmury** (plan T10): zamiast tego w ogóle
  nie seedujemy przy nieudanym loadzie (pusty dashboard + banner błędu) — Room-first
  nie ma warstwy pamięciowej, a to bezpieczniejsze niż persystowanie seedów.
- Warstwy wymagające urządzenia (TTS/STT/Media3/focus/notyfikacje, realny
  Firestore) — zweryfikowane statycznie 1:1 z TS, nie na sprzęcie.

## ~~Świadomie NIE ruszone: warstwa sync/Firestore~~ (NIEAKTUALNE — naprawione w trzecim przebiegu)

> Rozjazdy opisane w tej sekcji (batch bez chunkowania, brak SyncSchedulera,
> brak flushy) zostały **naprawione** w trzecim przebiegu — patrz sekcja wyżej.
> Warstwa Firestore pozostaje niezweryfikowana na realnym urządzeniu/emulatorze
> (brak `google-services.json` w repo), ale logika (schemat, diff, scheduler)
> jest pokryta testami jednostkowymi.

## Dług techniczny / nietknięte (świadomie)

- **`AppSettingsViewModel`, `SyncLifecycle`, `BootstrapLoader`** bez testów VM/integracyjnych — zależą od Firebase‑coupled `AuthRepository`; logika wyekstrahowana do testowanych modułów (`SyncScheduler`, `SyncDiff`, `FirestoreSchema`, `BackupRepository`). Dług testowy, nie funkcjonalny.
- **Rollback importu przy błędzie chmury** — patrz „Nadal świadomie pominięte" wyżej.
- **Warstwy N‑A** (playback TTS/STT/Media3, realny Firestore, nawigacja/predictive back, Auto Backup) wymagają urządzenia — poza zakresem audytu kodowego.

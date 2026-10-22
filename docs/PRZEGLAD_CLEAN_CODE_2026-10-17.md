# Przegląd clean code — 17.10.2026 (po red teamie)

Gałąź `main`, commit `c01d3128`, drzewo czyste. Tylko raport, kod nietknięty.

Ścieżki Kotlin w tabelach są względem `app/src/main/java/com/raycoll/app/`, testy względem `app/src/test/java/com/raycoll/app/`.
Waga: **Ś** średnia, czyli podnosi koszt zmian albo grozi ukrytym błędem. **N** niska, czyli czytelność i porządek.

## Jak to sprawdzono

**Pierwszy przegląd.** 7 agentów, każdy na swój obszar. Kluczowe znaleziska potwierdziłem ręcznie w kodzie.

**Red team.** 6 agentów dostało zadanie, żeby obalić każdą pozycję. Szukali trzech rzeczy:
- czy coś jest celowe (komentarz, opis commita, kontrakt w `docs/PLAN_SYNC_FIRESTORE.md` §14, `docs/PLAN_PORZADKI.md`, `docs/ODSTEPSTWA.md`, pamięć projektu, test),
- czy coś jest nieprawdą,
- czy poprawka po cichu zmienia zachowanie.

Sporne werdykty sprawdziłem sam: ktlint, konfigurację testów, kontrakt §14.2, commit foojay, zapis do Firestore i folder prototypu.

| Sekcja | Było | Zostaje bez zmian | Poprawione | Usunięte (celowe albo błędne) | Przeniesione do decyzji |
|---|---|---|---|---|---|
| A1 martwy kod | 16 | 9 | 5 | 2 (A1.12 i A1.15 celowe) | 0 |
| A2 ciche błędy | 12 | 3 | 8 | 0 | 1 (A2.11) |
| A3 duplikaty | 18 | 3 | 9 | 5 (A3.3, A3.4, A3.6, A3.13, A3.15) | 1 (A3.10) |
| A4 duże klasy | 15 | 2 | 8 | 5 (A4.2, A4.4, A4.12, A4.13, część A4.14) | 0 |
| A5 nazwy | 6 | 4 | 2 | 0 | 0 |
| A6 testy | 6 | 3 | 3 | 1 podpunkt (restoreCard ma test) | 0 |
| B decyzje | 12 | 3 | 8 | 1 (D3 błędne) | — |

**Ogólny wniosek.** Martwy kod i ciche błędy w większości się potwierdziły. Duplikaty i podziały klas były przesadzone:
- część „kopii” różni się celowo albo wspólny kod już istnieje,
- kilka podziałów łamałoby kontrakty z §14 albo zapisany format danych.

Red team znalazł też nowe rzeczy. Najważniejsze: asercja w teście logowania, która zawsze przechodzi, oraz dwa przypadki startu gościa bez danych (D2).

## Stan wyjściowy (zmierzony)

- `:app:ktlintCheck :app:testDebugUnitTest :app:lintDebug`: zielone. 541 testów zaliczonych, 8 pominiętych (to testy emulatora Firestore). Lint: 0 zgłoszeń.
- Przestarzałe API: 2 ostrzeżenia kompilatora w kodzie aplikacji, 0 w testach.
- Zasoby: 0 nieużywanych. Tłumaczenia kompletne w 5 językach.
- **Pułapka przy poprawkach.** Testy JVM nie mają `unitTests.isReturnDefaultValues` (`app/build.gradle.kts:102`). Dlatego `android.util.Log` w kodzie, który przechodzi przez zwykły test JVM bez Robolectric, wywali test błędem „not mocked”. Logi dopisuję tylko tam, gdzie testy biegną na Robolectric, albo w klasach Androida.

---

## A. Poprawiam od razu — bez zmiany zachowania i wyglądu

### A1. Martwy kod i przestarzałe API

| ID | Gdzie | W | Co | Poprawka |
|---|---|---|---|---|
| A1.1 ✔ | 29 plików (26 importów w kodzie, 47 w testach), w tym 12 w `ui/dashboard/DashboardScreen.kt:14-67` | N | 73 nieużywane importy. ktlint ich nie widzi, bo w 1.8.0 reguła `no-unused-imports` jest **opcjonalna** (`OnlyWhenEnabledInEditorconfig`, sprawdzone javap). Jeden import ktlint pomija nawet po włączeniu reguły: `DashboardScreen.kt:55` (`semantics.contentDescription`, używany tylko jako nazwa argumentu). | Usunąć importy. Włączyć regułę w `.editorconfig` (`ktlint_standard_no-unused-imports = enabled`). Do `ktlintSources` dodać `"!.claude/**"`, bo inaczej ktlint sprawdza też 6 starych kopii roboczych agentów. |
| A1.2 ✔ | `ui/appsettings/SettingsSections.kt:158` | N | Przestarzałe `Slider(value, …)`. | `Slider(state, onValueChange, …, onValueChangeFinished)` z `rememberSliderState` w `key(preferences.ttsRate)`. Bez tego klucza suwak przestałby nadążać za zmianą zapisaną gdzie indziej. Sprawdzę testem i zrzutem, że gałka się przesuwa. |
| A1.3 ✔ | `ui/imports/ImportViewModel.kt:397` | N | Przestarzałe `launch(NonCancellable)`. | `launch(start = UNDISPATCHED) { withContext(NonCancellable) { deleteDraft() } }`. Działa dokładnie tak samo. |
| A1.4 ✔ | `playback/StudySessionHolder.kt:31,73-74,130,170,204` | Ś | `SessionBoundary`/`boundary` jest zapisywane, ale nigdzie nieczytane. Jedyny odbiorca zniknął razem z Room (6a5461a4). | Usunąć. |
| A1.5 ✔ | `playback/StudySessionHolder.kt:60-64` | N | Konstruktor `(context)` nie ma wywołań. | Usunąć go i domyślny `focusRequester`. Domyślny `scope` zostaje, bo używa go test. |
| A1.6 ✔ | `data/remote/firestore/FirestoreSchema.kt:47-51` | N | Stałe `PATH_*` mają 0 użyć. | Usunąć tylko stałe. `UserPaths` zostaje, gdzie jest, zgodnie z kontraktem §14.1. |
| A1.7 ✔ | `ui/dashboard/DashboardScreen.kt:84,86,237` | N | `MinTouchTarget` ma 0 użyć. Dwa `@OptIn` są zbędne (sprawdzone w bibliotece). | Usunąć. |
| A1.8 ✔ | `domain/model/FlashcardGroup.kt:39,57` | N | `StudyFilter.fromKey` i `CardOrder.fromKey` mają 0 wywołań. | Usunąć. |
| A1.9 | `domain/model/ModeStep.kt:189,202`, `domain/session/SessionState.kt:15-16` | N | `BranchRating.key` i `RevealStyle.key` nikt nie czyta; to nie są pola zapisywane w danych. `threshold` nikt nie czyta, a `percent` czyta tylko test. | Usunąć. Poprawić konstruktory w `StudyPresentationTest.kt:132`, `SessionReducerTest.kt:118,135`. Asercja na `percent` jest zbędna, bo linia 213 sprawdza już SKIPPED. |
| A1.10 | `ui/components/SegmentedBar.kt:35`, `SnackbarMessages.kt:29`, `States.kt:61`, `SingleChoiceButtonGroup.kt:33` | N | Parametry, których żaden wywołujący nie podaje. | Usunąć `height`, `duration`, `icon`, `enabled`. `modifier` zostaje, bo tak każą zasady API Compose. |
| A1.11 | `ui/dashboard/StudySplitButton.kt:49-50,113`, `ui/decksettings/DeckSettingsViewModel.kt:106`, `ui/modedetail/ModeDetailScreen.kt:73` | N | Niepotrzebne wartości domyślne i martwa gałąź `deckName == null`. | Parametry obowiązkowe. Wartość domyślna w `DeckRepository.setActivePageCount` zostaje, bo korzystają z niej testy. |
| A1.13 | `app/build.gradle.kts:123,130,132` | N | `ui-tooling(-preview)` jest zbędne, bo nie ma żadnego `@Preview`. `lifecycle-viewmodel-compose` dostarcza już Hilt. | Usunąć te 3 zależności. |
| A1.14 ✔ | `AndroidManifest.xml:27`, `res/xml/backup_rules.xml` | N | `fullBackupContent` działa tylko do Androida 11, a minSdk to 33. | Usunąć atrybut i plik. |
| A1.16 | `gradle/libs.versions.toml:49` | N | Mockito jako jedyny ma wersję wpisaną w linii. | Użyć `version.ref`. |
| ~~A1.12~~ | — | — | **Celowe.** `AccountState.Ready.email` jest wpisane w kontrakt §14.2 (linia 781). | Zostaje. |
| ~~A1.15~~ | — | — | **Celowe.** Wtyczkę foojay dodano świadomie (e6338d82, `PLAN_PORZADKI.md` etap 1). Gradle pobiera nią Javę 21. | Zostaje. |

### A2. Ciche błędy — dopisanie logów

| ID | Gdzie | W | Co | Poprawka |
|---|---|---|---|---|
| A2.1 ✔ | `domain/backup/BackupCheck.kt:12-49` i `data/repository/BackupRepository.kt:74-80` | Ś | 17 `require` bez komunikatu. Właściwe połknięcie jest w `BackupRepository`: `catch`, potem `INVALID_BACKUP`, bez logu. | Komunikaty w `require` i `Log.w` w tym `catch`. Bez dzielenia `BackupCheck`, bo plan zakłada krótką kontrolę, ok. 50 linii. |
| A2.2 | `playback/SessionDepsAdapter.kt:34-35,51-54` | Ś | Nieoczekiwany wyjątek mowy lub rozpoznawania znika bez śladu. Zamiana na błąd jest celowa i przypięta testem (`SessionEngineSemanticsTest:488`). | Log w adapterze (log, potem dalej rzucić). Domena i interfejs `SessionDeps` bez zmian. |
| A2.3 | `playback/SttRecognizer.kt:52-59,97-102` | N | Kod błędu rozpoznawania mowy i wczesne wyjścia (brak zgody, brak usługi) nie zostawiają logu. | Log w `onError` i w obu wyjściach. Nie w `listenResultForError`, bo tę funkcję testuje zwykły test JVM. |
| A2.4 | `playback/SilentPlayback.kt:49,54,80` | N | Ciche jest tylko niepowodzenie `setLoopPoints` i `runCatching` przy pauzie i zwolnieniu. Błąd `write` już się loguje przez wyjątek `play()`. | Log tylko w tych miejscach. |
| A2.5 | `playback/TtsPlayer.kt:69,94` | N | Przekroczenie czasu startu TTS i błąd startu dają ten sam komunikat. Komentarz „wymagane przez starsze Androidy” jest nieprawdziwy: metoda jest abstrakcyjna. | Rozróżnić te dwa przypadki, poprawić komentarz. |
| A2.6 ✔ | `playback/HapticAndSoundPlayer.kt:52-53` | N | Nieudane wczytanie dźwięku: brak logu, dźwięk wisi w `pending`. | Log i usunięcie z `pending`. |
| A2.7 | `data/files/DocumentFiles.kt:74-82`, `data/local/prefs/UserPreferencesRepository.kt:74-76` | Ś | Błąd odczytu pliku wygląda jak brak pliku. Najważniejsze skutki to po cichu pominięty `pending_join` i błąd zapisu kopii. | `Log.w`. Oba miejsca mają testy na Robolectric. |
| A2.8 ✔ | `data/repository/DeckRepository.kt:76-78`, `BackupRepository.kt:52-54` | N | Wadliwe dni aktywności giną po cichu, w 2 kopiach. | `Schema.readActivity(doc)` z logiem. |
| A2.9 | `ui/imports/ImportViewModel.kt:454`, `ui/modedetail/ModeDetailViewModel.kt:264` | N | Odrzucenie uszkodzonego szkicu jest celowe, ale nie zostawia logu. | Dopisać log. Bez łączenia funkcji: to dwa osobne szkice. |
| A2.10 ✔ | `ui/appsettings/AppSettingsViewModel.kt:124-133` | Ś | Nieoczekiwany błąd wyboru konta jest podpisywany jako „Usługi Google Play”, bez logu. | Dopisać log. |
| A2.12 | `ui/navigation/ScreenResult.kt:21-23`, `ui/browse/CardEditorDialog.kt:68` | N | `runCatching` bez wyjaśnienia. | Komentarz „dlaczego wolno”, bez logu. |

### A3. Duplikaty — tylko kopie, które muszą zmieniać się razem

| ID | Gdzie | W | Co | Poprawka |
|---|---|---|---|---|
| A3.1 ✔ | `data/repository/Cleanup.kt:19` | Ś | 30 dni jest wpisane ręcznie, a reguły mają własną liczbę (`firestore.rules:13`). | Stała `TOMBSTONE_RETENTION_MS` z komentarzem „nie mniej niż w regułach” (por. D10). |
| A3.2 | `data/repository/*` | Ś | Nazwy pól Firestore wpisane jako tekst, chociaż `FIELD_UPDATED_AT` już istnieje. Filtr „nieusunięte” powtórzony ok. 20 razy. | Gotowe łatki i stałe pól w `FirestoreSchema`. `isLive` dla talii i trybów. Surowy odczyt w `Cleanup.kt:23-27` zostaje (celowo kasuje też wadliwe karty). |
| A3.5 | `data/local/prefs/UserPreferencesRepository.kt:99-114`, Dashboard i Browse | N | Licznik podpowiedzi gestu w 2 kopiach. | Jedna funkcja licznika i `rememberGestureHint`. |
| A3.7 | `ui/browse/BrowseScreen.kt:312`, `ui/stats/StatsScreen.kt:143`, `ui/components/SegmentedBar.kt:62` | Ś | Kategorie kart (podpis, liczba, kolor) są dobierane ręcznie w 3 miejscach. | `CardStats.countOf()` i `labelRes()`. Użyć istniejącego `categoryColor` z `ui/study/SessionProgressBar.kt:76`. |
| A3.8 | 4 miejsca z `take(activePageCount)` i 4 z `0 until activePageCount` | Ś | Dwie reguły stron („widoczne strony karty” i „zakres stron talii”) są powtórzone. | `Flashcard.activePages(n)` dla pierwszej grupy, `FlashcardGroup.activePageRange` dla drugiej. `DeckSettingsScreen` nie należy do żadnej z nich. |
| A3.9 | `ui/imports/ImportParsing.kt:25-70`, `ImportViewModel.kt:75`, `ImportScreen.kt:249` | Ś | Parser źródła importu siedzi w UI. Ekran i ViewModel osobno liczą „czemu nie można importować”. | `parseSource` przenieść do `domain/imports`. Ekran i ViewModel korzystają z jednego `blockingProblem`, w kolejności, jaką ma dziś ekran. Walidacja w domenie bez zmian. |
| A3.11 | `ui/imports/ImportPreview.kt:37,61`, `ui/decksettings/PageRow.kt:37` | N | Zastępcza „Strona N” dla pustej nazwy jest powtórzona 3 razy. | `pageDisplayName(name, i)`. Inna obsługa w edytorze fiszki: patrz D14. |
| A3.12 | `ui/study/AnswerPanel.kt:42-55`, `ui/study/FlashcardView.kt:213-226` | N | Para kolor i podpis dla „dobrze” i „źle” jest w 2 miejscach. Stany neutralne różnią się celowo. | Wspólny pomocnik tylko dla tej pary. |
| A3.14 | `ui/components/LanguagePicker.kt:5`, `domain/imports/LanguageDetector.kt:15` | N | Ta sama lista 6 języków w 2 miejscach, w różnej kolejności. | Jedna lista w `domain/model`. Kolejność wyświetlania i priorytet wykrywania zostają jak dziś. |
| A3.16 | `data/repository/DeckRepository.kt:133-170` | N | `setPageName` i `setPageLanguage` to kopie. | `withPageName`/`withPageLanguage` w `GroupOperations`, z zachowaniem `trim()`. |
| A3.17 | Drobne | N | `15_000` wpisane 4 razy w `AccountManager`. `HINT_DELAY_MS` 2 razy. Sprawdzanie sieci 2 razy. `0..100` zamiast `MAX_THRESHOLD_PERCENT`. Etykiety ocen powtórzone w `StepParamFields`. | Stałe i wspólne funkcje. `RatingColors` zostaje, bo wartości są czytane i testowane. |
| A3.18 | 4 Activity (Browse, DeckSettings, Study, ModeDetail) | N | Ten sam blok tworzenia ViewModelu. | Pomocnik `assistedViewModel`. Klucze `intent` zostają w każdej Activity. |
| ~~A3.3~~ | — | — | **Błędne.** Wspólne `launchSaving` już istnieje. „Kopie” to jednolinijkowe wiązania z własnymi zdarzeniami. | — |
| ~~A3.4~~ | — | — | **Celowe.** Trzy snackbary „Cofnij” różnią się świadomie, np. Browse zastępuje poprzedni „jak w Gmailu”. Wspólne `showAction` już istnieje. | — |
| ~~A3.6~~ | — | — | **Przesadzone.** `rememberModeDelete` i `ModeResetDialog` są już wspólne. Zostało tylko podpięcie. | — |
| ~~A3.13~~ | — | — | **Błędne.** Reguła jest w jednym miejscu (`sessionAudioIdle`). Dwa odbiorcy różnią się celowo. | — |
| ~~A3.15~~ | — | — | **Błędne.** Tabele słów się nie pokrywają: inne dopasowania i kody języków. | Zostaje. Ewentualnie tylko przeniesienie `detectPageCount`. |

### A4. Duże klasy i warstwy — tylko to, co warte kosztu

| ID | Gdzie | W | Co | Poprawka |
|---|---|---|---|---|
| A4.7 ✔ | `data/remote/auth/AuthRepository.kt:86-105`, `data/account/AccountManager.kt:7-8,161,170` | Ś | Typy Firebase wychodzą poza opakowanie. Kod łamie kontrakt §14.2: `linkWithGoogle` ma zwracać sukces albo kolizję, a dziś rzuca wyjątek. | Własne `AuthUser` i `LinkResult.Collision`. To przywraca kontrakt; trzeba przepisać mocki w `AccountManagerTest`. |
| A4.15 | `data/account/AccountManager.kt:50-53` | N | Publiczne `MutableStateFlow`. Kontrakt §14.2 mówi `StateFlow`. | Strumienie tylko do odczytu. Symbole UI zawęzić do `internal`. |
| A4.1 | `domain/session/SessionEngine.kt`, `StepExecutor.kt` | N | Silnik wystawia wykonawcy kroków 6 wewnętrznych metod. | Interfejs `StepHost` i wydzielony `ReviewLedger`. Gest przytrzymania i podmiana w tle zostają w silniku (ryzyko). |
| A4.3 | `playback/SttRecognizer.kt:51-137` | N | Jedna funkcja ma 87 linii. | Prywatna klasa `RecognitionRun`, wszystko na wątku głównym. |
| A4.5 | `data/repository/BackupRepository.kt:159-172` | N | Reguła przenoszenia trybów gościa siedzi w repozytorium. | Przenieść ją do `domain/merge` i dopisać test. Format id `guest-mode:…:…` zostaje co do znaku. Reszta klasy zostaje, bo `writeSeed` jest w kontrakcie §14.2. |
| A4.6 | `data/account/AccountManager.kt:159-215` | N | `signIn` ma 55 linii. | Prywatne funkcje. Kolejność kroków i jeden `try` zostają. |
| A4.8 | `ui/main/MainViewModel.kt:75-83` | N | ViewModel sam ustala, czy dane z serwera już doszły (`fromCache && empty`). | Ta reguła trafia do warstwy danych. `RejectedChangesMessage` zostaje, bo czytanie magazynu wprost przewiduje kontrakt §14.3. |
| A4.9 | `ui/imports/ImportViewModel.kt:400-444` | N | Zapis szkicu (plik, JSON, blokada) jest zaszyty w ViewModelu. | `ImportDraftStore`. Format szkicu i kolejność kroków bez zmian. |
| A4.10 | `ui/study/StudyCard.kt:234-247`, `:80-158` | N | Reguły „co pokazać” siedzą w composable. | Czyste funkcje `studyActionState()` i `answerPanelState()` z testami. |
| A4.11 | `ui/modedetail/ModeDetailScreen.kt:269-283` | N | Reguła nazwy trybu wbudowanego siedzi w composable. | `canonicalModeName()` obok `modeDisplayName`. |
| A4.14 | `data/firestore/SyncStatus.kt` | N | Enum stanu ekranu leży w warstwie danych, a `ERROR` znaczy w praktyce „offline”. | Przenieść do `ui/appsettings`, `ERROR` przemianować na `OFFLINE`. Nazwa `SyncModule` zostaje (plan §8.2). |
| ~~A4.2~~ | — | — | **Nie warto.** Fokus i trasa dźwięku są już wydzielone. A1.4 i A1.5 i tak zmniejszają klasę. | — |
| ~~A4.4~~ | — | — | **Łamie kontrakt.** §14.3: „publiczne API bez zmian”. Do tego wspólne blokady `metadata`/`reviews` chronią przed utratą edycji. | — |
| ~~A4.12~~ | — | — | **Błędne i ryzykowne.** Stałe mają wielu użytkowników, część to zapisane wartości domyślne i id. | — |
| ~~A4.13~~ | — | — | **Błędne.** `isDeleteConfirmed` to już czysta funkcja z testem. `LocaleManager` w ustawieniach to decyzja z `PLAN_PORZADKI.md` §3. | — |

### A5. Nazwy i komentarze

| ID | Gdzie | W | Co | Poprawka |
|---|---|---|---|---|
| A5.1 | `domain/session/SessionState.kt:6` | Ś | `REVEALED` znaczy „czekam na ocenę”. | `AWAITING_RATING`. Enum nie jest zapisywany, więc zmiana jest bezpieczna. |
| A5.2 | `domain/merge/MigrationRules.kt:9`, `domain/selectors/DueCards.kt:35,43` | N | `getGuestHasData(…)`. `getDueCards` zwraca też karty, które nie są do powtórki. | `StoreData.hasGuestData()`. `cardsForSession()` razem z `countDueCards`. |
| A5.3 | `ui/components/ExpressiveMotion.kt:29,34` | N | `expandIn()`/`shrinkOut()` zasłaniają funkcje Compose o innym działaniu. | `expandVerticallyIn()`/`shrinkVerticallyOut()`. |
| A5.4 | `ui/main/MainViewModel.kt:47`, `ui/browse/BrowseSearch.kt:26`, `ui/modedetail/ModeDetailViewModel.kt:145-232`, `ui/appsettings/AppSettingsViewModel.kt:60`, `ui/study/StudyViewModel.kt:150` | N | Nieprawdziwe komentarze: „wspólny dla każdego okna” i „kolejka zapisów”, której już nie ma. `onSearch` jest wołane przy każdej literze. Czasowniki w edytorze trybu są pomieszane. KDoc stoi przy złym zdarzeniu. | Poprawić komentarze i nazwy. |
| A5.5 | `ui/components/ModeListItem.kt` | N | Plik nie zawiera `ModeListItem`. | Przemianować na `ModeParts.kt`. Pozostałe przemianowania plików porzucam, bo proponowane nazwy były nietrafione. |
| A5.6 | `ui/browse/BrowseSearch.kt:31-33` | N | `LaunchedEffect` zapamiętuje pierwszą wersję `onSearch` na zawsze. | `rememberUpdatedState`. |

### A6. Testy

Istniejących testów zachowania nie usuwam ani nie osłabiam.

| ID | Gdzie | W | Co | Poprawka |
|---|---|---|---|---|
| A6.0 (nowe) | `ui/appsettings/AppSettingsSignInTest.kt:159` | Ś | `assertTrue(google.signedInTokens.isEmpty())` przechodzi zawsze, bo ViewModel tej metody nie woła. Martwe pola atrapy są w liniach 61-74, 83, 91 i 110. | Sprawdzić prawdziwe wywołanie (`accountManager.signInWithGoogle`). Usunąć martwe pola. |
| A6.1 ✔ | `data/repository/DeckRepositoryTest.kt:205-208`, `testing/InMemoryDocumentStore.kt:39-56` | Ś | Dwa zapisy pod różnymi blokadami biegną naprawdę równolegle, a atrapa nie ma blokady. Test może sporadycznie padać. | `documents.update {}` w atrapie. Asercje bez zmian. |
| A6.2 | `ui/imports/ImportViewModelTest.kt:105`, `ui/study/StudyRestartTest.kt:92`, `data/repository/DeckRepositoryTest.kt:150,340` | N | Czekanie na prawdziwy wątek. Przyczyną jest m.in. `Dispatchers.IO` wpisany na sztywno w `DocumentFiles.kt:32,43,74`. Do tego stare komentarze o Room. | Wstrzykiwany dispatcher w `DocumentFiles` i w `PreferencesRule`. |
| A6.3 | 12 plików | N | `setMain` powtórzone 12 razy, konstruktor `AccountManager` 7 razy. | `MainDispatcherRule` z parametrem i fabryka w teście. Atrapy fokusu audio zostają osobne (różnią się działaniem). |
| A6.4 | `playback/AudioTurnTest.kt` | N | Wykluczanie mowy i słuchania jest testowane naprawdę. Nieprzetestowane jest tylko podpięcie w adapterze. | Mały test `SessionDepsAdapter` (patrz A6.6). |
| A6.5 | Drobne | N | Komentarze o Room i SQLite. Czas systemowy w 6 testach FSRS. `assertTrue(x != null)`. `I18nConsistencyTest:25` z językami wpisanymi na sztywno. `LocalDatesTest:15-23` niczego nie sprawdza. | Uporządkować. |
| A6.6 | **Brakujące testy (po weryfikacji)** | Ś | Ścieżka „brak sieci” i blok `catch` w `AccountManager.signOut`. `StudyModeRepository.restoreMode`. Ok. 14 reguł `BackupCheck` (zakresy SRS, czasy, tryby wbudowane, strony). `TtsPlayer`: brak głosu, limit czasu. `SttRecognizer.listen`. `StepExecutor`: pauza dynamiczna, `SpeakAllPages`, limit nasłuchu. `StudyViewModel`: brak talii, brak kart, ponowienie po zgodzie na mikrofon. `AppSettingsViewModel`: wylogowanie, synchronizacja, import. `ImportViewModel.loadFile`. `DashboardViewModel.restoreDeck`. `Cleanup`: dokładna granica 30 dni i tryby. Podpięcie `SessionDepsAdapter`. | Nowe testy. `restoreCard` usuwam z listy, bo pokrywa go `BrowseScreenTest:98`. |

---

## B. Wymaga decyzji — zmienia zachowanie

| # | Gdzie | Co się dzieje dziś (sprawdzone) | Propozycja i po co |
|---|---|---|---|
| D1 ✔ | `data/account/AccountManager.kt:163-213` | Logowanie Google pokazuje „Zalogowano, ale nie udało się przygotować danych… Wyloguj się”, choć użytkownik dalej jest gościem i nie ma przycisku wyloguj. Dzieje się tak przy **każdym** błędzie przed przełączeniem konta: brak sieci, pełny dysk. Także gdy logowanie się nie uda i nie da się utworzyć gościa (`:201`). | W tych przypadkach pokazać „Logowanie nie powiodło się”. Po co: komunikat ma mówić prawdę. |
| D2 | `data/account/AccountManager.kt:83-105` | Uszkodzony `pending_join.json` daje ekran „potrzebny internet” przy każdym starcie, na zawsze. Uszkodzony `guest_stash.json`, albo dowolny błąd w połowie startu gościa: raz „potrzebny internet”, a po ponowieniu **gość bez żadnych talii**, bez przykładów. | Uszkodzony plik zalogować i odłożyć na bok. Przy ponowieniu dokończyć start gościa (przywrócić dane albo dodać przykłady). Po co: aplikacja nie zablokuje się ani nie zostawi pustego konta. |
| D4 | `data/account/AccountManager.kt:134` | Jedyny niezłapany wyjątek przy logowaniu to odczyt `backup.snapshot`. W praktyce prawie niemożliwy. Gdyby wystąpił, aplikacja się wywraca. | Złapać w tym jednym miejscu i pokazać „Logowanie nie powiodło się”. Po co: zero ryzyka crasha. |
| D5 | `ui/appsettings/AppSettingsViewModel.kt`, `SettingsSections.kt:53-69`, `AccountManager.kt:218` | „Offline” znaczy co innego dla przycisku Wyloguj (stan bazy) i co innego przy samym wylogowaniu (stan sieci). Przycisk bywa więc włączony, a wylogowanie odmawia, albo odwrotnie. „Synchronizuj” nie czeka na trwające logowanie. | Jedna definicja offline (ta z wylogowania) i jedna tabela „co blokuje co”. Po co: przycisk aktywny = działa. |
| D6 ✔ | `ui/dashboard/DashboardScreen.kt:126-134` | Obrót telefonu przy widocznym „Zarchiwizowano · Cofnij” pokazuje komunikat drugi raz. | Pokazać go raz. Koszt: obrót w trakcie gubi przycisk Cofnij, a talię da się przywrócić z Archiwum. |
| D7 | `ui/study/StudyScreen.kt:233,244` | Gdy telefon nie ma ekranu „zainstaluj głos” albo „ustawienia aplikacji”, przycisk nic nie robi. W ustawieniach to samo jest celowe, z komentarzem, więc zostaje. | Snackbar „Nie udało się otworzyć ustawień” w tych 2 miejscach nauki. |
| D8 ✔ | `ui/browse/BrowseViewModel.kt:142-146` | Błąd zapisu licznika podpowiedzi blokuje usunięcie fiszki gestem i pokazuje „błąd zapisu”. Pulpit robi to już osobno. | Najpierw usunięcie, licznik osobno, jak na pulpicie. |
| D9 ✔ | `ui/components/SwitchRow.kt` (7 miejsc: import, edytor trybu, 5 pól parametrów kroku) | Przełączniki w edytorze i imporcie wyglądają inaczej niż w ustawieniach. `PLAN_PORZADKI.md:134` wymagał tego samego wyglądu. | Ujednolicić. Pokażę zrzuty, także pól kroków. |
| D10 | `data/repository/Cleanup.kt:18-19` | Gdy zegar telefonu się spieszy, sprzątanie konta Google próbuje skasować coś, czego reguły jeszcze nie pozwalają. Odrzucona paczka **pokazuje użytkownikowi komunikat „odrzucone zmiany”**, choć sam nic nie robił. | Zapas 10 minut (tyle toleruje reguła `notFuture`) i brak komunikatu przy odrzuceniu sprzątania. |
| D11 | `data/remote/auth/AuthRepository.kt:55-64` | `signInWithIdToken` jest martwe: używa go tylko 1 test i atrapa. | Usunąć metodę i jej test. Z interfejsu zdjąć też `signOut`. Sama metoda wylogowania zostaje, bo aplikacja jej używa. |
| D12 | Pozostałości | 6 kopii roboczych agentów (563 MB, czyste). Jedna ma commit FSRS-6 (664f5983), już zastąpiony tym w `main`. 6 lokalnych gałęzi `worktree-agent-*`. Gałęzie `claude/frosty-hopper-32b4ee` i `przeglad-modernizacja` (0 niescalonych commitów). Folder **`RayColl-prototyp` z ok. 190 niezacommitowanymi zmianami** (pewnie prototyp przepisany już w c01d3128). Zdalna gałąź `origin/claude/prev-next-card-buttons-porhbm`. | Usunąć lokalne. Prototyp i gałąź zdalną tylko po twoim osobnym „tak”, bo usunięcie jest nieodwracalne. |
| D13 (z A2.11) | `ui/dashboard/DashboardViewModel.kt:100-106`, `ui/browse/BrowseViewModel.kt:137-139` | Zapisy licznika podpowiedzi gestu: rzadki błąd dysku wywraca aplikację. | Złapać i pominąć po cichu, z logiem. Po co: podpowiedź nie jest warta crasha. |
| D14 (nowe) | `ui/browse/CardEditorDialog.kt:88` | Po wyczyszczeniu nazwy strony edytor fiszki pokazuje puste pole bez etykiety. Wszędzie indziej widać „Strona N”. | Także tu „Strona N”. |
| ~~D3~~ | — | **Błędne.** Zapis „ostatnio uczona” do Firestore nie czeka na serwer i nie może wywrócić aplikacji. | — |

## C. Świadomie pomijam

- **`FlashcardGroup` → `Deck`.** Prawie każdy plik. Pole `groups` musi zostać w formacie kopii.
- **`CompoundStep.version`.** Usunięcie byłoby technicznie bezpieczne (`ignoreUnknownKeys`), ale plan każe pole zachować (`PLAN_SYNC_FIRESTORE.md:486`). Edycje go przenoszą, co sprawdza `StepCardTest`.
- **`SrsState.state` i `SessionStatus.ERROR`.** Kod ma komentarz „nie zmieniać”, a status `ERROR` jest używany.
- **Reguły ProGuard.** Ryzyko jest w release. Uwaga: komentarz w nagłówku pliku przeczy regułom pod nim.
- **Robolectric `sdk=34`.** To osobne zadanie.
- **Przenosiny pakietów.** Kilkadziesiąt importów przy małym zysku.
- **Przemianowania fokusu audio i `InteractionGate`.** Mały zysk.
- **Przyrostek „(z telefonu)” w `AccountManager.kt:114`.** Tekst z ekranu trafia do nazwy zapisanego trybu, ale to zatwierdzona decyzja produktowa.

## Skrót

- **Do poprawy od razu:** ok. 60 pozycji (było 85). Usunąłem pozycje celowe albo błędne, a podziały klas przyciąłem do tych wartych kosztu.
- **Do decyzji:** 13 pozycji, z czego 2 nowe (D13, D14). D3 wypada.
- **Najwięcej warte, w kolejności:**
  1. A1 i A2. Do tego włączenie reguły nieużywanych importów, żeby nie wracały.
  2. A6.0 i A6.1: test, który niczego nie sprawdza, i test, który może sporadycznie padać.
  3. A4.7 i A4.15: przywracają kontrakt §14.2.
  4. A3: duplikaty.
  5. Brakujące testy (A6.6).

---

## Wykonanie sekcji A (17.10.2026)

Zrobione pakiety A1–A6, każdy z pełną weryfikacją. Nic niezacommitowane.

### Wynik

- `ktlintFormat`, potem `ktlintCheck testDebugUnitTest lintDebug assembleRelease`: zielone.
- Testy: **581 zaliczonych**, 8 pominiętych (emulator Firestore), 0 błędów. Przed zmianami było 541.
- Lint: 0 zgłoszeń. Kompilator: 0 ostrzeżeń o przestarzałym API (były 2).
- Wygląd: 6 zrzutów Robolectric (PL/DE, jasny/ciemny, 360 dp i 840 dp; ustawienia, baner offline, pasek kategorii, nagłówki importu, panel odpowiedzi, przeglądanie, statystyki). Każdy jest **identyczny bajt w bajt** z wersją `c01d3128`. Tymczasowy test usunięty.

### Co inaczej niż w planie

- **A1.9:** `LastAnswerResult.percent` zostaje, bo usunięcie wymagałoby skasowania asercji testu. Usunięte jest tylko `threshold`.
- **A3.2:** w stałe zamienione są tylko pola `updatedAt`, `deletedAt` i `archivedAt`, plus `isLive` dla talii i trybów. Pozostałe nazwy pól zostają tekstem w `FirestoreSchema`.
- **A3.5:** wspólny licznik podpowiedzi w ustawieniach jest. Wspólny `rememberGestureHint` na ekranach pominięty, bo kopie mają po 3 linie.
- **A3.14:** pominięte. Listy języków różnią się kolejnością i rolą, więc wspólna lista nic nie daje.
- **A4.1:** wydzielona jest tylko księga ocen (`ReviewLedger`). Interfejs `StepHost` odpada: publiczny silnik nie może implementować interfejsu `internal`, a upublicznianie go nie jest warte zmiany.
- **A4.3:** pominięte. Rozpoznawania mowy nie da się przetestować na JVM, więc zmiana bez sieci bezpieczeństwa.
- **A4.15:** zawężona widoczność tylko w `AccountManager`. Symbole UI zostają publiczne.
- **A6.2:** wstrzykiwany dispatcher I/O w `DocumentFiles` (`@IoDispatcher`). Usunął 2 sporadyczne błędy testów importu i ekranu głównego, które wychodziły przy pełnym przebiegu. 42 wywołania `waitUntil` w testach ekranów zostają.
- Testy zmienione przy refaktorach (bez osłabiania): `AccountManagerTest` (atrapy bez klas Firebase), `MigrationRulesTest`, testy z `REVEALED` → `AWAITING_RATING`.

### Nowe testy (40)

`BackupCheckTest`, `StudyModeRepositoryTest`, `SessionDepsAdapterTest`, `StudyPresentationRulesTest`, nowy przypadek w `MigrationRulesTest`. Do tego nowe przypadki w testach:
- wylogowania,
- TTS (brak głosu, limit czasu),
- kroków sesji (pauza dynamiczna, czytanie wszystkich stron, limit nasłuchu),
- `StudyViewModel`,
- ustawień (wylogowanie, synchronizacja, import),
- `loadFile`,
- cofania archiwizacji,
- sprzątania trybów.

Agent wykonujący A6 sprawdził 11 nowych testów na celowo zepsutym kodzie: każdy padł zgodnie z oczekiwaniem.

### Dług techniczny po tej pracy

- Robolectric na Windows: `AtomicFile` nie nadpisuje istniejącego pliku. Test, który zapisze prywatny plik dwa razy, po cichu zgubi drugi zapis.
- Baner „Offline” w ustawieniach na 360 dp łamie tekst na wąską kolumnę, bo przycisk „Spróbuj ponownie” zajmuje pół szerokości. Tak było już przed zmianami. To sprawa wyglądu, do osobnej decyzji.
- Sekcja B (D1–D14) dalej czeka na decyzje.

## Test na telefonie (Pixel 10 Pro, Android 17, de-DE) i dwie poprawki

### Poprawka 1: mruganie przy zmianie motywu

**Aktualizacja z 17.10 — poniższe obejście jest wycofane.** Usunięto `configChanges="uiMode"`,
a także dodaną później nakładkę ze zrzutem ekranu i jej własną animację. Wersja release używa teraz
`UiModeManager.setApplicationNightMode` w jednym miejscu, w `RayCollApplication`. Android odtwarza
Activity, a Compose i paski systemowe odczytują tę samą konfigurację, zamiast przełączać motyw wcześniej
na podstawie samej preferencji. Skala animacji pozostaje ustawieniem systemowym.

Sprawdzone: 170 testów motywu, ustawień, nauki, silnika sesji i przywracania stanu, ktlint oraz lint.
Test odtworzenia Activity zachowuje ten sam ViewModel i silnik sesji, odkryte odpowiedzi oraz pojedynczy
zapis oceny. Release zainstalowano jako aktualizację na Pixelu 10 Pro bez usuwania danych. Na telefonie
sprawdzono jasny, ciemny, System oraz powrót do pulpitu. Natywne odtworzenie nadal daje krótki błysk;
nie zastępujemy go własnym efektem przenikania. Dalszy opis tej poprawki dokumentuje wcześniejszą próbę.

**Pomiar skal animacji (17.10, release, Pixel 10 Pro / Android 17):** przy wszystkich trzech skalach
systemowych ustawionych kolejno na 0×, 0,5× i 2× czarna przerwa trwała odpowiednio 58,8/58,8 ms,
53,8/64,6 ms i 50,9/65,1 ms (jasny → ciemny / ciemny → jasny; znaczniki czasu klatek nagrania).
Nie ma tu zależności od skali: obserwujemy odtworzenie okna, a nie osobną animację kolorów o regulowanym
czasie. Systemowe API zmiany motywu nie oznacza gotowego, płynnego przenikania całego interfejsu.
Po teście przywrócono wszystkie trzy skale 0,5× i zastany wybór motywu System. Nagrania i pomiary są
w `app/build/outputs/theme-scale-*.mp4` oraz `theme-scale-results.json`; kod aplikacji bez zmian.

Od commita `c01d3128` (każdy ekran jako osobna Activity) wybór motywu ustawia tryb nocny w systemie (`setApplicationNightMode`). Android uznawał to za zmianę konfiguracji i tworzył okno od nowa. Na nagraniu widać 90–100 ms czarnego ekranu.

Zwykła wersja na telefonie (z 16.10) jest sprzed tego commita, dlatego tam nie mrugało.

Naprawa: `android:configChanges="uiMode"` na wszystkich ekranach w manifeście. Compose przerysowuje kolory sam.

Sprawdzone nagraniami:
- 0 czarnych klatek;
- pulpit pod spodem przechodzi na nowy motyw po powrocie z ustawień.

Zostaje krótkie (ok. 0,1 s) przejście kolorów kafelków ustawień: to animacja komponentów Material.

### Poprawka 2: suwak szybkości mowy

Suwak był węższy od wiersza: wartość „1,2×” siedziała w końcowej kolumnie wiersza listy, a ta kolumna zabierała szerokość także suwakowi.

Wartość jest teraz w linii tytułu, a suwak ma pełną szerokość. Sprawdzone na telefonie.

### Język systemu bez tłumaczenia

Domyślne teksty aplikacji są po angielsku. Sprawdzone na telefonie z francuskim: aplikacja pokazuje angielski.

### Sprawdzone ekrany

Wszystko na wersji debug z wszystkimi zmianami, bez crashy:
- pulpit;
- przeglądanie: filtry kategorii z licznikami, wyszukiwanie, pusty wynik;
- statystyki;
- sesja nauki na „Gospodarka i Finanse” w trybie Odsłuch: czytanie, sterowanie przyciskami słuchawek trafia do RayColl, okno wyjścia, podsumowanie;
- import;
- menu trybów;
- ustawienia.

Podczas testów motyw w debugu raz ustawił się na „Jasny”, najpewniej przez mój dotyk w złym miejscu. Przywrócony „System”; po ponownym uruchomieniu aplikacji się nie zmienia.

### Kolory „przeskakujące” przy zmianie motywu: debug a zwykła wersja

Zwykła RayColl na telefonie to APK z przeglądu 16.10: skrót SHA-256 `8f8a295c…` zgadza się z `app/build/outputs/audit-20261016`, kod jak w `389ad656`.

W obu wersjach kafelki ustawień animują kolor (komponent Material). Porównanie nagrań:
- **Zwykła (16.10):** płynna animacja przez kilkanaście klatek w ok. 80 ms.
- **Obecny debug:** 4–5 skoków. Wersja debug nie jest optymalizowana i działa w trybie debugowania.
- **Obecny kod zbudowany jak release** (R8, bez trybu debugowania): płynnie, jak zwykła.
  - Przejście na ciemny bez różnic.
  - Przy przejściu na jasny jest dodatkowa pauza ok. 30 ms na starcie (4 klatki przy 120 Hz). To koszt zmiany trybu nocnego w systemie.

Sprawdzone też: przy motywie „System” przełączanie ciemnego trybu w Androidzie działa poprawnie w obu kierunkach.

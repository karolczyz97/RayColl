# Red team Codex: DAT (26 uwag)

Stan `39f0690b`, 15.10.2026. Ścieżki względem `app/src/main/java/com/raycoll/app/`. Dwa urządzenia jednego konta to wspierany scenariusz, nie skrajny przypadek. Nie zmieniono kodu.

### DAT-N1 — CONFIRMED — lokalna kolejność nie zbiega się między urządzeniami

`MergeEngine.mergeById` zachowuje lokalny początek, a `SyncDiff.diffSave` zapisuje zmiany indeksów. Wykonanie produkcyjnych funkcji dało `[a,b,c,p,t]` i `[a,b,c,t,p]`; kolejne scalenie zachowuje rozbieżność i generuje zapis.
Naprawa M, średnie ryzyko: dla dokumentów z pozycjami przyjmować kanoniczny porządek chmury `(position, docId)` i dopisywać nowe lokalne elementy; starsze dokumenty bez pozycji obsłużyć osobno, zachowując lokalną kolejność migracji. Nie używać `stalePositions.isEmpty()` jako informacji, że wszystkie dokumenty mają pozycje: zdublowane pozycje i luki też dają stalePositions, a właśnie przy równoległym dodaniu są normalne. Rozróżnić brak pozycji od potrzeby ich przepisania.
CROSS-LAYER DOM + TB. Test dwóch piszących urządzeń, ponowne sync w obie strony, duplikaty pozycji, stary format i no-op po osiągnięciu zgodności. Dotychczasowe R9 rozwiązało odczyt świeżego urządzenia, nie ten przypadek.

### DAT-N2 — DECISION — reset/przywrócenie mogą zostać cofnięte przez dane drugiego urządzenia

`LocalStore.replaceAll` twardo usuwa brakujące obiekty, a union przy merge nie interpretuje nieobecności jako usunięcia. Mechanizm odtworzony na produkcyjnym merge. Tekst „zastępuje wszystkie dane” nie ustala jednoznacznie, czy ma to objąć inne urządzenia.
Rekomendacja: zastępowanie danych konta ma obowiązywać wszędzie, z wyraźnym komunikatem. Po zatwierdzeniu M/L: zachować tombstony usuwanych talii/kart/trybów, a wczytywane obiekty o tych samych ID ustalić jako świadome przywrócenie z nową datą edycji. Samo dodanie tombstonów nie rozwiąże resetu aktywności: heatmapa scala się przez max, a dni nie mają tombstonów. Wbudowane tryby również mają specjalną regułę odżywania. Te ograniczenia trzeba rozwiązać lub jawnie ograniczyć obietnicę resetu; nie obiecywać „bez zmiany formatu” całego problemu.
CROSS-LAYER DOM + EKR + TB. Test resetu, importu starej kopii, ID obecnych po obu stronach i aktywności na drugim urządzeniu. Wysokie ryzyko danych; osobne zadanie po decyzji.

### DAT-N3 — CONFIRMED — timeout bootstrapu wygląda jak poprawna synchronizacja

`BootstrapLoader.kt:46` ogranicza load do 8 s. `reportingStatus` przy anulowaniu ustawia IDLE, a `BootstrapResult.LocalOnly` nie odróżnia gościa od awarii konta. Kolejnej próby w czasie pozostawania w aplikacji brak.
Naprawa M: osobny wynik bootstrapu dla niedostępnej chmury zalogowanego konta; jawny status błędu i ograniczone ponowienie. Zwykłe anulowanie właściciela nadal ma rzucać CancellationException. Nie ustawiać ERROR wszystkim LocalOnly, bo obejmuje też gościa. Test timeoutu i odzyskania sieci; średnie ryzyko. CROSS-LAYER EKR tylko komunikat statusu, jeśli potrzeba.

### DAT-N4 — CONFIRMED — powrót do aplikacji nie opróżnia oczekującej kolejki

`SyncScheduler.flush` zachowuje pending po niepowodzeniu. `SyncLifecycle.refreshIfCloudChanged` uruchamia wysyłkę tylko po merge, więc świeża chmura pozostawia niewysłane zmiany.
Naprawa S/M: na foreground ponawiać pending także przy świeżej bazie. Nie wkładać flush do `exclusive` (ten sam mutex), a retry nie powinno przepadać przez 30-sekundowy limit odczytu chmury. Test nieudany push → foreground bez zmiany chmury → udany push. Niskie/średnie ryzyko.

### DAT-N5 — PARTIAL — nieobsłużony wyjątek `await()` jest realny, diagnoza przestrzeni zbyt ogólna

`AppSettingsViewModel.chooseGoogleAccount` ma przy await tylko finally; `MainViewModel.resolveMigration` nie ma catch. Handler application scope nie obsługuje wyjątku przeniesionego przez await do ViewModelu. Crash jest realny. Nie jest prawdą, że każdy błąd bootstrapu pozostawia przestrzeń gościa: `BootstrapLoader` przełącza UID przed load.
Naprawa M: typowany wynik błędu logowania/bootstrapu oraz obsłużony wynik migracji, komunikat i możliwość ponowienia; zawsze przepuszczać anulowanie. Nie zwracać „porażki” z ogólnego `Deferred<T>` bez sprecyzowania typu. CROSS-LAYER EKR/NAU (main). Test wyjątku Room po uwierzytelnieniu i migracji. Średnie ryzyko spójności konta.

### DAT-N6 — PARTIAL — brak limitu na oczekiwanie za reconcile; wyjątki providera wymagają ostrożności

`scheduler.abort` anuluje wyłącznie push, a foreground reconcile może trzymać `exclusive` po upływie 5 s wylogowania. Odczyty talii są współbieżne, więc raport nie powinien sugerować N kolejnych timeoutów. Nie odtworzono dowolnego niespodziewanego wyjątku Credential Managera, ale cleanup nie powinien zależeć od powodzenia czyszczenia danych providera po udanym `FirebaseAuth.signOut`.
Naprawa M: kontrolowane anulowanie aktualnej operacji chmury przed końcowym wylogowaniem, zachować serializację namespace. Cleanup konta po potwierdzonym wylogowaniu Firebase, anulowania nie połykać. Nie dodawać finally, które przełączy na gościa także przy nieudanym wylogowaniu Firebase. Test z wiszącym reconcile i awarią czyszczenia credential state. CROSS-LAYER EKR dla stanu wylogowywania; średnie ryzyko.

### DAT-N7 — CONFIRMED — pusty save robi zbędny odczyt i może przesunąć znacznik bez odczytania danych

`SyncEngine.save:114–123` pobiera root i zastępuje baseline również dla pustego diffu. Jeśli drugi klient zapisze między freshness check a tym odczytem, znacznik może zostać uznany za znany mimo nieznanych dokumentów.
Naprawa S: przy pustych ops zwrócić sukces, zachowując dotychczasowy baseline/znacznik. R1 po rzeczywistym zapisie jest osobnym starym ograniczeniem; ta poprawka go nie rozwiązuje. Test produkcyjnej ścieżki save/no-op lub wyodrębnionej decyzji, nie samego fake'a. Średnie ryzyko synchronizacji, mały zakres.

### DAT-N8 — NOT_WORTH — sam fakt podwójnego cache nie dowodzi potrzeby zmiany polityki SDK

`FirebaseModule` korzysta z domyślnej persistence; odczyty aplikacji są SERVER. To nie powoduje odczytu danych poprzedniego konta przez aplikację. Brak pomiaru I/O i pamięci; zmiana na memory cache nie usuwa kolejki zapisów działającego procesu, jak ustalono w R12.
Nie wdrażać jako automatycznej optymalizacji. Ograniczenie backupu jest osobną zasadną sprawą AUD-N14/TB-N13. Jeśli pojawią się pomiary lub wymaganie usuwania lokalnych danych konta, wrócić do konkretnego kontraktu cache/clearPersistence.

### DAT-N9 — PARTIAL — wydzielić niezależne pomocniki, nie budować nowej warstwy dla długości pliku

Końcówka `SyncEngine.kt` zawiera niezależne typy pozycji i helpery operacji. Naprawa S/M: przenieść do `CloudPositions.kt` i `CloudOperations.kt`, zachowując widoczność internal. Nowy `FirestoreReader` nie jest konieczny do poprawności ani testowalności dwóch prywatnych funkcji; nie tworzyć dodatkowej klasy automatycznie. Istniejące testy pozycji/statusów/batchy i pełna kompilacja; niskie ryzyko przy czystym ruchu.

### DAT-N10 — PARTIAL — nazwy i kolejność tak, czyszczenie archiwów nie musi trafić do Application

`authRepository: CurrentUser` i `syncEngine: CloudSync` mogą mieć nazwy odpowiadające kontraktom; pola stanu można umieścić przed metodami. To drobne porządki S, sprawdzane kompilacją i testami sync.
Nie przenosić purge do samego jednorazowego `Application.onCreate`, bo dziś działa też po powrocie i po bootstrapie właściwego namespace. Dwa wywołania przy starcie można skonsolidować bez utraty tych momentów. CROSS-LAYER AUD tylko jeśli zmienia się wiring lifecycle.

### DAT-N11 — PARTIAL — guard archiwizacji/przywrócenia zasadny; pozostały scenariusz jest wąski

DAO rzeczywiście aktualizuje także tombstony. `restoreDeck/archiveDeck` podnoszą updatedAt i przy kolejnym merge mogą ożywić usuniętą talię; to nie wymaga nowej świadomej edycji treści. Dodanie `deletedAt IS NULL` jest tanie i spójne z innymi UPDATE.
Naprawa S: guard w archive/restore oraz idempotentny soft delete talii/karty/trybu. Nie zmieniać świadomej reguły merge „nowsza prawdziwa edycja może przywrócić”. Test DAO tombstone + te operacje, potem merge. Niskie ryzyko, CROSS-LAYER DOM tylko test integracji.

### DAT-N12 — CONFIRMED — zapis bez zmiany może wygrać z faktyczną edycją

`editPageMetadata` zapisuje wynik równy odczytanemu; `updateName` przyjmuje tę samą przyciętą nazwę. To zbędny timestamp i sygnał sync.
Naprawa S: guard porównujący zmieniane metadane (bez updatedAt, bo `withActivePageCount` sam je podnosi) i warunek nierównej nazwy w SQL. Analogiczne proste selektory można chronić przy okazji. Test brak zmiany timestamp/sygnału i rzeczywista zmiana. Niskie ryzyko.

### DAT-N13 — CONFIRMED — purge ma prostsze i atomowe rozwiązanie SQL

`purgeExpiredArchives` odczytuje i dekoduje całą listę, potem osobno kasuje znalezione ID. Zamiast uzasadniać wyłącznie mikroskopijnym wyścigiem, warto usunąć zbędny odczyt.
Naprawa S: punktowy UPDATE z namespace, `deletedAt IS NULL`, archivedAt i cutoff; sygnał tylko dla liczby >0. Test granicy 14 dni, przywróconej i już usuniętej talii. Niskie ryzyko, bez migracji schematu.

### DAT-N14 — CONFIRMED — walidator dopuszcza dane odrzucane po zapisaniu do chmury

`BackupValidator.validateGroup/validateStudyMode` sprawdza typ, `FirestoreSchema.requireString` także niepustość. `normalizeStoreData` nie naprawia nazw ani activeModeId.
Naprawa S: spójnie odrzucić blank wymaganych pól na granicy importu z komunikatem INVALID_BACKUP; nie zaostrzać dowolnie pól opcjonalnych ani istniejących poprawnych kopii. Test kopia → normalizacja → mapa Firestore → odczyt, w tym puste/spacje. CROSS-LAYER DOM + TB; niskie ryzyko. Nie pomijać tylko dlatego, że kopia może być ręcznie edytowana.

### DAT-N15 — DECISION — jawny przycisk Google powinien używać przepływu przycisku

Dokumentacja zaleca `GetSignInWithGoogleOption` dla jawnego przycisku; obecny `GetGoogleIdOption(filterByAuthorizedAccounts=false)` nie jest automatycznie niepoprawny, lecz otwiera inny przepływ. Rekomendacja: przejść na wariant przycisku; alternatywa zachować arkusz. Po decyzji test tokenu i telefonu z/bez konta. CROSS-LAYER EKR + TB; S/M.

### DAT-N16 — PARTIAL — komentarz konfiguracji wymaga korekty; migracja Firebase nie jest samym porządkiem

APP_ID jest webowy, a inicjalizacja ręczna. To nie dowodzi, że obecne Auth/Firestore nie działają. Tworzenie rejestracji Android, konfiguracja odcisków obu podpisów i wtyczka google-services wymagają rzeczywistej konfiguracji projektu.
Naprawa S teraz: KDoc opisujący aktualną ręczną inicjalizację i wymagania. Osobne zadanie konfiguracji, jeśli potrzebna pełna rejestracja Android; bez samowolnych działań w konsoli. CROSS-LAYER TB. Kompilacja, bez nowego testu komentarza.

### DAT-N17 — PARTIAL — komentarze poprawić, a nowa sesja wymaga sprawdzenia kolejki ocen

Usunięcie „web”, uzupełnienie dispatchera i poprawa gramatyki są zasadne S. `ReviewWriteQueue` obiecuje oczekiwanie przed nową sesją, ale `StudyViewModel.startSession` go nie wykonuje; `restartWith` wykonuje. Samo skrócenie komentarza nie usuwa ryzyka szybkiego wyjścia i ponownego wejścia przed zapisem.
Naprawa S/M: przed odczytaniem kart nowej sesji poczekać na kolejkę i obsłużyć false bez rozpoczęcia na starych danych; zachować możliwość ponowienia. CROSS-LAYER NAU + AUD. Test opóźnionego i nieudanego zapisu → nowy ekran nauki. Średnie ryzyko.

### DAT-N18 — CONFIRMED — parametr kart w dekoderze talii nie jest potrzebny produkcji

Jedyny produkcyjny caller przekazuje pustą listę, karty przypinane są po odczycie subkolekcji. Naprawa S: usunąć parametr, tworzyć pustą listę w dekoderze, dostosować testy przez copy. `FirestoreSchemaTest` i kompilacja. Niskie ryzyko, format bez zmian.

### DAT-N19 — PARTIAL — wspólna domyślna nazwa zamiast równoległego usuwania API

Duplikat DOM-N10 i EKR-N33. Testy repozytorium wywołują metodę bez lambdy, więc „martwy kod” dotyczy tylko wywołań produkcyjnych. Wykonać raz wspólny fallback; nie równocześnie wprowadzać obowiązkowego parametru i helpera. S, niskie ryzyko, istniejące testy repozytorium.

### DAT-N20 — CONFIRMED — dwa jednakowe odczyty talii z kartami można nazwać

`getDeck` i `swapPages` składają żywą talię oraz jej karty. Prywatne `loadDeck(ns,id)` jest dopuszczalnym małym uproszczeniem S. Zachować transakcję obejmującą swap i namespace pobrany raz; nie sugerować, że helper sam daje atomowość odczytowi poza transakcją. Testy repozytorium i ktlint; niskie ryzyko.

### DAT-N21 — CONFIRMED — wspólne pobranie dokumentów z serwera usuwa powtórzenia

Cztery razy ten sam łańcuch w loaderze. Prywatne `CollectionReference.serverDocs()` może zachować SERVER i `awaitCloud`. S, niskie ryzyko; kompilacja i istniejące testy timeoutów. Nie usuwać timeoutu w trakcie uproszczenia.

### DAT-N22 — CONFIRMED — `ensureWorker` powinno jawnie zwracać Unit

Wyrażenie `synchronized` łączy pusty return z `worker?.start()`, dając niepotrzebny wynik. Naprawa S: funkcja blokowa. Zachować LAZY i rejestrację workera przed startem. `ReviewWriteQueueTest`, bez testu samego typu prywatnej funkcji; niskie ryzyko.

### DAT-N23 — CONFIRMED — logi tracą wyjątek

`SyncEngine` oraz mapper logują sam message. Naprawa S: `Log.w(TAG, opis, e)` tam, gdzie przechwytuje się wyjątek; bez dodawania treści fiszek/tokenów. Kompilacja i ktlint; niskie ryzyko, test logowania niepotrzebny.

### DAT-N24 — NOT_WORTH — wrapper i lokalizacja factoryReset mają sens w obecnej warstwie

`BackupRepository` obsługuje eksport oraz zastąpienie danych przez przywrócenie/reset; to spójna odpowiedzialność. `LocalStore.seedData` dokumentuje język i zakres ID, jest używane przez reset. Przeniesienie obu rzeczy dla samego skrócenia nie daje istotnego zysku, a zaciera tę dokumentację. Zostawić.

### DAT-N25 — INTENTIONAL — snapshot całego konta to świadomy prosty model sync

Zgodnie z raportem nie stwierdzono odczuwalnego opóźnienia. Diff ogranicza zapis do zmienionych dokumentów; śledzenie dirty ID doda nowy stan wymagający odtwarzania. Zostawić do czasu pomiaru rzeczywistego problemu, bez tworzenia zadania naprawczego.

### DAT-N26 — PARTIAL — fake ma pozycje i sortowanie, ale pomija stalePositions oraz kod produkcyjnego transportu

`FakeCloudBackend.snapshot()` sortuje po `deckPositions/cardPositions`; teza „load nie liczy pozycji” jest zbyt szeroka. `FakeDeviceCloud.save` rzeczywiście wywołuje diff bez stalePositions, a sam transport SyncEngine nie jest wykonywany.
Naprawa M: dodać przypadki DAT-N1 i migracji brakujących pozycji, jawnie modelując stalePositions; osobny test save/no-op dla DAT-N7. Nie uznawać bardziej rozbudowanej atrapy za test produkcyjnego transportu. CROSS-LAYER TB + DOM; średnie ryzyko testowego fałszywego poczucia bezpieczeństwa.

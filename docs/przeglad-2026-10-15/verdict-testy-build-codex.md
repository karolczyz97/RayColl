# Red team Codex: TB (38 uwag)

Stan `39f0690b`, 15.10.2026. Ścieżki względem repo; dla testów skrót oznacza plik pod `app/src/test/java/com/raycoll/app/`. Aktualna ponowna weryfikacja: 533 testy, 0 failures/errors/skipped, ktlint zielony. Nie zmieniono konfiguracji ani kodu.

### TB-N1 — PARTIAL — cudzysłowy trzeba naprawić, lecz ogólny zakaz znaków `"` w zasobach byłby błędny

`dialog_delete_permanent_desc` ma surowe cudzysłowy wokół placeholdera we wszystkich locale; Android przetwarza takie cytowanie. Naprawa S: typograficzne cudzysłowy zgodne z locale lub poprawne escaping.
Nie dodawać ogólnego regexowego zakazu wszystkich nieucieczonych cudzysłowów: Android dopuszcza cytowanie całych wartości m.in. dla zachowania białych znaków. Sprawdzać konkretny tekst po skompilowaniu zasobów, ewentualnie precyzyjny przypadek tej wiadomości. Parytet i kontrola rzeczywistego dialogu; niskie ryzyko.

### TB-N2 — CONFIRMED — backup i reset wymagają testów rzeczywistego repozytorium

Są testy DocumentFiles i walidatora, ale nie round-trip BackupRepository ani atomowej podmiany. Naprawa testowa M: eksport/import tych samych danych, błędny JSON bez naruszenia obecnych danych, nieczytelny plik i reset. Dodać także zachowanie tombstonów/ukrytych stron. Osobno zachowanie przy błędzie bazy, nie tylko błędnym JSON.
Nie zmieniać kontraktu backupu przy okazji pisania testów. CROSS-LAYER DAT; niski poziom ryzyka produkcyjnego, duża wartość regresyjna.

### TB-N3 — CONFIRMED — brak scenariuszy MIGRATE/FRESH

Wyszukanie testów potwierdza brak właściwych ścieżek, istnieje CANCEL. Naprawa testowa M: bootstrap MigrationRequired, MIGRATE kopiuje i zachowuje gościa, FRESH tworzy stabilne ID konta, brak duplikatów po retry. Dodać błąd i dwuklik z EKR-N14/DAT-N5. CROSS-LAYER DAT + NAU/main; bez zmian formatu danych.

### TB-N4 — CONFIRMED — nazwy kroków w komunikatach są rozbieżne

Duplikat EKR-N7. Jedna wspólna naprawa strings + StepTexts, nie osobne podmiany literalnych nazw w dwóch pakietach. S, test placeholderów i powiązania etykiet, niskie ryzyko.

### TB-N5 — CONFIRMED — włoskie Masterizzata nie opisuje opanowanej fiszki

W `values-it` użyto słowa kojarzonego z nagraniem nośnika. Naprawa S: `Padroneggiata` spójnie z żeńską nazwą jednostki nauki. Test parytetu i oględziny szerokości; nie potrzeba testu jednostkowego pojedynczego słowa.

### TB-N6 — PARTIAL — komunikat NO_TOKEN jest nieprzydatny, ale wyjątek nie oznacza wyłącznie braku konta

`AuthRepository` mapuje NoCredentialException na NO_TOKEN. Oficjalna dokumentacja wymienia również konta wymagające ponownego uwierzytelnienia i wyłączone prompty Google. Propozycja „Nie znaleziono konta na urządzeniu” byłaby nową nieprawdą w części przypadków.
Naprawa S/M: osobny neutralny błąd „Nie udało się znaleźć dostępnego konta Google” z możliwymi krokami sprawdzenia konta/ponownego logowania; rozważyć przepływ jawnego przycisku z DAT-N15 po decyzji. CROSS-LAYER DAT/EKR; test mapowania, 5 locale i urządzenie bez dostępnych danych; niskie ryzyko.

### TB-N7 — CONFIRMED — debug bez klucza może zostać zbudowany, lecz nie uruchomi się poprawnie

BuildConfig dopuszcza pusty klucz, a `RayCollApplication` inicjalizuje Firebase przy starcie. Istniejący guard obejmuje tylko generateReleaseBuildConfig. To nie jest działający lokalny tryb debug bez Firebase.
Naprawa S: sprawdzać klucz przy pakowaniu instalowalnych APK debug/release; pozostawić możliwość kompilacji/testów/lintu bez klucza, jeżeli taki jest zamierzony workflow. Nie drukować klucza w błędzie. Test Gradle dla packaging bez klucza i testów bez klucza; CROSS-LAYER DAT/FirebaseConfig komentarz, niskie ryzyko buildu.

### TB-N8 — CONFIRMED — domyślny filtr i kolejność HARDEST nie mają bezpośrednich testów

Samo ustawienie NEW_AND_REVIEW w teście Dashboardu nie sprawdza jego granic. Naprawa S: nowe+zaległe, bez przyszłych i tombstonów; HARDEST wraz z remisami; countDueCards odpowiada getDueCards. Sprawdzić także filtry po czasie zegara. Czyste JUnit, bez Robolectric; niskie ryzyko.

### TB-N9 — CONFIRMED — scalanie metadanych i usunięć talii jest słabo przypięte testami

`MergeEngineTest` mocniej testuje karty niż grupy; ogólny mergeUserData nie przypina zwycięskiej nazwy. Naprawa S: nowsza nazwa, tombstone, świadoma późniejsza edycja, tylko jedna strona i brak utraty kart. Dołączyć DAT-N1 jako oddzielną właściwość kolejności. CROSS-LAYER DOM; niskie ryzyko.

### TB-N10 — PARTIAL — izolować preferencje testów; awarii zależnej od kolejności nie odtworzono

Test preferencji używa produkcyjnego delegata i pozostawia zmiany; StudyRestartTest jawnie resetuje wspólne ustawienie. To realna zależność testowej infrastruktury. Nie wykazano jednak, że każda kolejność klas musi dawać błąd — Robolectric może izolować sandboxy.
Naprawa S: istniejący internal konstruktor z DataStore wykorzystać w testach z TemporaryFolder i własnym scope. Wspólny mały helper, bez nowego mechanizmu produkcyjnego. Test wartości domyślnych po innym teście i zamknięcie scope; niskie ryzyko.

### TB-N11 — CONFIRMED — usługa i platformowy fokus nie są testowane bezpośrednio

Są testy SessionAudioFocus i SessionPlayer, lecz nie lifecycle samego StudySessionService/AudioFocusManager. Naprawa M: Robolectric/Hilt lub minimalnie wstrzykiwalny wiring do sprawdzenia attach/detach, kanału/powiadomienia, FINISHED i receivera słuchawek. Nie zastępować produkcyjnej usługi całkiem inną atrapą.
Robolectric nie dowodzi dźwięku, routingu Bluetooth i ograniczeń tła na Pixelu; te przypadki zostają do urządzenia. CROSS-LAYER AUD; średni koszt, niskie ryzyko produkcyjne.

### TB-N12 — PARTIAL — unsigned release jest opisany jako zamierzony; brakuje kroku podpisania do testu R8

README jawnie nazywa plik `app-release-unsigned.apk`; brak keystore w repo nie jest błędem. Raport trafnie zauważa, że taki plik nie wystarcza do opisanej instalacyjnej weryfikacji R8.
Naprawa S: udokumentować lokalne podpisanie właściwym istniejącym kluczem i jego SHA-1 albo zewnętrzną konfigurację podpisu. Nie przełączać automatycznie release na klucz debug i nie wymieniać podpisu już zainstalowanej aplikacji, bo utrudni aktualizację z zachowaniem danych. Jeśli konfiguracja ma wejść do Gradle, najpierw ustalić istniejącą tożsamość podpisu. Build + weryfikacja podpisu gotowego APK; średnie ryzyko instalacji.

### TB-N13 — PARTIAL — zakres backupu za szeroki; wzorzec firestore.* nie zadziała w XML

Duplikat AUD-N14. Używać konkretnych ścieżek lub allowlist bazy aplikacji; XML path nie jest globem. Bez telefonu nie potwierdzono nazw/wielkości plików i nie wykonano restore. S/M, test rzeczywistego odtworzenia i WAL; nie obiecywać usunięcia problemu 25 MB samą zmianą cache.

### TB-N14 — PARTIAL — prostszy język tak, zawężenie feedbacku do dźwięku byłoby nieprawdziwe

Propozycje „usunięcie”, „kreska pionowa” i jaśniejszy fallback mają sens. `HapticAndSoundPlayer.playSuccess/playError` uruchamiają również wibrację, więc `Play success sound` pomija część działania; „sygnał poprawnej odpowiedzi” jest bezpieczniejsze. „Kasuje odpowiedź” dotyczy wyniku rozpoznania, nie usuwania treści fiszki.
Naprawa S: poprawić niejasne teksty zachowując pełne znaczenie, nie każdy synonim z listy. CROSS-LAYER EKR-N8/NAU-N31; parytet zasobów i oględziny, niskie ryzyko.

### TB-N15 — PARTIAL — dotknięcie i wiersz można ujednolicić; karta/fiszka czeka na decyzję

W obrębie tego samego działania stosować „dotknięcie”, a dla wierszy CSV „wiersz”. Nie utożsamiać automatycznie „nieudanej próby” z „błędną odpowiedzią”: błąd techniczny mikrofonu i zła odpowiedź mają różne znaczenia.
Karta/fiszka = wspólna DECISION NAU-N31, nie samodzielne narzucenie „fiszka”. S, zasoby i kontrola kontekstu; niskie ryzyko.

### TB-N16 — PARTIAL — część ES/IT wymaga korekty, nie każdy synonim jest błędem

`Export/Import` jako urwane rzeczowniki i `Scadenza: %d` dla liczby powtórek są niejasne. Naprawa S: naturalne „Error al exportar/importar”, „Importazione...” i „Da ripassare”, spójna forma czasowników kroków. Ujednolicić termin nauki w obrębie tej samej roli.
Nie zmieniać mechanicznie wszystkich carta/scheda/flashcard ani passo/passaggio bez sprawdzenia kontekstu. Nie wykonano niezależnej recenzji native speakera całych 5 locale. Parytet i placeholdery nie sprawdzają naturalności; niskie ryzyko edycji, kontrola wizualna.

### TB-N17 — PARTIAL — poprawić zdania, nie zmieniać poprawnej typografii dla samej spójności

Niemieckie „Du hast vor dem Ende beendet” warto uzupełnić; włoski opis usunięcia może jawnie wskazać operację. BORRAR/Eliminar są synonimami; zgodność tokenu z przyciskiem to propozycja spójności, nie błąd porównania. Typograficzny apostrof ’ i prawidłowo escaped ASCII apostrof są legalne, nie trzeba ich ujednolicać jako naprawy.
Naprawa S: tylko realna niejasność zdań; jeśli zmieni się token, test isDeleteConfirmed i tłumaczenia. Niskie ryzyko.

### TB-N18 — PARTIAL — część asercji warto wzmocnić, ale raport pomija istniejące pokrycie

LocalDatesTest „handles timezone” nie przekracza dnia, lecz dalszy test w tym samym pliku sprawdza Los_Angeles=poprzedni dzień. Nie zgłaszać braku obsługi granicy; poprawić mylący komentarz o Auckland. Dashboard nie przesuwa zegara, repozytorium nie asertuje sygnału, test restartu karty sprawdza tylko indeks — te uwagi są zasadne.
`>=1 || FINISHED` nie jest tautologią (nie przejdzie dla zatrzymanej karty 0), lecz słabo sprawdza samo odsłonięcie. `events.first()` w testach z jednoelementowym enum może już jednoznacznie oznaczać awarię; sam brak dodatkowego assert nie dowodzi luki. „error” vs ostrzeżenie i lokalizacja SpeechMatcher są porządkami.
Naprawa S/M: konkretne asercje efektu restartu, przesunięcia czasu, sygnału i odsłonięcia; nie mnożyć istniejącego testu stref. Niskie ryzyko.

### TB-N19 — PARTIAL — lokalne powtórzenia tak, trzy nowe frameworki testowe nie są wymagane

Budownicze kart w różnych testach mają różne istotne domyślne wartości, a jawne setup/close bywa czytelne. W ModeDetailViewModelTest istniejący `editing(vm)` warto wykorzystać.
Naprawa S/M: usuwać identyczne powtórzenia i stosować istniejący VmTestHarness, współdzielić tylko zgodne fixture. JUnitRule/ViewModelTracker dopiero gdy zmniejszają rzeczywisty kod i zachowują anulowanie przed db.close. Nie przepisywać 13+21 plików w jednym mechanicznym zadaniu. Pełny zestaw testów; średnie ryzyko samej infrastruktury.

### TB-N20 — PARTIAL — teksty do znajdowania kontrolek można wiązać z zasobami, lecz nie wszystkie literały są błędem

Wiele testów szuka angielskich nazw. Dla testu zachowania kliknięcia zasób jest lepszy; dla testu treści tłumaczenia literal jest oczekiwanym kontraktem i ma pozostać.
Naprawa S/M: helper zasobów we właściwym kontekście Activity/locale, nie zawsze ApplicationProvider (ważne dla locale AppCompat na starym Androidzie). Tekst fiszek/dane testowe pozostają literalami. Nie zastępować testów treści asercją tego samego zasobu. Testy UI i 5 locale; niskie ryzyko.

### TB-N21 — PARTIAL — negatywne asercje po sleep są kruche; integracja z prawdziwym executorem nie zawsze ma być wirtualna

Wskazane sleep istnieją. Wspólny waitUntil ograniczy duplikat, ale nie da deterministyczności sam z siebie.
Naprawa S/M: dla negatywnej asercji poczekać na ukończenie konkretnego zadania/barierę; dla prawdziwego executora używać bounded wait/latch, a zegara testowego tam, gdzie faktycznie kontroluje scheduler. Deadline oparty na monotonicznym nanoTime. Nie wymieniać wszystkich sleep na advanceUntilIdle bez przejęcia dispatcherów. Pełny zestaw testów; niskie ryzyko.

### TB-N22 — PARTIAL — część testów się pokrywa, lecz nie wolno usuwać par testujących inne ścieżki

Duplikat listy języków można scalić; testy kategorii SRS częściowo się powtarzają, lecz granica reps 2/3 powinna zostać. Testy pustego importu i statystyk sprawdzić pod kątem tego samego bodźca.
Ważna korekta: `audio mode pacing...` testuje SEED_MODES, a `CompoundExpander expands ListenGrade...` testuje inną konfigurację/kod i inne długości gałęzi; to nie jest duplikat do skasowania. Testy SyncDiff obejmują odmienne mieszanki Put/Delete; podobieństwo asercji nie wystarcza.
Naprawa S: usunąć tylko rzeczywiście identyczne scenariusze po zestawieniu wejść, zachować granice i obie ścieżki audio. Pełne testy; niskie ryzyko przy ograniczonym zakresie.

### TB-N23 — PARTIAL — testy schematu należą bliżej danych; wspólne atrapy muszą zachować różnice

BackupValidatorTest importuje FirestoreSchema, więc te przypadki warto przenieść do istniejącego testu schematu. Scenariusze SyncLifecycle w AppSettingsSignInTest też można wydzielić przy rozbudowie migracji.
Nie łączyć automatycznie fake GoogleSignIn z licznikiem/błędem z prostym zawsze-udanym fake ani dwóch SessionControls bez porównania zachowania. Naprawa S/M: przenoszenie testów do właściwej odpowiedzialności i reuse identycznych atrap, bez ogólnego 11-argumentowego buildera maskującego zależności. Pełne testy; średnie ryzyko infrastruktury.

### TB-N24 — CONFIRMED — obliczenie pauzy w tle nie ma testów granic

BackgroundSubstitutionTest obejmuje kwalifikowanie trybu, nie calculateBgWaitMs. Naprawa S: puste strony, suma długości kilku stron, wartość pośrednia oraz min/max; nie potrzebuje Robolectric. Niskie ryzyko.

### TB-N25 — CONFIRMED — fallback uszkodzonego JSON ma znaczenie i wymaga regresji

Mapper łapie błąd i daje emptyList. Naprawa S: test mappera dla błędnego JSON i poprawnego round-trip; jeżeli Log wymaga Robolectric, użyć obecnego runnera dla tego testu. Nie trzeba uszkadzać całej bazy Room, żeby sprawdzić czysty mapping. Niskie ryzyko.

### TB-N26 — PARTIAL — testować mapowanie Credential Managera; nie wymusza to nowej funkcji dla każdej gałęzi

Brak testów produkcyjnego mapowania potwierdzony. Naprawa S/M: test cancel/no credential/configuration/token parsing/Firebase failure, z zachowaniem anulowania coroutine. Mała czysta funkcja ma sens dla rzeczywiście współdzielonego mapowania; nie wyciągać logowania tokenem wyłącznie dla pokrycia procentowego. Testy z TB-N6/DAT-N5, CROSS-LAYER DAT; niskie/średnie ryzyko.

### TB-N27 — PARTIAL — bezpośredni wpis zależności może być zbędny, same importy nie są rozstrzygające

Nie ma importów androidx.test.ext, lecz Compose UI testy korzystają z AndroidX testowej infrastruktury również pośrednio. Brak importu nie dowodzi, że cały artefakt znika z runtime po usunięciu wpisu.
Naprawa S: usunąć zbędną bezpośrednią deklarację/alias, jeśli dependencyInsight i pełny zestaw 533 testów potwierdzą, że wymagane zależności nadal przychodzą przez Compose. Nie deklarować zysku rozmiaru bez porównania grafu. Niskie ryzyko.

### TB-N28 — PARTIAL — zabezpieczyć lokalne sekrety i worktree, `.firebaserc` nie musi być ignorowane

Warto dodać *.jks/*.keystore/keystore.properties, firebase-debug.log* i .firebase oraz .claude/worktrees do współdzielonego gitignore. To drobny porządek S, nie twierdzenie, że klucze już wyciekły.
`.firebaserc` może poprawnie przechowywać niesekretne aliasy projektu; nie ignorować go automatycznie. Sprawdzenie git check-ignore, bez nowego testu jednostkowego. Nie usuwać istniejących plików lokalnych.

### TB-N29 — PARTIAL — README ma rzeczywiste nieścisłości, ograniczanie klucza wymaga poprawnej konfiguracji

proguard-rules zawiera reguły serializacji, a reszta może przychodzić z consumer rules zależności. Opis lokalizacji reguł wymaga poprawy; ktlint jest już dostępny przez Gradle; opis braku klucza i kroku podpisania także jest niepełny. Drzewo pakietów pomija files/studymodes.
Naprawa S: uaktualnić te instrukcje do faktycznego buildu. Wzmianka o restrykcjach klucza może linkować oficjalną konfigurację, lecz nie wdrażać samemu zmian w konsoli ani obiecywać działania Auth po dowolnym ograniczeniu. Weryfikacja poleceń lokalnych, bez testu komentarzy; niskie ryzyko.

### TB-N30 — PARTIAL — skrypt nie kontroluje kodów zakończenia, lecz część diagnozy configów jest błędna

`pack-repomix.ps1` kontynuuje po błędzie zewnętrznego npx. Sam `$ErrorActionPreference='Stop'` w Windows PowerShell 5.1 nie zamienia niezerowego exit code procesu w wyjątek — trzeba sprawdzić `$LASTEXITCODE`. Warto też przypiąć wersję przyjętego narzędzia.
`repomix.config.json` już zawiera `app/**/*.json`, zatem obejmuje schematy `app/schemas`; twierdzenie o ich braku we wszystkich configach jest błędne. Ignorowanie nieistniejących starych nazw nie szkodzi. W Androidzie Compose nadal istnieją zasoby themes/styles, więc sama wzmianka „style” nie jest błędem. Brak firestore.rules/firebase.json w paczce całości jest zasadny do poprawy.
Naprawa S: kontrola exit code po każdym npx i brak komunikatu sukcesu po porażce, dopisać pliki Firebase do właściwej paczki; kodowanie sprawdzać dla rzeczywiście używanej powłoki, nie zakładać 5.1. Self-check z atrapą npx zwracającą błąd; niskie ryzyko.

### TB-N31 — PARTIAL — stare worktree to zasoby robocze, nie martwy kod aplikacji

Git potwierdza 5 worktree; jeden ma lock Claude. Sama wielkość 495 MB nie jest usterką. Przed usuwaniem konieczne sprawdzenie statusu, włączenia commitów i braku aktywnego użytkownika; nie używać force na podstawie wieku.
Naprawa S: przenośny lock Gradle zamiast zaszytej ścieżki martwej sesji; nie usuwać blokady trwającego buildu po samym czasie. Pusty component to lokalny katalog, nie plik git. Cleanup worktree pozostawić jako osobną czynność po zakończeniu integracji, nie wykonywać w review. Test dwóch wywołań locka i zwolnienia po błędzie; niskie ryzyko skryptu, wysokie ryzyko nieostrożnego kasowania.

### TB-N32 — CONFIRMED — zbędne queries aktywności rozpoznawania

Duplikat AUD-N12. Jedna zmiana manifestu, zachować wpisy usług TTS i rozpoznawania. S, manifest/build/lint; niskie ryzyko.

### TB-N33 — DECISION — osobna nazwa debug ułatwi rozróżnianie dwóch instalacji

Obie odmiany rzeczywiście mają tę samą nazwę. To poprawne technicznie; etykieta z dopiskiem zmienia widoczny wygląd.
Rekomendacja: resource override `app_name=RayColl (debug)` dla debug, bez nowego manifest placeholdera i bez zmiany applicationId. Alternatywa obecna nazwa. S, kontrola label gotowych APK po zgodzie.

### TB-N34 — CONFIRMED — zadanie check nie obejmuje przyjętego lintu Kotlin

ktlintSources jest relatywne do app i nie obejmuje dwóch skryptów roota; check nie zależy od ktlintCheck. Naprawa S: dołączyć oba pliki jawnie i podpiąć ktlintCheck do check. Nie uruchamiać formatowania automatycznie w check. Sprawdzić graf zadań i faktyczne wywołanie, niskie ryzyko.

### TB-N35 — CONFIRMED — wrapper i końce linii można zabezpieczyć małym kosztem

Brak distributionSha 256 Sum i gitattributes. Naprawa S: checksum dokładnie dystrybucji 9.8.1-bin z oficjalnego źródła, reguły tekstowe LF i wyjątek BAT CRLF. Nie zmieniać wersji Gradle w tej samej poprawce, nie przepisywać plików binarnych. Test wrappera i git check-attr; niskie ryzyko.

### TB-N36 — CONFIRMED — reguła root jest redundantna przy rules_version 2

Oficjalna dokumentacja potwierdza, że `{document=**}` w wersji 2 obejmuje zero segmentów, więc drugi blok już obejmuje dokument users/uid. Oba warunki są identyczne. To drobne uproszczenie, nie luka bezpieczeństwa.
Naprawa S opcjonalna w pakiecie porządków: jeden blok; przed wdrożeniem test tabeli anonymous/own UID/other UID dla root i podkolekcji w emulatorze lub Playground. Nie wdrażano ani nie sprawdzano aktualnych reguł projektu. Niskie ryzyko przy zachowanej semantyce.

### TB-N37 — PARTIAL — komentarze ui 1/ui 2 można usunąć, identyczny format nie oznacza zakazu tłumaczenia

App name i czysto techniczny format mogą być translatable=false, lecz kolejność liczb/punktacja w postępie i opis kroku mogą wymagać przyszłej lokalizacji. Nie przenosić automatycznie wszystkich identycznych wartości i nie osłabiać parytetu kluczy tylko po to, by usunąć kilka linii.
Naprawa S: usunąć znaczniki po agentach (raz z EKR-N33). Ewentualne app_name nontranslatable osobno, po dostosowaniu parsera/testu parytetu. Niskie ryzyko.

### TB-N38 — PARTIAL — kilka drobnych porządków; globalne default values w testach byłyby pogorszeniem

assertTrue/False/Null zamiast assertEquals i usunięcie nieużywanego scope to drobne zasadne zmiany S. Kolejność pól i język nazw testów są konwencją, nie błędem. Nie zmieniać wszystkich nazw tylko dla jednego języka.
Nie ustawiać globalnie `unitTests.isReturnDefaultValues=true`, aby usunąć Robolectric z jednego testu Log: mogłoby ukryć przypadkowe wywołania niezamockowanych API Androida. SDK 34 jest świadomym środowiskiem testowym; target 37 sam w sobie nie wymaga przerzucenia wszystkich testów na 37. Dodać wybrane testy API 32/33 i nowszych obsługiwanych przez runner tam, gdzie zachowanie rzeczywiście się różni. Pełne testy i ktlint; niskie ryzyko ograniczonych porządków.

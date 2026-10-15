# Odstępstwa od planu porządków

## Etap 2 — konfiguracja testów Androida

- **Co:** zmieniono wersję SDK w dwóch plikach testów, a nie w trzech wymienionych w planie.
- **Dlaczego:** `SessionPermissionsTest` już miał `@Config(sdk = [33])`, więc nie wymagał zmiany.
- **Gdzie:** `PlatformAudioFocusTest.kt` i `StudySessionServiceTest.kt` — usunięto SDK 32;
  `SessionPermissionsTest.kt` pozostawiono bez zmian.

Nie zmienia to zakresu ani zachowania przewidzianego w planie.

## Firestore — granice etapów 1–3 i 5

- **Co:** etap 1 dodaje kontrakt magazynu, implementację Firestore i atrapę. Przełączenie
  repozytoriów, usunięcie Room i zależnego kodu synchronizacji następuje razem z kontem
  w etapie 2. Podstawowe operacje kopii potrzebne kontu są również w etapie 2; etap 3
  domyka ich walidację i testy. Etap 5 kończy pozostałe porządki.
- **Dlaczego:** obecne logowanie, kopie i synchronizacja współdzielą LocalStore oraz Room.
  Usunięcie samego Room w etapie 1 pozostawiłoby niekompilujący się projekt, zamiast
  wymaganego działającego commitu po każdym etapie.
- **Gdzie:** `data/firestore`, `data/account`, `data/repository`, DI i testowy harness.

## Firestore — zachowany test akcji komunikatu

- **Co:** test w `StudyPresentationTest` sprawdza teraz trwałą dostępność akcji banera offline,
  zamiast akcji usuniętego banera kolejki ocen; zachowuje sprawdzenie kliknięcia po minucie.
- **Dlaczego:** plan usuwa komponent `ReviewWriteError`, ale nie wymienia tego pliku testowego
  na liście usuwanych testów. Test został dostosowany, a nie usunięty.
- **Gdzie:** `ui/study/StudyPresentationTest.kt`.

## Firestore — dane startowe i kontrola budowania

- **Co:** wspólne `writeSeed` ma opcjonalny argument `reset`, żeby reset używał tej samej
  funkcji co nowe konta, ale oznaczał stare dane jako usunięte i stemplował daty bieżącym czasem.
  Nieruszone przykłady nowego gościa zachowują daty 0, których wymaga istniejące rozpoznawanie
  własnych danych gościa; reguły z 14.5 przyjmują takie dane startowe.
- **Gdzie:** `BackupRepository.writeSeed`, `MigrationRules.isUntouchedSeed`.
- **Co i dlaczego:** rozszerzono istniejącą zależność lint od kompilacji Hilt również na
  źródła testów. Rzeczywisty wspólny przebieg `test lint` ujawnił błąd narzędzi: lint otwierał
  plik `StudySessionServiceTest_TestComponentDataSupplier.java` w czasie jego ponownego
  generowania. Żadna kontrola nie została wyłączona.
- **Gdzie:** `app/build.gradle.kts`, zależności `lintAnalyze…UnitTest`.

## Firestore — test nieznanej wartości enum

- **Co:** zachowano przypadek `flip_reveal` w `CompoundExpanderTest`, dodając oczekiwanie
  odrzucenia nieznanego `revealStyle`. Dotychczasowe asercje rozwinięcia do trzech kroków
  i oczekiwania na dotknięcie zostały zachowane dla wariantu domyślnego.
- **Dlaczego:** §9.3 każe zachować testy sesji, ale ten przypadek sprawdzał usuwaną cichą
  zamianę nieznanych enumów na wartości domyślne. Kontrakt z §14 i opis z §8.5 wymagają
  zwykłej serializacji oraz odrzucania nieznanych wartości; test nie został usunięty.
- **Gdzie:** `domain/steps/CompoundExpanderTest.kt`.

## Etap 3 — opcjonalna adnotacja Commons CSV

- **Co:** dodano jedną regułę R8: `-dontwarn edu.umd.cs.findbugs.annotations.SuppressFBWarnings`.
- **Dlaczego:** Commons CSV 1.14.1 zawiera odwołanie do opcjonalnej adnotacji SpotBugs,
  której nie dostarcza jako zależności. Testy przechodziły, ale R8 blokował paczkę release.
  Adnotacja służy analizie kodu biblioteki i nie jest potrzebna aplikacji. Zastosowano dokładną
  regułę wygenerowaną przez R8, bez dodawania kolejnej zależności i bez wyłączania innych ostrzeżeń.
- **Gdzie:** `app/proguard-rules.pro`; biblioteka w `gradle/libs.versions.toml`.
- Problem opisany przez Apache: [CSV-320](https://issues.apache.org/jira/browse/CSV-320).

## Firestore etap 6 — scenariusze na emulatorze

- **Co:** scenariusze z §9.4 działają pod Robolectric na prawdziwym Firestore SDK i emulatorze
  (plan awaryjny z `androidTest` nie był potrzebny). Uruchamia je `npm run test:android`
  w `tools/firestore-tests`: `firebase emulators:exec` wokół
  `gradlew :app:testDebugUnitTest --tests "*Emulator*"`, przez mały skrypt Node
  (`run-android-tests.mjs`), bo na Windows `emulators:exec` nie uruchamia `gradlew.bat`
  bezpośrednio. Bez emulatora zwykłe `test` pomija te scenariusze (`Assume`), jak w planie.
- **Co:** każde „urządzenie” w teście ma własną pamięć Firestore w RAM z czyszczeniem LRU,
  a nie trwałą pamięć na dysku jak aplikacja. Domyślne czyszczenie pamięci w RAM usuwa
  potwierdzone dokumenty bez nasłuchu, więc odczyty z pamięci telefonu (`cachedDoc`) zwracałyby
  pustkę, czego w aplikacji nie ma. LRU z dużym limitem zachowuje się jak pamięć aplikacji.
- **Co:** test nie usuwa obiektów `FirebaseApp` po scenariuszu (tylko wylogowuje i zamyka
  Firestore). Firebase Auth trzyma statyczne odwołanie do pierwszej aplikacji, więc jej
  usunięcie psuło kolejne scenariusze w tym samym procesie testów.
- **Co (narzędzie spoza planu):** `stop-stale-emulators.mjs` uruchamiany przed i po testach
  npm. Na Windows emulator Firestore przeżywa zakończenie `emulators:exec` i blokuje port 8089
  przy następnym uruchomieniu. Skrypt zatrzymuje wyłącznie procesy emulatora Firebase
  nasłuchujące na portach 8089 i 9099.
- **Poprawka w aplikacji wykryta przez scenariusze:** Firebase Auth zgłasza Firestore zmianę
  użytkownika przez wątek główny, a konto zmienia się w tle (`Dispatchers.IO`). Zapis wysłany
  zaraz po zmianie konta (talie przykładowe nowego gościa lub nowego konta Google, dane
  przenoszone z gościa, dane odłożone przez „Zostaw”) mógł trafić do kolejki poprzedniego
  użytkownika i nigdy nie dotrzeć na nowe konto. Scenariusz logowania padał przez to w 3 z 4
  przebiegów. `AuthRepository.signInAnonymously` i `signInWithCredential` czekają teraz na
  wątek główny, zanim zwrócą wynik; po poprawce 4 pełne przebiegi (32 scenariusze) zielone.
  Plan tego nie przewidywał; publiczne API bez zmian.
- **Co:** scenariusz logowania sprawdza dodatkowo zawartość konta Google po każdej opcji
  (używane konto bez zmian, nowe konto z taliami przykładowymi), czego wcześniejszy szkic nie
  sprawdzał. Przy przekroczeniu czasu test podaje ostatni osiągnięty krok.
- **Testy reguł** w Node powstały już w etapie 4 (razem z regułami, zgodnie z §11 krok 4)
  i pokrywają wszystkie przypadki z §9.4. W etapie 6 zostały ponownie uruchomione: 19/19.
- **Gdzie:** `app/src/test/.../data/firestore/FirestoreEmulatorTest.kt`, `tools/firestore-tests/`,
  `data/remote/auth/AuthRepository.kt`.

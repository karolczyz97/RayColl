# RayColl

Natywna aplikacja Android do nauki fiszkami z powtórkami rozłożonymi w czasie
(FSRS), napisana w Kotlin/Compose. Talie z fiszkami
wielostronicowymi, w pełni konfigurowalne **tryby nauki** (sekwencje kroków:
pokaż/wypowiedz stronę, słuchaj-i-sprawdź STT, oceny, auto-rating, pauzy),
nauka w tle przez Media3 (odtwarzanie bez patrzenia w ekran), import CSV
z auto-detekcją separatora/nagłówka/języka, statystyki (streak, heatmapa),
backup JSON oraz synchronizacja w chmurze (Firestore + logowanie Google).

## Stack

| Warstwa | Technologia |
|---|---|
| Język | Kotlin 2.4.21 |
| UI | Jetpack Compose (BOM 2026.09.00), single-activity, Material 3 |
| DI | Hilt 2.60.1 |
| Baza lokalna | Room 2.8.5 (źródło prawdy) |
| Preferencje | DataStore |
| Serializacja | kotlinx.serialization 1.11.0 (JSON) |
| Async | kotlinx.coroutines 1.11.0 (StateFlow) |
| Audio/tło | Media3 1.11.1 (MediaSessionService), Android TTS + SpeechRecognizer |
| Chmura | Firebase (BOM 35.0.0): Firestore + Auth, Credential Manager (Google Sign-In) |
| Build | AGP 9.4.1, Gradle 9.8.1, `compileSdk`/`targetSdk` 37, `minSdk` 26 |

- `applicationId`: `com.raycoll.app` (release), `com.raycoll.app.debug` (debug, na telefonie jako „RayColl (debug)”)
- `versionName`: 1.0.0

## Wymagania

- JDK 17 (sprawdzony pełny `check` oraz release z R8 i profilami ART)
- Android SDK w `%LOCALAPPDATA%\Android\Sdk`
- Wrapper Gradle w repo (`./gradlew.bat`)

## Budowanie

```bat
:: Debug APK (instalowalny, do smoke-testów)
./gradlew.bat :app:assembleDebug
:: → app/build/outputs/apk/debug/app-debug.apk

:: Release APK (R8/proguard)
./gradlew.bat :app:assembleRelease
:: → app/build/outputs/apk/release/app-release.apk (z podpisem) albo app-release-unsigned.apk (bez)
```

Oba APK wymagają klucza Firebase (sekcja niżej); testy i lint budują się bez niego.

Release buduje się z minifikacją R8. W [`app/proguard-rules.pro`](app/proguard-rules.pro)
są tylko reguły dla serializerów kotlinx.serialization klas aplikacji; Room, Media3,
Firebase, Hilt i Credential Manager dostarczają własne reguły konsumenckie.

### Podpis release

Klucz podpisu nie jest w repozytorium. Żeby zainstalować release na telefonie,
dopisz do `local.properties` (ścieżka względem katalogu repo albo bezwzględna):

```
release.storeFile=C:/Users/<ty>/klucze/raycoll-release.jks
release.storePassword=...
release.keyAlias=raycoll
release.keyPassword=...
```

Bez tych wpisów release zostaje niepodpisany. Raz wybranego klucza nie zmieniaj —
Android nie zaktualizuje aplikacji podpisanej innym kluczem bez odinstalowania
(a więc bez utraty danych lokalnych). SHA-1 nowego klucza
(`keytool -list -v -keystore <plik>`) trzeba dodać w Firebase Console do
aplikacji `com.raycoll.app` (sekcja „wymagany SHA-1”).

## Testy

```bat
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:ktlintCheck
./gradlew.bat :app:lint
:: wszystko naraz (testy, ktlint, lint)
./gradlew.bat :app:check
```

Testy jednostkowe (JUnit4; część na Robolectric z in-memory Room). Pokrywają
silnik FSRS, dopasowanie mowy, normalizację/merge danych, operacje domenowe,
sesję nauki, import, DAO oraz ViewModele (Dashboard, Browse, DeckSettings,
Import, Stats, Archive, ModeDetail) oraz polecenia odtwarzacza Media3. Czas jest
wstrzykiwany przez `Clock`, więc testy
FSRS/streak/archiwum sterują czasem deterministycznie.

Formatowanie Kotlin i skryptów Gradle: ktlint 1.8.0 z ustawieniami w `.editorconfig`,
uruchamiany przez Gradle (`:app:ktlintCheck` sprawdza, `:app:ktlintFormat` poprawia).
Obejmuje kod aplikacji, testy i wszystkie skrypty `*.gradle.kts`.

### Ręczna weryfikacja serializacji w release

Polimorficzna serializacja `ModeStep` (sealed interface + `@SerialName`)
i `CompoundParams` jest weryfikowana round-tripem w teście jednostkowym na
buildzie debug. R8 na release nie stripuje generowanych `$$serializer`
(reguła `-keep ...**$$serializer`), ale po zmianach w regułach proguard warto
potwierdzić ręcznie na buildzie release: utwórz własny tryb nauki z krokami
złożonymi (compound), zamknij i otwórz aplikację — jeśli kroki wczytują się
poprawnie z Room, serializacja przeżywa minifikację.

## Firebase — klucz API

Klucz Firebase API nie jest w repozytorium. Przed pierwszym buildem dopisz go
do `local.properties` (plik jest ignorowany przez git):

```
firebase.apiKey=AIza...
```

Klucz znajdziesz w [Google Cloud Console → Credentials](https://console.cloud.google.com/apis/credentials?project=raycoll-io).
W CI ustaw zamiast tego zmienną środowiskową `FIREBASE_API_KEY` (np. z GitHub Secrets).
Bez klucza budowanie APK (debug i release) kończy się czytelnym błędem — aplikacja
bez klucza i tak nie uruchomiłaby się poprawnie. Klucz można dodatkowo ograniczyć
w Google Cloud Console do aplikacji Android (nazwa pakietu + SHA-1), według
[dokumentacji Firebase](https://firebase.google.com/docs/projects/api-keys);
po takiej zmianie sprawdź logowanie i synchronizację na obu wariantach.

## Firebase — wymagany SHA-1

Logowanie Google i sync Firestore działają dopiero po zarejestrowaniu aplikacji
w Firebase Console (projekt **raycoll-io**) z odciskiem SHA-1 debug keystore.
Dodaj **obie** aplikacje Android: `com.raycoll.app` oraz `com.raycoll.app.debug`,
każdą z SHA-1:

```
35:80:D2:75:83:68:BB:CB:8C:3F:CD:B5:CF:01:BC:24:3B:F8:23:7C
```

Release podpisany własnym kluczem (sekcja „Podpis release”) potrzebuje dodatkowo SHA-1
tego klucza przy `com.raycoll.app`.

Bez tego logowanie Google rzuci `DEVELOPER_ERROR`. **Reszta aplikacji działa
lokalnie** (Room jest źródłem prawdy) — chmura jest warstwą synchronizacji, nie
warunkiem uruchomienia.

### Reguły dostępu Firestore — wdrożenie przez właściciela

[`firestore.rules`](firestore.rules) pozwala zalogowanemu użytkownikowi czytać
i zapisywać wyłącznie `users/{jego uid}` i dokumenty poniżej. Pozostałe ścieżki
są zabronione. Plik w repo nie zmienia reguł działających w projekcie Firebase.

1. Otwórz [reguły projektu raycoll-io](https://console.firebase.google.com/project/raycoll-io/firestore/rules).
   Skopiuj obecne reguły i zapisz je z datą w opisie zmiany przed wdrożeniem.
2. Zainstaluj [Firebase CLI](https://firebase.google.com/docs/cli), jeśli go nie masz.
3. W katalogu repo zaloguj konto właściciela i sprawdź projekt:

   ```powershell
   firebase login
   firebase projects:list
   firebase use raycoll-io
   ```

4. W konsoli, w symulatorze reguł (Rules Playground), wklej treść `firestore.rules`
   do edytora i sprawdź poniższą tabelę przed opublikowaniem. UID A i B zastąp
   dwoma różnymi UID z Firebase Authentication. Testy symulują żądania klienta,
   a nie uprawnienia administracyjne konsoli.

   | Tożsamość | Ścieżka | Odczyt / zapis |
   |---|---|---|
   | bez logowania | `users/A` oraz `users/A/decks/x/cards/y` | zabronione |
   | UID A | `users/B` oraz `users/B/decks/x/cards/y` | zabronione |
   | UID A | `users/A` | dozwolone |
   | UID A | `users/A/decks/x/cards/y` | dozwolone |
   | UID A | `users/A/studyModes/m` oraz `users/A/activity/2026-10-14` | dozwolone |
   | UID A | `other/document` | zabronione |

5. Wdróż wyłącznie reguły do wskazanego projektu:

   ```powershell
   firebase deploy --only firestore:rules --project raycoll-io
   ```

6. Sprawdź logowanie, edycję i synchronizację własnej talii oraz jej odczyt
   na drugim urządzeniu. Zapisz wynik i datę w opisie zmiany.

Opis składni i wdrożenia: [dokumentacja Firebase](https://firebase.google.com/docs/firestore/security/get-started).
Agent przygotowuje pliki i instrukcję; wdrożenie i weryfikacja aktywnych reguł
pozostają po stronie właściciela.

## Struktura pakietów (skrót)

```
com/raycoll/app/
├── RayCollApplication.kt      # @HiltAndroidApp
├── MainActivity.kt            # single-activity, edge-to-edge, splash
├── di/                        # moduły Hilt (baza, Firebase, zegar, dispatchery)
├── core/                      # Ids, LocalDates, Clock
├── domain/                    # CZYSTY Kotlin — zero importów Androida
│   ├── model/                 # Deck, Card, StudyMode, ModeStep (sealed), enums
│   ├── srs/                   # SrsEngine (FSRS), SrsCardCategory, SpeechMatcher
│   ├── normalize/  merge/     # DataNormalizer, MergeEngine, MigrationRules
│   ├── operations/            # czyste funkcje-akcje (stan → stan)
│   ├── selectors/             # DueCards, Streak, BrowseFilter, ...
│   ├── steps/                 # CompoundExpander, ModeTemplate, ModeStepValidator
│   ├── session/               # SessionEngine, StepExecutor, reducer
│   ├── imports/               # CsvParser, Separator/Header/LanguageDetector
│   └── seed/                  # SeedData (2 talie), SeedModes (2 tryby)
├── data/
│   ├── files/                 # pliki z systemowego okna wyboru (import CSV, kopie JSON)
│   ├── local/                 # Room DB (encje z namespace), DAO, mappery, prefs
│   ├── remote/                # AuthRepository, Firestore (schema, dataSource)
│   ├── repository/            # Deck/StudyMode/Backup, LocalStore, NamespaceManager
│   └── sync/                  # SyncEngine, CloudOperations, BootstrapLoader, MigrationController, SyncLifecycle
├── playback/                  # TTS, STT, Media3 service, notyfikacja, audio focus
└── ui/                        # theme, navigation, components + ekrany:
                               # main/ dashboard/ browse/ study/ decksettings/ modedetail/
                               # studymodes/ imports/ stats/ archive/ appsettings/
```

Reguły architektury: Room = źródło prawdy (ekrany obserwują Flow → StateFlow);
akcje domenowe to czyste funkcje wołane wewnątrz transakcji Room; `SessionEngine`
żyje w ViewModelu sesji, a holder udostępnia go serwisowi; dzięki temu sesja
przeżywa obrót ekranu. Czas jest wstrzykiwany przez `Clock`.


## Dodatkowe testy infrastruktury

```powershell
# lokalny emulator, wyłącznie projekt demo-raycoll (Node + Java 21 lub nowsza)
npm ci --prefix tools/firestore-tests
npm test --prefix tools/firestore-tests

# blokada między procesami/checkoutami (Python 3)
python -m unittest discover -s tools/tests -v
python tools/gradle_locked.py :app:check
```

Pakiet Node służy wyłącznie testom reguł, nie jest częścią APK. Testy obejmują
odczyt/zapis roota i podkolekcji przez anonimowego klienta, właściciela i inne UID.
Konfiguracja testowa: `firebase.test.json`. Polecenia niczego nie wdrażają.

Synchronizacja przechowuje generację zastąpienia w bazie Room i dokumentach Firestore.
Import kopii i reset tworzą nową generację całego konta; synchronizujące urządzenia
muszą używać wersji obsługującej ten kontrakt. Room v1→v2 zachowuje dane;
nie instaluj ponownie starszej aplikacji z bazą v1 jako sposobu wycofania aktualizacji.
Przed testem aktualizacji zrób synchronizację i kopię JSON.

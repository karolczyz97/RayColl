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

- `applicationId`: `com.raycoll.app` (release), `com.raycoll.app.debug` (debug)
- `versionName`: 1.0.0

## Wymagania

- JDK 23
- Android SDK w `%LOCALAPPDATA%\Android\Sdk`
- Wrapper Gradle w repo (`./gradlew.bat`)

## Budowanie

```bat
:: Debug APK (instalowalny, do smoke-testów)
./gradlew.bat :app:assembleDebug
:: → app/build/outputs/apk/debug/app-debug.apk

:: Release APK (R8/proguard, unsigned — brak keystore w repo)
./gradlew.bat :app:assembleRelease
:: → app/build/outputs/apk/release/app-release-unsigned.apk
```

Release buduje się z minifikacją R8. Reguły `keep` (Room, kotlinx.serialization,
Media3, Firebase, Hilt) są w [`app/proguard-rules.pro`](app/proguard-rules.pro).

## Testy

```bat
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:lint
```

Testy jednostkowe (JUnit4; część na Robolectric z in-memory Room). Pokrywają
silnik FSRS, dopasowanie mowy, normalizację/merge danych, operacje domenowe,
sesję nauki, import, DAO oraz ViewModele (Dashboard, Browse, DeckSettings,
Import, Stats, Archive, ModeDetail) oraz polecenia odtwarzacza Media3. Czas jest
wstrzykiwany przez `Clock`, więc testy
FSRS/streak/archiwum sterują czasem deterministycznie.

Formatowanie Kotlin i skryptów Gradle: ktlint 1.8.0 z ustawieniami w `.editorconfig`.
Po pobraniu oficjalnego wykonywalnego JAR ktlint można uruchomić:

```powershell
java -jar ktlint.jar -F 'app/src/**/*.kt' '*.gradle.kts' 'app/*.gradle.kts'
java -jar ktlint.jar 'app/src/**/*.kt' '*.gradle.kts' 'app/*.gradle.kts'
```

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
Bez klucza build kończy się czytelnym błędem.

## Firebase — wymagany SHA-1

Logowanie Google i sync Firestore działają dopiero po zarejestrowaniu aplikacji
w Firebase Console (projekt **raycoll-io**) z odciskiem SHA-1 debug keystore.
Dodaj **obie** aplikacje Android: `com.raycoll.app` oraz `com.raycoll.app.debug`,
każdą z SHA-1:

```
35:80:D2:75:83:68:BB:CB:8C:3F:CD:B5:CF:01:BC:24:3B:F8:23:7C
```

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
│   ├── local/                 # Room DB (encje z namespace), DAO, mappery, prefs
│   ├── remote/                # AuthRepository, Firestore (schema, dataSource)
│   ├── repository/            # Deck/StudyMode/Backup, LocalStore, NamespaceManager
│   └── sync/                  # SyncEngine, BootstrapLoader, MigrationController, SyncLifecycle
├── playback/                  # TTS, STT, Media3 service, notyfikacja, audio focus
└── ui/                        # theme, navigation, components + ekrany:
                               # dashboard/ browse/ study/ decksettings/ modedetail/
                               # imports/ stats/ archive/ appsettings/
```

Reguły architektury: Room = źródło prawdy (ekrany obserwują Flow → StateFlow);
akcje domenowe to czyste funkcje wołane wewnątrz transakcji Room; `SessionEngine`
żyje w ViewModelu sesji, a holder udostępnia go serwisowi; dzięki temu sesja
przeżywa obrót ekranu. Czas jest wstrzykiwany przez `Clock`.

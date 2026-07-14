# RayColl

Natywna aplikacja Android do nauki fiszkami z powtórkami rozłożonymi w czasie
(FSRS). Port aplikacji webowej (TensorDeck) na Kotlin/Compose. Talie z fiszkami
wielostronicowymi, w pełni konfigurowalne **tryby nauki** (sekwencje kroków:
pokaż/wypowiedz stronę, słuchaj-i-sprawdź STT, oceny, auto-rating, pauzy),
nauka w tle przez Media3 (odtwarzanie bez patrzenia w ekran), import CSV
z auto-detekcją separatora/nagłówka/języka, statystyki (streak, heatmapa),
backup JSON oraz synchronizacja w chmurze (Firestore + logowanie Google).

## Stack

| Warstwa | Technologia |
|---|---|
| Język | Kotlin 2.0.21 |
| UI | Jetpack Compose (BOM 2024.12.01), single-activity, Material 3 |
| DI | Hilt 2.54 |
| Baza lokalna | Room 2.6.1 (źródło prawdy) |
| Preferencje | DataStore |
| Serializacja | kotlinx.serialization 1.7.3 (JSON) |
| Async | kotlinx.coroutines 1.9.0 (StateFlow) |
| Audio/tło | Media3 1.5.1 (MediaSessionService), Android TTS + SpeechRecognizer |
| Chmura | Firebase (BOM 33.7.0): Firestore + Auth, Credential Manager (Google Sign-In) |
| Build | AGP 8.7.3, `compileSdk`/`targetSdk` 36, `minSdk` 26 |

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
```

Testy jednostkowe (JUnit4; część na Robolectric z in-memory Room). Pokrywają
silnik FSRS, dopasowanie mowy, normalizację/merge danych, operacje domenowe,
sesję nauki, import, DAO oraz ViewModele (Dashboard, Browse, DeckSettings,
Import, Stats). Czas jest zawsze wstrzykiwany przez `Clock`, więc testy
FSRS/streak/archiwum sterują czasem deterministycznie.

### Ręczna weryfikacja serializacji w release

Polimorficzna serializacja `ModeStep` (sealed interface + `@SerialName`)
i `CompoundParams` jest weryfikowana round-tripem w teście jednostkowym na
buildzie debug. R8 na release nie stripuje generowanych `$$serializer`
(reguła `-keep ...**$$serializer`), ale po zmianach w regułach proguard warto
potwierdzić ręcznie na buildzie release: utwórz własny tryb nauki z krokami
złożonymi (compound), zamknij i otwórz aplikację — jeśli kroki wczytują się
poprawnie z Room, serializacja przeżywa minifikację.

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

## Struktura pakietów (skrót)

```
com/raycoll/app/
├── RayCollApplication.kt      # @HiltAndroidApp
├── MainActivity.kt            # single-activity, edge-to-edge, splash
├── di/                        # moduły Hilt (DB, Playback, Dispatchers, Clock)
├── core/                      # Ids, LocalDates, Clock
├── domain/                    # CZYSTY Kotlin — zero importów Androida
│   ├── model/                 # Deck, Card, StudyMode, ModeStep (sealed), enums
│   ├── srs/                   # SrsEngine (FSRS), SpeechMatcher
│   ├── normalize/  merge/     # DataNormalizer, MergeEngine, MigrationRules
│   ├── operations/            # czyste funkcje-akcje (stan → stan)
│   ├── selectors/             # DueCards, Streak, BrowseFilter, ...
│   ├── steps/                 # StepRegistry, CompoundExpander, ModeTemplates
│   ├── session/               # SessionEngine, StepExecutor, reducer
│   ├── imports/               # CsvParser, Separator/Header/LanguageDetector
│   └── seed/                  # SeedData (2 talie + 2 tryby)
├── data/
│   ├── local/                 # Room DB (encje z namespace), DAO, mappery, prefs
│   ├── remote/                # AuthRepository, Firestore (schema, dataSource)
│   ├── repository/            # Deck/StudyMode/Activity/Backup, ArchivePurger, Namespace
│   └── sync/                  # SyncEngine, BootstrapLoader, MigrationController, SyncLifecycle
├── playback/                  # TTS, STT, Media3 service, notyfikacja, audio focus
└── ui/                        # theme, navigation, components + ekrany:
                               # dashboard/ browse/ study/ decksettings/ modedetail/
                               # imports/ stats/ archive/ appsettings/
```

Reguły architektury: Room = źródło prawdy (ekrany obserwują Flow → StateFlow);
akcje domenowe to czyste funkcje wołane wewnątrz transakcji Room; `SessionEngine`
żyje w `@Singleton` holderze i przeżywa rotację/tło; czas zawsze przez `Clock`.
Pełny opis: [`docs/PORT_PLAN.md`](docs/PORT_PLAN.md) sekcja 7.

## Status

**v1 kompletne.** Cała logika (T1–T10 planu) i audyt UX zrealizowane. Chmura
(Firestore + Auth) w zakresie v1: schemat dokumentów 1:1 z aplikacją webową
(TensorDeck), zapis diffowy z chunkowaniem batchy, polityka schedulera
(debounce 1200 ms / flush co 10 ocen w nauce / immediate dla operacji
krytycznych / flush przy zejściu w tło). Historia audytów i znany dług:
[`docs/REDTEAM_AUDIT.md`](docs/REDTEAM_AUDIT.md).

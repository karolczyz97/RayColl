# RayColl

Natywna aplikacja Android do nauki fiszkami z powtórkami rozłożonymi w czasie
(FSRS), napisana w Kotlin/Compose. Talie z fiszkami
wielostronicowymi, w pełni konfigurowalne **tryby nauki** (sekwencje kroków:
pokaż/wypowiedz stronę, słuchaj-i-sprawdź STT, oceny, auto-rating, pauzy),
nauka w tle przez Media3 (odtwarzanie bez patrzenia w ekran), import CSV
z auto-detekcją separatora/nagłówka/języka, statystyki (streak, heatmapa),
kopia JSON oraz dane w chmurze (Firestore + konto gościa albo logowanie Google).

## Stack

| Warstwa | Technologia |
|---|---|
| Język | Kotlin 2.4.21 |
| UI | Jetpack Compose (BOM-alpha 2026.10.00), single-activity, Material 3 1.5 Expressive, Navigation 3 |
| DI | Hilt 2.60.1 |
| Dane | Cloud Firestore jako jedyne źródło danych, z trwałą pamięcią podręczną na telefonie (praca offline) |
| Konto | Firebase Auth: konto gościa (anonimowe) albo Google przez Credential Manager |
| Preferencje | DataStore |
| Serializacja | kotlinx.serialization 1.11.0 (JSON) |
| Async | kotlinx.coroutines 1.11.0 (Flow, StateFlow) |
| Audio/tło | Media3 1.11.1 (MediaSessionService), Android TTS + SpeechRecognizer |
| Import CSV | Apache Commons CSV 1.14.1, Commons Text 1.15.0 |
| Chmura | Firebase BOM 35.0.2 |
| Build | AGP 9.4.1, Gradle 9.8.1 na Javie 21, `compileSdk` 37.1, `targetSdk` 37, `minSdk` 33 (Android 13) |

- `applicationId`: `com.raycoll.app` (release), `com.raycoll.app.debug` (debug, na telefonie jako „RayColl (debug)”)
- `versionName`: 1.0.0

## Wymagania

- Java 21 do budowania. Gradle pobiera ją sam (`gradle/gradle-daemon-jvm.properties`),
  więc `gradlew.bat` może startować z innej Javy.
- Android SDK w `%LOCALAPPDATA%\Android\Sdk`
- Wrapper Gradle w repo (`./gradlew.bat`)
- Do testów na emulatorze Firebase: Node.js i Java 21 lub nowsza

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
są reguły dla serializerów kotlinx.serialization klas aplikacji i jedna reguła dla
opcjonalnej adnotacji Commons CSV; Media3, Firebase, Hilt i Credential Manager
dostarczają własne reguły konsumenckie.

### Podpis release

Klucz podpisu nie jest w repozytorium. To klucz z czasów wersji Expo (EAS, projekt
`@karolczyz97/raycoll-io`), ten sam, którego SHA-1 jest zarejestrowany w Firebase dla
`com.raycoll.app`. Kopia leży w `C:\Users\Karol\klucze\raycoll-release.jks`, a hasła i alias są
na koncie Expo (`eas credentials -p android`). Do budowania dopisz je do `local.properties`:

```
release.storeFile=C:/Users/Karol/klucze/raycoll-release.jks
release.storePassword=...
release.keyAlias=...
release.keyPassword=...
```

Bez tych wpisów release zostaje niepodpisany. Klucza nie zmieniaj — Android nie zaktualizuje
aplikacji podpisanej innym kluczem bez odinstalowania. Przy nowym kluczu jego SHA-1
(`keytool -list -v -keystore <plik>`) trzeba dodać w Firebase Console przy `com.raycoll.app`
(sekcja „Firebase — logowanie”).

## Testy

```bat
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:ktlintCheck
./gradlew.bat :app:lint
:: wszystko naraz (testy, ktlint, lint)
./gradlew.bat :app:check
```

Testy jednostkowe (JUnit4; część na Robolectric). Repozytoria działają w testach na
atrapie `InMemoryDocumentStore` zamiast prawdziwego Firestore. Pokrywają silnik FSRS,
dopasowanie mowy, operacje domenowe, sesję nauki, import CSV, kopię/import/reset,
dane gościa przy logowaniu, ViewModele i polecenia odtwarzacza Media3. Czas jest
wstrzykiwany przez `Clock`, więc testy FSRS/streak/archiwum sterują czasem
deterministycznie.

Formatowanie Kotlin i skryptów Gradle: ktlint 1.8.0 z ustawieniami w `.editorconfig`,
uruchamiany przez Gradle (`:app:ktlintCheck` sprawdza, `:app:ktlintFormat` poprawia).

### Testy na emulatorze Firebase

Lokalny emulator Firestore + Auth, wyłącznie projekt `demo-raycoll` (nic nie trafia
do prawdziwej chmury). Konfiguracja: [`firebase.test.json`](firebase.test.json).

```powershell
npm ci --prefix tools/firestore-tests
# reguły firestore.rules (Node)
npm test --prefix tools/firestore-tests
# scenariusze aplikacji (dwa „urządzenia”, offline, konflikty, logowanie)
npm run test:android --prefix tools/firestore-tests
```

- Testy reguł (`firestore.test.mjs`) sprawdzają m.in.: blokadę zapisu do `users/{uid}`
  (stara wersja aplikacji), odrzucenie starszej daty i daty z przyszłości, odrzucenie
  edycji usuniętego dokumentu i przyjęcie przywrócenia importem, twarde kasowanie
  (konto Google tylko tombstony starsze niż 30 dni, gość wszystko).
- Scenariusze aplikacji (`FirestoreEmulatorTest`) to prawdziwy Firestore SDK pod
  Robolectric, połączony z emulatorem. Bez emulatora zwykłe `testDebugUnitTest`
  je pomija.
- Na Windows emulator potrafi przeżyć zakończenie testów i zająć port 8089.
  Skrypty npm same go zatrzymują przed i po przebiegu (`stop-stale-emulators.mjs`).

Pakiet Node służy wyłącznie testom, nie jest częścią APK.

### Ręczna weryfikacja serializacji w release

Polimorficzna serializacja `ModeStep` (sealed interface + `@SerialName`)
i `CompoundParams` jest weryfikowana round-tripem w teście jednostkowym na
buildzie debug. R8 na release nie stripuje generowanych `$$serializer`
(reguła `-keep ...**$$serializer`), ale po zmianach w regułach proguard warto
potwierdzić ręcznie na buildzie release: utwórz własny tryb nauki z krokami
złożonymi (compound), zamknij i otwórz aplikację — jeśli kroki wczytują się
poprawnie, serializacja przeżywa minifikację.

## Firebase — aplikacje i klucz API

W projekcie **raycoll-io** są zarejestrowane dwie aplikacje Android (wersji webowej już nie ma):

| Wariant | Pakiet | Firebase App ID | SHA-1 w konsoli |
|---|---|---|---|
| debug | `com.raycoll.app.debug` | `1:328078920834:android:466b072e1524b9e442789b` | `35:80:D2:75:83:68:BB:CB:8C:3F:CD:B5:CF:01:BC:24:3B:F8:23:7C` (debug keystore) |
| release | `com.raycoll.app` | `1:328078920834:android:f37e190bbecf360542789b` | `00:DC:BD:8A:C8:1D:61:01:CA:C7:22:53:AD:6E:15:0F:F6:74:BF:94` |

Identyfikatory aplikacji są w `app/build.gradle.kts` (osobno dla debug i release). Klucz API
nie jest w repozytorium. Przed pierwszym buildem dopisz do `local.properties` (plik jest
ignorowany przez git) klucz **„Android key (auto created by Firebase)”** z
[Google Cloud Console → Credentials](https://console.cloud.google.com/apis/credentials?project=raycoll-io):

```
firebase.apiKey=AIza...
```

W CI ustaw zamiast tego zmienną środowiskową `FIREBASE_API_KEY`. Bez klucza budowanie APK
kończy się czytelnym błędem; testy i lint budują się bez niego.

Uwaga: Firebase zapamiętuje zalogowanie osobno dla każdego App ID. Zmiana App ID (np. przejście
z dawnego identyfikatora web na Android) wylogowuje telefon jeden raz.

## Firebase — logowanie

- **Google:** działa, gdy w konsoli aplikacja danego wariantu ma SHA-1 klucza, którym podpisano
  APK (tabela wyżej). Release podpisany innym kluczem (sekcja „Podpis release”) wymaga dopisania
  jego SHA-1 przy `com.raycoll.app`, inaczej logowanie rzuci `DEVELOPER_ERROR`.
- **Gość:** w konsoli musi być włączone logowanie anonimowe (Authentication → Sign-in method →
  Anonymous). Bez niego aplikacja nie utworzy konta gościa przy pierwszym uruchomieniu.

## Dane w chmurze: jak to działa

- Wszystkie dane (talie, karty, tryby nauki, aktywność) są w Firestore pod
  `users/{uid}/…`. Telefon trzyma ich kopię w pamięci podręcznej Firestore, więc
  aplikacja działa offline, a zmiany wysyłają się same po odzyskaniu sieci.
- Przy pierwszym uruchomieniu powstaje konto gościa (wymaga internetu). Logowanie
  Google pyta, co zrobić z danymi gościa: przenieść na konto, zostawić na telefonie,
  usunąć albo nie logować.
- Konflikty: wygrywa nowsza zmiana według zegara telefonu. Pilnują tego reguły
  serwera w [`firestore.rules`](firestore.rules); odrzucone zmiany aplikacja zgłasza
  komunikatem.
- Usunięte elementy zostają w chmurze jako „tombstony” i znikają po 30 dniach.
- Kopia JSON (eksport/import) i reset działają na danych bieżącego konta.

Szczegóły i decyzje: [`docs/PLAN_SYNC_FIRESTORE.md`](docs/PLAN_SYNC_FIRESTORE.md).
Odstępstwa od planów: [`docs/ODSTEPSTWA.md`](docs/ODSTEPSTWA.md).

### Wdrożenie reguł Firestore

Plik w repo nie zmienia sam reguł działających w projekcie Firebase. Obecne reguły wdrożono
15.10.2026 (razem z włączeniem logowania anonimowego). Poprzednie reguły są zapisane w
[`docs/firestore-rules-backup/`](docs/firestore-rules-backup). Po zmianie `firestore.rules`:

```powershell
npm test --prefix tools/firestore-tests
firebase deploy --only firestore:rules --project raycoll-io
```

Nowe reguły odrzucają każdy zapis starej wersji aplikacji (opartej na Room), więc stara
wersja nie może już niczego zmienić w chmurze. Opis składni:
[dokumentacja Firebase](https://firebase.google.com/docs/firestore/security/get-started).

## Paczki repomix (kod dla modeli AI)

Konfiguracje i skrypt są w [`repomix/`](repomix). Uruchom z dowolnego miejsca:

```powershell
powershell -ExecutionPolicy Bypass -File repomix/pack.ps1
```

Paczki trafiają do `repomix/out/` (ignorowane przez git). Domyślnie powstają trzy części,
które razem obejmują każdy plik tekstowy repo dokładnie raz:

| Konfiguracja | Zawartość | Rozmiar (ok.) |
|---|---|---|
| `part1-logic.json` | konfiguracja Gradle i Firebase, `firestore.rules`, manifest, `core`, `data`, `domain`, `di`, plany i odstępstwa z `docs/` | 85 tys. tokenów |
| `part2-ui.json` | ekrany, ViewModele, motyw, nawigacja, `playback`, zasoby (teksty w 5 językach) | 130 tys. tokenów |
| `part3-tests.json` | testy Kotlin (atrapa, Robolectric, scenariusze emulatora) i testy reguł w Node | 112 tys. tokenów |

Z przełącznikiem `-All` powstaje jedna paczka z całością (`all.json`, ok. 330 tys. tokenów,
także skrypty repomix). Pomijane są tylko pliki binarne (ikony, dźwięki, `gradle-wrapper.jar`)
i `package-lock.json`.

## Struktura pakietów (skrót)

```
com/raycoll/app/
├── RayCollApplication.kt      # @HiltAndroidApp, start konta
├── MainActivity.kt            # single-activity, edge-to-edge, splash, okno wyboru danych gościa
├── di/                        # moduły Hilt (Firebase, magazyn, zegar, dispatchery, sesja)
├── core/                      # Ids, LocalDates, Clock
├── domain/                    # CZYSTY Kotlin — zero importów Androida
│   ├── model/                 # FlashcardGroup, Flashcard, StudyMode, ModeStep (sealed), StoreData
│   ├── srs/                   # SrsEngine (FSRS), SrsCardCategory, SpeechMatcher
│   ├── backup/  normalize/    # walidacja kopii, normalizacja danych
│   ├── merge/                 # MigrationRules (dane gościa przy logowaniu)
│   ├── operations/            # czyste funkcje-akcje (stan → stan)
│   ├── selectors/             # DueCards, BrowseFilter, CardStats, ArchiveCountdown
│   ├── steps/                 # CompoundExpander, ModeTemplate, ModeStepValidator
│   ├── session/               # SessionEngine, StepExecutor, reducer
│   ├── imports/               # CsvParser, Header/LanguageDetector, walidacja
│   └── seed/                  # SeedData (2 talie), SeedModes (2 tryby)
├── data/
│   ├── firestore/             # DocumentStore, FirestoreDocumentStore, stan połączenia
│   ├── account/               # AccountManager (gość, logowanie, wylogowanie), CurrentAccount
│   ├── remote/                # AuthRepository, FirestoreSchema (format dokumentów)
│   ├── repository/            # Deck/StudyMode/Backup, Cleanup (30 dni)
│   ├── files/                 # pliki z systemowego okna wyboru (import CSV, kopie JSON)
│   └── local/prefs/           # ustawienia w DataStore
├── playback/                  # TTS, STT, Media3 service, notyfikacja, audio focus
└── ui/                        # theme, navigation, components + ekrany:
                               # main/ dashboard/ browse/ study/ decksettings/ modedetail/
                               # studymodes/ imports/ stats/ archive/ appsettings/
```

Reguły architektury: Firestore = jedyne źródło prawdy; repozytoria wystawiają
współdzielone nasłuchy kolekcji jako Flow, a ekrany zbierają je jako StateFlow.
Zapisy nie czekają na serwer (działają offline). Akcje domenowe to czyste funkcje.
`SessionEngine` żyje w ViewModelu sesji, a holder udostępnia go serwisowi; dzięki
temu sesja przeżywa obrót ekranu. Czas jest wstrzykiwany przez `Clock`.

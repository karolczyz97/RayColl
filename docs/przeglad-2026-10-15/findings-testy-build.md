# Skan 15.10.2026 — Testy, build, zasoby, tłumaczenia

Pełna lista agenta (tylko odczyt kodu, gałąź `przeglad-modernizacja`). Zbiorczy raport: [RAPORT.md](RAPORT.md).

## Ocena ogólna

Konfiguracja buildu jest zdrowa. Wczorajsze poprawki są wdrożone (blok `lint {}` usunięty, klucz Firebase sprawdzany tylko dla release, `DocumentFilesTest` istnieje), a katalog wersji nie ma osieroconych wpisów poza jednym. Zasoby są czyste: wszystkie 289 stringów jest użyte w kodzie, wszystkie drawable, raw i xml też, a w Kotlinie nie ma zahardkodowanych tekstów interfejsu. Klucze tłumaczeń pilnuje `I18nConsistencyTest`, ale on nie wyłapuje błędów treści. Znalazłem tam kilka realnych błędów widocznych dla użytkownika. Testy są solidne w logice domenowej i sesji, a słabe w dwóch miejscach: ścieżki danych bez żadnego testu (backup, migracja gościa) i duży boilerplate. Nie uruchamiałem Gradle ani testów, więc to analiza statyczna. Faktów sprawdzonych poza kodem są trzy: zrzut `aapt2` z gotowego APK, bytecode `FirebaseOptions` w cache Gradle i `grep` użycia zasobów.

Wszystkie ścieżki poniżej są względem `C:\Users\Karol\.gemini\antigravity\scratch\RayColl`.

## Znaleziska (od najważniejszych)

**N1. [tłumaczenia] [XS]** `app/src/main/res/values*/strings.xml:26` (wszystkie 5 języków), klucz `dialog_delete_permanent_desc`: cudzysłowy `"%1$s"` nie są poprzedzone backslashem. `aapt2` je wycina, co potwierdza zrzut APK (`Deck %1$s will be permanently deleted`). W oknie trwałego usunięcia nazwa talii wtapia się w zdanie. → Zamienić na `\"%1$s\"` albo typograficzne („%1$s” w PL i DE, “%1$s” w EN, ES, IT). Dodać do `I18nConsistencyTest` sprawdzenie „brak nieucieczonego `"` w wartościach”. Wpływ na użytkownika: tak.

**N2. [testy] [M]** `BackupRepository.exportTo/importFrom/factoryReset` (`data/repository/BackupRepository.kt:40-64`) i opakowania `AppSettingsViewModel.exportBackup/importBackup/factoryReset` nie mają żadnego testu (`grep` po `src/test` zwraca 0). Testowany jest tylko walidator. → Dodać `BackupRepositoryTest` z Robolectric i plikiem tymczasowym, obejmujący:
- round-trip eksport→import (te same dane),
- zły JSON → `INVALID_BACKUP` i dane nietknięte,
- nieczytelny plik → `FILE_ERROR`,
- `factoryReset` podmienia dane na seed.

Wpływ na użytkownika: tak (ochrona przed utratą danych przy regresji).

**N3. [testy] [M]** Migracja gościa na konto jest nietestowana. Dotyczy `BootstrapResult.MigrationRequired` (`data/sync/BootstrapLoader.kt:68`), `MigrationController.execute` gałęzie MIGRATE i FRESH (`data/sync/MigrationController.kt:27-36`) oraz `SyncLifecycle.resolveMigration`. W testach jest tylko CANCEL (`ui/appsettings/AppSettingsSignInTest.kt:201-208`). → Dodać scenariusz w stylu `TwoDeviceSyncTest`: gość ma edytowane dane, konto ma pustą chmurę, wynik `MigrationRequired`. Dla MIGRATE konto dostaje talie gościa, gość je zachowuje i chmura je dostaje. Dla FRESH konto dostaje seed ze stałymi id. Wpływ na użytkownika: tak.

**N4. [tłumaczenia] [S]** `mode_issue_no_terminal` i `mode_issue_auto_rate_without_stt` (EN/DE 215-216, ES/IT 220-221, PL 225-226) cytują nazwy kroków w cudzysłowach, ale nazwy z listy kroków brzmią inaczej.
- W polskim żadna z czterech nazw nie pasuje: „Pokaż przyciski ocen” kontra „Pokaż przyciski oceny”, „Następna karta” kontra „Przejdź do kolejnej karty (bez oceny)”, „Oceń z odpowiedzi” kontra „Oceń automatycznie z wyniku”, „Sprawdź mowę (STT)” kontra „Sprawdź wymowę (STT)”.
- W pozostałych językach pasuje tylko część, np. „Nächste Karte” i „Siguiente tarjeta” nie istnieją w UI.

→ Przekazać etykiety jako `%1$s` z tych samych `step_*` w `ui/modedetail/StepTexts.kt:40,42`, żeby nie mogły się rozjechać. Wpływ na użytkownika: tak.

**N5. [tłumaczenia] [XS]** `values-it/strings.xml:186`: `srs_badge_mastered` = „Masterizzata”. Po włosku „masterizzare” znaczy „nagrać na płytę”. → „Appresa” albo „Padroneggiata”. Wpływ na użytkownika: tak.

**N6. [tłumaczenia] [S]** `data/remote/auth/AuthRepository.kt:96` mapuje `NoCredentialException` (brak konta Google na telefonie) na `NO_TOKEN`. UI pokazuje `auth_error_no_token` (`ui/appsettings/AppSettingsScreen.kt:205`): „Brak tokena logowania. Spróbuj ponownie.” Ponowienie nie pomoże. → Osobny komunikat, np. „Nie znaleziono konta Google na tym urządzeniu. Dodaj konto w ustawieniach telefonu i spróbuj ponownie.”, w 5 językach. Wpływ na użytkownika: tak.

**N7. [build] [S]** `app/build.gradle.kts:12-13,22-34` + `data/remote/FirebaseConfig.kt:26`. Komentarz twierdzi „bez klucza działa debug”, ale build debug bez klucza przechodzi, a aplikacja wywala się przy starcie. `FirebaseOptions.Builder.setApiKey("")` kończy się `Preconditions.checkNotEmpty` → `IllegalArgumentException("ApiKey must be set.")`; sprawdziłem to w bytecode `firebase-common-23.0.0`. Wywołanie jest w `RayCollApplication.onCreate:22`. → Podpiąć `requireFirebaseApiKey` pod `packageDebug` (i `packageRelease` zamiast `generateReleaseBuildConfig`). Testy i lint zostają bez klucza, a `assembleDebug` daje czytelny błąd. Wpływ na użytkownika: nie (dotyczy tylko builda bez klucza).

**N8. [testy] [S]** `domain/selectors/DueCards.kt:13-33`: `CardOrder.HARDEST` i filtr `NEW_AND_REVIEW` (domyślny!) nie mają testu. `SelectorsTest` sprawdza tylko ALL, NEW, REVIEW. `countDueCards` (licznik na dashboardzie) też nie ma testu bezpośredniego. → Testy: NEW_AND_REVIEW (nowe plus zaległe, bez niezaległych), HARDEST z stabilnymi remisami, `countDueCards == getDueCards.size`. Wpływ na użytkownika: tak.

**N9. [testy] [S]** `domain/merge/MergeEngine.kt:83-102`: `mergeGroups` ma nietrywialną logikę (nowszy `updatedAt` wygrywa, usunięcie kontra późniejsza edycja, scalanie kart). Testowane jest tylko uporządkowanie. `MergeEngineTest.kt:149-201` (`mergeUserData basic`) nie sprawdza nawet nazwy talii, choć chmurowa jest nowsza. → Dodać: wygrywa nowsza nazwa, tombstone talii propaguje, edycja nowsza niż usunięcie reanimuje, talia tylko po jednej stronie. Wpływ na użytkownika: tak.

**N10. [testy] [S]** Wspólny singleton DataStore przecieka między testami. `UserPreferencesRepository(context)` używa globalnego `Context.dataStore` (`data/local/prefs/UserPreferencesRepository.kt:25`). Trzy klasy testów z niego korzystają: `UserPreferencesRepositoryTest.kt:72-103` zapisuje DARK, `useSystemColors=false` i `ttsRate`, a `StudyRestartTest.kt:116-119` musi to ręcznie resetować (komentarz o „współdzielonym ustawieniu”). Wynik zależy od kolejności klas. → Helper w `testing/`, który buduje `PreferenceDataStoreFactory.create` na pliku tymczasowym, używany wszędzie. Wpływ na użytkownika: nie.

**N11. [testy] [M]** Brak testów dla `playback/StudySessionService.kt` (232 linie), `SessionDepsAdapter.kt`, `AudioFocusManager.kt`. Usługa pojawia się w testach tylko jako nazwa klasy w asercjach intentów (`StudyRestartTest.kt:235`). To rdzeń nauki w tle. → Test Robolectric `buildService`: powiadomienie i kanał, start jako foreground, zatrzymanie po końcu sesji, podpięcie i odpięcie sterowania w holderze. Wpływ na użytkownika: tak (regresje wykryte dopiero na telefonie).

**N12. [build] [S]** `app/build.gradle.kts:53-60` (release bez `signingConfig`) a `README.md:42-44,95-100`. README zakłada, że SHA-1 debug-keystore zarejestrowane dla obu id wystarczy, ale release jest niepodpisany i nie da się go zainstalować (a jest potrzebny do ręcznej weryfikacji R8 z README:72-80). → Podpisać release kluczem debug (do użytku własnego) albo czytać keystore z `local.properties`. Opisać w README, że nowy klucz wymaga nowego SHA-1 w Firebase. Wpływ na użytkownika: tak (właściciel).

**N13. [zasoby] [S]** `app/src/main/res/xml/backup_rules.xml:3`, `data_extraction_rules.xml:4,9`: `<include domain="database" path=".">` obejmuje cały katalog baz, więc także lokalny cache Firestore. Firestore domyślnie trzyma go na dysku, a w kodzie nie ma ustawień tego zmieniających. To zbędne dane w backupie i potencjalnie stare, niewysłane zapisy (patrz R12 z wczoraj). → Wykluczyć pliki `firestore.*` albo dołączyć tylko `raycoll.db`, `-wal`, `-shm`. Wcześniej sprawdzić nazwy na telefonie: `adb shell run-as com.raycoll.app.debug ls databases`. Wpływ na użytkownika: tak, drobny.

**N14. [tłumaczenia] [S]** Nienaturalne PL (`values-pl/strings.xml`), propozycje:
- :117-124 „Usuwa się za 3 dni / jutro / dzisiaj” → „Usunięcie za %d dni”, „Usunięcie jutro”, „Usunięcie dziś”.
- :163 „Oblej i dalej” → „Oznacz jako błędną i dalej”.
- :173 „Fallback ręczny” → „Oceń ręcznie, gdy nasłuch zawiedzie”. EN „Manual fallback” → „Rate manually if listening fails”.
- :137-138 „Odtwórz feedback sukcesu/błędu” → „Odtwórz sygnał poprawnej/błędnej odpowiedzi”. EN „Play success feedback” → „Play success sound”.
- :293 „kasuje odpowiedź” → „czyści wynik rozpoznania mowy”.
- :317 „Które karty” → „Karty do nauki”.
- :71 „Kreska (|)” → „Kreska pionowa (|)”.
- :14 „Dane lokalne” → „Znaleziono dane lokalne”.

Wpływ na użytkownika: tak.

**N15. [tłumaczenia] [S]** Niespójna terminologia w PL.
- „karta” (:50-51, :143, :294, :292) kontra „fiszka” (:280 „Fiszki:”, :193-198, ekran przeglądania). Ujednolicić do „fiszka”.
- „tapnięcie” (:129-130, :142) kontra „dotknięcie” (:49, :293). Ujednolicić do „dotknięcie”.
- „linia” (:72) kontra „wiersz” (:277, :305).
- „nieudaną” (:141) kontra „błędne” (:46, :295).

Wpływ na użytkownika: tak.

**N16. [tłumaczenia] [S]** ES i IT, anglicyzmy i niespójności.
- `values-es:105-107`: „Export fallido.”, „¡Import completado!”, „Import fallido.” → „Error al exportar.”, „¡Importación completada!”, „Error al importar.”.
- `values-it:106-107`: „Import completato!”, „Import non riuscito.” → „Importazione completata!”, „Importazione non riuscita.”.
- `values-es:139` „Espera un toque” → „Esperar un toque” (reszta kroków jest w bezokoliczniku).
- ES miesza „valoración” i „calificación” (`:144`, `:214`, `:220`).
- IT miesza „carta”, „scheda” (`:49-50`, `:287`) i „flashcard” (`:60-62`, `:256`, `:271`).
- IT miesza „passo” (`:287-288`) i „passaggio”.
- IT `dashboard_due_count` „Scadenza: %d” (`:37`) → „Da ripassare: %d”.

Wpływ na użytkownika: tak.

**N17. [tłumaczenia] [XS]** Drobne błędy gramatyczne i spójności.
- `values-de:58` „Du hast vor dem Ende beendet.” (brak dopełnienia) → „Du hast die Sitzung vorzeitig beendet.”.
- `values-it:26` „Non può essere annullato.” → „L'operazione non può essere annullata.”.
- `values-es:261` token „BORRAR”, a przycisk to „Eliminar” (w pozostałych językach token równa się przyciskowi) → „ELIMINAR”.
- `values-it:76,230` używają `’`, reszta pliku `\'`.

Wpływ na użytkownika: tak.

**N18. [testy] [S]** Mylące nazwy i słabe asercje.
- `data/…/LocalDatesTest.kt:14-23`: „handles timezone” przechodzi także, gdy strefa jest ignorowana (00:00 UTC to 01:00 Warszawa, ten sam dzień).
- `DeckRepositoryTest.kt:251-261`: nazwa mówi „signal sync once”, a asercji sygnału brak.
- `DashboardViewModelTest.kt:63-74`: „due count follows the injected clock” nie rusza zegara, a `setStudyFilter(NEW_AND_REVIEW)` to wartość domyślna.
- `StatsViewModelTest.kt:151-162`: „heatmapa pole istnieje” sprawdza `isEmpty()`.
- `SessionEngineBackgroundTest.kt:113-125`: `>= 1 || FINISHED` jest prawie tautologią.
- `SessionEngineBackgroundTest.kt:163-184`: „restarts current card” sprawdza tylko niezmieniony indeks, więc nie odróżnia restartu od no-op.
- `SaveFailureTest.kt:102-115`: `events.first()` bez sprawdzenia typu zdarzenia.
- `ModeStepValidatorTest.kt:91-96`: „gives error” dla ostrzeżenia nieblokującego.
- `SrsEngineTest` zawiera testy `SpeechMatcher`, więc nazwa klasy myli.

Wpływ na użytkownika: nie.

**N19. [testy] [M]** Boilerplate.
- 13 plików definiuje własne budowniczne kart i talii: `DaoTest`, `FirestoreSchemaTest`, `CloudPositionsTest`, `SyncDiffTest`, `MergeEngineTest`, `BackupValidatorTest`, `DataNormalizerTest`, `OperationsTest`, `SelectorsTest`, oba `SessionEngine*Test`, `AudioTurnTest`, `BrowseScreenTest`.
- 21 plików powtarza `VmTestHarness.create` + `db.close()`.
- 5 testów VM powtarza `viewModels += it` + `cancelAndJoin` (Archive, Browse, Dashboard, DeckSettings, Stats).
- `ModeDetailViewModelTest` ma 22× `(vm.uiState.value as …Editing)`, choć helper `editing(vm)` jest w linii 229.

→ `testing/Fixtures.kt` oraz JUnit `ReposRule` i `ViewModelTracker`. Wpływ na użytkownika: nie.

**N20. [testy] [M]** Testy UI mają około 100 zahardkodowanych angielskich tekstów (`onNodeWithText("Save")`, „Add card” itd.) i zero odwołań do `R.string`. Najwięcej w `DialogStateRestorationTest` (22), `ModeDetailScreenTest` (20), `StudyCardTest` (17). Każda zmiana brzmienia w `values/strings.xml`, a ostatnio były, psuje testy. → Helper `str(@StringRes id)` oparty o `ApplicationProvider`. Wpływ na użytkownika: nie.

**N21. [testy] [S]** Kruche czekanie w czasie rzeczywistym.
- Ten sam `waitUntil` na `System.currentTimeMillis()` + `Thread.sleep(10)` jest w `TwoDeviceSyncTest.kt:375-381` i `StudyRestartTest.kt:103-111`.
- Negatywne asercje po `Thread.sleep(50)` (`TwoDeviceSyncTest.kt:349,355,361`) mogą przejść z niewłaściwego powodu.

→ Jeden współdzielony helper. Negatywne warunki sprawdzać deterministycznie (`join` zadania albo harmonogram testowy). Wpływ na użytkownika: nie.

**N22. [testy] [S]** Zdublowane testy do usunięcia lub scalenia.
- `SettingsSectionsTest.kt:83-92` ≡ `I18nConsistencyTest.kt:16-23` (ten sam test języków).
- `SrsEngineTest.kt:49-72` jest zawarte w `:168-176`. Zostawić granicę reps 2/3.
- `SyncDiffTest.kt:31-53` ≈ `:94-109`.
- `ModeStepValidatorTest.kt:80-89` ≈ `:173-187`, oraz `:91-96` ≈ `:98-105`.
- `ImportViewModelTest.kt:443-451` ≈ `:159-167`.
- `StatsViewModelTest.kt:88-95` ≈ `:164-168`.
- `CompoundExpanderTest.kt:152-217` (dwa testy tej samej sekwencji).

Wpływ na użytkownika: nie.

**N23. [testy] [S]** Testy w złych klasach i zdublowane atrapy.
- `AppSettingsSignInTest.kt:192-234` testuje `SyncLifecycle` (sign-out, migracja), a nie ViewModel.
- `StudyModeConsistencyTest.kt:155-164` testuje `isDeleteConfirmed` (ekran talii).
- `BackupValidatorTest.kt:38-61,106-144` testuje `FirestoreSchema`, a test domeny importuje warstwę danych.
- Dwie atrapy `GoogleSignIn`: `TwoDeviceSyncTest.kt:148-154` i `AppSettingsSignInTest.kt:60-78`.
- Atrapa `SessionControls` zdublowana w `SessionPlayerTest.kt:19-38` i `SessionMediaControllerTest.kt:29-47`.
- 11-argumentowa budowa `SyncLifecycle` powtórzona w `TwoDeviceSyncTest.kt:158-170` i `AppSettingsSignInTest.kt:125-137`.

→ Przenieść do `testing/` i `SyncLifecycleTest`. Wpływ na użytkownika: nie.

**N24. [testy] [S]** `domain/session/BackgroundSubstitution.kt:18-20` (`calculateBgWaitMs`): wzór długości pauzy w tle plus granice min i max nie mają testu. `BackgroundSubstitutionTest` pokrywa tylko `isModeHandsFreeCapable`. → Test czystej funkcji: pusty tekst, długi tekst, granice. Wpływ na użytkownika: tak.

**N25. [testy] [S]** `data/local/mappers/EntityMappers.kt:33-38` (`decodeOrEmpty`): fallback dla uszkodzonej kolumny JSON jest nietestowany. → W `DaoTest` wstawić `pagesJson="not json"` i sprawdzić, że `toDomain()` daje puste strony bez wyjątku. Wpływ na użytkownika: tak.

**N26. [testy] [M]** `AuthRepository.kt:85-110` (mapowanie błędów Credential Manager, `signInWithIdToken`) nie ma testów. → Wyciągnąć czystą funkcję `mapCredentialFailure(e): SignInError` i ją przetestować. Wpływ na użytkownika: tak.

**N27. [build] [XS]** `gradle/libs.versions.toml:25,74` + `app/build.gradle.kts:135`: `androidx.test.ext:junit` jest zadeklarowany, ale żaden plik nie importuje `androidx.test.ext` (testy używają `RobolectricTestRunner`). → Usunąć wpis, wersję `androidxTestJunit` i linię w buildzie. Wpływ na użytkownika: nie.

**N28. [build] [XS]** `.gitignore`: brakuje `*.jks`, `*.keystore`, `keystore.properties` (przy dodaniu podpisywania z N12), `firebase-debug.log*` i `.firebase/` (README każe uruchamiać `firebase deploy` w katalogu repo, a to generuje te pliki). Brakuje też `.claude/worktrees/` (jest tylko w lokalnym `.git/info/exclude`) i decyzji o `.firebaserc`. Wpływ na użytkownika: nie.

**N29. [build] [S]** `README.md` nieaktualny w kilku miejscach:
- :47-48 twierdzi, że `proguard-rules.pro` ma reguły Room, Media3, Firebase, Hilt (są tam tylko reguły kotlinx.serialization).
- :64-70 opisuje ręczny JAR ktlint, choć istnieją `:app:ktlintCheck` i `ktlintFormat`.
- :93 „bez klucza build kończy się błędem” (dotyczy tylko release, patrz N7).
- Drzewo pakietów (:156-181) nie zawiera `data/files` i `ui/studymodes`.
- Brak zalecenia, by ograniczyć klucz API w Google Cloud do pakietu i SHA-1 (klucz i tak trafia do APK).

Wpływ na użytkownika: nie.

**N30. [build] [XS]** Skrypty w katalogu głównym.
- Wszystkie 4 `repomix*.json` mają resztki po starym projekcie: `.agents/**`, `plan_*.txt`, `konkluzje_*.txt` (np. `repomix.config.json:45-47`).
- `repomix.full-part2-ui.config.json:8` wspomina „layouty, style”, a w Compose ich nie ma.
- Żaden config nie obejmuje `firestore.rules`, `firebase.json` ani `app/schemas`.
- `pack-repomix.ps1`: `npx repomix` bez przypiętej wersji, brak `$ErrorActionPreference='Stop'`. Plik jest UTF-8 bez BOM z polskimi znakami (3 linie), a powłoka to Windows PowerShell 5.1, więc komunikaty będą zniekształcone.

Wpływ na użytkownika: nie.

**N31. [martwy-kod] [XS]** Śmieci lokalne.
- `.claude/worktrees` zajmuje 495 MB i zawiera 5 starych worktree z gałęziami `worktree-agent-*` (jedno oznaczone locked). Usunąć dopiero, gdy żaden agent z nich nie korzysta.
- `docs/przeglad-2026-10-14/gradle-locked.sh:4` ma zaszytą ścieżkę do katalogu tymczasowego martwej sesji.
- Pusty katalog `app/src/main/java/com/raycoll/app/ui/component/` (obok prawdziwego `components/`).

Wpływ na użytkownika: nie.

**N32. [zasoby] [XS]** `app/src/main/AndroidManifest.xml:20-22`: w `<queries>` jest `RECOGNIZE_SPEECH`, ale kod niczego nie rozwiązuje (`grep` po `queryIntent*` i `resolveActivity` daje 0). `startActivity` dla TTS nie wymaga `queries`. Potrzebne są `TTS_SERVICE` (:15) i `RecognitionService` (:18). → Usunąć trzeci wpis. Wpływ na użytkownika: nie.

**N33. [zasoby] [XS]** Debug i release mają tę samą etykietę „RayColl” (`android:label="@string/app_name"`, `applicationIdSuffix=".debug"`). Na telefonie są dwie identyczne ikony. → Placeholder `${appLabel}` z `"RayColl (debug)"` dla debug. Wpływ na użytkownika: tak (właściciel).

**N34. [build] [XS]** `app/build.gradle.kts:149`: `ktlintSources` ma `"*.kts"` tylko w katalogu `app/`, więc `build.gradle.kts` i `settings.gradle.kts` z roota nie są lintowane. `check` nie uruchamia `ktlintCheck`. Wpływ na użytkownika: nie.

**N35. [build] [XS]** `gradle/wrapper/gradle-wrapper.properties` nie ma `distributionSha256Sum`. Brak `.gitattributes`: dziś wszystkie pliki są LF przy `autocrlf=false`, ale na innej maszynie `gradlew` może dostać CRLF. → `* text=auto eol=lf`, `*.bat eol=crlf`. Wpływ na użytkownika: nie.

**N36. [build] [XS]** `firestore.rules:4-6`: blok `match /users/{uid}` prawdopodobnie jest zbędny. W `rules_version = '2'` rekursywny `{document=**}` w drugim bloku pasuje do zera lub więcej segmentów, więc obejmuje też sam dokument. → Do potwierdzenia w Rules Playground (tabela z README:132-139) przed usunięciem. Wpływ na użytkownika: nie.

**N37. [zasoby] [XS]** Stringi o tej samej wartości we wszystkich 5 językach (`app_name`, `list_bullet`, `mode_editor_step_title`, `filter_chip_count`, `study_progress`, `app_settings_tts_rate_value`) mogłyby mieć `translatable="false"`. Wymaga to dostosowania testu parytetu kluczy. W `values*/strings.xml:290,294` są komentarze `<!-- ui1 -->` i `<!-- ui2 -->` po agentach, które nic nie znaczą. Wpływ na użytkownika: nie.

**N38. [testy] [XS]** Styl i szybkość.
- 14× `assertEquals(false/true/null, …)` zamiast `assertFalse` / `assertNull` (najwięcej w `BackgroundSubstitutionTest`).
- W ok. 8 plikach testy stoją przed deklaracją pól (np. `DeckRepositoryTest.kt:42`, `ImportViewModelTest.kt:36`).
- Mieszane polskie i angielskie nazwy testów.
- Nieużywany parametr `scope` w `AudioTurnTest.kt:39`.
- 40 z 73 plików działa na Robolectric (wolny start). `ReportingStatusTest` potrzebuje go tylko przez `Log.w` w `SyncEngine.kt:276`. `unitTests.isReturnDefaultValues = true` pozwoliłoby go zdjąć.
- `src/test/resources/robolectric.properties` ustawia sdk=34, a apka celuje w 37, więc różnice zachowania systemu nie są testowane.

Wpływ na użytkownika: nie.

## TOP 5 zmian

1. **N1 + N4 + N5 + N6** (teksty): brakujące cudzysłowy w oknie usunięcia talii, komunikaty o błędach wskazujące nieistniejące przyciski, „Masterizzata” i mylący komunikat o braku konta Google. Cztery realne błędy widoczne dla użytkownika, naprawa to głównie edycja XML.
2. **N2 + N3**: testy backupu/przywracania i migracji gościa na konto. To jedyne ścieżki, w których błąd kosztuje użytkownika dane, a dziś nie ma tam żadnego testu.
3. **N7 + N12**: build debug bez klucza crashuje przy starcie, a release jest niepodpisany i niezgodny z opisem SHA-1 w README.
4. **N8 + N9 + N24 + N25**: brakujące testy czystej logiki: kolejność i filtry nauki (`HARDEST`, `NEW_AND_REVIEW`), scalanie talii, pauza w tle i odporność na uszkodzony JSON. Są tanie i bezpieczne.
5. **N10 + N19 + N20 + N21**: porządek w testach (singleton DataStore, wspólne fixture i reguły, `R.string` zamiast zahardkodowanych tekstów, jeden helper czekania), żeby następne zmiany tekstów i ekranów nie rozwalały testów.

Do nowego zadania naprawczego polecam Claude Sonnet dla N1, N4, N5, N14–N17, N27–N35 (mechaniczne zmiany w XML, tomlu i README) oraz Claude Opus dla N2, N3, N9, N11 (nowe testy sync i migracji wymagają zrozumienia fake'ów chmury).

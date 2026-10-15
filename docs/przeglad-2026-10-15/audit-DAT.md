> Historyczny audyt Claude’a przed końcowymi poprawkami. Aktualny stan: [WDROZENIE.md](WDROZENIE.md).

# Audyt DAT: wdrożenie Codexa (working tree na `39f0690b`)

Ścieżki są względne do `app/src/main/java/com/raycoll/app/`, chyba że podano inaczej. Werdykty opierają się na przeczytanym kodzie. Gradle i testy nie były uruchamiane.

## KRYTYCZNE: ryzyka utraty danych

**Twój scenariusz jest bezpieczny.** Telefon ma `main` z bazą v1 i jedno konto, a dane w chmurze są w starym formacie. Zainstalowanie nowej wersji jako aktualizacji niczego nie kasuje. Uzasadnienie:
- Room v1→v2 to `AutoMigration(1,2)`, które tylko dodaje tabelę `namespace_state` (`data/local/db/RayCollDatabase.kt:18`). Cztery istniejące tabele w `2.json` są identyczne z `1.json`. Hash `1.json` (`4b4f63b9…`) jest ten sam w `main` i od `970762ff`. Nie ma `fallbackToDestructiveMigration` (`di/DatabaseModule.kt:19`), więc zła migracja kończy się crashem, a nie wyczyszczeniem bazy. `DatabaseMigrationTest` buduje bazę z prawdziwego `1.json`: obie przestrzenie, SRS, tombstony, archiwum, tryby i aktywność przechodzą bez zmian.
- Lokalnie po migracji nie ma wiersza → generacja `(0,"")` (`LocalStore.kt:49-55`). W chmurze nie ma pól `replacementAt/Id`, więc `replacementFromMap` też zwraca `(0,"")` (`FirestoreSchema.kt:187-189`). Root zapisany przez `main` i przez Expo (`touchUserRoot`) ma tylko `updatedAt`, więc nic nie koliduje. Równe generacje dają zwykły union merge, identyczny jak w `main` (`MergeEngine.kt:141-148`). Diff wysyła wtedy same `Put`, bez `Delete` dokumentów z chmury.
- Stare dokumenty nie mają `position`, więc `positionsComplete=false` i zostaje kolejność z telefonu (`SyncEngine.kt:93-95`, `LocalStore.kt:84`). Pierwsza wysyłka przepisze raz wszystkie dokumenty talii i kart, żeby dopisać `position`. To tylko koszt czasu i zapisów.

Ryzyka, które istnieją (żadne nie dotyczy samej aktualizacji):

1. **K1 = NEW-DAT-1 (realny błąd, bardzo mało prawdopodobny).**
   - Problem: `MigrationController.kt:27-30` (MIGRATE) kopiuje do konta także generację gościa. Generacja gościa jest > 0, jeśli jako gość zrobiono reset albo przywrócono kopię.
   - Kiedy szkodzi: między załadowaniem pustej chmury a wyborem w dialogu migracji drugie urządzenie wysyła dane na to nowe konto. Wtedy reconcile uzna chmurę za „starą generację”. Dane drugiego urządzenia znikną z chmury, a potem z niego samego.
   - Naprawa (1 linia): `localStore.snapshotOf(GUEST).copy(replacementAt = 0, replacementId = "")`. Migracja gościa to dołączenie danych, nie zastąpienie konta.
2. **K2 (operacyjne, prawdopodobne przy testach).**
   - Po instalacji tej wersji nie da się wrócić do APK z `main` przez aktualizację. Room nie robi downgrade v2→v1 i aplikacja padnie przy starcie. Jedyne wyjście to odinstalowanie, a wtedy przepadają niewysłane zmiany lokalne.
   - Przed instalacją: „Synchronizuj teraz” i eksport kopii JSON.
   - Wariant debug (`.debug`) to osobna aplikacja z własną bazą. Zalogowany na to samo konto zachowuje się jak drugie urządzenie ze starą wersją: reset zrobiony w debug zostanie cofnięty przez release z `main`, ale nic nie zginie.
3. **K3 (świadoma semantyka N2, do akceptacji).** Po resecie albo przywróceniu kopii na urządzeniu A drugie urządzenie (nowa wersja) przy synchronizacji odrzuca cały swój stan sprzed resetu. Dotyczy to także ocen zrobionych offline po resecie, bez żadnego komunikatu. Tak działa „zastąp wszystko”, ale warto o tym wiedzieć.
4. **K4 = NEW-DAT-2 (wyścig; nic nie ginie trwale, reset może się cofnąć).**
   - Problem: każdy commit zapisuje w roocie lokalną generację (`SyncEngine.kt:212-221`). Urządzenie B z generacją 0 mija sprawdzenie świeżości, A w tym czasie publikuje reset (T), a B commituje i nadpisuje znacznik zerem.
   - Skutek: jeśli A już nie wróci, B przy kolejnym starcie zrobi union i reset się cofnie. Jeśli wróci (lokalnie T > 0 w chmurze), A ponownie narzuca reset. Przy trzech urządzeniach A może cofnąć w chmurze świeże zmiany urządzenia C, ale C ma je lokalnie i odtworzy je przy swoim następnym sync.

Uwaga do punktu 5 (kopie JSON): stare kopie są czytelne. Pola znacznika są opcjonalne (`domain/normalize/BackupValidator.kt:326-336`), `StoreData` ma wartości domyślne, a `ignoreUnknownKeys` jest włączone, więc `main` przeczyta też nowe kopie. Wyjątek to kopia z pustą nazwą talii lub trybu (patrz N14). Nic nie ginie, ale takiej kopii nie da się przywrócić.

## Tabela

| ID | Stan | Uzasadnienie / co zrobić (plik:linia) |
|---|---|---|
| DAT-N1 | DONE | `mergeById(preferCloudOrder)` (`domain/merge/MergeEngine.kt:39-50`) najpierw ustawia kolejność z chmury, potem dopisuje lokalne nowości. Flaga `positionsComplete` = wszystkie pozycje różne od null (`SyncEngine.kt:93-95`), czyli brak pola jest odróżniony od duplikatów. Sortowanie `(position, docId)` jest deterministyczne. Przeliczyłem ręcznie scenariusz p/t (sekwencyjny i równoległy): zbiega się, a potem push jest no-op. Testy: `TwoDeviceSyncTest` (różne kolejności + duplikaty, migracja bez pozycji, no-op), `StoreGenerationTest`. Brakuje dosłownego testu „oba urządzenia dopisują po jednej karcie”. |
| DAT-N2 | DONE_WORSE | DECISION wdrożona jako generacja w Room i w roocie konta. Działa dla resetu i importu (test `reset replaces another devices…`). Dwa błędy: NEW-DAT-1 (MIGRATE przenosi generację gościa, `data/sync/MigrationController.kt:27-30`) i NEW-DAT-2 (nadpisywanie znacznika w roocie, `SyncEngine.kt:212-221`). Oba naprawia się kilkoma liniami. |
| DAT-N3 | DONE | Nowy wynik `BootstrapResult.CloudUnavailable` + `reportFailure()` dają status ERROR (`data/sync/BootstrapLoader.kt:58-61`). Gość dalej dostaje `LocalOnly`. Są ponowienia po 2/4/8 s (`SyncLifecycle.kt:155,274-284`). Test: `SyncLifecycleRecoveryTest`. |
| DAT-N4 | DONE | `refreshIfCloudChanged` woła `flush()` także w oknie 30 s i bez merge, poza `exclusive` (`SyncLifecycle.kt:221-233`). Jest test. |
| DAT-N5 | DONE | `signIn` przy błędzie zwraca `SignInError.LOCAL_DATA` (`SyncLifecycle.kt:79-93`). `resolveMigration` zwraca Boolean, a dialog dostaje stan `failed` (`ui/main/MainViewModel.kt:34-45`, `MigrationDialog.kt:32`). `start()` łapie wyjątki. Anulowanie jest przepuszczane. Tekst komunikatu: NEW-DAT-7. |
| DAT-N6 | DONE | `abort()` anuluje także `exclusiveJob` (`SyncScheduler.kt:106-124`). `onSignedOut()` w `finally` działa tylko po potwierdzonym `currentUid()==null` (`SyncLifecycle.kt:113-119`). `AuthRepository.signOut` łapie `Exception` i przepuszcza `CancellationException` (`data/remote/auth/AuthRepository.kt:124-134`). Są testy (5 s, scheduler). |
| DAT-N7 | DONE | `cloudCommitRequired` (`data/sync/SyncDiff.kt:26-27`): pusty diff przy tej samej generacji zwraca true bez odczytu roota i bez przesuwania bazy (`SyncEngine.kt:126`). Test `CloudCommitTest`. |
| DAT-N8 | KEPT | NOT_WORTH. Zgadzam się: aplikacja czyta tylko z SERVER i nie ma pomiaru kosztu. |
| DAT-N9 | DONE | `CloudPositions.kt` i `CloudOperations.kt` wydzielone (internal), bez nowego `FirestoreReader`. `SyncEngine.kt` ma teraz 260 linii. |
| DAT-N10 | DONE | Nowe nazwy `currentUser`/`cloudSync` (też w BootstrapLoader). Pola stanu stoją przed metodami. `purgeExpiredArchives` jest skonsolidowane w `runBootstrap` (`SyncLifecycle.kt:160`), a wywołanie po powrocie do aplikacji zostało. |
| DAT-N11 | DONE | Warunek `AND deletedAt IS NULL` w archive/restore i trzech soft delete (`data/local/db/Daos.kt:35-46,130,159`). Jest test. |
| DAT-N12 | DONE | Strażnik w `editPageMetadata` (`data/repository/DeckRepository.kt:151-155`) i warunki `!= :x` w SQL dla nazwy, trybu, filtra i kolejności (`Daos.kt:54-76`). `swapPages` z `from==to` nic nie robi. Jest test. |
| DAT-N13 | DONE | Jeden `UPDATE` z cutoff (`Daos.kt:93-96`, `DeckRepository.kt:210-216`). Semantyka granicy jest identyczna z `isArchiveExpired`, a sygnał idzie tylko przy wyniku > 0. |
| DAT-N14 | DONE_WORSE | Walidator odrzuca puste `name`/`activeModeId` i pustą nazwę trybu (`domain/normalize/BackupValidator.kt:256,276,290`). Gorzej dla użytkownika, bo jedna pusta nazwa blokuje przywrócenie całej kopii. Expo pozwalało zapisać tryb wbudowany z pustą nazwą (`useStudyModeDraftController.ts`: `isNameValid = isBuiltIn \|\| …`), więc stare kopie z Expo mogą to mieć. Ryzyko niskie. Naprawa: zamiast odrzucać, uzupełniać przy imporcie. Tryb wbudowany dostaje nazwę z seeda, własny „Tryb”, talia nazwę domyślną, a `activeModeId` = `DEFAULT_MODE_ID`. Wtedy `requireString` w chmurze też się nie wywróci. |
| DAT-N15 | DONE | DECISION: `GetSignInWithGoogleOption.Builder(WEB_CLIENT_ID)` (`AuthRepository.kt:85`). Wybór dobry, wymaga sprawdzenia na telefonie (patrz Uwagi). |
| DAT-N16 | DONE | Poprawiony KDoc (`data/remote/FirebaseConfig.kt:8-14`). Bez zmian w konsoli. |
| DAT-N17 | PARTIAL | Zrobione: `StudyViewModel.startSession` czeka na `awaitIdle()` (`ui/study/StudyViewModel.kt:138`). Brakuje trzech poprawek komentarzy: „web” w `data/sync/CloudSync.kt:16`; KDoc `@DefaultDispatcher` mówi tylko o imporcie (`di/AppModule.kt:23`), a używa go też `BackupRepository`; „inne urządzenie nie zapisał” zamiast „nie zapisało” (`SyncEngine.kt:104-105`). |
| DAT-N18 | DONE | `deckFromMap(docId, data)` bez kart (`FirestoreSchema.kt:106`). Testy dostosowane przez `copy`. |
| DAT-N19 | DONE | Wspólne `defaultPageName` (`domain/model/PageNames.kt`) w repozytorium, normalizerze, GroupOperations i VM. |
| DAT-N20 | DONE | Prywatne `loadDeck(ns,id)` (`DeckRepository.kt:68`). `swapPages` używa go w transakcji z jednym `ns`. |
| DAT-N21 | DONE | `CollectionReference.serverDocs()` (`SyncEngine.kt:174`) w czterech miejscach. Timeout został (`awaitCloud`). |
| DAT-N22 | DONE | Funkcja blokowa zwracająca Unit, LAZY i rejestracja przed startem zachowane (`data/repository/ReviewWriteQueue.kt:96-103`). |
| DAT-N23 | DONE | `Log.w(TAG, …, e)` w SyncEngine, CloudOperations, EntityMappers i AuthRepository. W `data/` nie ma już `${e.message}`. |
| DAT-N24 | KEPT | NOT_WORTH. Zgadzam się. |
| DAT-N25 | KEPT | INTENTIONAL. Zgadzam się. |
| DAT-N26 | DONE | Atrapa modeluje `stalePositions` i `positionsComplete` (`TwoDeviceSyncTest.kt` FakeDeviceCloud). Są testy duplikatów, migracji pozycji i no-op, osobno `CloudCommitTest` dla N7. Brak testu `replacementFromMap`: NEW-DAT-3. |

## Nowe błędy

- **NEW-DAT-1 (wysoki wpływ, bardzo rzadki).** `data/sync/MigrationController.kt:27-30`. MIGRATE przenosi `replacementAt/Id` gościa na konto. Scenariusz w K1. Naprawa: wyzerować generację przy kopiowaniu danych gościa. Test: gość po resecie, MIGRATE, drugie urządzenie zapisuje dane w chmurze przed pushem, potem reconcile. Dane drugiego urządzenia muszą zostać.
- **NEW-DAT-2 (średni, wyścig).** `SyncEngine.kt:212-221`. Każdy commit wpisuje do roota lokalną generację, więc może nadpisać nowszą (K4). Naprawa: przekazać `known` do `commit` i dodawać pola `FIELD_REPLACEMENT_*` tylko wtedy, gdy `data.replacementAt/Id != known.data.replacementAt/Id`. Wersja pełna: transakcja na roocie, która nie obniża generacji.
- **NEW-DAT-3 (testy, ważne dla Twojego przypadku).** Ścieżka zgodności ze starą chmurą nie ma testu jednostkowego:
  - `FirestoreSchema.replacementFromMap` (`FirestoreSchema.kt:187-199`): brak pól → `(0,"")`, `(0,"")` → OK, zły typ → wyjątek.
  - Uszkodzony znacznik trwale daje `CloudLoad.Failed` bez komunikatu innego niż ERROR. Zostawić rzucanie (bezpieczne), ale dopisać test.
  - Brakuje też testu „lokalna baza po migracji (brak wiersza) + chmura bez znacznika → union, zero Delete”.
- **NEW-DAT-4 (wydajność, drobne).** `data/repository/LocalStore.kt:96`. `replaceAccount` czyta cały snapshot (wszystkie karty z dekodowaniem JSON) tylko po to, żeby poznać poprzednią generację. Naprawa: `namespaceStateDao.get(ns)?.replacementAt ?: 0`.
- **NEW-DAT-5 (pułapka API).** Niespójne wartości domyślne: `LocalStore.mergeWithCloud(preferCloudOrder = true)` (`LocalStore.kt:84`), `mergeUserData(… = false)` (`MergeEngine.kt:140`) i `CloudLoad.Loaded(positionsComplete = true)` (`SyncEngine.kt:31`). Nowy wywołujący bez argumentu dostanie kolejność chmury także dla starych dokumentów. Naprawa: usunąć te wartości domyślne.
- **NEW-DAT-6 (drobne, współbieżność).** `SyncLifecycle.kt:65,274-284`. `retryJob` to zwykły `var` zmieniany z kilku wątków IO. Sprawdź-i-ustaw może uruchomić dwie pętle ponowień, co nie szkodzi, bo obie są ograniczone. Naprawa: `AtomicReference` albo `@Synchronized`.
- **NEW-DAT-7 (teksty).**
  - `res/values-pl/strings.xml:316` `auth_error_local_data`: „Konto jest zalogowane… Spróbuj zalogować się ponownie”. Użytkownik dalej jest zalogowany i na ekranie ma tylko „Wyloguj”. Lepiej: „Zalogowano, ale nie udało się przygotować danych na tym urządzeniu. Wyloguj się i zaloguj ponownie.” Tak samo w 4 pozostałych językach.
  - Nieudane wylogowanie pokazuje „Nie udało się zapisać zmian” (`ui/appsettings/AppSettingsViewModel.kt:153` → `errors_save_failed`). Potrzebny osobny komunikat.
- **NEW-DAT-8 (komentarz).** `SyncLifecycle.kt:251`: KDoc `reconcileWithCloud` mówi, że false = chmura nieosiągalna. Teraz false oznacza także „wymagana migracja”.

## Uwagi do decyzji

- **DAT-N2 (generacja zamiast tombstonów).** Wybór Codexa jest lepszy od propozycji Claude.
  - Dlaczego: tombstony nie obejmują aktywności (merge przez max), trybów wbudowanych (reguła odżywania) ani identyfikatorów obecnych po obu stronach. Generacja daje prostą i przewidywalną zasadę „nowsze zastąpienie wygrywa w całości”.
  - Warunki: (1) naprawić NEW-DAT-1 i NEW-DAT-2; (2) wszystkie urządzenia na nowej wersji (także wariant debug zalogowany na to samo konto), inaczej reset się cofa, choć nic nie ginie; (3) zaakceptować K3.
  - Komunikaty resetu i przywrócenia kopii mówią już, że zmiana obejmie inne urządzenia (`app_settings_factory_reset_desc`, `app_settings_import_confirm_desc`).
- **DAT-N15 (przycisk Google).** `GetSignInWithGoogleOption` to właściwy przepływ dla jawnego przycisku i działa też bez konta na urządzeniu. Zmienia się wygląd arkusza wyboru konta. Do sprawdzenia na telefonie: logowanie, anulowanie i telefon bez konta Google.
- Znacznik audio (A/B): żadna pozycja DAT go nie dotyczy.
- Porządki poza obszarem: w `app/` leżą nieśledzone zrzuty JVM `hs_err_pid24024.log` i `replay_pid24024.log`. Nie mogą trafić do commita.

# Skan 15.10.2026 — Dane, synchronizacja, DI

Pełna lista agenta (tylko odczyt kodu, gałąź `przeglad-modernizacja`). Zbiorczy raport: [RAPORT.md](RAPORT.md).

## Ocena ogólna

Obszar `data/` i `di/` jest w dobrym stanie. Poprawki z wczoraj są wdrożone zgodnie z werdyktami: R3, R4, R5, R7, R9, R17, R20 (jest `SessionEndSync`), R28–R35, R38, R42, R44–R48. Nie znalazłem starych odwołań do Expo/TS poza jednym słowem "web" (N17). Najsłabsze są trzy rzeczy. Pierwsza to obsługa ponowień i błędów w `SyncLifecycle` po nieudanej sieci. Druga to dwie luki w poprawce R9 (kolejność) oraz brak tombstonów przy imporcie i resecie. Trzecia to rozrost `SyncEngine.kt` (340 linii, cztery tematy w jednym pliku). Nie uruchamiałem testów ani Gradle, więc wszystko wynika z czytania kodu.

## Znaleziska

**N1. [bug] [M] `domain/merge/MergeEngine.kt:42` + `data/sync/SyncDiff.kt:41,50` + `LocalStore.kt:74-78` — poprawka R9 jest niekompletna: kolejność kart i talii potrafi się nie zbiegać między dwoma urządzeniami.**
- Merge układa elementy "najpierw lokalne, potem tylko z chmury". Diff zapisuje `position` na nowo, gdy zmieni się indeks.
- Scenariusz: telefon dopisuje kartę `p`, tablet dopisuje `t` (obie na pozycji 3).
  - Tablet scala do `[a,b,c,t,p]` i wysyła `p=4`.
  - Telefon scala do `[a,b,c,p,t]` i wysyła `p=3, t=4`.
  - Każde urządzenie przy każdym powrocie na pierwszy plan przepisuje pozycje na swoją kolejność, więc nie ma zbieżności.
- Efekt uboczny: każdy powrót jednego urządzenia wymusza pełne przeładowanie na drugim, bo zmienia się znacznik konta.
- Test `TwoDeviceSyncTest` tego nie pokrywa. Są w nim tylko przypadki z jednym piszącym urządzeniem.
- Poprawka: gdy w chmurze każdy dokument ma `position`, merge ma zachować kolejność chmury, a lokalne nowe elementy dopisać na końcu. Lokalną kolejność zostawić tylko dla starych dokumentów bez pola. Do tego `CloudLoad.Loaded` potrzebuje flagi "pozycje kompletne", czyli `stalePositions` puste. Dopisać test dwóch urządzeń z równoległym dopisaniem.
- Wpływ na użytkownika: tak, niewielki. Kolejność kart w trybie SEQUENTIAL różni się między urządzeniami, plus zbędne zapisy i odczyty.

**N2. [bug] [M] `LocalStore.kt:53-68`, `BackupRepository.kt:59,64` — import kopii i reset do ustawień fabrycznych nie zostawiają tombstonów, więc drugie urządzenie "wskrzesza" usunięte dane.**
- `replaceAll` twardo kasuje wiersze. Push usuwa też dokumenty w chmurze.
- Drugie urządzenie ma nieaktualną bazę, więc przy powrocie scala chmurę ze swoimi starymi taliami. Stare talie są dla niego "nowymi lokalnymi" i wracają do chmury, a potem na pierwsze urządzenie.
- Poprawka: dla importu i resetu oznaczać brakujące talie, karty i tryby jako `deletedAt = now` zamiast je gubić. Tombstony już istnieją w formacie, więc nie ma zmiany formatu.
- Wymaga potwierdzenia właściciela, bo zmienia znaczenie "zastąp wszystko".
- Wpływ na użytkownika: tak, przy dwóch urządzeniach reset lub import cofa się po synchronizacji.

**N3. [bug] [S] `BootstrapLoader.kt:46`, `SyncEngine.kt:272-274`, `SyncLifecycle.kt:114-125` — po 8 s timeoutu bootstrapu nie ma ponowienia, a status wraca na IDLE zamiast ERROR.**
- `withTimeoutOrNull` anuluje `load`. `reportingStatus` traktuje to jako anulowanie i ustawia IDLE.
- Wynik to `LocalOnly`. Dane z chmury pojawiają się dopiero po następnym powrocie do aplikacji lub pierwszej edycji, a użytkownik widzi "zsynchronizowano".
- Poprawka: przy `LocalOnly` z zalogowanym użytkownikiem ustawić ERROR albo zaplanować jedną próbę `refreshIfCloudChanged` po kilku sekundach.
- Wpływ na użytkownika: tak, na słabej sieci widać nieaktualne dane bez żadnego sygnału.

**N4. [bug] [S] `SyncLifecycle.kt:171-183`, `SyncScheduler.kt:58-90` — nieudana wysyłka nie jest ponawiana po powrocie do aplikacji, gdy chmura się nie zmieniła.**
- Po porażce `pending=true` zostaje w pamięci. Ponowienie następuje dopiero przy kolejnej edycji lub zejściu w tło.
- `refreshIfCloudChanged` odsyła dane tylko wtedy, gdy zrobił merge.
- Poprawka: na końcu `refreshIfCloudChanged` dodać `else scheduler.flush()`. To jest no-op, gdy nic nie czeka.
- Wpływ na użytkownika: tak, drobny. Status "błąd" i niewysłane oceny zostają do wyjścia z aplikacji.

**N5. [bug] [S] `SyncLifecycle.kt:62-70,127-137` + `AppSettingsViewModel.kt:104-114` + `MainViewModel.kt:29` — wyjątek z `runBootstrap()` lub `migrationController.execute()` kończy się crashem.**
- Handler z R5 działa tylko dla `launch`. Wyjątek z `async` wraca przez `await()` do `viewModelScope.launch` bez handlera i zabija proces. Dotyczy błędów Room, np. pełny dysk.
- Po zalogowaniu użytkownik jest wtedy w Firebase zalogowany, ale w przestrzeni gościa.
- Poprawka: w `inAccountScope` złapać `Exception` (nie `CancellationException`), zalogować i zwrócić porażkę. Dla `signIn` dodać wariant `SignInError`.
- Wpływ na użytkownika: rzadko, ale to crash.

**N6. [bug] [S] `AuthRepository.kt:127-134`, `SyncLifecycle.kt:76-85` — wylogowanie ma dwie słabe strony.**
- `signOut()` łapie tylko `ClearCredentialException`. Inny wyjątek z Credential Managera pomija `onSignedOut()`. Firebase jest wtedy wylogowany, a przestrzeń i baza zostają konta. Push kończy się błędem uprawnień, a naprawia to dopiero restart.
- `scheduler.abort()` anuluje tylko wysyłkę (`running`). Trwający `exclusive` (reconcile z N+3 zapytaniami po 30 s) nadal blokuje wylogowanie, które jest wywołane bez żadnego wskaźnika.
- Poprawka: `try/catch (Exception)` wokół `clearCredentialState` albo `onSignedOut()` w `finally`. Do anulowania reconcile dołożyć `Job` w `exclusive`.
- Wpływ na użytkownika: rzadko. Przy złej sieci "Wyloguj" potrafi wisieć.

**N7. [wydajność] [S] `SyncEngine.kt:114-124` — `save` czyta dokument konta po raz drugi, nawet gdy nic nie zapisał.**
- Bez operacji (`ops` puste) i tak następuje odczyt znacznika (linie 119-121), a to ok. 2 odczyty na każdy pusty push. Dzieje się to po każdym starcie bez zmian, po merge i przy końcu sesji.
- Dodatkowo przyjmuje nieznane zmiany drugiego urządzenia jako "znane" (wąska wersja R1, bez żadnego zapisu).
- Poprawka: `if (ops.isEmpty()) return true`, z zachowaniem `known` jako bazy. R1 po zapisie zostaje bez zmian.
- Wpływ na użytkownika: nie, to koszt odczytów i szybkość.

**N8. [wydajność / modernizacja] [S] `FirebaseModule.kt:17` — Firestore ma domyślny trwały cache na dysku, choć aplikacja czyta wyłącznie `Source.SERVER`.**
- Odczyty z `Source.SERVER` i tak zapisują wynik do cache. Każdy pełny `load` przepisuje całe konto na dysk drugi raz (Room plus cache SDK), a po wylogowaniu dane poprzedniego konta zostają w pliku.
- R12 odrzucił ten pomysł, bo kolejka zapisów w procesie zostaje. Nowy argument to koszt cache i dane po wylogowaniu. Przy okazji znika też kolejka zapisów między restartami.
- Poprawka: `firestoreSettings { setLocalCacheSettings(memoryCacheSettings {}) }` przed pierwszym użyciem.
- Wpływ na użytkownika: nie, to mniej I/O przy ładowaniu.

**N9. [czytelność] [M] `SyncEngine.kt` (340 linii) — jeden plik zawiera cztery tematy.**
- Są tam: klasa silnika, wysyłka partiami, helpery timeoutu i statusu (`cloudOperation`, `reportingStatus`, `uploadInBatches`), odczyt z chmury (`fetchData`, `fetchDeck`) oraz typy pozycji (linie 298-339).
- Poprawka: wydzielić `CloudPositions.kt` (test już nazywa się `CloudPositionsTest`), `CloudOperations.kt` oraz wewnętrzny `FirestoreReader` dla `fetchData`/`fetchDeck`. Samo `SyncEngine` zostaje przy baseline, mutexie i statusie.
- Wpływ na użytkownika: nie.

**N10. [czytelność] [S] `SyncLifecycle.kt:62-85,88-112` — kolejność członków klasy i mieszanie tematów.**
- Metody (`signIn`, `signOut`, `signOutAccount`) stoją przed polami stanu (`bootstrapped`, `lastCloudCheckAt`, `_migrationRequired`, `start()`).
- `purgeExpiredArchives()` (linie 100 i 162) nie dotyczy synchronizacji, a jest powodem zależności od `DeckRepository`.
- Nazwy `authRepository: CurrentUser` (linia 46, tak samo w `BootstrapLoader.kt:33`) i `syncEngine: CloudSync` sugerują konkretne klasy, a to porty.
- Poprawka: kolejność stan → start → konto → odświeżanie → prywatne; zmiana nazw na `currentUser` i `cloud`; czyszczenie archiwów do osobnego prostego wywołania w `RayCollApplication`. To nie jest podział klasy, więc nie łamie werdyktu R19.
- Wpływ na użytkownika: nie.

**N11. [bug] [XS] `Daos.kt:35-42,121-122,150-151` — `archiveDeck`, `restoreDeck`, `softDeleteDeck/Card/Mode` nie mają `AND deletedAt IS NULL`.**
- Pozostałe `UPDATE`-y w tym pliku to mają.
- `restoreDeck` i `archiveDeck` na usuniętej talii podbijają `updatedAt`, co przy merge działa jak "edycja nowsza niż usunięcie" i wskrzesza talię.
- Powtórne `softDelete*` przesuwa `deletedAt` na późniejszy moment.
- Poprawka: dodać warunek do tych pięciu zapytań.
- Wpływ na użytkownika: w praktyce tylko przy wyścigu z synchronizacją.

**N12. [bug] [XS] `DeckRepository.kt:118-127,143-158` — edycja bez zmiany (R17 PARTIAL) nadal podbija `updatedAt` i wysyła do chmury.**
- Pole nazwy zapisuje się przy każdym znaku (`PersistedTextField.kt:44-48`), więc np. dopisanie spacji (po `trim()` wartość bez zmian) kończy się zapisem i `signal`.
- Taki `updatedAt` przy LWW (wygrywa nowszy czas edycji) wygrywa z prawdziwą zmianą z drugiego urządzenia.
- Poprawka: w `editPageMetadata` zwracać 0, gdy `edited == deck`. To samo w `updateDeck` dla `renameDeck`, np. warunek `WHERE name != :name`.
- Wpływ na użytkownika: nie widać, ale może cicho przegrać prawdziwa edycja.

**N13. [modernizacja] [S] `DeckRepository.kt:203-210` — `purgeExpiredArchives` wczytuje wszystkie talie, dekoduje ich JSON-y i dopiero potem zapisuje.**
- Odczyt i zapis są osobno, więc `restoreDeck` w międzyczasie jest przez purge od razu kasowany. Okno jest mikroskopijne.
- Poprawka: jedno `UPDATE decks SET deletedAt = :now WHERE namespace = :ns AND deletedAt IS NULL AND archivedAt IS NOT NULL AND archivedAt <= :cutoff` zwracające `Int`. Sygnał tylko przy wyniku > 0.
- Wpływ na użytkownika: nie.

**N14. [bug] [S] `BackupValidator.kt:255,271,285` vs `FirestoreSchema.kt:112,114,172` — walidacja kopii jest luźniejsza niż parser Firestore.**
- Kopia z talią o pustej `name` lub `activeModeId` (albo trybem z pustą `name`) przechodzi walidację, ale `requireString` odrzuca taki dokument.
- Na każdym `load` talia z kartami jest pomijana. Na każdym push baza jej nie zna, więc cała talia leci do chmury od nowa.
- Poprawka: w walidatorze kopii wymagać niepustych napisów dla tych pól (lub naprawiać wartość przy imporcie).
- Wpływ na użytkownika: tylko dla ręcznie edytowanej lub bardzo starej kopii.

**N15. [modernizacja] [M] `AuthRepository.kt:83-92` — `GetGoogleIdOption` zamiast `GetSignInWithGoogleOption`.**
- Dla przycisku "Zaloguj przez Google" dokumentacja Credential Managera zaleca `GetSignInWithGoogleOption`. Dalej działa ten sam token do Firebase.
- Zmiana UI: inny arkusz wyboru konta.
- Poprawka: opcjonalna, do decyzji właściciela.
- Wpływ na użytkownika: tak (wygląd okna logowania).

**N16. [modernizacja] [S] `FirebaseConfig.kt:8-14,19` — ręczna inicjalizacja na konfiguracji aplikacji "web".**
- `APP_ID` ma postać `1:…:web:…` i jest to konfiguracja z dawnego klienta webowego. Komentarz opisuje hipotetyczny `google-services.json`.
- Poprawka: zarejestrować aplikację Android w Firebase i użyć wtyczki google-services. Przynajmniej przeredagować KDoc, żeby mówił, co jest, a nie co "kiedyś może być".
- Wpływ na użytkownika: nie. Wymaga czynności w konsoli Firebase po stronie właściciela.

**N17. [czytelność] [XS] Nieaktualne lub niedokładne komentarze.**
- `CloudSync.kt:16` — "(inne urządzenie, web)". Jedynym klientem jest Android, usunąć "web".
- `AppModule.kt:23` — KDoc `@DefaultDispatcher` mówi tylko o parsowaniu importu, a używa go też `BackupRepository`.
- `SyncEngine.kt:96-99` — zdanie "inne urządzenie nie zapisał niczego…" jest niegramatyczne.
- `ReviewWriteQueue.kt:33-37` — KDoc obiecuje czekanie "przed ponowną sesją", a `StudyViewModel.startSession` nie woła `awaitIdle()`. Poprawić tekst albo dodać wywołanie (UI).
- Wpływ na użytkownika: nie.

**N18. [martwy-kod] [XS] `FirestoreSchema.kt:104` — parametr `cards` w `deckFromMap`.**
- Produkcja zawsze przekazuje `emptyList()` (`SyncEngine.kt:176`), a karty dokleja `fetchData`. Parametr istnieje tylko dla testów (`FirestoreSchemaTest:127,135`).
- Poprawka: usunąć parametr i dostosować testy (`copy(cards = …)`).
- Wpływ na użytkownika: nie.

**N19. [martwy-kod] [XS] `DeckRepository.kt:129` — domyślna lambda `"Page ${it + 1}"` w `setActivePageCount`.**
- Jedyny wywołujący zawsze podaje własną (`DeckSettingsScreen.kt:157`). Ta sama lambda jest zdublowana w `DeckSettingsViewModel.kt:95`, `GroupOperations.kt:31` i `DataNormalizer.kt:44`.
- Poprawka: w repozytorium zrobić parametr obowiązkowy.
- Wpływ na użytkownika: nie.

**N20. [czytelność] [XS] `DeckRepository.kt:65-69` i `169-170` — ten sam fragment "talia z kartami z namespace".**
- Dwa razy `deckDao.getLiveDeck(ns, id)?.toDomain(cardDao.getLiveCardsOf(ns, id))`.
- Poprawka: prywatna `loadDeck(ns, deckId)`.
- Wpływ na użytkownika: nie.

**N21. [czytelność] [XS] `SyncEngine.kt:133,140,145,166-168` — cztery razy `collection(..).get(Source.SERVER).awaitCloud().documents`.**
- Poprawka: prywatne `suspend fun CollectionReference.serverDocs()`.
- Wpływ na użytkownika: nie.

**N22. [czytelność] [XS] `ReviewWriteQueue.kt:96-101` — `ensureWorker()` z wyrażeniem zamiast bloku.**
- `return@synchronized` miesza `Unit` z wynikiem `worker?.start()`, więc zwracany typ to `Any?`.
- Poprawka: blok `{ synchronized(queue) { … } }` z jawnym `Unit`.
- Wpływ na użytkownika: nie.

**N23. [czytelność] [XS] `SyncEngine.kt:108,184,276`, `EntityMappers.kt:36` — logi z `${e.message}` zamiast wyjątku.**
- Tracimy klasę i kod błędu (np. `FirebaseFirestoreException.code`). `AuthRepository` robi to już poprawnie.
- Poprawka: `Log.w(TAG, "Cloud sync failed", e)`.
- Wpływ na użytkownika: nie, ale ułatwia diagnozę z logcata.

**N24. [czytelność] [XS] `BackupRepository.kt:64` i `LocalStore.kt:84-85` — pozostałe drobne uporządkowania.**
- `factoryReset` nie jest operacją kopii zapasowej, a siedzi w `BackupRepository`.
- `LocalStore.seedData` to tylko cienki wrapper na `SeedData.createSeedData`.
- Poprawka: przenieść `factoryReset` do `LocalStore` i wołać `SeedData` bezpośrednio, albo zostawić.
- Wpływ na użytkownika: nie.

**N25. [wydajność] [L, niski priorytet] `SyncLifecycle.kt:194` — `localStore.snapshot()` przy każdej wysyłce czyta całą bazę.**
- Bierze wszystkie karty i dekoduje ich JSON. Dzieje się to co 10 ocen i na końcu sesji.
- Przy kilku tysiącach kart to dziesiątki do setek ms w tle, więc użytkownik tego nie odczuje. Rozwiązanie wymagałoby śledzenia zmienionych id.
- Poprawka: na razie nic. Odnotowuję, żeby nie wróciło jako "odkrycie".
- Wpływ na użytkownika: nie.

**N26. [testy] [M] `TwoDeviceSyncTest.kt:130` — atrapa chmury omija część prawdziwego `SyncEngine`.**
- `FakeDeviceCloud.save` woła `diffSave` bez `stalePositions`, a `load` nie liczy pozycji. Ścieżka "pierwszy zapis po aktualizacji przepisuje pozycje raz" (R9) nie jest sprawdzana od początku do końca, tylko na poziomie helperów w `CloudPositionsTest`.
- `SyncEngine.load/save/fetchData` nie mają żadnego testu na kodzie produkcyjnym.
- Poprawka: dodać do atrapy `stalePositionsOf` na podstawie tych samych funkcji pomocniczych i test z N1.
- Wpływ na użytkownika: nie.

## TOP 5

1. **N1** — kolejność kart i talii się nie zbiega i powoduje zbędne przeładowania. Zamyka R9, który ma wadę przy równoległym dopisywaniu.
2. **N2** — import i reset nie zostawiają tombstonów, więc dane wracają przez drugie urządzenie (wymaga decyzji właściciela).
3. **N3 + N4** — ponowienia po nieudanej sieci: timeout bootstrapu i niewysłane zmiany po powrocie do aplikacji.
4. **N5 + N6** — crash przez `await()` po błędzie bootstrapu oraz wylogowanie, które pomija `onSignedOut()` lub wisi za reconcile.
5. **N9 + N10** — rozbicie `SyncEngine.kt` i uporządkowanie `SyncLifecycle`. To największy zysk czytelności bez ruszania zachowania.

Do wdrożenia: N7, N11, N12, N13, N17–N24 to zadania XS/S (Sonnet). N1 i N2 zmieniają zachowanie i dotykają kilku warstw (Opus). N2 wymaga najpierw potwierdzenia właściciela.

# Plan: Firestore jako jedyne źródło danych

Gałąź `przeglad-modernizacja`, stan wyjściowy: commit `25ec6c1c`. Data: 2026-10-15.
Status: **plan, wersja 4, nic nie wdrożone, gotowy do wdrożenia.** Decyzje są w sekcji 13,
dokładne umowy dla wykonawcy (interfejsy, okno logowania, atrapa testowa, pełne reguły, lista
„nie wdrażać”) w sekcji 14.
Wszystko, co nie dotyczy Firestore (Java 21, Android 13, biblioteki, tryb eksperta, sprzątanie),
jest w osobnym planie `docs/PLAN_PORZADKI.md`.

Gdzie są Twoje punkty: 1 (pliki i linie) → §8, 2 (odczyty, zapisy, konflikty) → §4,
3 (koszt) → §5, 4 (konto anonimowe) → §6, 5 (kopia, import, reset) → §7, 6 (testy) → §9,
7 (ryzyka) → §10.

## 0. W skrócie

- Zostajemy przy Firebase. Room i cała własna synchronizacja znikają. Talie, karty, tryby
  i aktywność czytamy i zapisujemy wyłącznie przez Firestore z jego wbudowaną pamięcią
  offline. Poza chmurą zostają tylko ustawienia urządzenia i one już są w DataStore.
- **Synchronizacja to zwykłe nasłuchy Firestore**: jeden na kolekcję, wspólny dla wszystkich
  ekranów. Bez własnego mechanizmu, **bez zmiany formatu w chmurze**. Przy 1000 fiszek to
  około 7 tys. odczytów dziennie, czyli 15% darmowego limitu.
- Zapisy przez `update()` konkretnych pól, bez czekania na serwer. Firestore od razu zmienia dane
  na telefonie i sam wysyła zapis, gdy jest sieć.
- **Wygrywa nowsza zmiana, a nie później wysłana.** Pilnują tego reguły serwera, porównując
  daty, które aplikacja już dziś zapisuje. Telefon, który rok leżał w szufladzie, nie nadpisze
  nowszych danych i nie skasuje karty, której się od tamtej pory uczysz. Gdy serwer odrzuci
  starsze zmiany, aplikacja o tym informuje, o ile nie była w międzyczasie zamknięta (4.4).
- **Stara wersja aplikacji nie może niczego zapisać** (reguły), więc zapomniany telefon nic
  nie zepsuje.
- **Usunięte rzeczy** mają datę usunięcia (`deletedAt`) i po 30 dniach znikają z chmury
  całkowicie.
- **Tryb bez konta** to konto anonimowe Firebase. Przy logowaniu Google, gdy gość ma własne
  dane, okno pyta: przenieś na konto, zostaw na telefonie, usuń albo nie loguj. Co dokładnie
  dzieje się z danymi w chmurze i na telefonie, opisuje tabela w 6.2. Porzucone dane gościa
  są kasowane z chmury.
- Znika ok. 2,3 tys. linii kodu aplikacji. Import, eksport i reset dostają nowe testy.
- Porządki niezwiązane z Firestore są w osobnym planie `docs/PLAN_PORZADKI.md`. Jego etapy 1–2
  (Java 21, Android 13) idą przed tym planem.

## 1. Stan obecny w liczbach

| Obszar | Pliki | Linie |
|---|---|---|
| Własna synchronizacja (`data/sync`) | 11 | 1 156 |
| Room (`data/local/db`, mapery) | 4 | 439 |
| Magazyn lokalny, przestrzenie, kolejka ocen, sygnały, `LegacyBackup` | 5 | 384 |
| Scalanie (`MergeEngine`) i walidacja starych kopii (`BackupValidator`) | 2 | 496 |
| Cały kod aplikacji (`src/main/java`) | — | 17 105 |
| Testy (`src/test/java`), 643 przypadki | — | 13 292 |

Dzisiejszy przepływ: zapis do Room → sygnał `DataMutations` → `SyncScheduler` (opóźnienie,
co 10 ocen, natychmiast) → `SyncEngine` liczy różnice względem „bazy” (`SyncDiff`) → transakcje
porcjami po 400 z kontrolą `syncToken` i generacji (`replacementAt/Id`) → przy cudzej zmianie
pełny odczyt chmury i scalenie (`MergeEngine`). Usuwanie przez tombstony w Room.

## 2. Architektura docelowa

```
Ekrany / ViewModele
      │  Flow (bez zmian w API repozytoriów)
DeckRepository · StudyModeRepository · BackupRepository
      │  odczyt: wspólne nasłuchy Firestore (jeden na kolekcję)
      │  zapis: update() / set() / WriteBatch, bez czekania na serwer
DocumentStore (interfejs) ── FirestoreDocumentStore (produkcja)
      │                     └ InMemoryDocumentStore (testy)
Firestore: pamięć na telefonie + automatyczna synchronizacja
AccountManager ── konto gościa, logowanie Google, okno wyboru danych, wylogowanie
firestore.rules ── właściciel, „wygrywa nowsza zmiana”, blokada starej wersji, sprzątanie
```

- **`DocumentStore`** (nowy, ok. 130 linii): wąski interfejs `observe(kolekcja)`, `cached(kolekcja)`,
  `set`, `update`, `increment`, `delete`, `batch`. Produkcja na Firestore, w testach
  implementacja w pamięci. Dzięki temu ok. 20 plików testów ekranów i nowe testy kopii działają
  bez Firestore. Każdy zapis dostaje obsługę odrzucenia przez serwer (4.4).
- **Repozytoria** zachowują obecne publiczne API (ekrany się nie zmieniają). Nasłuchy są
  współdzielone (`shareIn` w zakresie aplikacji, odłączenie 5 s po zniknięciu ostatniego
  ekranu), więc kilka ekranów to nadal jeden nasłuch. Strumień „aktywnego konta” to `uid`
  z Firebase Auth, a nie `NamespaceManager`.
- **`AccountManager`** (nowy) zastępuje `SyncLifecycle`, `BootstrapLoader`, `MigrationController`
  i `NamespaceManager`.
- Pamięć Firestore na telefonie: `PersistentCacheSettings` z `CACHE_SIZE_UNLIMITED` (1000 kart
  to około 1 MB).

## 3. Format dokumentów w chmurze

**Bez zmian.** Nowa wersja czyta i zapisuje dzisiejsze dokumenty:
`users/{uid}/decks/{id}`, `…/decks/{id}/cards/{id}` (zagnieżdżony `srsState`), `…/studyModes/{id}`
(strukturalne `steps`, także pole `version` kroku złożonego), `…/activity/{dzień}` (`count`,
`updatedAt` z serwera). Pola `deletedAt`, `archivedAt`, `position` nadal opcjonalne. Klucze
wartości (`new+review`, `fromAnswer` itd.) bez zmian. Nie dochodzi żadne nowe pole.

**Przestajemy zapisywać** pola maszynerii, która znika: `replacementAt`, `replacementId`
w dokumentach oraz cokolwiek w `users/{uid}` (`syncToken`, `updatedAt`, generacja). Stare
wartości zostają w chmurze i są ignorowane.

**Zmiana znaczenia istniejącego pola:** `updatedAt` talii podbijamy także przy nauce (raz na
sesję), nie tylko przy edycji. Dzięki temu stare usunięcie talii, zrobione offline, przegrywa
z późniejszą nauką tej talii (4.3).

**Usuwanie:** talia, karta i tryb dostają `deletedAt` (pole, które format już ma) z datą
usunięcia. Data jest potrzebna regule „nowsza wygrywa”, bo zwykłe skasowanie dokumentu nie niesie
daty. Po 30 dniach usunięte dokumenty są kasowane z chmury całkowicie (4.5). Znika cała obsługa
tombstonów po stronie aplikacji: kopie w Room, reguły ożywiania, scalanie.

## 4. Odczyty, zapisy i konflikty

### 4.1 Odczyty jako Flow

- `callbackFlow` wokół `addSnapshotListener`, współdzielony przez `shareIn`. Firestore najpierw
  podaje dane z pamięci telefonu (od razu, także offline), a potem z serwera. Zmiany z innego
  urządzenia przychodzą same.
- `activeDecks()` = nasłuch `decks` + po jednym nasłuchu kart na talię (`combine`). Karty
  usuniętych talii pomijane. Kolejność: `position`, a przy braku pola na końcu wg id (jedna linia
  sortowania zamiast `CloudPositions`).
- Odczyt jednorazowy (np. karta przed oceną): `get(Source.CACHE)`.
- Nowe urządzenie z kontem Google: dopóki pierwsza odpowiedź serwera nie przyjdzie, a pamięć
  jest pusta, ekran główny pokazuje „Pobieranie danych konta…”. Bez sieci: „Dane konta pojawią
  się po połączeniu”.

### 4.2 Zapisy

| Operacja | Zapis do Firestore |
|---|---|
| Zmiana nazwy, filtra, kolejności, trybu talii | `update(talia, pole, updatedAt)` |
| Nazwy, języki, liczba stron | `update(talia, pageNames, pageLanguages, activePageCount, updatedAt)` |
| Zamiana stron | `WriteBatch`: talia + `update(pages, contentUpdatedAt)` każdej karty, porcje po 400 |
| Nowa karta | `set(karta)` z `position` = ostatnia + 1 |
| Edycja karty | `update(pages, contentUpdatedAt)`; strony ukryte zostają jak dziś |
| Ocena | `update(srsState, srsUpdatedAt)` + `activity/{dzień}`: `set(count = increment(1), updatedAt = serverTimestamp(), merge)` |
| Start sesji nauki (są karty do nauki) | `update(talia, updatedAt)` przez `markStudied` (1 zapis na sesję, 14.3) |
| Usunięcie talii lub trybu | `update(deletedAt, updatedAt)` |
| Usunięcie karty | `update(deletedAt)` (karta nie ma `updatedAt`) |
| Archiwizacja i przywrócenie | `update(archivedAt, updatedAt)` |
| Wygaśnięcie archiwum (14 dni) | po pierwszej odpowiedzi serwera w danej sesji (czyli na aktualnych danych): `update(deletedAt, updatedAt)` |
| Import CSV | `WriteBatch`: talia + karty, porcje po 400 |

Zasady:
- **Nigdy nie czekamy na `Task` zapisu.** Offline kończy się dopiero po połączeniu, więc
  czekanie zawiesiłoby ekran.
- Kolejne operacje Firestore wykonuje po kolei, więc odczyt z pamięci po zapisie widzi ten zapis.
  Dlatego znikają `ReviewWriteQueue`, `awaitIdle`, `ReviewSaveBlocked` i baner błędu zapisu oceny.
- Ponowna ocena tej samej karty (stan sprzed pierwszej) i „bez drugiego wpisu w aktywności”
  działają jak dziś. Oceny jednej karty idą przez `Mutex`, żeby dwie szybkie oceny nie liczyły się
  od tego samego stanu.
- „Synchronizuj teraz” = `waitForPendingWrites()` z limitem 15 s. Wynik pokazuje komunikat:
  „wszystko wysłane” albo „zmiany czekają na internet”.

### 4.3 Konflikty: wygrywa nowsza zmiana

Domyślnie w Firestore wygrywa zapis, który dotarł na serwer ostatni. Tego nie chcesz, więc
reguły serwera przyjmują zmianę tylko wtedy, gdy jej data (z zegara telefonu, który ją zrobił)
nie jest starsza od zapisanej. Daty te aplikacja już dziś zapisuje, w samej aplikacji nie ma do
tego kodu.

| Dokument | Data zmiany | Zasada |
|---|---|---|
| Talia | `updatedAt` (edycja, archiwizacja, usunięcie, **nauka raz na sesję**) | zmiana przyjęta, gdy `updatedAt` ≥ zapisanego |
| Tryb | `updatedAt` | jw. |
| Karta: ocena | `srsUpdatedAt` | ocena przyjęta, gdy `srsUpdatedAt` ≥ zapisanego |
| Karta: treść | `contentUpdatedAt` | treść przyjęta, gdy `contentUpdatedAt` ≥ zapisanego |
| Karta: usunięcie | `deletedAt` | przyjęte, gdy `deletedAt` ≥ obu dat karty |
| Aktywność dnia | — | `increment`, zawsze się sumuje (powtórki naprawdę się odbyły) |

Dodatkowo:
- **Usunięte zostaje usunięte.** Zwykła edycja usuniętej rzeczy jest odrzucana. Serwer wpuszcza
  tylko zapis całego dokumentu bez `deletedAt` z nowszą datą. Robi tak wyłącznie import kopii
  (sekcja 7). Reguła tego nie odróżnia, to umowa w kodzie aplikacji.
- **Data z przyszłości** (ponad 10 minut po czasie serwera) jest odrzucana. Telefon z popsutym
  zegarem nie zablokuje karty na zawsze.
- Talia i tryb mają jedną datę na cały dokument. Starsza zmiana jednego pola (np. filtr ustawiony
  offline) przegra z nowszą zmianą innego pola (np. nazwy). Karta ma osobno ocenę i treść.
- Ocena odrzucona przez serwer nadal liczy się w heatmapie (zapis aktywności jest osobny i zawsze
  przyjmowany). To świadoma decyzja: powtórka się odbyła, przegrał tylko jej wynik.

Przykłady:
- Stary telefon rok temu offline ocenił „dom”, nowy telefon powtarzał „dom” przez rok. Po
  połączeniu serwer odrzuca ocenę sprzed roku, a stary telefon sam pobiera aktualny stan.
- Rok temu offline usunięta karta, której potem się uczyłeś: usunięcie jest starsze niż ostatnia
  ocena, więc zostaje odrzucone i karta żyje na obu telefonach.
- To samo dla talii: nauka podbiła `updatedAt` talii, więc stare usunięcie przegrywa.

Szkic reguł (ok. 70 linii zamiast dzisiejszych 9):

```
function owner(uid) { return request.auth != null && request.auth.uid == uid; }
function guest() { return request.auth.token.firebase.sign_in_provider == 'anonymous'; }
function nowMs() { return request.time.toMillis(); }
function notFuture(f) { return request.resource.data.get(f, 0) <= nowMs() + 600000; }
function notOlder(f) { return request.resource.data.get(f, 0) >= resource.data.get(f, 0); }
function changed(keys) { return request.resource.data.diff(resource.data).affectedKeys().hasAny(keys); }
function alive() { return resource.data.get('deletedAt', null) == null; }
function restoring() { return request.resource.data.get('deletedAt', null) == null; }
function oldTombstone(d) { return d.get('deletedAt', null) != null && d.deletedAt < nowMs() - 2592000000; }

// stara wersja aplikacji przy każdej wysyłce zapisuje ten dokument → cała jej transakcja odrzucona
match /users/{uid} { allow read: if owner(uid); allow write: if false; }

match /users/{uid}/decks/{deckId} {
  allow read: if owner(uid);
  allow create: if owner(uid) && notFuture('updatedAt');
  allow update: if owner(uid) && notFuture('updatedAt') && notOlder('updatedAt') && (alive() || restoring());
  allow delete: if owner(uid) && (guest() || oldTombstone(resource.data));

  match /cards/{cardId} {
    allow read: if owner(uid);
    allow create: if owner(uid) && notFuture('srsUpdatedAt') && notFuture('contentUpdatedAt');
    allow update: if owner(uid) && notFuture('srsUpdatedAt') && notFuture('contentUpdatedAt')
      && (alive() || restoring())
      && (!changed(['srsState', 'srsUpdatedAt']) || notOlder('srsUpdatedAt'))
      && (!changed(['pages', 'contentUpdatedAt']) || notOlder('contentUpdatedAt'))
      && (!changed(['deletedAt']) || restoring()
          || (request.resource.data.deletedAt >= resource.data.srsUpdatedAt
              && request.resource.data.deletedAt >= resource.data.contentUpdatedAt));
    allow delete: if owner(uid) && (guest() || oldTombstone(resource.data)
      || oldTombstone(get(/databases/$(database)/documents/users/$(uid)/decks/$(deckId)).data));
  }
}
// studyModes: jak decks. activity: zapis gdy owner && updatedAt == request.time;
// kasowanie tylko owner && guest().
```

### 4.4 Komunikat o odrzuconych zmianach

`DocumentStore` przy każdym zapisie dopina obsługę błędu (bez czekania na wynik). Odrzucenie
przez serwer (`PERMISSION_DENIED`) zbiera się w liczniku. Ekran pokazuje komunikat
(snackbar): „3 zmiany z tego telefonu były starsze niż dane w chmurze i nie zostały zapisane”.
Firestore sam cofa takie zmiany na telefonie, a w logach jest szczegół, co odrzucono.

Ograniczenie Firestore: o odrzuceniu dowiemy się tylko wtedy, gdy aplikacja nie była zamknięta
między zrobieniem zmiany a jej wysłaniem. Zmiany wysłane po ponownym uruchomieniu (typowy stary
telefon) są odrzucane po cichu. Wiedzieć o nich zawsze dałoby się tylko z własną kolejką zapisów,
którą właśnie usuwamy.

### 4.5 Sprzątanie usuniętych po 30 dniach

Przy starcie, po pierwszej odpowiedzi serwera, aplikacja kasuje z chmury dokumenty z `deletedAt`
starszym niż 30 dni. Dla usuniętych talii kasuje najpierw karty, potem samą talię. Reguła pozwala
kasować tylko takie stare tombstony. Kartę usuniętej talii rozpoznaje po talii nadrzędnej
(1 dodatkowy odczyt na kartę, jednorazowo przy sprzątaniu).

Stary telefon jest bezpieczny: jego zaległa edycja skasowanego dokumentu zostanie odrzucona
(dokumentu już nie ma), a nasłuch usunie go z pamięci telefonu.

## 5. Koszt

Nasłuch z serwera kosztuje 1 odczyt za każdy dokument w wyniku. Po przerwie dłuższej niż
30 minut ponowne podpięcie liczy się jak nowe zapytanie, czyli pełny odczyt kolekcji. Każde
zapytanie kosztuje co najmniej 1 odczyt. Darmowy limit projektu: 50 000 odczytów, 20 000 zapisów
i 20 000 usunięć dziennie. Na darmowym planie przekroczenie nie kosztuje, tylko blokuje
synchronizację do rana (ok. 9:00 polskiego czasu). Aplikacja w tym czasie działa offline.

Założenia: 1000 kart w 15 taliach, 6 trybów, do 365 dni aktywności, jedno urządzenie, dziennie:
150 ocen w 3 sesjach, 10 edycji, 5 otwarć aplikacji po przerwie dłuższej niż 30 minut.

| Pozycja dziennie | Liczba |
|---|---|
| Zapisy: oceny (karta + licznik dnia) | 300 |
| Zapisy: podbicie talii raz na sesję + edycje | 13 |
| **Zapisy razem** | **≈ 315 (1,6% limitu)** |
| Odczyty: pełne pobranie po przerwie (1000 + 15 + 6 + 365 ≈ 1 390) × 5 | ≈ 6 950 |
| Odczyty: własne zapisy wracające przez nasłuch | ≈ 315 |
| **Odczyty razem** | **≈ 7 300 (15% limitu)** |

Inne przypadki:
- dwa urządzenia: ≈ 14 600 odczytów (29%);
- najgorszy zwykły dzień, 10 otwarć po przerwach: ≈ 14 000 na urządzenie;
- nowe urządzenie albo reinstalacja: jednorazowo ≈ 1 400;
- import kopii 1000 kart: ≈ 1 400 zapisów (karty, talie, tryby, dni aktywności);
- reset: ≈ 400 zapisów;
- dni aktywności przybywa ok. 365 rocznie, więc za kilka lat pełne pobranie urośnie do ok. 2,5 tys.
  Wtedy można ograniczyć nasłuch aktywności do ostatniego roku.

**Na później, jeśli koszt okaże się za wysoki:** pobieranie tylko zmian (pole `syncedAt` z czasem
serwera i zapytania „zmienione po”). Wymaga zmiany formatu, więc wraca tylko po Twojej zgodzie.
Pierwsze 2–3 dni po wdrożeniu sprawdzamy zużycie w konsoli Firebase.

## 6. Konto gościa i logowanie

### 6.1 Start aplikacji

1. **Jest zalogowany użytkownik** (gość albo Google), więc pracujemy na nim. Firebase Auth
   pamięta go między uruchomieniami. Wyjątek: zalogowane konto Google i czekający
   `pending_join.json` dla tego konta (aplikacja zabita w środku „Przenieś”). Wtedy najpierw
   dokończ import.
2. **Nie ma użytkownika**, więc zakładamy konto gościa (`signInAnonymously()`, **wymaga
   internetu**, jednorazowo). Nowy gość dostaje, w tej kolejności:
   - dane z `pending_join.json` (przerwane „Przenieś”, zanim konto Google się zalogowało);
   - dane z `guest_stash.json` (odłożone przez „Zostaw”);
   - jeśli żadnego pliku nie ma, dwie talie przykładowe i tryby wbudowane.

   Ta sama funkcja działa przy wylogowaniu (6.3).
3. **Pierwsze uruchomienie bez internetu:** ekran „Połącz się z internetem, żeby zacząć”
   z przyciskiem „Spróbuj ponownie”.

W Firebase Console trzeba włączyć dostawcę „Anonymous” (krok ręczny).

### 6.2 Logowanie: co dzieje się z danymi gościa

**Gdzie są dane gościa przed logowaniem:** w chmurze, na jego koncie anonimowym (Firebase) i na
telefonie (pamięć Firestore). Do konta anonimowego da się dostać tylko z tego telefonu, a po
zalogowaniu na Google już wcale (nie ma do niego hasła). Dlatego wszystko, co dotyczy danych
gościa, dzieje się **przed** przełączeniem konta.

**Gość ma własne dane?** Liczą się talie, które edytował albo których się uczył, aktywność
i własne tryby. Same nieruszone talie przykładowe się nie liczą (`getGuestHasData`, jak dziś).
Jeśli ma, przed logowaniem pokazuje się okno (`MigrationDialog`), tak samo dla konta Google
nowego i używanego wcześniej.

| Sytuacja | Konto Google w chmurze po zalogowaniu | Dane gościa w chmurze | Na telefonie po zalogowaniu | Po wylogowaniu |
|---|---|---|---|---|
| **Bez okna** (gość bez własnych danych), konto nowe | talie przykładowe gościa | to to samo konto (`link`), nic nie zostaje osobno | dane konta | nowy gość z taliami przykładowymi |
| **Bez okna**, konto używane | bez zmian | skasowane | dane konta (pobrane) | nowy gość z taliami przykładowymi |
| **Przenieś na konto**, konto nowe | wszystkie dane gościa | to to samo konto (`link`), nic nie zostaje osobno | te same dane co przed logowaniem | nowy gość z taliami przykładowymi |
| **Przenieś na konto**, konto używane | dane konta + talie, karty (ze stanem nauki) i własne tryby gościa + aktywność gościa dodana do dni konta | skasowane | dane konta z dołączonymi | nowy gość z taliami przykładowymi |
| **Zostaw na telefonie**, konto nowe | talie przykładowe | skasowane | dane konta; dane gościa schowane w pliku na telefonie (niewidoczne) | wracają dane gościa z pliku |
| **Zostaw na telefonie**, konto używane | bez zmian | skasowane | jw. | wracają dane gościa z pliku |
| **Usuń**, konto nowe | talie przykładowe | skasowane | dane konta | nowy gość z taliami przykładowymi |
| **Usuń**, konto używane | bez zmian | skasowane | dane konta | nowy gość z taliami przykładowymi |
| **Nie loguj** | — | bez zmian | bez zmian, nadal gość | — |

Szczegóły „Przenieś na konto” (konto używane):
- **nie przechodzą** nieruszone talie przykładowe gościa (konto nie dostaje duplikatów) i tryby
  wbudowane gościa (zostają te z konta);
- **tryb wbudowany zmieniony przez gościa** (decyzja A; zmieniony = inna nazwa albo kroki niż
  fabryczne, istniejące `isModifiedBuiltIn()`) przechodzi jako osobny tryb własny
  „<nazwa> (z telefonu)” z nowym id. Talie gościa, które go używały, dostają `activeModeId` tej
  kopii. Dopisek „(z telefonu)” jest tłumaczony na 5 języków. Niezmienione tryby wbudowane gościa
  nie przechodzą;
- **talia gościa o tej samej nazwie co talia konta** (decyzja B) przechodzi jako osobna talia,
  bez łączenia kart. Łączenie wymagałoby zgadywania, które karty są „te same”. W kodzie nic
  dodatkowego, to zwykłe zachowanie importu dołączającego.

Szczegóły „Zostaw na telefonie”:
- plik `guest_stash.json` w formacie kopii zapasowej, w katalogu aplikacji;
- jest jedno miejsce. Przy jednym użytkowniku wystarczy: plik jest wczytywany i kasowany przy
  wylogowaniu, więc nie ma dwóch odłożonych zestawów naraz;
- **plik wchodzi do kopii zapasowej Androida** (decyzja C): wpis w `backup_rules.xml` oraz
  w `data_extraction_rules.xml`, w części kopii w chmurze i przenosin na nowy telefon. Po
  reinstalacji aplikacji albo zmianie telefonu Android odtworzy plik. Logowanie Firebase nie jest
  w kopii, więc aplikacja startuje bez użytkownika, a nowy gość od razu dostaje odłożone dane
  (6.1 krok 2). Działa, gdy kopia Androida jest włączona w telefonie (zwykle domyślnie).

**Kolejność kroków przy logowaniu** (żeby niczego nie zgubić i nie zostawić w chmurze):
1. Wybór konta Google (token).
2. Gość bez własnych danych: próba `linkWithCredential`. Udana oznacza koniec (konto nowe).
   Kolizja (konto używane) prowadzi do kroków 5–7, tak jak „Usuń”, bez pliku.
3. Gość z własnymi danymi: okno. Dla „Przenieś”: próba `link`; udana oznacza koniec, kolizja
   prowadzi do kroku 4. „Zostaw” i „Usuń” przechodzą od razu do kroku 4. „Nie loguj” kończy.
4. Plik: `pending_join.json` (Przenieś) albo `guest_stash.json` (Zostaw). „Usuń” bez pliku.
5. Skasowanie wszystkich danych gościa w chmurze (lista z pamięci telefonu; reguła pozwala
   kasować tylko kontom gościa) i `waitForPendingWrites()` z limitem 15 s. Firestore wysyła
   zapisy tylko aktualnego użytkownika, więc po przełączeniu niewysłane kasowania by przepadły.
6. Próba usunięcia samego konta gościa w Firebase Auth. Odmowa nie szkodzi: zostaje puste konto
   bez danych, które nic nie kosztuje.
7. `signInWithCredential` na konto Google. Przy „Przenieś” import dołączający z pliku, potem
   plik usunięty. Nowe konto po „Zostaw” albo „Usuń” dostaje talie przykładowe.

Gdy krok 7 się nie uda: powstaje nowy gość (6.1 krok 2), więc dane z pliku wracają, a przy
„Usuń” są talie przykładowe. Gdy aplikacja padnie między krokami 5 i 7: przy następnym starcie
to samo. Gdy limit z kroku 5 minie (słaba sieć): logowanie idzie dalej, a niewysłana część danych
gościa zostaje w chmurze (rzadkie, akceptowalne). Koszt: jedno skasowanie na dokument, np. 200
przy gościu z 200 kartami.

### 6.3 Wylogowanie

`signOut()`, potem nowy gość (6.1 krok 2): odłożone dane z `guest_stash.json` albo talie
przykładowe. Wymaga internetu. Offline przycisk „Wyloguj” jest nieaktywny z podpowiedzią.
Wylogowanie nie zostawia śmieci w chmurze: konto Google zostaje ze swoimi danymi, a nowy gość
jest w użyciu.

Jedyne, czego aplikacja nie posprząta: konto gościa na telefonie, z którego odinstalowano
aplikację bez logowania. To zwykle same talie przykładowe, kilka KB. Usuwałby je skrypt
administracyjny albo funkcja na serwerze (sekcja 13, poza planem).

## 7. Kopia zapasowa, eksport, import, reset

Tylko obecny format: JSON `StoreData` z `groups`, `studyModes`, `activityHeatmap`.

- **Eksport**: dane konta z pamięci telefonu, bez usuniętych elementów. Pola `replacementAt/Id`
  znikają z modelu i z pliku.
- **Import (zastępuje wszystko)**: ścisłe dekodowanie `kotlinx.serialization` plus krótka
  kontrola (`BackupCheck`, ok. 50 linii: niepuste i unikalne id bez „/”, niepuste nazwy,
  zakresy progów, ocen i stron). Błąd oznacza „to nie jest kopia RayColl” i dane zostają
  nietknięte. `ignoreUnknownKeys` zostaje, więc kopie z dzisiejszej wersji (z polami generacji)
  wczytują się normalnie. Elementy kopii z `deletedAt` są pomijane.
  Wszystkie daty w importowanych dokumentach (`updatedAt`, `contentUpdatedAt`, `srsUpdatedAt`)
  dostają **czas importu**. Import jest wtedy najnowszą zmianą, więc reguły go przyjmą, także dla
  rzeczy usuniętych (przywrócenie). Stan nauki w `srsState` zostaje z kopii. Zapis w porcjach
  po 400:
  - `set` talii, kart, trybów i dni z kopii;
  - `deletedAt` dla obecnych talii i trybów własnych, których w kopii nie ma;
  - `deletedAt` dla kart, których nie ma w kopii, ale tylko w taliach obecnych w kopii;
  - `count = 0` dla dni, których w kopii nie ma.
- **Import dołączający** („Przenieś na konto”) i **powrót odłożonych danych** (nowy gość):
  tylko `set` nowych dokumentów, bez oznaczania czegokolwiek jako usunięte, daty bez zmian.
- **Reset**: `deletedAt` dla talii i trybów własnych, tryby wbudowane do wersji fabrycznej,
  aktywność `count = 0`, nowe talie przykładowe. Daty = teraz.
- **Usuwane**: `LegacyBackup.repairLegacyBuiltInNames`, `BackupValidator` (347 linii), generacje.
- Import i reset offline trafiają do kolejki Firestore i wyślą się po połączeniu.
- **Testy** (Twoja decyzja): nowe testy na atrapie w pamięci (9.4).

## 8. Pliki: usunięcie i przepisanie

### 8.1 Do usunięcia (kod aplikacji)

| Plik | Linie | Dlaczego |
|---|---|---|
| `data/sync/SyncEngine.kt` | 297 | diff, baza, transakcje |
| `data/sync/SyncLifecycle.kt` | 289 | harmonogram, ponowienia, uzgadnianie; zastępuje go `AccountManager` |
| `data/sync/SyncScheduler.kt` | 159 | harmonogram wysyłek |
| `data/sync/SyncDiff.kt` | 89 | diff |
| `data/sync/BootstrapLoader.kt` | 79 | scalanie przy starcie |
| `data/sync/CloudOperations.kt` | 58 | porcje transakcji, limity czasu |
| `data/sync/CloudPositions.kt` | 52 | pozycje dokumentów sprzed pola `position` |
| `data/sync/MigrationController.kt` | 41 | logika okna przechodzi do `AccountManager` |
| `data/sync/CloudGeneration.kt` | 39 | generacje |
| `data/sync/CloudSync.kt` | 31 | interfejs bazy |
| `data/sync/CloudRevision.kt` | 22 | rewizje |
| `data/local/db/*` (Daos, Entities, RayCollDatabase z AutoMigration 1→2) | 304 | Room |
| `data/local/mappers/EntityMappers.kt` | 135 | Room |
| `data/repository/LocalStore.kt` | 152 | snapshoty, scalanie, `refreshUntouchedBuiltInModes` (cicha migracja kroków trybów) |
| `data/repository/ReviewWriteQueue.kt` | 137 | kolejka zapisów |
| `data/repository/DataMutations.kt` | 33 | sygnały do harmonogramu |
| `data/repository/NamespaceManager.kt` | 31 | przestrzenie gość/konto |
| `data/repository/LegacyBackup.kt` | 31 | naprawa starych kopii |
| `domain/merge/MergeEngine.kt` | 149 | scalanie |
| `domain/normalize/BackupValidator.kt` | 347 | walidacja surowego JSON (także dokumentów Firestore) |
| `domain/model/KeyedEnum.kt` | 31 | ciche zamienianie nieznanych wartości na domyślne |
| `di/DatabaseModule.kt` | 20 | Room |
| `playback/SessionEndSync.kt` | 36 | wysyłka na koniec sesji |
| `ui/study/ReviewWriteError.kt` | 31 | błąd kolejki ocen |
| **Razem** | **2 593** | |

Do tego: `app/schemas/` (schematy Room), wtyczka i zależności Room w `build.gradle.kts`
i `libs.versions.toml`, test „stale transaction…” w `tools/firestore-tests`.
W `backup_rules.xml` i `data_extraction_rules.xml` wpisy `raycoll.db` znikają, a dochodzi
`guest_stash.json` (decyzja C).

### 8.2 Do przepisania

| Plik | Przed | Po (szac.) | Zmiana |
|---|---|---|---|
| `data/repository/DeckRepository.kt` | 268 | 240 | na `DocumentStore`, wspólne nasłuchy, API bez zmian, podbicie talii raz na sesję |
| `data/repository/StudyModeRepository.kt` | 67 | 70 | jw. |
| `data/repository/BackupRepository.kt` | 68 | 170 | eksport, import (daty = teraz), reset, import dołączający, odkładanie i powrót danych gościa |
| `data/remote/firestore/FirestoreSchema.kt` | 275 | 170 | bez generacji i `assertStudyModeStep`; kroki przez `kotlinx` (`ignoreUnknownKeys`, bo w chmurze są pola z dawnej wersji) |
| `data/remote/auth/AuthRepository.kt` | 138 | 170 | konto gościa, `link`, kolizja |
| `domain/normalize/DataNormalizer.kt` | 173 | 80 | zostaje `padPageMetadata` i sanityzacja edytora trybów; znika normalizacja danych z zewnątrz |
| `domain/seed/SeedData.kt` | 91 | 80 | bez stałych id z `uid` |
| `domain/merge/MigrationRules.kt` | 27 | 20 | tylko „czy gość ma własne dane” |
| `domain/model/FlashcardGroup.kt`, `ModeStep.kt` | 324 | 315 | enumy przez `@SerialName` zamiast `KeyedEnumSerializer` |
| `data/local/prefs/UserPreferencesRepository.kt` | 87 | 85 | tylko bez `KeyedEnum` |
| `di/SyncModule.kt`, `di/FirebaseModule.kt` | 47 | 50 | `DocumentStore`, ustawienia pamięci Firestore |
| `RayCollApplication.kt` | 36 | 30 | `AccountManager.start()` |
| `ui/main/MainViewModel.kt`, `ui/components/MigrationDialog.kt` | 105 | 115 | cztery opcje okna, nowe teksty w 5 językach |
| `ui/appsettings/AppSettingsViewModel.kt` | 212 | 200 | „Synchronizuj teraz” = `waitForPendingWrites`; wylogowanie nieaktywne offline |
| `ui/study/StudyViewModel.kt`, `StudyScreen.kt` | 679 | 610 | bez kolejki ocen |
| `playback/SessionDepsAdapter.kt` | 61 | 58 | ocena wprost do repozytorium |
| `ui/components/SyncStatusBanner.kt` | 59 | 50 | „Offline — zmiany wyślą się po połączeniu” |
| `firestore.rules` | 9 | 70 | właściciel, „wygrywa nowsza zmiana”, blokada starej wersji, sprzątanie (4.3) |
| **Razem** | **2 726** | **≈ 2 583** | |

### 8.3 Nowe

| Plik | Linie (szac.) |
|---|---|
| `data/firestore/DocumentStore.kt` + `FirestoreDocumentStore.kt` (z obsługą odrzuceń) | 130 |
| `data/account/AccountManager.kt` (okno na 4 opcje, pliki gościa, sprzątanie konta gościa) | 250 |
| `data/repository/Cleanup.kt` (usunięte po 30 dniach, wygasłe archiwa) | 40 |
| `ui/components/RejectedChangesMessage.kt` (komunikat 4.4) | 30 |
| `domain/backup/BackupCheck.kt` | 50 |
| **Razem** | **500** |

### 8.4 Bilans

Dotknięty kod: 2 593 + 2 726 = 5 319 → 2 583 + 500 = 3 083 linie (−2 236), w tym reguły
Firebase (9 → 70). Sam Kotlin: −2 297. Kod aplikacji: 17 105 → ok. 14 800 linii.

### 8.5 Przegląd kodu „dla starych danych” (cała aplikacja)

| Miejsce | Werdykt |
|---|---|
| `LegacyBackup` | usunąć |
| `BackupValidator` | usunąć; zastępuje go `BackupCheck` na typowanym modelu |
| `DataNormalizer` | usunąć normalizację danych z chmury i kopii; zostaje dopełnianie metadanych stron i przycinanie wartości z edytora |
| `ModeStep` | **zostaje**: to aktualny format, wszystkie 17 kroków jest w edytorze, `version` jest zapisywane w chmurze; zmiana tylko w serializacji enumów |
| `KeyedEnum` | usunąć; klucze zostają przez `@SerialName`; nieznana wartość = dokument pominięty (dziś: cicha zamiana na domyślną) |
| `UserPreferencesRepository` | **zostaje**: nie ma tu migracji ani starych formatów; obsługa uszkodzonego pliku to odporność, nie zgodność; tylko bez `KeyedEnum` |
| `FirestoreSchema` | przepisać: bez generacji i podwójnej walidacji; opcjonalne `position`/`deletedAt`/`archivedAt` zostają, bo są w Twoich danych |
| `CloudPositions` | usunąć |
| `LocalStore.refreshUntouchedBuiltInModes` | usunąć (cicha aktualizacja zapisanych trybów); „Przywróć domyślne” zostaje |
| `SeedData` (id z `uid`) | usunąć |
| Room `AutoMigration`, `app/schemas`, `DatabaseMigrationTest` | usunąć |
| `StoreData.replacementAt/Id` | usunąć |

## 9. Testy

Zasady od Ciebie: testy kodu, który znika, idą do kosza bez przepisywania. Zostają testy logiki
aplikacji. Nowe testy dla scenariuszy z emulatorem Firestore oraz (decyzja z rewizji) dla
importu, eksportu i resetu.

### 9.1 Usuwane w całości: 2 824 linie, 131 przypadków

`data/sync/*` (11 plików: diff, baza, rewizje, generacje, porcje, harmonogram, odzyskiwanie,
dwa urządzenia), `DaoTest`, `DatabaseMigrationTest`, `EntityMappersTest`, `ReviewWriteQueueTest`,
`BackupRepositoryTest`, `MergeEngineTest`, `StoreGenerationTest`, `BackupValidatorTest`,
`TypedSettingsSerializationTest`.

### 9.2 Przycinane (zostaje logika, znikają przypadki o synchronizacji i starych danych)

| Plik | Przypadki | Co znika |
|---|---|---|
| `DeckRepositoryTest` | 24 → 17 | snapshot z tombstonami, przełączanie przestrzeni, scalanie, wyścigi zapisów kolumn |
| `FirestoreSchemaTest` | 20 → ~11 | dokumenty bez `position`, stare pola, walidacja kopii; zostaje odczyt i zapis formatu |
| `DataNormalizerTest` | 9 → ~4 | normalizacja danych z zewnątrz |
| `AppSettingsSignInTest` | 11 → ~5 | przestrzenie, wylogowanie z limitem wysyłki |
| `StudyRestartTest` | −1 | blokada sesji po nieudanym zapisie oceny |
| `SeedDataTest`, `MigrationRulesTest`, `OperationsTest` | −1 do −2 | stałe id seedów, `shouldTriggerMigration`, normalizacja |

### 9.3 Zostają bez zmian treści (tylko inna konstrukcja repozytoriów)

Algorytm powtórek (`SrsEngineTest`), sesja nauki (`SessionEngine*`, `SessionReducer`,
`CompoundExpander`, `ModeStepValidator`, audio), import CSV (`ImportParserTest`,
`ImportViewModelTest`, `ImportScreenTest`, `ImportDraftTest`), wszystkie ekrany i ViewModele,
selektory, i18n. `VmTestHarness` buduje repozytoria na `InMemoryDocumentStore` (nowa atrapa
testowa, ok. 120 linii) zamiast Room w pamięci. Testy ekranów używają dziś w ok. 60 miejscach
`repos.localStore` / `repos.db` / `repos.namespaceManager` (głównie seedowanie, `db.close()`
i zapis danych startowych testu). Te wywołania przejdą na pomocnicze funkcje harnessu
(`seed()`, `put(deck)`), a asercje zostają bez zmian. `MainViewModelTest` (podwójne kliknięcie
w oknie) i `AuthRepositoryTest` (wybór konta Google) zostają.

### 9.4 Nowe

**Kopia, import, reset, dane gościa** (atrapa w pamięci, bez emulatora, ok. 12 przypadków):
- eksport i import wracają do tych samych danych;
- błędny plik niczego nie zmienia;
- import zastępuje wszystko i przywraca usunięte;
- daty po imporcie są czasem importu, a stan nauki zostaje z kopii;
- reset zostawia tylko dane startowe;
- import dołączający pomija talie przykładowe i tryby wbudowane;
- tryb wbudowany zmieniony przez gościa przechodzi jako kopia „(z telefonu)”, a talie gościa
  wskazują na kopię (decyzja A);
- talia gościa o nazwie talii konta przechodzi jako osobna (decyzja B);
- odłożenie i powrót danych gościa.

**Scenariusze na emulatorze Firestore + Auth.** Dwa „urządzenia” w jednym teście to dwie
instancje `FirebaseApp`, każda z własną pamięcią. Offline symuluje `disableNetwork()`. Konto
Google symuluje token-atrapa, który emulator Auth przyjmuje. Emulator ładuje prawdziwe
`firestore.rules`, więc scenariusze sprawdzają też „wygrywa nowsza zmiana”.

1. **Usunięcie offline**:
   - A offline usuwa talię i kartę, B w tym czasie edytuje inną kartę. Po połączeniu obie
     zmiany są na obu urządzeniach.
   - A offline usuwa kartę, a B później ją ocenia. Usunięcie A zostaje odrzucone, karta żyje
     na obu.
   - B usuwa kartę, a A offline wcześniej ją edytował. Edycja A zostaje odrzucona.
   - Karta z `deletedAt` sprzed 31 dni (data ustawiona w teście, bo czasu serwera emulatora nie
     da się przesunąć) znika z chmury przy sprzątaniu, a świeżo usunięta zostaje.
2. **Edycja na dwóch urządzeniach**:
   - Różne pola karty (treść na A, ocena na B): obie przetrwają.
   - Ta sama talia: nowsza zmiana wygrywa niezależnie od kolejności wysłania (A offline zmienia
     nazwę o 10:00, B online o 11:00, A łączy się o 12:00 → zostaje nazwa B).
3. **Ocena offline**:
   - A offline ocenia 3 karty, B online 1 inną, potem B ocenia jedną z kart A. Po połączeniu A:
     oceny A przyjęte poza tą jedną, aktywność dnia = 5.
   - A dostaje komunikat o 1 odrzuconej zmianie.
   - Talia usunięta offline na A, a potem uczona na B: usunięcie odrzucone.
4. **Wybór danych przy logowaniu**: każdy wiersz tabeli z 6.2, a do tego:
   - po każdej opcji poza „Nie loguj” i `link` w chmurze nie zostaje żaden dokument gościa;
   - zabicie aplikacji w środku „Przenieś” kończy się dokończeniem albo powrotem danych na nowego
     gościa.

Do testów reguł w `tools/firestore-tests` (Node, ten sam emulator) dochodzą przypadki:
- zapis do `users/{uid}` odrzucony (blokada starej wersji);
- starsza data odrzucona;
- data z przyszłości odrzucona;
- edycja usuniętego odrzucona, przywrócenie importem przyjęte;
- twarde kasowanie: przez konto Google tylko tombstonów starszych niż 30 dni, przez gościa
  przyjęte.

Uruchamianie: `firebase emulators:exec --only firestore,auth` (do `firebase.test.json` dochodzi
emulator Auth) wokół `gradlew :app:testDebugUnitTest --tests "*Emulator*"`. Bez emulatora te
testy są pomijane (`Assume`), więc zwykłe `test` działa jak dziś. Plan awaryjny: jeśli Firestore
SDK nie ruszy pod Robolectric, te same scenariusze jako `androidTest` na telefonie
z `adb reverse tcp:8089 tcp:8089`.

Bilans testów Kotlin: ok. 643 → 500 przypadków, ok. 13 300 → 10 600 linii (−2 824 usunięte,
ok. −630 przycięte, ok. +750 atrapa, testy kopii i scenariusze). Do tego ok. +150 linii testów
reguł w Node.

## 10. Ryzyka

| # | Ryzyko | Skutek | Zabezpieczenie |
|---|---|---|---|
| 1 | Błąd w regułach odrzuca poprawne zapisy | zmiany cofane na telefonie | testy reguł (9.4); komunikat 4.4; w logach każde odrzucenie |
| 2 | Zapomniany telefon ze starą wersją aplikacji | po wgraniu reguł jego zapisy są odrzucane: niczego nie zepsuje, pokaże błąd synchronizacji, jego niewysłane zmiany zostaną tylko na nim | kolejność kroku 0 w §11 |
| 3 | Zmiany w Room jeszcze niewysłane w chwili aktualizacji | utracone (zero migracji); dane gościa na telefonie też | przed aktualizacją: „Synchronizuj teraz” w obecnej wersji |
| 4 | W chmurze zostały dokumenty starej generacji (przerwany reset albo import) | dziś ukryte, w nowej wersji widoczne | ten sam krok co w 3 (udana synchronizacja je sprząta) |
| 5 | Czekanie na `Task` zapisu w kodzie | ekran wisi offline | zasada w 4.2 + przegląd kodu; atrapa może symulować „nigdy nie kończy” |
| 6 | Zły zegar w telefonie | zmiana z przeszłości przegrywa, choć jest nowsza | telefony ustawiają czas same; daty z przyszłości odrzucane |
| 7 | `WriteBatch` z jednym odrzuconym dokumentem (np. zamiana stron, gdy jedna karta została w międzyczasie usunięta na innym urządzeniu) | cała zamiana cofnięta | rzadkie; komunikat 4.4; użytkownik powtarza |
| 8 | Nasłuch podpinany w kółko (błąd w kodzie) | dzienny limit odczytów zjedzony w minuty, synchronizacja stoi do rana | nasłuchy współdzielone w repozytoriach, nie w ekranach; podgląd zużycia w konsoli przez 2–3 dni |
| 9 | Pierwszy start i wylogowanie wymagają internetu | brak pracy offline na świeżej instalacji | komunikat z ponowieniem |
| 10 | Firestore SDK nie działa pod Robolectric z emulatorem | brak szybkich testów scenariuszy | `androidTest` na telefonie |
| 11 | Import 1000 kart offline | ok. 4 partie w kolejce; zabicie aplikacji między partiami = import częściowy | każda partia zapisuje się na telefonie atomowo; ponowny import naprawia stan |
| 12 | `increment` ponowiony po zerwaniu połączenia | możliwe +1 w heatmapie (do sprawdzenia na emulatorze) | akceptowalne |
| 13 | Odrzucenia po restarcie aplikacji są ciche (4.4) | nie wiesz, że stary telefon miał przestarzałe zmiany | to ograniczenie Firestore; odrzucone były starsze, więc dane w chmurze są dobre |
| 14 | „Zostaw na telefonie” a odinstalowanie aplikacji | odłożone dane giną | plik w kopii Androida (decyzja C); ginie tylko przy wyłączonej kopii w telefonie |
| 15 | Konto gościa z odinstalowanej aplikacji | kilka KB w chmurze bez końca | skrypt administracyjny poza planem (sekcja 13) |

## 11. Kolejność wdrożenia

Każdy etap kończy się zielonym `test`, `ktlintCheck`, `lint` i `assembleRelease`.

0. **Kroki ręczne (Ty), w dniu przesiadki, w tej kolejności:**
   - włączyć logowanie anonimowe w Firebase Console;
   - na każdym urządzeniu „Synchronizuj teraz” w obecnej wersji;
   - wgrać reguły (`firebase deploy --only firestore:rules`, pokażę komendę); od tej chwili
     stara wersja nie może niczego zapisać;
   - odinstalować starą wersję, zainstalować nową.

   Te kroki dotyczą dnia przesiadki, czyli końca wdrożenia. Etapy 1–7 robię wcześniej, na
   gałęzi. Etap 8 (telefon) jest już po przesiadce.
1. **Wcześniej:** etapy 1–2 planu porządków (Java 21, Android 13). Potem `DocumentStore`
   + `FirestoreSchema` + `InMemoryDocumentStore`; repozytoria na nowym magazynie, wspólne
   nasłuchy; usunięcie Room. Testy ekranów zielone na atrapie.
2. `AccountManager`: konto gościa, dane startowe, okno z czterema opcjami, pliki gościa,
   sprzątanie konta gościa, wylogowanie.
3. Kopia, import, reset + testy na atrapie.
4. `firestore.rules` + testy reguł w Node; sprzątanie po 30 dniach; komunikat o odrzuceniach;
   baner stanu; „Synchronizuj teraz”.
5. Usunięcie starego kodu i testów (8.1, 9.1, 9.2), porządki w zasobach (`strings` od kolejki
   ocen i starego okna).
6. Scenariusze na emulatorze (9.4).
7. Etap 5 planu porządków: usunięcie starych raportów, repomix w folderze, README opisujące
   nowy stos (Firestore zamiast Room).
8. Telefon:
   - zalogować się i sprawdzić pełne wczytanie;
   - nauka offline, drugie urządzenie, każda opcja okna logowania, wylogowanie z powrotem
     danych gościa;
   - przez 2–3 dni obserwować zużycie w konsoli i odrzucenia w logach.

## 12. Przegląd krytyczny: co poprawiłem

**Wersje 1–3** (skrót): okno logowania przed przełączeniem konta (powrót do gościa jest
niemożliwy); dołączanie bez duplikatów talii przykładowych; zostawiona obsługa braku `position`;
nauka podbija `updatedAt` talii (stare usunięcie talii przegrywa); import stempluje daty czasem
importu (inaczej przegrywałby z regułą „nowsza wygrywa”); wygasanie archiwów tylko na aktualnych
danych; pliki chroniące dane gościa przy przerwanym logowaniu; sprzątanie konta gościa przed
przełączeniem i `waitForPendingWrites`; kolejność kroków przesiadki; poprawki liczb.
Pobieranie przyrostowe z tamtych wersji (`syncedAt`, znaczniki) wypadło w wersji 4.

**Wersja 4** (Twoje 9 decyzji + rozpisanie logowania):
1. **Blokada starej wersji bez `syncedAt`.** Wcześniej opierała się na nowym polu. Teraz na tym,
   że stara wersja przy każdej wysyłce zapisuje dokument `users/{uid}`, a reguła tego zabrania,
   więc cała jej transakcja jest odrzucana. Format zostaje bez zmian.
2. **Sprzątanie po 30 dniach a karty usuniętej talii.** Karty usuniętej talii nie mają własnego
   `deletedAt`, więc reguła by ich nie wpuściła do skasowania. Reguła sprawdza teraz talię
   nadrzędną, a aplikacja kasuje najpierw karty, potem talię (inaczej sprawdzenie nie miałoby
   czego czytać).
3. **Sprzątanie a stary telefon.** Sprawdzone: jego zaległa edycja skasowanego dokumentu jest
   odrzucana (dokumentu nie ma), a nasłuch usuwa go z pamięci telefonu. Bez ryzyka ożywienia.
4. **Komunikat o odrzuceniach ma ograniczenie** (tylko w tej samej sesji). Opisane w 4.4
   i ryzyku 13, żeby nie obiecywać więcej, niż Firestore daje.
5. **Ocena odrzucona, a aktywność liczona.** Wcześniej tylko wspomniane, teraz zapisane wprost
   jako decyzja w 4.3 (zwrócił na to uwagę przegląd ChatGPT).
6. **„Przywrócić może tylko import”** było opisane tak, jakby pilnowała tego reguła. Poprawione:
   reguła wpuszcza każdy nowszy zapis całego dokumentu bez `deletedAt`, a „tylko import” to umowa
   w kodzie.
7. **Tabela logowania pokazała dwie luki**: zmiany gościa w trybie wbudowanym przepadałyby przy
   „Przenieś” (decyzja A), a „Zostaw” gubi dane przy reinstalacji (decyzja C).
8. **Koszt przeliczony dla 1000 fiszek** (wcześniej 3000) i dla zwykłych nasłuchów.
9. **Reguła kart blokowała przywrócenie importem.** Przy imporcie `deletedAt` znika, a reguła
   porównywała nieistniejące pole, co Firestore traktuje jak odmowę. Dodane `restoring()`
   w warunku usunięcia.
10. **Liczba zapisów importu** była zaniżona (1 050 zamiast ok. 1 400, bo doszły tryby i dni
    aktywności), a za nią liczba partii w ryzyku 11.
11. **Test sprzątania po 30 dniach** zakładał przesunięcie czasu serwera, a w emulatorze się nie
    da. Test ustawia starą datę `deletedAt`.
12. **Ciche logowanie gościa bez własnych danych na używane konto** nie miało opisanej ścieżki
    kroków. Idzie jak „Usuń”, bez pliku. Uporządkowana też kolejność kroków startu (6.1).

Przegląd porządków (Java, tryb eksperta, Android 13) jest w `docs/PLAN_PORZADKI.md`, sekcja 11.

## 13. Decyzje

**Twoje decyzje:**
- Zostajemy przy Firebase, prostsza synchronizacja (zwykłe nasłuchy, format bez zmian).
- Wygrywa nowsza zmiana według zegara telefonu, pilnowane regułami serwera.
- Komunikat o odrzuconych zmianach.
- Testy importu, eksportu i resetu.
- Sprzątanie usuniętych po 30 dniach.
- Blokada starej wersji aplikacji.
- Kasowanie danych porzuconego konta gościa.
- Pierwsze uruchomienie i wylogowanie wymagają internetu.
- Okno przy logowaniu: przenieś na konto / zostaw na telefonie / usuń / nie loguj, zawsze gdy gość
  ma własne dane.
- Po wylogowaniu wracają dane odłożone przez „Zostaw”, a bez nich talie przykładowe. Po
  „Przenieś na konto” lokalnej kopii nie ma.
- Użytkownicy: na razie tylko Ty.

- **A.** Tryb wbudowany zmieniony przez gościa przechodzi przy „Przenieś” jako osobny tryb
  „… (z telefonu)”, a talie gościa dalej go używają.
- **B.** Talia gościa o tej samej nazwie co talia konta przechodzi jako osobna talia, bez łączenia
  kart.
- **C.** Plik z danymi odłożonymi przez „Zostaw” wchodzi do kopii zapasowej Androida, więc
  przetrwa reinstalację i zmianę telefonu.

Decyzje o porządkach (narzędzia, raporty, repomix, języki, biblioteki, Android 13, tryb
eksperta, FSRS) są w `docs/PLAN_PORZADKI.md`, sekcja 12.

**Poza planem:** skrypt administracyjny sprzątający konta gości z odinstalowanych aplikacji
(próg nieużywania 180 albo 365 dni), przy jednym użytkowniku niepotrzebny.

## 14. Kontrakty dla wykonawcy (wiążące)

Ta sekcja rozstrzyga szczegóły, których nie wolno projektować po swojemu. Przy sprzeczności
z wcześniejszymi sekcjami obowiązuje ta sekcja. Jeśli kod tego nie pozwala, zapisz odstępstwo
w `docs/ODSTEPSTWA.md` i wybierz rozwiązanie najbliższe temu opisowi.

### 14.1 `DocumentStore` (pakiet `com.raycoll.app.data.firestore`)

```kotlin
/** Dokument: id + pola jak w Firestore (Long, Double, String, Boolean, List, Map, Timestamp, null). */
data class Doc(val id: String, val data: Map<String, Any?>)

/** Wynik nasłuchu; fromCache = dane z pamięci telefonu, serwer jeszcze nie odpowiedział. */
data class Docs(val docs: List<Doc>, val fromCache: Boolean)

/** Wartości specjalne; implementacja zamienia je na FieldValue. */
sealed interface FieldOp {
    data object ServerTime : FieldOp               // FieldValue.serverTimestamp()
    data class Increment(val by: Long) : FieldOp   // FieldValue.increment(by)
}

sealed interface WriteOp {
    val path: String
    data class Set(override val path: String, val data: Map<String, Any?>, val merge: Boolean = false) : WriteOp
    data class Update(override val path: String, val fields: Map<String, Any?>) : WriteOp
    data class Delete(override val path: String) : WriteOp
}

interface DocumentStore {
    /** Nasłuch kolekcji (np. "users/u1/decks"), źródło domyślne (pamięć + serwer). Ta sama ścieżka = jeden nasłuch. */
    fun observe(collectionPath: String): Flow<Docs>
    /** Jednorazowo z pamięci telefonu (Source.CACHE); nigdy nie czeka na sieć. */
    suspend fun cached(collectionPath: String): List<Doc>
    suspend fun cachedDoc(path: String): Doc?
    /** Bez czekania na serwer. Rzuca wyjątek tylko przy błędzie lokalnym (złe dane). */
    fun write(op: WriteOp)
    /** Porcje po 400; każda porcja to jeden WriteBatch. */
    fun writeAll(ops: List<WriteOp>)
    /** +N za każdy zapis odrzucony przez serwer (PERMISSION_DENIED) w tym procesie; źródło komunikatu 4.4. */
    val rejections: SharedFlow<Int>
    /** Czy ostatni nasłuch jest offline (fromCache przy braku sieci); źródło banera. */
    val offline: StateFlow<Boolean>
    /** waitForPendingWrites z limitem; false = minął limit. */
    suspend fun awaitPendingWrites(timeoutMs: Long): Boolean
}
```

- `FirestoreDocumentStore`: `observe` to `callbackFlow` + `shareIn(@ApplicationScope, WhileSubscribed(5_000), replay = 1)`,
  trzymane w mapie po ścieżce. Ustawienia: `persistentCacheSettings { setSizeBytes(CACHE_SIZE_UNLIMITED) }`
  w `FirebaseModule`, przed pierwszym użyciem Firestore.
- Ścieżki tylko przez jeden obiekt `UserPaths(uid)`: `decks()`, `deck(id)`, `cards(deckId)`,
  `card(deckId, id)`, `modes()`, `mode(id)`, `activity()`, `activityDay(day)`. Dzień w formacie
  `yyyy-MM-dd` (`LocalDates.dayKey`).
- `FirestoreSchema`: obecne `deckToMap/cardToMap/modeToMap/activityToMap` i `…FromMap` zostają (bez
  generacji, bez `assertStudyModeStep`), czytają z `Doc.data`. Sortowanie: `position`, brak pola na
  końcu, potem id.

### 14.2 Konto (pakiet `com.raycoll.app.data.account`)

```kotlin
/** Zastępuje MigrationOption (MIGRATE/FRESH/CANCEL). */
enum class GuestDataChoice { MOVE_TO_ACCOUNT, KEEP_ON_DEVICE, DELETE, CANCEL }

sealed interface AccountState {
    data object Starting : AccountState
    data object NeedsInternet : AccountState          // brak użytkownika i brak sieci (6.1 krok 3)
    data class Ready(val uid: String, val isGuest: Boolean, val email: String?) : AccountState
}

/** Źródło uid dla repozytoriów (w testach atrapa). */
interface CurrentAccount { val uid: StateFlow<String?> }

@Singleton class AccountManager : CurrentAccount {
    val state: StateFlow<AccountState>
    val choiceRequired: StateFlow<Boolean>            // pokazuje MigrationDialog
    val busy: StateFlow<Boolean>
    fun start()                                       // RayCollApplication.onCreate
    suspend fun retryStart()                          // „Spróbuj ponownie”
    suspend fun signInWithGoogle(idToken: String): SignInResult   // kroki 1–3 z 6.2
    suspend fun resolveChoice(choice: GuestDataChoice): Boolean    // kroki 4–7; false = błąd
    suspend fun signOut(): Boolean
}
```

- `SignInResult` dostaje wariant `ChoiceRequired`. Token Google czeka w polu `AccountManager`, dopóki
  okno jest otwarte. Po zabiciu procesu przepada i logujesz się od nowa (bez zapisu tokena na dysk).
- `signIn`, `resolveChoice` i `signOut` działają w zakresie aplikacji (`@ApplicationScope`), więc obrót
  ekranu ich nie przerywa. Ekrany tylko czekają na wynik, jak dziś przy `SyncLifecycle`.
- **Okno zostaje tam, gdzie jest dziś:** `MainActivity` pokazuje `MigrationDialog`, gdy
  `choiceRequired`; `MainViewModel.resolve(choice)` woła `resolveChoice`. Zostają stany `busy`
  i `failed` oraz podwójne kliknięcie = jedna operacja (`MainViewModelTest`).
- Przyciski i klucze tekstów (5 języków): `auth_choice_move` „Przenieś na konto”,
  `auth_choice_keep` „Zostaw na tym telefonie”, `auth_choice_delete` „Usuń dane z telefonu”,
  `auth_migration_cancel_btn` „Nie loguj” (bez zmian), opis `auth_choice_desc`. Klucze
  `auth_migration_migrate_btn`, `auth_migration_fresh_btn`, `auth_migration_desc` do usunięcia.
- Pliki w `context.filesDir`:
  - `guest_stash.json` (format kopii `StoreData`);
  - `pending_join.json` (to samo, bez dodatkowych pól). Przy starcie: jeśli plik jest i zalogowane
    jest konto Google, import dołączający do niego. W przeciwnym razie (gość albo brak użytkownika)
    import do konta gościa. Potem plik usuwany.
- Dane startowe: jedna funkcja `BackupRepository.writeSeed(uid, language)`, używana przez nowego
  gościa, nowe konto Google po „Zostaw”/„Usuń” (`additionalUserInfo.isNewUser`) i reset.
- `AuthRepository` dostaje: `signInAnonymously()`, `linkWithGoogle(idToken)` (zwraca sukces albo
  kolizję z `updatedCredential`), `signInWithCredential(credential)`, `deleteCurrentUser()`
  (błąd ignorowany).

### 14.3 Repozytoria

- Konstruktory: `DeckRepository(store: DocumentStore, account: CurrentAccount, clock: Clock)`,
  `StudyModeRepository` tak samo, `BackupRepository(store, account, documentFiles, clock,
  @DefaultDispatcher)`. Odczyty: `account.uid.filterNotNull().flatMapLatest { store.observe(...) }`.
- **Publiczne API bez zmian** poza dwoma miejscami:
  - `reviewCard(deckId, cardId, rating, baseState)` traci parametr `namespace`;
  - nowe `suspend fun markStudied(deckId)` (`update(talia, updatedAt = now)`). `StudyViewModel` woła
    je raz, po wczytaniu kart sesji, gdy lista nie jest pusta. Zastępuje „pierwszą ocenę w sesji”
    z 4.2.
- Funkcje zapisu zostają `suspend` (ViewModele bez zmian), ale nie czekają na sieć.
- `reviewCard`: `Mutex`, `cachedDoc(karta)`, obliczenie FSRS, `Update(srsState, srsUpdatedAt)`, a gdy
  `baseState == null`: `Set(activityDay, {count: Increment(1), updatedAt: ServerTime}, merge = true)`.
- Komunikat o odrzuceniach: `MainActivity` zbiera `store.rejections` (zsumowane w oknie 2 s)
  i pokazuje jeden snackbar z liczbą. Nowy tekst `sync_changes_rejected` (5 języków, z liczbą).

### 14.4 Atrapa testowa (`app/src/test/.../testing`)

- `InMemoryDocumentStore : DocumentStore`: mapa ścieżka → pola w `MutableStateFlow`. `observe`
  zwraca bezpośrednie dzieci kolekcji z `fromCache = false`. Obsługuje `FieldOp` (`ServerTime` =
  `Timestamp` z `FakeClock`). Pole `var failWhen: (WriteOp) -> Boolean = { false }`: gdy zwraca
  true, `write` rzuca `IllegalStateException("simulated write failure")`.
- `FakeAccount(initialUid = "uid-1") : CurrentAccount` z `signIn(uid)`.
- `TestRepos`: `store`, `account`, `deckRepository`, `studyModeRepository`, `backupRepository` oraz
  pomocnicze `seed(language)`, `snapshot(): StoreData` (także usunięte), `put(data: StoreData)`,
  `setActivity(day, count)`.
- Zamiana wywołań w testach, które zostają (asercje bez zmian):

| Dziś | Po zmianie |
|---|---|
| `repos.localStore.snapshot()` | `repos.snapshot()` |
| `repos.db.close()` | usunąć |
| `CREATE TRIGGER …` przez `repos.db.openHelper` | `repos.store.failWhen = { <ten sam rodzaj zapisu> }` |
| `DROP TRIGGER …` | `repos.store.failWhen = { false }` |
| `repos.namespaceManager.switchTo(x)` / `.current()` | `repos.account.signIn(x)` / `repos.account.uid.value` |
| `repos.localStore.seedIfEmpty(lang)` | `repos.seed(lang)` |
| `repos.db.activityDao().upsertActivity(…)` | `repos.setActivity(day, count)` |
| `repos.localStore.replaceAll(data)` | `repos.put(data)` |

Dotyczy m.in. `SaveFailureTest`, `DialogStateRestorationTest`, `StudyRestartTest` i
`AppSettingsSignInTest`. Testów symulujących błąd zapisu **nie usuwać**.

### 14.5 Pełne `firestore.rules`

Zastępuje cały obecny plik. **Stara reguła `match /users/{uid}/{document=**}` musi zniknąć**, bo reguły
się sumują i zostawiona przepuszczałaby wszystko.

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    function owner(uid) { return request.auth != null && request.auth.uid == uid; }
    function guest() { return request.auth.token.firebase.sign_in_provider == 'anonymous'; }
    function nowMs() { return request.time.toMillis(); }
    function notFuture(f) { return request.resource.data.get(f, 0) <= nowMs() + 600000; }
    function notOlder(f) { return request.resource.data.get(f, 0) >= resource.data.get(f, 0); }
    function changed(keys) { return request.resource.data.diff(resource.data).affectedKeys().hasAny(keys); }
    function alive() { return resource.data.get('deletedAt', null) == null; }
    function restoring() { return request.resource.data.get('deletedAt', null) == null; }
    function oldTombstone(d) { return d.get('deletedAt', null) != null && d.deletedAt < nowMs() - 2592000000; }

    // Stara wersja aplikacji zapisuje ten dokument przy każdej wysyłce: odmowa blokuje całą jej transakcję.
    match /users/{uid} {
      allow read: if owner(uid);
      allow write: if false;
    }

    match /users/{uid}/decks/{deckId} {
      allow read: if owner(uid);
      allow create: if owner(uid) && notFuture('updatedAt');
      allow update: if owner(uid) && notFuture('updatedAt') && notOlder('updatedAt') && (alive() || restoring());
      allow delete: if owner(uid) && (guest() || oldTombstone(resource.data));

      match /cards/{cardId} {
        allow read: if owner(uid);
        allow create: if owner(uid) && notFuture('srsUpdatedAt') && notFuture('contentUpdatedAt');
        allow update: if owner(uid)
          && notFuture('srsUpdatedAt') && notFuture('contentUpdatedAt')
          && (alive() || restoring())
          && (!changed(['srsState', 'srsUpdatedAt']) || notOlder('srsUpdatedAt'))
          && (!changed(['pages', 'contentUpdatedAt']) || notOlder('contentUpdatedAt'))
          && (!changed(['deletedAt']) || restoring()
              || (request.resource.data.deletedAt >= resource.data.get('srsUpdatedAt', 0)
                  && request.resource.data.deletedAt >= resource.data.get('contentUpdatedAt', 0)));
        allow delete: if owner(uid) && (guest() || oldTombstone(resource.data)
          || oldTombstone(get(/databases/$(database)/documents/users/$(uid)/decks/$(deckId)).data));
      }
    }

    match /users/{uid}/studyModes/{modeId} {
      allow read: if owner(uid);
      allow create: if owner(uid) && notFuture('updatedAt');
      allow update: if owner(uid) && notFuture('updatedAt') && notOlder('updatedAt') && (alive() || restoring());
      allow delete: if owner(uid) && (guest() || oldTombstone(resource.data));
    }

    match /users/{uid}/activity/{day} {
      allow read: if owner(uid);
      allow create, update: if owner(uid) && request.resource.data.updatedAt == request.time;
      allow delete: if owner(uid) && guest();
    }
  }
}
```

Uwaga do zapisów aplikacji wynikająca z reguł:
- każdy zapis karty, talii i trybu niesie odpowiednią datę `= now` (`updatedAt`,
  `contentUpdatedAt`, `srsUpdatedAt`, `deletedAt`);
- każdy zapis aktywności niesie `updatedAt = ServerTime`;
- przy sprzątaniu najpierw kasujemy karty, potem talię.

### 14.6 Nie wdrażać (odrzucone w trakcie ustaleń)

- pole `syncedAt`, `RemoteChanges`, znaczniki synchronizacji, nasłuch „tylko z pamięci” z pobieraniem
  przyrostowym;
- „wygrywa ostatni wysłany”, czyli zwykłe zasady Firestore bez reguł z 14.5;
- twarde kasowanie poza dwoma wyjątkami (sprzątanie konta gościa, tombstony starsze niż 30 dni);
- jakikolwiek zapis do dokumentu `users/{uid}`; pola `replacementAt/Id`, `syncToken`, generacje;
- własna kolejka zapisów, harmonogram, ponowienia; czekanie na `Task` zapisu (poza
  `awaitPendingWrites` w „Synchronizuj teraz” i w logowaniu);
- Room w jakiejkolwiek formie; nowe pola w dokumentach Firestore;
- zapytania `collectionGroup` (reguły ich nie przepuszczą);
- inne backendy (PowerSync, Supabase), Navigation 3, FsrsKt, FastCSV, zmiana algorytmu FSRS.

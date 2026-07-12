# RayColl — KOMPLETNY PLAN przepisania na natywny Android (Kotlin + Jetpack Compose)
# Wersja 3 — FINALNA. Greenfield. Bez chmury w v1.

Ten dokument jest JEDYNYM źródłem prawdy dla całego projektu. Zawiera: decyzje, spec
produktu, spec każdego ekranu, model danych, wszystkie stałe, algorytmy, architekturę,
mapowanie starego kodu, kontrakt wykonania, 10 tasków z checklistami Definition-of-Done,
listę TODO i finalną checklistę regresji. Agent wykonujący task NIE potrzebuje żadnej
innej wiedzy poza tym plikiem i wskazanymi plikami-referencjami w `../TensorDeck`.

---------------------------------------------------------------------------------------
## SPIS TREŚCI
---------------------------------------------------------------------------------------

0.  Jak używać tego pliku + uniwersalny prompt startowy
1.  Decyzje projektowe (przeczytaj ZANIM napiszesz linijkę kodu)
2.  Czym jest ta aplikacja — spec produktu
3.  Spec ekranów — zachowanie punkt po punkcie (9 ekranów)
4.  Model danych — pola, typy, niezmienniki
5.  Stałe i liczby magiczne — kompletna tabela
6.  Algorytmy — FSRS, dopasowanie mowy, silnik sesji, kroki, import
7.  Architektura — drzewo pakietów z odpowiedzialnościami
8.  Mapowanie: stary kod (TensorDeck) → nowy kod
9.  KONTRAKT WYKONANIA TASKA (obowiązuje każdego agenta, w każdym tasku)
10. TASKI T1–T10 — szczegółowe checklisty DoD
11. LISTA TODO — tracking postępu
12. Finalna checklista regresji manualnej (akceptacja przed releasem)
13. Ryzyka i pułapki implementacyjne
14. FAZA 2 (opcjonalna, POZA zakresem v1): sync między urządzeniami

---------------------------------------------------------------------------------------
## 0. JAK UŻYWAĆ TEGO PLIKU + UNIWERSALNY PROMPT STARTOWY
---------------------------------------------------------------------------------------

Agent wykonujący task dostaje jeden i ten sam prompt, różni się tylko numer taska:

```
Jesteś wykonawcą taska T<N> przepisania aplikacji RayColl na natywny Android.
Katalog roboczy (nowa apka): C:\Users\Karol\.gemini\antigravity\scratch\RayColl
Stare repo (TYLKO referencja zachowań): C:\Users\Karol\.gemini\antigravity\scratch\TensorDeck

1. Przeczytaj w docs/PORT_PLAN.md sekcje: 1 (decyzje), 9 (KONTRAKT WYKONANIA)
   oraz CAŁĄ sekcję swojego taska "T<N>" w sekcji 10.
2. Przeczytaj sekcje merytoryczne, do których odsyła Twój task (spec ekranów w sekcji 3,
   model danych w 4, stałe w 5, algorytmy w 6 — zgodnie z odnośnikami w tasku).
3. Przeczytaj pliki-referencje wskazane w tasku (stary kod TypeScript + jego testy).
4. Wykonaj task W CAŁOŚCI według checklisty DoD. Kontrakt z sekcji 9 obowiązuje bez
   wyjątków (zakazy TODO/stubów, weryfikacja gradle, format raportu).
5. Nie pytaj o pozwolenie między krokami — checklista DoD jest ostateczną specyfikacją.
   Punkt niewykonalny oznaczasz BLOCKED(powód) w raporcie i wykonujesz całą resztę.
6. Na końcu oddaj raport dokładnie w formacie z sekcji 9 pkt 11.
```

Zasady prowadzenia projektu (dla orkiestratora):
- Taski wykonuj ściśle w kolejności T1→T10. Nie zaczynaj taska, dopóki poprzedni nie
  jest zamknięty (zielony build + testy + raport zweryfikowany + diff przejrzany).
- Po każdym zamkniętym tasku odhacz pozycje w sekcji 11 (LISTA TODO).
- Weryfikacja po każdym tasku: `gradlew.bat :app:testDebugUnitTest`,
  `gradlew.bat :app:assembleDebug`, `grep -rn "TODO\|FIXME\|@Ignore" app/src`,
  porównanie raportu agenta z checklistą DoD.

---------------------------------------------------------------------------------------
## 1. DECYZJE PROJEKTOWE (przeczytaj ZANIM napiszesz linijkę kodu)
---------------------------------------------------------------------------------------

### D1. GREENFIELD — zero kompatybilności wstecznej
Aplikacja jest pisana od zera i NIE MA starych użytkowników ani starych danych.
- NIE czytamy AsyncStorage starej apki, NIE importujemy jej backupów.
- Nazwy pól serializowanych wynikają z nowych modeli (sekcja 4) — nie z TypeScriptu.
- Room startuje na wersji schematu 1 — ZERO klas Migration. W devie dozwolony
  destructive fallback.
- Stare repo `../TensorDeck` służy WYŁĄCZNIE jako specyfikacja zachowania (co apka ma
  robić) i źródło testów (jak to sprawdzić). Jego struktura plików jest bez znaczenia.

### D2. BEZ CHMURY W v1 — żadnego Firebase, kont, logowania, syncu
Stara apka miała Firestore, bo była też appką WEBOWĄ i chmura była mostem web↔telefon.
Nowa apka jest Android-only, więc:
- ŻADNEJ zależności od Firebase (SDK, google-services.json, auth, Firestore).
- Bezpieczeństwo danych zapewniają dwa mechanizmy:
  a) natywny **Android Auto Backup** (system sam trzyma kopię danych apki na Dysku
     Google użytkownika i przywraca przy reinstalacji/nowym telefonie) — konfiguracja
     w T1, weryfikacja w T10;
  b) ręczny **eksport/import backupu JSON** przez SAF (ekran ustawień, T9).
- Konsekwencje kaskadowe (WAŻNE — upraszczają połowę projektu):
  - BRAK silnika merge (scalanie lokalne↔chmura) — nie portujemy `merge.ts`.
  - BRAK tombstonów: `deletedAt` NIE istnieje w modelu. Usunięcie karty/talii/trybu
    = twarde DELETE z bazy (w transakcji). Purge archiwum = twarde DELETE.
  - BRAK kolumny `namespace` (gość/konto) — jest jeden lokalny zbiór danych.
  - BRAK kolejki/debounce zapisów do chmury, progu 10 ocen, flushy lifecycle —
    Room zapisuje transakcyjnie i natychmiast przy każdej akcji. Kropka.
  - BRAK sekcji "Konto" w ustawieniach, dialogów logowania, banera syncu.
- Sync może wrócić jako Faza 2 (sekcja 14) — dlatego:
  - ZOSTAWIAMY w modelu pola `contentUpdatedAt`, `srsUpdatedAt` (Card) i `updatedAt`
    (Deck, StudyMode) i STEMPLUJEMY je poprawnie w operacjach (koszt ~zero, a bez nich
    późniejszy sync nie miałby metadanych do rozstrzygania konfliktów).

### D3. Zachowanie logiki = IDENTYCZNE ze starą apką
SRS (FSRS), dopasowanie mowy, silnik sesji nauki, normalizacja danych, import CSV,
selektory i statystyki mają zachowywać się DOKŁADNIE tak samo. Stare testy jednostkowe
portujemy test-po-teście (wyjątki: testy tombstonów/merge/migracji/syncu — wycięte
funkcje; każdy pominięty test wymieniasz w raporcie z powodem).

### D4. Stack i wersje
- Kotlin 2.x (stable), AGP stable, Compose BOM stable, Material 3.
- Navigation-Compose (typowane trasy @Serializable) + natywny predictive back.
- Hilt (DI), Room (baza = źródło prawdy), DataStore Preferences (ustawienia),
  kotlinx.serialization (JSON: kroki trybów, strony kart, backup).
- Media3 (MediaSessionService — nauka w tle), android.speech (TTS + SpeechRecognizer).
- Testy: JUnit + kotlinx-coroutines-test (domain, JVM), Robolectric (Room, DAO).
- minSdk 26, compileSdk/targetSdk = aktualne stable. JEDEN moduł Gradle `app`.
- `domain/` = czysty Kotlin, ZERO importów Androida (twarda granica).

### D5. Identyfikacja i defaulty
- applicationId `com.raycoll.app`, debug z suffiksem `.debug`. Nazwa: RayColl.
- v1 = phone-first (portret; bez navigation rail / layoutów tabletowych).
- UI w 5 językach: pl (główny), en (domyślny fallback), de, es, it — tłumaczenia
  bierzemy GOTOWE z plików `TensorDeck/src/i18n/locales/*.ts` (nie tłumaczysz sam).
- Ikony/splash: assets ze starej apki (`TensorDeck/assets/images/*`).
- Konwencje wewnętrzne: timestampy = epoch millis (Long); klucz dnia heatmapy =
  lokalna data `yyyy-MM-dd`; id = UUID v4 string (`UUID.randomUUID().toString()`).

### D6. Celowo POMINIĘTE w v1 (nie implementuj; nie dodawaj "przy okazji")
Chmura/konta/sync (→ Faza 2, sekcja 14); warstwa web; rail/tablety/landscape;
OTA updates; WorkManager; Paging 3; Encrypted DataStore; App Shortcuts; Android Auto;
shared element transitions; changelog/update-notification; rejestracja kont;
jakiekolwiek tombstony i pola `deletedAt`.

---------------------------------------------------------------------------------------
## 2. CZYM JEST TA APLIKACJA — SPEC PRODUKTU
---------------------------------------------------------------------------------------

RayColl = aplikacja do nauki fiszek z powtórkami rozłożonymi w czasie (SRS/FSRS)
i naciskiem na naukę języków przez słuchanie (TTS) i mówienie (STT).

Fundamentalne pojęcia:
- **Talia (Deck)** — nazwana kolekcja fiszek. Ma: nazwy stron, języki stron (BCP-47,
  np. `en-US`), liczbę aktywnych stron (2–5), filtr nauki, kolejność kart, aktywny
  tryb nauki. Talia może być zarchiwizowana (znika z dashboardu, po 14 dniach jest
  trwale usuwana).
- **Fiszka (Card)** — lista 2–20 stron tekstowych (strona = "strona kartki", np.
  [fraza EN, tłumaczenie PL, przykład]). Widocznych w nauce jest `activePageCount`
  pierwszych stron; reszta jest ukryta, ale NIE ginie. Każda fiszka ma stan SRS.
- **SRS/FSRS** — po każdej ocenie (1=Again, 2=Hard, 3=Good, 4=Easy) algorytm FSRS
  wylicza nowy interwał; fiszka "wraca" gdy `nextReviewTimestamp <= now`.
  Kategorie fiszek: new / learning / review / mastered (sekcja 6.1).
- **Tryb nauki (StudyMode)** — PROGRAM złożony z kroków (DSL): co pokazać, co
  przeczytać na głos, kiedy słuchać użytkownika, kiedy pokazać przyciski ocen.
  17 kroków atomowych + 9 rodzajów kroków złożonych (compound), rozwijanych do
  atomowych. Są 2 tryby wbudowane (Klasyczny, Audio) + tryby własne użytkownika.
- **Sesja nauki** — przejście przez należne (due) karty talii wg kroków aktywnego
  trybu. Karty ocenione na 1 trafiają na listę "failed" (można powtórzyć same oblane).
- **Nauka w tle** — dla trybów hands-free (bez ocen ręcznych i STT) sesja gra dalej
  po zablokowaniu ekranu; notyfikacja medialna (Media3) steruje sesją
  (pauza/wznów/następna/poprzednia/stop).
- **Heatmapa aktywności** — licznik ocenionych kart per dzień; z niej liczony streak.
- **Import CSV** — wklejenie/plik; auto-detekcja separatora, nagłówka, liczby stron
  i języków; edytowalny podgląd; tworzy nową talię.
- **Backup** — eksport/import całego stanu jako JSON przez systemowy picker plików.

Persystencja: Room = jedyne źródło prawdy, każda akcja zapisuje się transakcyjnie
od razu. Ustawienia UI w DataStore. Android Auto Backup obejmuje bazę i ustawienia.

---------------------------------------------------------------------------------------
## 3. SPEC EKRANÓW — ZACHOWANIE PUNKT PO PUNKCIE
---------------------------------------------------------------------------------------

Nawigacja: 9 tras (typowane @Serializable): Dashboard (start), Study(deckId),
Browse(deckId), DeckSettings(deckId), ModeDetail(modeId?), Import, Stats, Archive,
AppSettings. Predictive back działa na każdej trasie; ekran Study przechwytuje back
dialogiem potwierdzenia gdy sesja aktywna.

### 3.1 Dashboard (start)
- Grid/lista talii (tylko żywe i niezarchiwizowane), stabilna kolejność utworzenia.
- Karta talii: nazwa, segmentowy pasek postępu (4 kategorie kart w kolorach
  semantycznych, proporcjonalnie), liczba należnych kart, przycisk Study, przycisk
  Browse, menu wyboru aktywnego trybu nauki (lista trybów; wybór zapisuje się w talii
  i przeżywa restart), menu talii (Ustawienia, Archiwizuj).
- Study przy 0 należnych kart → wejście pokazuje ekran "brak kart na dziś" (3.2).
- FAB "+" → Import. W top bar wejścia do Stats, Archive, AppSettings.
- Stan pusty (0 talii): ilustracja + CTA do importu. Pojawia się DOPIERO gdy
  użytkownik usunie/zarchiwizuje wszystkie talie.

### 3.2 Study (sesja nauki)
Wejście: dueCards = karty wg filtra talii + kolejności talii (sekcja 6.2). Kroki =
rozwinięte kroki aktywnego trybu, przefiltrowane z kroków wskazujących strony
>= activePageCount. Sesja startuje automatycznie.
- Brak należnych kart → stan "wszystko powtórzone" z przyciskiem powrotu.
- Nagłówek: nazwa talii, segmentowy pasek postępu sesji (segment per karta:
  past/current/future, kolor = kategoria karty) + licznik "x/y".
- Karta: widoczne strony wg stanu sesji (strona 0 zawsze widoczna od startu karty);
  auto-scroll do najnowiej odsłoniętej strony; adaptacyjny rozmiar czcionki wg
  długości tekstu (progi w sekcji 5); wskaźnik audio przy stronie (ikona: mówi/słucha).
- INTERAKCJE (pełna tabela co robi tap/hold — sekcja 6.5):
  - tap odsłania kolejne strony w gate'ach; pomija TTS/pauzy; zatrzymuje nasłuch STT
    (bez oceniania!); NIC nie robi na ekranie ocen;
  - przytrzymanie ≥500 ms = podgląd (peek) następnej ukrytej strony; puszczenie
    <500 ms = trwałe odsłonięcie; ≥500 ms = strona chowa się z powrotem;
  - trzymanie karty BLOKUJE przejście do następnej karty (bez limitu czasu).
- Panel STT (podczas/po nasłuchu): rozpoznany tekst na żywo (partial), po zakończeniu
  procent dopasowania + próg + status (correct/incorrect/skipped/error).
- Przyciski ocen (tylko gdy status revealed): 1/2/3/4 z kolorami semantycznymi
  i haptyką; ocena → zapis SRS + heatmapa dziś +1 → następna karta.
- Przyciski poprzednia/następna: następna = pominięcie reszty karty; poprzednia
  w ciągu 3 s od startu karty = cofnięcie do poprzedniej karty, później = restart
  bieżącej od kroku 0 (bez zapisu SRS). Ponowna ocena cofniętej karty NADPISUJE
  poprzednią (SRS liczony od stanu sprzed pierwszej oceny, heatmapa BEZ drugiego +1).
- Błędy TTS/STT: komunikat (snackbar/dialog) z i18n, sesja nie crashuje; brak głosu
  TTS dla języka → dialog z przyciskiem do systemowych ustawień TTS (intent
  ACTION_INSTALL_TTS_DATA).
- Koniec sesji: ekran podsumowania (pasek kategorii, statystyki) + przyciski:
  "Powtórz oblane" (tylko gdy failed > 0), "Od nowa", "Wróć". Wariant endedEarly
  (przerwano) różni się nagłówkiem.
- Back/gest podczas aktywnej sesji → dialog "przerwać naukę?"; potwierdzenie kończy
  sesję (endedEarly). Ekran trzyma keep-screen-on podczas aktywnej sesji.
- NAUKA W TLE (tylko gdy włączona w ustawieniach ORAZ tryb jest hands-free):
  zablokowanie ekranu/zejście w tło NIE przerywa sesji; notyfikacja medialna:
  tytuł = front bieżącej karty, drugi wiersz = nazwa talii, licznik x/y; przyciski
  play/pauza/następna/poprzednia/stop sterują sesją; kroki interaktywne są w tle
  zastępowane pauzami (sekcja 6.6); auto-pauza (błąd, wymagana interakcja) zmienia
  notyfikację na "wstrzymane"; swipe apki z recents = stop wszystkiego; tryb
  nie-hands-free w tle = zwykła pauza sesji.

### 3.3 Browse (przegląd talii)
- Lista żywych kart talii; nagłówek z segmentowym paskiem statystyk talii,
  odświeżanym po każdej zmianie.
- Pole szukania: filtruje po WSZYSTKICH widocznych stronach karty (case-insensitive).
- Chipy filtrów: wszystkie / new / learning / review / mastered — z licznikami.
- Edycja karty (dialog/arkusz): pole tekstowe per WIDOCZNA strona (etykiety = nazwy
  stron); zapis disabled gdy wypełnione < 2 pola; anulowanie nie zmienia karty;
  UKRYTE strony karty pozostają nietknięte przy zapisie.
- FAB "+": tworzy pustą kartę i od razu otwiera jej edycję.
- Usunięcie karty: menu → dialog potwierdzenia → twarde usunięcie.

### 3.4 Deck settings (ustawienia talii)
- Nazwa talii: zapis przy utracie fokusu (NIE per znak).
- Strony: lista aktywnych stron — nazwa (zapis na blur) + język (dropdown z listy
  języków; zapis od razu po wyborze) + strzałki góra/dół zamieniające strony miejscami
  (zamiana obejmuje nazwy + języki + ZAWARTOŚĆ wszystkich kart — transakcyjnie).
- Liczba aktywnych stron: stepper 2..5. Zmniejszenie NIE kasuje zawartości ukrytych
  stron; zwiększenie pokazuje je z powrotem z danymi.
- Filtr nauki (4 opcje) i kolejność kart (3 opcje): zapis od razu.
- Aktywny tryb nauki: wybór z listy (żywe tryby).
- Archiwizuj talię: dialog potwierdzenia → archiwizacja → powrót na dashboard.
- Usuń talię: dialog z polem tekstowym — trzeba wpisać token zależny od języka UI
  (PL: USUŃ, EN: DELETE, DE: LÖSCHEN, ES: BORRAR, IT: ELIMINA); usunięcie twarde,
  await na zapisie; błąd zapisu → zostań na ekranie + komunikat.

### 3.5 Edytor trybów nauki (ModeDetail + lista w DeckSettings)
- Lista trybów: wbudowane (badge "wbudowany"; akcja "Przywróć domyślne" nadpisuje
  kroki i nazwę seedem zachowując id) i własne (usuwanie z potwierdzeniem).
- Nowy tryb: z szablonu (sekcja 6.4 — szablony) albo pusty; zapis ustawia go jako
  aktywny w talii, z której przyszedł użytkownik.
- Edytor kroków wybranego trybu:
  - lista kroków z czytelnym opisem (nazwa typu + parametry, np. "Przeczytaj stronę 1",
    "Czekaj 500 ms", warunek jeśli jest: "jeśli dobrze/źle");
  - reorder strzałkami góra/dół; usuwanie kroku;
  - dialog dodawania kroku atomowego: wybór typu (pogrupowane kategoriami:
    wyświetlanie / audio / czas / ocena / przepływ), pola parametrów z defaultami
    i clampami (sekcja 6.4), wybór warunku (zawsze / jeśli dobrze / jeśli źle);
  - dialog kroku złożonego (compound): wybór rodzaju (9), parametry, edycja obu
    gałęzi (correct/wrong: feedback, czytanie strony, pauza, odsłonięcie, mark failed,
    ocena, przejście dalej);
  - walidacja przed zapisem (sekcja 6.4) — błędy pokazane przy krokach, zapis
    zablokowany dopóki tryb niepoprawny.

### 3.6 Import
- Źródło: duże pole tekstowe (wklej) LUB przycisk pliku (SAF OpenDocument:
  text/csv, text/plain, text/markdown; czytamy jako UTF-8).
- Po wprowadzeniu tekstu automatycznie: detekcja separatora (przełączalna ręcznie:
  tab/średnik/przecinek/pipe), detekcja "pierwszy wiersz to nagłówek" (checkbox
  z auto-sugestią), liczba stron, nazwy stron i języki z nagłówka (edytowalne
  ręcznie: nazwa + dropdown języka per strona).
- Podgląd wierszy (przyszłych kart): edycja komórki i usunięcie wiersza możliwe;
  edycja w podglądzie aktualizuje tekst źródłowy i odwrotnie (jedno źródło prawdy).
- Nazwa nowej talii: wymagana (puste → przycisk Import disabled). Brak poprawnych
  wierszy → też disabled.
- Import: walidacja payloadu (sekcja 6.7) → utworzenie talii z kartami (wszystkie
  karty jako "new") → powrót na dashboard; błąd walidacji → komunikat, nic nie zapisane.

### 3.7 Stats
- Heatmapa aktywności (siatka dni, ~ostatni rok; intensywność koloru wg liczby
  ocen w dniu; dzisiejszy dzień wyróżniony).
- Streak (dni z rzędu z aktywnością, licząc od dziś lub wczoraj — sekcja 6.2).
- Sumy globalne: total/new/learning/review/mastered ze wszystkich żywych talii.
- Lista talii z ich paskami postępu.

### 3.8 Archive
- Lista zarchiwizowanych talii (sort: najnowiej zarchiwizowana pierwsza).
- Każda pozycja: nazwa, liczba kart, countdown "zniknie za X dni" (14 dni od
  archiwizacji), przycisk Przywróć.
- Po upływie retencji talia jest TRWALE usuwana (purge przy starcie apki i co godzinę).
- Stan pusty: komunikat.

### 3.9 App settings
- Motyw: jasny/ciemny/systemowy (natychmiastowa zmiana, persystowana).
- Kolory dynamiczne (Material You): toggle (default ON; działa na Androidzie 12+,
  poniżej — ukryty/disabled, kolory brandowe).
- Język UI: 5 opcji; zmiana natychmiastowa (per-app locale), persystowana.
- Szybkość lektora (ttsRate): slider 0.5–2.0 (krok 0.1), default 1.0.
- Odtwarzanie w tle: toggle (default OFF) — steruje nauką w tle (3.2).
- Backup: "Eksportuj dane" → SAF CreateDocument, plik
  `raycoll-backup-YYYY-MM-DD-HH-mm.json`; "Importuj dane" → SAF OpenDocument →
  walidacja → CAŁKOWITE zastąpienie stanu (transakcja); błędny plik → komunikat
  i stan nietknięty.
- "Przywróć dane fabryczne": dialog potwierdzenia → stan = seed (2 talie + 2 tryby),
  heatmapa pusta.
- Wersja aplikacji (nazwa + build).

---------------------------------------------------------------------------------------
## 4. MODEL DANYCH — POLA, TYPY, NIEZMIENNIKI
---------------------------------------------------------------------------------------

Wszystkie klasy w `domain/model`. Serializacja kotlinx.serialization. Timestampy Long
(epoch millis). BEZ pól `deletedAt` (decyzja D2 — twarde usuwanie).

### 4.1 SrsState
| pole | typ | zakres/uwagi |
|---|---|---|
| difficulty | Double | 1.0–10.0 |
| stability | Double | dni; pełni rolę interwału |
| repetitions | Int | licznik ocen |
| state | Int | 0=New, 1=Learning, 2=Review, 3=Relearning |
| lastReviewTimestamp | Long | 0 = nigdy |
| nextReviewTimestamp | Long | 0 = od razu należna |

Nowa fiszka: difficulty=5.0, stability=0.0, repetitions=0, state=0, oba timestampy 0.

### 4.2 Card
| pole | typ | uwagi |
|---|---|---|
| id | String | UUID v4 |
| pages | List<String> | 2..20 elementów; w Room jako JSON string |
| srsState | SrsState | w Room płaskie kolumny |
| contentUpdatedAt | Long | stempel edycji treści (D2: zostaje pod Fazę 2) |
| srsUpdatedAt | Long | stempel ostatniej oceny |

### 4.3 Deck
| pole | typ | uwagi |
|---|---|---|
| id | String | UUID v4 |
| name | String | trim, niepusta |
| cards | List<Card> | w Room osobna tabela z FK |
| activeModeId | String | id trybu nauki (default: 'classic') |
| studyFilter | enum CardFilter | all / new / review / new+review (default new+review) |
| cardOrder | enum CardOrder | sequential / random / hardest (default sequential) |
| pageNames | List<String> | długość = storedPageCount |
| pageLanguages | List<String> | BCP-47; długość = storedPageCount |
| activePageCount | Int | 2..min(5, storedPageCount) |
| updatedAt | Long | stemplowane przy każdej zmianie talii |
| archivedAt | Long? | null = aktywna; >0 = w archiwum |

### 4.4 StudyMode
| pole | typ | uwagi |
|---|---|---|
| id | String | UUID v4 (wbudowane: 'classic', 'listen-speak') |
| name | String | |
| steps | List<ModeStep> | w Room jako JSON string |
| isBuiltIn | Boolean | true tylko z poprawnym builtInSourceId |
| builtInSourceId | String? | 'classic' / 'listen-speak' |
| updatedAt | Long | |

### 4.5 ModeStep — sealed interface (JSON: dyskryminator `type`)
KROKI ATOMOWE (17) — parametry w nawiasach:
`show_page(pageIndex)`, `show_all_pages`, `wait_for_tap_to_reveal_next`,
`wait_for_tap_to_reveal`, `show_ratings`, `speak_page(pageIndex)`, `speak_all_pages`,
`dynamic_pause(nextPageIndex, pauseMultiplier)`, `wait(ms)`,
`listen_and_check(pageIndex, successThreshold)`, `feedback_success`, `feedback_error`,
`auto_rate_from_answer`, `auto_rate_fixed(rating)`, `mark_failed`, `wait_for_tap`,
`next_card`.
Każdy krok atomowy MOŻE mieć opcjonalne pole `condition`: `correct` | `wrong`.

KROK COMPOUND: `{ "type": "compound", "version": 1, "params": {...} }`, gdzie params
ma dyskryminator `kind` (9 rodzajów): `present_front(page, speak)`,
`flip_reveal(revealStyle: all|next)`, `show_all_grade`,
`speak_pause_next(page, nextPage, multiplier)`,
`auto_flip(questionPage, answerPage, multiplier, speakQuestion, speakAnswer)`,
`listen_grade(answerPage, threshold, onCorrect, onWrong, manualFallback)`,
`grade_after_listen(onCorrect, onWrong, manualFallback)`,
`auto_pass_next(rating)`, `fail_next`.
CompoundBranch: `{feedback: Bool, speakPage: Int?, pause: CompoundPause?,
revealAll: Bool, markFailed: Bool, rate: fromAnswer|fixed|none, fixedRating: Int,
advance: Bool}`. CompoundPause: `{kind: fixed, ms}` | `{kind: dynamic, page, multiplier}`.

UWAGA kotlinx.serialization: dyskryminator klasowy dla ModeStep = `"type"`,
dla CompoundParams = `"kind"` (osobne @JsonClassDiscriminator). Round-trip bez strat.

### 4.6 StoreSnapshot (backup / seed / reset)
`{ groups: List<Deck>, studyModes: List<StudyMode>, activityHeatmap: Map<String, Int> }`
Backup JSON = dokładnie ta struktura (pretty print). Import backupu: parse → normalize
→ validate → transakcyjne zastąpienie całego stanu.

### 4.7 Niezmienniki normalizacji (DataNormalizer — wywoływana na granicach:
import backupu, seed, odczyt uszkodzonych rekordów)
- `storedPageCount` = max(pageNames.size, pageLanguages.size, max stron w kartach,
  MIN_PAGE_COUNT), cap MAX_STORED_PAGE_COUNT.
- pageNames/pageLanguages dopełniane do storedPageCount wartościami "Page N" / "en-US".
- activePageCount = clamp(wartość lub storedPageCount, 2..min(5, storedPageCount)).
- studyFilter/cardOrder spoza enum → default.
- srsState.state spoza {0,1,2,3} → 0.
- steps: elementy nieznanego typu ODRZUCANE; pola liczbowe clampowane wg rejestru
  kroków (sekcja 6.4); `condition` spoza {correct,wrong} usuwany; compound params
  normalizowane z defaultami gałęzi; `steps` nie-lista → pusta lista.
- isBuiltIn=true wymaga builtInSourceId ze zbioru seedów; inaczej isBuiltIn=false.
- heatmapa: wpisy z nie-skończoną/ujemną liczbą odrzucane.

### 4.8 Walidacja backupu (BackupValidator — twarde błędy, import odrzucony)
- Struktura root: groups[], studyModes[], activityHeatmap{} — wymagane.
- Deck: id/name string, cards[], pageNames[], pageLanguages[], activeModeId string.
- Card: id string, pages[] stringów, kompletny srsState (liczby skończone).
- StudyMode: id/name string, steps[] — każdy krok przechodzi walidację typu i pól
  (w tym compound z gałęziami); isBuiltIn/builtInSourceId spójne.
- Heatmapa: wartości = liczby skończone >= 0.
- Komunikaty błędów zawierają id/nazwę problematycznego rekordu.

---------------------------------------------------------------------------------------
## 5. STAŁE I LICZBY MAGICZNE — KOMPLETNA TABELA
---------------------------------------------------------------------------------------

| stała | wartość | znaczenie |
|---|---|---|
| FSRS W[0..16] | 0.4, 0.6, 2.4, 5.8, 4.93, 0.94, 0.86, 0.01, 1.49, 0.14, 0.94, 2.18, 0.05, 0.34, 1.26, 0.29, 2.61 | parametry FSRS v4.5 |
| FSRS DECAY | -0.5 | krzywa zapominania |
| FSRS FACTOR | 0.9^(1/DECAY) − 1 | wyprowadzona |
| MIN_INTERVAL_DAYS | 0.1 | min. stability |
| MAX_INTERVAL_DAYS | 3650 | max. stability |
| MASTERED_MIN_REPS | 3 | state==2 && reps>=3 → mastered |
| MIN_PAGE_COUNT | 2 | min stron talii |
| MAX_VISIBLE_PAGE_COUNT | 5 | max aktywnych stron |
| MAX_STORED_PAGE_COUNT | 20 | max przechowywanych stron |
| DEFAULT_STUDY_FILTER | new+review | filtr nauki |
| DEFAULT_CARD_ORDER | sequential | kolejność kart |
| MAX_PAUSE_MULTIPLIER | 5 | clamp mnożnika pauzy |
| STEP_DEFAULT_THRESHOLD | 70 | próg listen_and_check |
| STEP_DEFAULT_WAIT_MS | 500 | default kroku wait |
| STEP_DEFAULT_RATING | 3 | default auto_rate_fixed |
| RATING_RANGE | 1..4 | oceny |
| MATCH_EASY / GOOD / HARD | 85 / 60 / 40 | mapowanie % na ocenę (bez progu) |
| WORD_MATCH_MIN | 0.5 | min podobieństwo słowa (word similarity) |
| PEEK_HOLD_THRESHOLD_MS | 500 | próg peek vs trwałe odsłonięcie |
| PREVIOUS_GOES_BACK_WINDOW_MS | 3000 | okno "poprzednia karta" |
| DYNAMIC_PAUSE_MS_PER_CHAR | 60 | pauza = len × 60 × multiplier |
| STT_SOFT_TIMEOUT_MS | max(5000, lastTtsDuration×3) | miękki timeout nasłuchu |
| STT_HARD_EXTRA_MS | +10000 | twardy timeout = soft + 10000 |
| BG_WAIT_SUBSTITUTE_MS | clamp(sumaZnaków×120, 1500..15000) | zastępstwo tap-gate w tle |
| CARD_FONT_TIERS | 70 / 140 / 260 znaków | progi zmniejszania czcionki karty |
| ARCHIVE_RETENTION_DAYS | 14 | retencja archiwum |
| ARCHIVE_PURGE_INTERVAL | 60 min | częstotliwość purge |
| STREAK_MAX_DAYS | 365 | limit liczenia streaka |
| TTS_RATE_RANGE | 0.5–2.0 (default 1.0) | szybkość lektora |
| SEPARATORS | tab \t, semicolon ;, comma , , pipe \| | kandydaci separatora CSV |
| SEPARATOR_DEFAULT | semicolon | fallback detekcji |
| SEP_DETECT_ROWS | 5 | wierszy do detekcji separatora |
| HEADER_CONFIDENCE_MIN | 0.5 | próg "pierwszy wiersz to nagłówek" |
| LANG_CODE_REGEX | ^[a-z]{2,3}(-[A-Za-z0-9]{2,8})?$ | walidacja kodu języka importu |
| BACKUP_FILENAME | raycoll-backup-YYYY-MM-DD-HH-mm.json | wzorzec eksportu |
| DELETE_TOKENS | PL USUŃ / EN DELETE / DE LÖSCHEN / ES BORRAR / IT ELIMINA | potwierdzenie usunięcia talii |

---------------------------------------------------------------------------------------
## 6. ALGORYTMY
---------------------------------------------------------------------------------------

### 6.1 FSRS (port 1:1 z `TensorDeck/src/srs/srsEngine.ts`)
`calculateFsrs(current: SrsState, rawRating: Int, now: Long): SrsState`
- rating = clamp(round(rawRating), 1, 4); elapsedDays = max((now − lastReview)/86400000, 0)
  (lastReview==0 → elapsed traktowany jak 0 przez `now - (last || now)`).
- Jeśli state==0 LUB stability<=0 (pierwsza ocena):
  D = clamp(W4 − (rating−3)·W5, 1, 10); S = max(W[rating−1], 0.1);
  state = (rating==1 ? 1 : 2).
- W przeciwnym razie:
  D' = d − W6·(rating−3);  D = clamp(W7·initD(3) + (1−W7)·D', 1, 10)  gdzie
  initD(3) = clamp(W4 − 0·W5,1,10) = W4;
  R = (1 + FACTOR·elapsed/S)^DECAY  (S<=0 → R=0);
  rating==1 → S = W11·D^(−W12)·((S+1)^W13 − 1)·e^((1−R)·W14); state=3;
  rating>1  → S = S·(1 + e^W8·(11−D)·S^(−W9)·(e^((1−R)·W10) − 1)·hard·easy);
              hard = (rating==2 ? W15 : 1); easy = (rating==4 ? W16 : 1); state=2.
- S = clamp(S, 0.1, 3650); nextReview = now + S·86400000; repetitions+1;
  lastReview = now.
Kategoria karty: state 0→new; 1|3→learning; 2→ (reps>=3 ? mastered : review).

### 6.2 Selektory
- filterCards(cards, filter, now): all→wszystkie; new→state==0;
  review→state>0 && nextReview<=now; new+review→state==0 || nextReview<=now.
- orderCards: sequential→bez zmian; random→Fisher-Yates; hardest→sort difficulty
  malejąco, remis: nextReview rosnąco, remis: index pierwotny.
- dueCards = orderCards(filterCards(...)).
- progres talii (%) = round(100 × liczba kart z reps>=1 / liczba kart); 0 kart → 0.
- computeCardStats: liczniki kategorii + total.
- streak: jeśli dziś jest wpis → 1 + kolejne dni wstecz z wpisem; jeśli dziś brak —
  liczymy od wczoraj wstecz (dzisiejszy dzień nie zeruje streaka); pętla max 365.
- browse filter: search po widocznych stronach (contains, ignoreCase) + kategoria.
- archive countdown: dni do usunięcia = ceil((archivedAt + 14 dni − now)/dzień), min 0.
- widoczne strony karty: pages dopełnione '' do activePageCount i przycięte do niego.

### 6.3 Dopasowanie mowy (SpeechMatcher)
- normalizeText: lowercase → usuń wszystko poza literami/cyframi/spacjami (Unicode!)
  → zbij wielokrotne spacje → trim.
- levenshtein: klasyczny DP (dwa wiersze).
- globalSim = (1 − lev(a,b)/max(len)) × 100 (obie puste → 100; oryginał pusty →
  100 gdy rozpoznany pusty, inaczej 0).
- wordSimilarity: dla każdego słowa oryginału znajdź NAJLEPSZE nieużyte słowo
  rozpoznane wg 1−lev/maxLen; licz do sumy tylko gdy > 0.5; wynik =
  suma dopasowań / liczba słów oryginału × 100.
- matchSpeech = round(max(globalSim, wordSimilarity)).
- mapMatchToRating(percent, threshold?):
  z progiem: percent<t → 1; easyT = (t>=100 ? 100 : t + ceil((100−t)/2));
  percent>=easyT → 4; inaczej 3. Bez progu: >=85→4, >=60→3, >=40→2, inaczej 1.

### 6.4 Kroki trybów — rejestr, walidacja, szablony, ekspansja compound
REJESTR (typ → kategoria → pola z clampami):
- wyświetlanie: show_page(pageIndex: 0..pageCount−1, def 0), show_all_pages,
  wait_for_tap_to_reveal_next, wait_for_tap_to_reveal
- audio: speak_page(pageIndex), speak_all_pages,
  listen_and_check(pageIndex; successThreshold 0..100 def 70),
  feedback_success, feedback_error
- czas: dynamic_pause(nextPageIndex; pauseMultiplier 0..5 def 1), wait(ms >=0 def 500)
- ocena: show_ratings, auto_rate_from_answer, auto_rate_fixed(rating 1..4 def 3),
  mark_failed
- przepływ: wait_for_tap, next_card

WALIDACJA trybu (przed zapisem w edytorze): nazwa niepusta; steps niepuste;
każdy krok poprawnego typu z polami w zakresach; indeksy stron < pageCount talii
kontekstu (edytor clampuje na bieżąco).

SZABLONY nowego trybu (port z `modeTemplates.ts` — przeczytaj plik):
minimum "Klasyczny" i "Audio" jako punkty startowe + pusty.

EKSPANSJA COMPOUND → atomowe (dokładnie w tej kolejności):
- present_front(p, speak): show_page(p) [+ speak_page(p) gdy speak]
- flip_reveal(all): wait_for_tap_to_reveal, show_all_pages, show_ratings;
  flip_reveal(next): wait_for_tap_to_reveal_next, show_ratings
- show_all_grade: show_all_pages, show_ratings
- speak_pause_next(p, n, m): speak_page(p), dynamic_pause(n, m), show_page(n)
- auto_flip(q, a, m, sq, sa): show_page(q) [+speak_page(q)], dynamic_pause(q, m),
  show_all_pages [+speak_page(a)]
- listen_grade(a, t, onC, onW, mf): listen_and_check(a, t), expand(onC,'correct'),
  expand(onW,'wrong') [+ show_all_pages, show_ratings gdy mf]
- grade_after_listen(onC, onW, mf): jak wyżej bez listen_and_check
- auto_pass_next(r): auto_rate_fixed(r), next_card
- fail_next: mark_failed, auto_rate_fixed(1), next_card
Ekspansja GAŁĘZI (wszystkie kroki z condition gałęzi):
feedback_(success|error) → speak_page(speakPage) → wait(ms)/dynamic_pause(page, mult)
→ show_all_pages (revealAll) → mark_failed (markFailed) →
auto_rate_from_answer / auto_rate_fixed(fixedRating) (rate) → next_card (advance).
Defaulty gałęzi: correct = {feedback:true, revealAll:true, rate:fromAnswer,
fixedRating:3, advance:true}; wrong = {feedback:true, revealAll:true, markFailed:true,
rate:fixed, fixedRating:1, advance:true}.

### 6.5 Silnik sesji nauki (SessionEngine) — semantyka
STAN (StateFlow, czysty reducer): status (idle/speaking/listening/revealed/finished/
error), currentCardIndex, currentStepIndex, revealedPages: List<Int>,
peekedPageIndex: Int?, lastAnswerResult {status: none/correct/incorrect/skipped/error,
text, percent?, threshold?, pageIndex?}, audioPageIndex: Int?, interactionGate
{kind: none/tap_to_reveal; revealMode: single/remaining/none; continueStepIndex},
pendingStepIndexToRun: Int?, errorMsg?.

AKCJE reducera (20): START_SESSION, SET_CURRENT_STEP, START_SPEAKING, END_SPEAKING,
START_LISTENING, UPDATE_PARTIAL_STT (ignorowana gdy status != listening),
SET_LAST_ANSWER_RESULT, REVEAL_PAGES, REVEAL_PAGE, REVEAL_NEXT_PAGE_IN_GATE
(REVEAL_* czyszczą peek jeśli podglądana strona właśnie się odsłoniła), SHOW_RATINGS,
FINISH_SESSION, ADVANCE_CARD (pełny reset stanu karty z nowym indexem),
START_TAP_REVEAL_GATE, COMPLETE_INTERACTION_GATE, SET_PENDING_STEP_INDEX,
CONSUME_PENDING_STEP_INDEX, SET_ERROR, CLEAR_ERROR (czyści TYLKO komunikat, NIE status),
PEEK_SET, PEEK_CLEAR.

RUNNER (StepExecutor) = głupi interpreter listy kroków atomowych:
- Krok z `condition`: correct pasuje tylko do lastAnswer.status==correct, wrong tylko
  do incorrect; skipped/error/none NIE pasują NIGDY → krok pominięty (idź dalej).
- Krok 0 karty = reset: stan oceny karty 'none', wynik odpowiedzi, odsłonięcia.
- Koniec listy kroków / abort = STOP. Runner NICZEGO nie zgaduje (żadnego
  automatycznego show_ratings na końcu — niekompletny tryb to błąd konfiguracji).
- Zachowania kroków: patrz sekcja 6.4 + te reguły:
  - wait_for_tap_to_reveal(_next): jeśli wszystkie aktywne strony już odkryte →
    przejdź dalej BEZ gate'a; inaczej otwórz gate i STOP.
  - show_ratings: jeśli karta już oceniona → no-op, dalej; inaczej SHOW_RATINGS i STOP.
  - speak_page: TTS strony w jej języku; po zakończeniu/przerwaniu → dalej (tap
    podczas TTS przerywa TYLKO audio, nie odsłania stron).
  - speak_all_pages: czyta kolejno aktywne strony; tap przerywa i POMIJA pozostałe.
  - dynamic_pause/wait: czekanie przerywalne tapem.
  - listen_and_check: STOP-krok audio; soft timeout max(5000, ttsDur×3), hard
    +10000; nasłuch kończy się wynikiem: skip(tap) → status 'skipped' (bez liczenia
    dopasowania!), błąd techniczny → 'error', inaczej percent = matchSpeech i status
    correct/incorrect wg progu. Krok SAM NIE ocenia (to robi auto_rate_from_answer).
  - auto_rate_from_answer: tylko gdy status correct/incorrect i karta nieoceniona →
    rating = mapMatchToRating(percent, threshold) → zapis oceny; dalej.
  - auto_rate_fixed: gdy nieoceniona → zapis oceny; dalej.
  - mark_failed: dodaje kartę do listy failed (bez oceny SRS); dalej.
  - next_card: czeka aż użytkownik puści kartę (hold), potem następna karta / FINISH.

TAP (handleCardPress) wg stanu:
| stan | efekt tapa |
|---|---|
| speaking / wait / dynamic_pause | skip kroku (przerwij audio/czekanie, idź dalej) |
| listening (STT) | stop nasłuchu → wynik 'skipped' → dalsze kroki (gałęzie się nie odpalą, manual fallback pokaże oceny) |
| gate revealMode=single | odsłoń następną ukrytą stronę + domknij gate + wznów od continueStepIndex |
| gate revealMode=remaining | odsłoń następną ukrytą; gdy po tym wszystko odkryte → domknij gate + wznów |
| gate revealMode=none | domknij gate + wznów |
| revealed (przyciski ocen) | NIC |
| finished | NIC |

HOLD (przytrzymanie karty):
- >=500 ms → PEEK_SET(następna ukryta strona); puszczenie: jeśli trzymano <500 ms →
  trwałe odsłonięcie tej strony; jeśli >=500 ms → PEEK_CLEAR (strona znów ukryta).
- Przez cały czas trzymania holding=true → advanceToNextCard czeka (`first{!it}`,
  BEZ timeoutu). Wyjście w tło / pauza / poprzednia karta zawsze zwalniają hold
  (inaczej advance wisiałby wiecznie).
- Peek podczas nasłuchu STT: odsłania podgląd, mikrofon dalej słucha, logika kroków
  nietknięta.

OCENA (rate(rating)):
- Ignorowana jeśli karta już oceniona w tej próbie... UWAGA: ręczna ocena z ekranu
  revealed jest jedyną ścieżką; cardReviewState chroni przed podwójną oceną
  (auto + manual). Haptyka wg oceny. rating==1 → karta na listę failed.
- PONOWNA ocena tej samej karty w tej samej próbie (po cofnięciu "poprzednia"):
  najnowsza ocena ZASTĘPUJE poprzednią — SRS liczony od stanu bazowego karty
  (snapshot sprzed pierwszej oceny), heatmapa NIE inkrementowana drugi raz,
  członkostwo w failed ustawiane od nowa. Tracking: zbiór kluczy "próba:cardId";
  restart/restartFailed otwiera nową próbę (czyści zbiór).
- Po ocenie → advanceToNextCard (z czekaniem na hold).

STEROWANIE:
- start(cards): reset, nowa próba; 0 kart → od razu FINISH.
- restart(): świeże wersje WSZYSTKICH kart sesji (po id z aktualnego stanu talii).
- restartFailed(): jw. ale tylko karty z listy failed (gdy niepusta).
- skipToNextCard(): przerwij łańcuch (anuluj Job + stop audio) → następna karta.
- goToPreviousCard(): <=3000 ms od startu karty i index>0 → poprzednia karta;
  inaczej → replay bieżącej od kroku 0 (bez zapisu SRS).
- pause(): no-op gdy revealed/gate/finished (w tle: revealed/gate → auto-pauza
  z callbackiem notyfikacji); inaczej anuluj łańcuch + stop audio. ZAWSZE zwalnia hold.
- resume(): replay bieżącej karty od kroku 0 (TTS/STT nie da się wznowić od połowy).
- end(): stop + FINISH (endedEarly w VM). stop(): anuluj wszystko (wyjście z ekranu).
- WSPÓŁBIEŻNOŚĆ: przebieg karty = coroutine Job; skip/pauza/replay/advance ANULUJĄ
  poprzedni Job — anulowany łańcuch nie może wykonać już żadnego efektu (odpowiednik
  runEpoch/aborted ze starego kodu). Dispatch reducera synchroniczny
  (MutableStateFlow.update). Auto-run karty od kroku 0 z blokadą lastExecutedCardIndex
  (żeby ADVANCE_CARD nie odpalał karty dwa razy); pendingStepIndex konsumowany PRZED
  wykonaniem.

ZALEŻNOŚCI (SessionDeps — interfejs, fake'i w testach): tts.speak(text, lang, rate)
suspend; stt.listen(lang, timeoutMs) suspend → wynik/partiale; feedback success/error;
haptyka; onCardReviewed(deckId, cardId, rating, replaceFromBase: SrsState?).

### 6.6 Nauka w tle — substytucje i cykl życia
- Tryb hands-free = steps niepuste ORAZ bez show_ratings ORAZ bez listen_and_check.
- Substytucje krokow w tle (gdy backgroundMode): wait_for_tap /
  wait_for_tap_to_reveal(_next) → pauza clamp(sumaZnakówAktywnychStron×120,
  1500..15000) ms, potem odsłonięcie zgodne z wariantem (next → jedna następna;
  reveal → wszystkie; goły wait_for_tap → nic) i dalej; show_ratings (karta
  nieoceniona) → SHOW_RATINGS + auto-pauza + callback (notyfikacja "wstrzymane");
  błąd TTS w tle → auto-pauza zamiast SET_ERROR.
- Serwis (Media3 MediaSessionService, FGS mediaPlayback): aktywowany gdy pref ON
  i tryb hands-free; notyfikacja steruje silnikiem (play→resume, pause→pause,
  next→skipToNextCard, previous→goToPreviousCard, stop→pauza+dezaktywacja);
  koniec sesji → serwis zwolniony; onTaskRemoved → stop; audio focus loss → pauza;
  systemowa zmiana stanu playera → dosynchronizowanie silnika; metadane per karta.

### 6.7 Import CSV
- Normalizacja tekstu: strip BOM, `\r\n?` → `\n`.
- Parser pól: cudzysłowy (`"` otwiera/zamyka; `""` w środku = literalny cudzysłów),
  separator może być wielo-znakowy, `\n` poza cudzysłowem kończy wiersz; komórki
  trimowane; wiersze całkiem puste pomijane.
- Detekcja separatora: dla każdego z 4 kandydatów sparsuj pierwsze 5 niepustych
  wierszy; score = (suma dodatkowych kolumn) × (wiersze z >1 kolumną / wiersze);
  najwyższy score wygrywa; wszystko 0 → semicolon.
- Liczba stron: max liczby kolumn w wierszach danych (opcjonalnie z pominięciem
  pierwszego wiersza), min 2.
- Detekcja nagłówka (pierwszy wiersz): score = +0.65 gdy >=2 pola pasują do słów
  kluczowych (word/phrase/translation/... — pełna lista w
  `TensorDeck/src/import/headerDetection.ts`, znormalizowane bez diakrytyków),
  +0.45 gdy 1 pole; +0.15 gdy wszystkie pola to krótkie etykiety (<=24 znaki,
  <=3 słowa); +0.1 gdy drugi wiersz ma podobną liczbę kolumn (±1); −0.15 za każde
  pole wyglądające na dane (długie/>=5 słów/z cyframi), cap −0.35. Nagłówek gdy
  score >= 0.5 (clamp 0..1).
- Języki z nagłówka: tokeny exact+prefix dla en/pl/es/de/fr/it (pełne listy w
  `languageDetection.ts`); default en-US.
- Walidacja payloadu importu: nazwa talii wymagana (trim); stron 2..20; języki
  dokładnie po jednym na stronę, każdy pasuje do LANG_CODE_REGEX; >=1 karta;
  każdy wiersz ma >= pageCount pól (inaczej błąd "Row N is missing..."), przycinany
  do pageCount, nie może być cały pusty.

---------------------------------------------------------------------------------------
## 7. ARCHITEKTURA — DRZEWO PAKIETÓW
---------------------------------------------------------------------------------------

```
app/src/main/java/com/raycoll/app/
├── RayCollApplication.kt          # @HiltAndroidApp
├── MainActivity.kt                # single-activity, edge-to-edge, splash
├── di/                            # Hilt: DatabaseModule, PlaybackModule,
│                                  # DispatchersModule, ClockModule
├── core/                          # Ids (UUID), LocalDates (yyyy-MM-dd), Clock
├── domain/                        # CZYSTY Kotlin — zero importów Androida
│   ├── model/                     # sekcja 4: Deck, Card, SrsState, StudyMode,
│   │                              # ModeStep (sealed), CompoundParams, enums, stałe
│   ├── srs/                       # SrsEngine (FSRS), SpeechMatcher, CardCategory
│   ├── normalize/                 # DataNormalizer, BackupValidator
│   ├── operations/                # czyste funkcje-akcje: CardOperations,
│   │                              # GroupOperations, StudyModeOperations
│   ├── selectors/                 # DueCards, CardStats, Streak, VisiblePages,
│   │                              # BrowseFilter, ArchiveCountdown, SessionProgress
│   ├── steps/                     # StepRegistry, CompoundExpander,
│   │                              # ModeStepValidator, ModeTemplates
│   ├── session/                   # SessionState/Action/Reducer, StepExecutor,
│   │                              # SessionEngine, ReviewTracker,
│   │                              # BackgroundSubstitution, SessionDeps
│   ├── imports/                   # CsvParser, SeparatorDetector, HeaderDetector,
│   │                              # LanguageDetector, ImportPayloadValidator
│   └── seed/                      # SeedData (2 talie + 2 tryby)
├── data/
│   ├── local/db/                  # RayCollDatabase, encje, DAO, konwertery JSON
│   ├── local/mappers/             # encja <-> model domenowy
│   ├── local/prefs/               # UserPreferencesRepository (DataStore)
│   └── repo/                      # DeckRepository, StudyModeRepository,
│                                  # ActivityRepository, BackupRepository,
│                                  # ArchivePurger — transakcje wołające operations
├── playback/                      # TtsPlayer, SttRecognizer, akumulator+timery STT,
│                                  # FeedbackSoundPlayer, Haptics, AudioFocusManager,
│                                  # StudySessionHolder, StudySessionService (Media3),
│                                  # SessionPlayerAdapter, MediaNotification
└── ui/
    ├── theme/                     # Theme (dynamic + brand fallback), SrsColors,
    │                              # Type, Shapes
    ├── navigation/                # Routes (@Serializable), NavGraph
    ├── components/                # SegmentedProgressBar, ConfirmDialog(+token),
    │                              # EmptyState, snackbar host, wspólne pola
    ├── dashboard/  browse/  study/  decksettings/  modedetail/
    ├── imports/  stats/  archive/  appsettings/
    │                              # każdy: <Feature>Screen.kt + <Feature>ViewModel.kt
```

Reguły architektury:
- Room = źródło prawdy; ekrany obserwują Flow z DAO przez ViewModel (StateFlow).
- Akcje domenowe = czyste funkcje w `domain/operations` (stan → stan), wołane
  z metod repozytoriów WEWNĄTRZ transakcji Room. Dzięki temu testy starych
  `store/actions` portują się 1:1 na czyste funkcje.
- SessionEngine żyje w `StudySessionHolder` (@Singleton) — ekran study i serwis
  tła podpinają się do tej samej instancji; silnik przeżywa rotację/odejście z ekranu
  w trybie tła.
- Warstwa UI: stan ekranu jako data class/sealed UiState ze StateFlow; błędy przez
  stan, nie wyjątki w composable.
- Czas ZAWSZE przez wstrzykiwany Clock (testy FSRS/streak/archiwum sterują czasem).

---------------------------------------------------------------------------------------
## 8. MAPOWANIE: STARY KOD → NOWY KOD
---------------------------------------------------------------------------------------

| Stare (TensorDeck/src/…) | Nowe / decyzja |
|---|---|
| `types/models.ts` | `domain/model/*` (BEZ pól deletedAt) |
| `srs/srsEngine.ts` | `domain/srs/*` |
| `store/storeDataNormalization.ts` | `domain/normalize/DataNormalizer.kt` |
| `utils/backupValidation.ts` | `domain/normalize/BackupValidator.kt` |
| `store/actions/{card,group,studyMode}Actions.ts` | `domain/operations/*` (delete = twarde usunięcie; bez zachowywania tombstonów w updateGroup) |
| `store/selectors/{cardSelectors,stats,pages}.ts` | `domain/selectors/*` |
| `store/selectors/liveSelectors.ts` | zapytania DAO (bez deletedAt — trywialne) |
| `features/browse/browseFilter.ts` | `domain/selectors/BrowseFilter.kt` |
| `features/archive/archiveCountdown.ts` | `domain/selectors/ArchiveCountdown.kt` |
| `features/study/session/sessionProgress.ts` | `domain/selectors/SessionProgress.kt` |
| `features/settings/{stepRegistry,compoundSteps,validateModeSteps,modeTemplates}.ts` | `domain/steps/*` |
| `features/study/session/*` (types, reducer, stepExecutor, SessionEngine, utils, review, background) | `domain/session/*` |
| `import/*` | `domain/imports/*` |
| `store/seed/*` | `domain/seed/SeedData.kt` |
| `constants/*` | stałe przy typach w `domain/model` (sekcja 5) |
| `store/persistence/localPersistence.ts` + persistenceQueue (lokalnie) | Room — mechanizm ZNIKA (transakcje) |
| `store/persistence/{firestoreSchema,firestorePersistence,firebasePersistence}.ts` | NIE portujemy (D2) — notatka w sekcji 14 |
| `store/persistence/persistenceQueue.ts` (chmura) | NIE portujemy (D2) |
| `store/selectors/merge.ts` + tombstones | NIE portujemy (D2) — semantyka spisana w sekcji 14 |
| `store/{useStoreBootstrap,useStorePersistence,useAuthActions}.ts` | NIE portujemy (bootstrap = otwarcie Room + seed-on-empty; auth nie istnieje) |
| `store/rollbackHelper.ts` | NIE portujemy (transakcje Room dają atomowość) |
| `store/useArchivePurger.ts` | `data/repo/ArchivePurger.kt` (twarde delete) |
| `services/ttsService.ts` | `playback/TtsPlayer.kt` |
| `services/stt*.ts` | `playback/SttRecognizer.kt` + czyste klasy akumulatora/timerów |
| `services/{audioFeedback,hapticFeedback}.ts` | `playback/{FeedbackSoundPlayer,Haptics}.kt` |
| `services/mediaSession.native.ts` + registerPlaybackService | `playback/StudySessionService.kt` (Media3) |
| `services/{firebase,firebaseClient,googleNativeAuth,secureAuthPersistence}.ts` | NIE portujemy (D2) |
| `services/updateNotification.ts` | NIE portujemy (D6) |
| `contexts/UserPreferencesContext.tsx` + `storage/uiPreferences.ts` | `data/local/prefs/UserPreferencesRepository.kt` (bez railExpanded) |
| `features/study/hooks/useStudySession.ts` | `ui/study/StudyViewModel.kt` |
| `features/study/hooks/useStudyCardGestures.ts` | gesty w `ui/study/StudyCard.kt` + logika tap/hold w silniku |
| `features/study/hooks/useBackgroundStudy.ts` | `playback/StudySessionHolder` + serwis |
| `features/import/importDraft*` | `ui/imports/ImportViewModel.kt` |
| `i18n/locales/*.ts` + `plural.ts` | `res/values{,-pl,-de,-es,-it}/strings.xml` + plurals |
| `theme/{tokens,createAppTheme,srsTokens,semanticColors}.ts` | `ui/theme/*` |
| ekrany `app/*.tsx` + `components/*` | `ui/<feature>/` wg sekcji 3 |
| `utils/{id,date,math,withTimeout,deepEqual,array}.ts` | `core/*` / idiomy Kotlina |
| hooki webowe, webDocumentTitle, scrollbar, nav rail | NIE portujemy (D6) |

Testy — mapa portu (stary plik → los):
| stary test | los |
|---|---|
| `srs/__tests__/srsEngine.test.ts` | port CAŁY (T2) |
| `store/__tests__/storeDataNormalization.test.tsx` | port bez asercji deletedAt (T2) |
| `store/seed/__tests__/seedModes.test.ts` | port CAŁY (T2) |
| `utils/__tests__/{backupValidation…}` (część walidacyjna backupImport.test) | port (T2) |
| `store/actions/__tests__/{card,group,activity}Actions.test.ts` | port; asercje tombstonów ZAMIENIONE na twarde usunięcie — każdą zmianę wypisz w raporcie (T3) |
| `store/selectors/__tests__/{cardSelectors,stats,pages}.test.ts` | port CAŁY (T3) |
| `store/selectors/__tests__/{merge,tombstones,migrationLogic}.test.ts` | NIE portujemy (D2) |
| `features/browse/__tests__/browseFilter.test.ts` | port (T3) |
| `features/archive/__tests__/archiveCountdown.test.ts` | port (T3) |
| `import/__tests__/{importParser,importDeck}.test.ts` | port CAŁY (T3) |
| `features/settings/__tests__/{stepRegistry,compoundSteps,validateModeSteps}.test.ts` | port CAŁY (T4) |
| `features/study/session/__tests__/{sessionEngine,sessionState,sessionUtils,sessionBackground}.test.ts` | port CAŁY (T4) |
| `features/study/__tests__/useStudySession.test.tsx` | scenariusze silnikowe → T4; reactowe pomiń z adnotacją |
| `services/__tests__/{sttTranscriptAccumulator,sttSessionHelpers,sttExpo}.test.ts` | akumulator+timery port (T6); expo-specyficzne pomiń |
| `store/persistence/__tests__/*` | NIE portujemy (D2); localPersistence → zastąpione testami DAO/repo (T5) |
| `store/__tests__/{useStoreActionsCore,useStorePersistence,useStoreBootstrap,backupImport,rollbackHelper}.test.*` | zachowania store'owe → testy repo/backupu (T5); sync/bootstrap NIE |
| `features/import/__tests__/{importDraftHelpers,importDraftReducer,useImportDeckDraft}.test.*` | port do testów ImportViewModel (T9) |
| `features/settings/__tests__/{useStudyModeDraftController,deckPageReorder}.test.*` | port do testów VM/repo (T9/T5) |
| `i18n/__tests__/localeConsistency.test.ts` | odpowiednik = test spójności kluczy XML między językami (T1) |

---------------------------------------------------------------------------------------
## 9. KONTRAKT WYKONANIA TASKA (obowiązuje każdego agenta, w każdym tasku)
---------------------------------------------------------------------------------------

**Zakres i kompletność:**
1. Wykonujesz WSZYSTKIE pozycje z checklisty DoD swojego taska. Task bez każdego
   odhaczonego punktu = task NIEWYKONANY. Nie ma "zrobię później", nie ma "poza
   zakresem", nie ma samodzielnego zmniejszania zakresu.
2. ZABRONIONE w dostarczonym kodzie: `TODO`, `FIXME`, `stub`, `NotImplementedError`,
   `UnsupportedOperationException`, puste ciała funkcji, wykomentowane testy,
   `@Ignore`/`@Disabled`, zaślepki typu "na razie zwracam pustą listę". Przed oddaniem
   uruchom `grep -rn "TODO\|FIXME\|@Ignore\|@Disabled" app/src` i wklej wynik do
   raportu (musi być pusty dla plików, które dodałeś/zmieniłeś).
3. Punkt DoD NIEMOŻLIWY do wykonania (sprzeczność, brak pliku, niekompilowalna
   zależność) → NIE pomijasz po cichu: oznaczasz w raporcie jako BLOCKED(powód)
   i dowozisz całą resztę.
4. Nie dotykasz plików spoza zakresu taska (wyjątki: dopisanie zależności do version
   catalog, wpis do di/, oraz pliki jawnie wymienione w Twoim tasku).
5. Niczego nie dodajesz "przy okazji" (żadnych ficzerów spoza DoD — lista D6).

**Port logiki:**
6. Portując moduł, NAJPIERW czytasz wskazany stary plik W CAŁOŚCI oraz jego test.
   Zachowujesz: wartości stałych (sekcja 5!), kolejność rozstrzygnięć, warunki
   brzegowe, klucze i18n.
7. Testy portujesz test-po-teście: każdy `it(...)` ze starego pliku = jeden `@Test`
   o tej samej intencji i tych samych wartościach oczekiwanych. W raporcie: liczba
   testów w starym pliku vs nowym; każda różnica wyjaśniona per przypadek (np. "test
   dotyczył deletedAt — wycięte przez D2", "test Reacta — odpowiedzialność w VM").
8. Nazwy pól serializowanych (Room JSON, backup) ustala T2 i od tego momentu są
   STABILNE — kolejne taski ich nie zmieniają.

**Weryfikacja (uruchamiasz SAM, przed oddaniem):**
9. `./gradlew.bat :app:compileDebugKotlin` — zielone.
10. `./gradlew.bat :app:testDebugUnitTest` — zielone, zero pominiętych.
    Task dotykający UI/manifestu/zasobów: dodatkowo `./gradlew.bat :app:assembleDebug`.

**Raport końcowy (obowiązkowy format):**
11. (a) tabela: każdy punkt DoD → DONE / BLOCKED(powód); (b) lista utworzonych/
    zmienionych plików; (c) ostatnie linie wyników komend z pkt 9–10; (d) wynik
    grepa z pkt 2; (e) mapa testów: stary plik → nowy plik → liczba testów
    stara/nowa + wyjaśnienia różnic.

**Konwencje kodu:** UI: stan ekranu = data class/sealed `UiState` ze StateFlow;
operacje repo suspend; błędy do UI przez stan. Komentarz w kodzie tylko tam, gdzie
stary kod miał komentarz wyjaśniający semantykę (przenosisz SENS). Nazwy angielskie,
stringi użytkownika WYŁĄCZNIE z zasobów.

---------------------------------------------------------------------------------------
## 10. TASKI T1–T10 — SZCZEGÓŁOWE CHECKLISTY DoD
---------------------------------------------------------------------------------------

Kolejność wykonania: T1 → T2 → T3 → T4 → T5 → T6 → T7 → T8 → T9 → T10.
Kamienie milowe: po T8 pełna nauka (z tłem); po T9 pełny feature-set; po T10 release.

---

### T1 — Fundament: projekt + motyw + nawigacja + i18n + preferencje + Auto Backup

**Cel:** kompilujący się szkielet z motywem MD3, nawigacją po 9 placeholderach,
kompletem stringów w 5 językach, preferencjami i skonfigurowanym Auto Backup.
**Czytaj:** sekcje 1, 3 (nagłówek — trasy), 5 (stałe), 7; referencje:
`TensorDeck/app.config.js`, `TensorDeck/assets/images/*`,
`TensorDeck/src/theme/{tokens,createAppTheme,srsTokens,semanticColors}.ts`,
`TensorDeck/src/i18n/locales/{pl,en,de,es,it}.ts`, `TensorDeck/src/i18n/plural.ts`,
`TensorDeck/src/contexts/UserPreferencesContext.tsx`.
Wrapper gradle: skopiuj `TensorDeck/android/gradle/wrapper/*` + `gradlew`/`gradlew.bat`.

**Pliki:** `settings.gradle.kts`, root+app `build.gradle.kts`,
`gradle/libs.versions.toml`, `gradle.properties`, `local.properties` (sdk.dir =
`%LOCALAPPDATA%\Android\Sdk`; w .gitignore), `.gitignore`, `AndroidManifest.xml`,
`RayCollApplication.kt`, `MainActivity.kt`, `di/{AppModule,DispatchersModule,
ClockModule}.kt`, `core/{Ids,LocalDates,Clock}.kt`, `ui/theme/*`,
`ui/navigation/{Routes,NavGraph}.kt`, 9 × placeholder `ui/<feature>/<Feature>Screen.kt`,
`data/local/prefs/UserPreferencesRepository.kt`, `res/values/strings.xml` (en,
domyślny) + `res/values-{pl,de,es,it}/strings.xml`, `res/xml/{backup_rules,
data_extraction_rules}.xml`, ikony adaptive z assets starej apki.

**Checklista DoD:**
- [ ] Version catalog: Kotlin 2.x, AGP stable, Compose BOM stable, Hilt,
      Navigation-Compose, DataStore, kotlinx.serialization, JUnit,
      kotlinx-coroutines-test, Robolectric. BEZ: Firebase, Room, Media3 (dojdą
      w swoich taskach). minSdk 26, compileSdk/targetSdk stable.
- [ ] applicationId `com.raycoll.app`, debug suffix `.debug`; `android:label`
      z zasobów (per język).
- [ ] Manifest: `android:enableOnBackInvokedCallback="true"` (predictive back);
      `android:allowBackup="true"` + `android:fullBackupContent="@xml/backup_rules"`
      + `android:dataExtractionRules="@xml/data_extraction_rules"` — reguły obejmują
      bazę Room (dojdzie w T5 — reguły pisz od razu na ścieżkę `databases/`)
      i DataStore; nic nie wykluczamy (brak sekretów).
- [ ] Motyw: dynamicLight/DarkColorScheme na Androidzie 12+ z przełącznikiem
      logicznym `useSystemColors`; fallback = brand scheme wyprowadzony z kolorów
      `createAppTheme.ts`/`tokens.ts`; tryby light/dark/system; `SrsColors` =
      4 kolory kategorii z `srsTokens.ts` (new/learning/review/mastered) dostępne
      przez CompositionLocal lub rozszerzenie motywu.
- [ ] Nawigacja: typowane trasy @Serializable dla 9 ekranów (sekcja 3 nagłówek);
      placeholdery = Scaffold + TopAppBar (tytuł z zasobów) + tekst trasy;
      przejścia + predictive back działają.
- [ ] i18n: KAŻDY klucz z `pl.ts` ma odpowiednik w `values-pl/strings.xml`; analogicznie
      en→values (default), de/es/it. Placeholdery `{x}` → `%1$s`/`%1$d` konsekwentnie
      (ta sama kolejność we wszystkich językach!). Formy mnogie z `plural.ts` →
      `<plurals>`. Klucze czysto webowe pomiń i WYLICZ pominięte w raporcie.
      Test jednostkowy spójności: wszystkie języki mają identyczny zbiór kluczy.
- [ ] UserPreferencesRepository (DataStore): themePref(light/dark/system, def system),
      useSystemColors(Bool, def true), uiLanguage(pl/en/de/es/it), ttsRate(Float,
      clamp 0.5–2.0, def 1.0), backgroundPlayback(Bool, def false). Flow + settery.
      Zmiana języka → `AppCompatDelegate.setApplicationLocales`.
- [ ] `Clock` (interfejs + SystemClock impl + wstrzykiwanie); `Ids.newId()` = UUID v4;
      `LocalDates.dayKey(now, zone)` = `yyyy-MM-dd`.
- [ ] `assembleDebug` + `testDebugUnitTest` zielone (min.: test spójności i18n,
      test prefs round-trip, test dayKey).
**NIE WOLNO:** logiki w placeholderach; własnych tłumaczeń; pomijania języka.

---

### T2 — Domain: modele + SRS + normalizacja + walidacja + seed

**Cel:** kompletny model domenowy + FSRS + SpeechMatcher + normalizacja + walidator
backupu + seed. Czysty Kotlin.
**Czytaj:** sekcje 4, 5, 6.1, 6.3; referencje: `src/types/models.ts`,
`src/srs/srsEngine.ts` + `__tests__/srsEngine.test.ts`,
`src/store/storeDataNormalization.ts` + `__tests__/storeDataNormalization.test.tsx`,
`src/utils/backupValidation.ts`, `src/store/seed/{seedGroups,seedModes}.ts` +
`__tests__/seedModes.test.ts`, `src/constants/*.ts`, `src/utils/{math,array}.ts`.

**Pliki:** `domain/model/*` (sekcja 4), `domain/srs/{SrsEngine,SpeechMatcher,
CardCategory}.kt`, `domain/normalize/{DataNormalizer,BackupValidator}.kt`,
`domain/seed/SeedData.kt` + testy.

**Checklista DoD:**
- [ ] Modele DOKŁADNIE wg sekcji 4 (BEZ deletedAt). Sealed ModeStep: wszystkie
      17 typów atomowych + compound; serializacja: dyskryminator `type` dla kroków,
      `kind` dla CompoundParams; `condition` opcjonalne. Round-trip test
      obiekt→JSON→obiekt (przykłady: wszystkie kroki seedów + po jednym compound
      każdego z 9 rodzajów, w tym pełne gałęzie).
- [ ] SrsEngine wg sekcji 6.1 — stałe z sekcji 5 co do wartości; czas przez
      parametr `now`/Clock (ŻADNEGO System.currentTimeMillis w domain).
- [ ] SpeechMatcher wg sekcji 6.3 (normalize unicode, levenshtein, wordSimilarity
      z progiem 0.5 i greedy bez powtórzeń, matchSpeech = max, mapMatchToRating
      z easyThreshold).
- [ ] getCardCategory (0→new, 1|3→learning, 2→reps>=3?mastered:review) —
      z exhaustive when.
- [ ] DataNormalizer wg sekcji 4.7 (padding, clampy, odrzucanie nieznanych kroków,
      strip id kroków, walidacja builtInSourceId względem seedów, heatmapa).
- [ ] BackupValidator wg sekcji 4.8 — twarde błędy z opisami; Number.isFinite
      semantyka (NaN/Infinity odrzucane).
- [ ] SeedData: dokładnie 2 talie (Angielski — Podstawy: 5 kart en-US/pl-PL;
      Hiszpański — Podstawy: 4 karty es-ES/pl-PL — treści kart 1:1 z `seedGroups.ts`)
      + 2 tryby: 'classic' (show_page 0, speak_page 0, wait_for_tap_to_reveal,
      show_all_pages, show_ratings) i 'listen-speak' (kroki DOKŁADNIE jak
      w `seedModes.ts`: STT z gałęziami correct/wrong i terminalnym fallbackiem);
      `isBuiltInModeSourceId`.
- [ ] Testy: port CAŁYCH `srsEngine.test.ts` i `storeDataNormalization.test.tsx`
      (bez asercji deletedAt — wypisz pominięte), `seedModes.test.ts`, walidacyjna
      część `backupImport.test.tsx`. Raport: liczby testów stare→nowe.
**NIE WOLNO:** zmieniać stałych FSRS; "poprawiać" clampów; dodawać pól do modeli
(w tym deletedAt!).

---

### T3 — Domain: operacje + selektory + import

**Cel:** czyste akcje domenowe (twarde usuwanie!), selektory, parser importu.
**Czytaj:** sekcje 5, 6.2, 6.7; referencje: `src/store/actions/*.ts` + testy,
`src/store/selectors/{cardSelectors,stats,pages}.ts` + testy,
`src/features/browse/browseFilter.ts` + test, `src/features/archive/
archiveCountdown.ts` + test, `src/features/study/session/sessionProgress.ts`,
`src/import/*.ts` + `__tests__/{importParser,importDeck}.test.ts`.

**Pliki:** `domain/operations/{CardOperations,GroupOperations,StudyModeOperations}.kt`,
`domain/selectors/*`, `domain/imports/*` + testy.

**Checklista DoD:**
- [ ] Operations (czyste funkcje `(stan, argumenty, now) → nowy stan`):
      addCard (nowa karta: contentUpdatedAt=now, srsUpdatedAt=0, świeży SrsState),
      updateCard (stempluje contentUpdatedAt; NIE dotyka srsState),
      **deleteCard = twarde usunięcie z listy**, addGroup/addGroupWithCards
      (activeModeId='classic', defaulty filtrów, activePageCount z clampem),
      updateGroup (stempluje updatedAt; bez żadnej magii tombstonów),
      **deleteGroup = twarde usunięcie talii**, archiveGroup (archivedAt=now,
      updatedAt=now), restoreGroup (archivedAt=null), purgeExpiredArchives
      (archivedAt>0 && now−archivedAt>=14 dni → **usuń talię**; zwraca changed),
      setVisiblePageCount (clamp 2..5 + padding metadanych; treści ukrytych stron
      NIETKNIĘTE), setStudyFilter/setCardOrder/setActiveStudyMode (stemplują
      updatedAt), movePage (zamiana names+langs+zawartości stron wszystkich kart),
      reviewCard (FSRS od aktualnego stanu karty + heatmapa[dziś]+1 — ATOMOWO,
      zwraca todayKey), reviewCardAgain (FSRS od PODANEGO bazowego SrsState,
      heatmapa BEZ zmian), recordActivity, addStudyMode/updateStudyMode
      (normalizacja + updatedAt=now), **deleteStudyMode = twarde usunięcie**,
      resetStudyMode (wbudowany → kroki+nazwa z seeda, id zachowane).
- [ ] Selektory wg sekcji 6.2 (filtry, kolejności z tie-breakerami, progres,
      statystyki, streak z regułą "dziś nie zeruje", widoczne strony, browse filter,
      archive countdown, session progress — kategorie ze ŚWIEŻYCH kart talii po id).
- [ ] Import wg sekcji 6.7 — parser (quotes/multi-char sep/BOM/CRLF), detekcje
      z dokładnymi wagami scoringu, walidator payloadu z tymi samymi komunikatami.
- [ ] Testy: port plików z mapy w sekcji 8 (actions — asercje tombstonów zamienione
      na twarde usunięcie, każdą zamianę wypisz; selektory, browseFilter,
      archiveCountdown, importParser, importDeck — w CAŁOŚCI).
**NIE WOLNO:** żadnego IO; żadnych tombstonów; zmiany reguł tie-breaków.

---

### T4 — Domain: kroki trybów + silnik sesji (SessionEngine) — NAJTRUDNIEJSZY TASK

**Cel:** rejestr/walidacja/szablony/ekspansja kroków + kompletna maszyna stanów sesji.
**Czytaj:** sekcje 5, 6.4, 6.5, 6.6 (semantyka substytucji), 3.2 (zachowania ekranu);
referencje: `src/features/settings/{stepRegistry,compoundSteps,validateModeSteps,
modeTemplates}.ts` + testy, `src/features/study/session/*` + testy,
`src/features/study/hooks/{useStudySession,useStudyAudio,useStudyCardGestures}.ts`,
`TensorDeck/docs/REGRESSION.md` sekcja Study.

**Pliki:** `domain/steps/{StepRegistry,CompoundExpander,ModeStepValidator,
ModeTemplates}.kt`, `domain/session/{SessionState,SessionAction,SessionReducer,
StepExecutor,SessionEngine,ReviewTracker,BackgroundSubstitution,SessionDeps}.kt`
+ testy.

**Checklista DoD:**
- [ ] StepRegistry wg sekcji 6.4 (17 definicji z kategoriami, polami, clampami,
      defaultami; buildStep; clamp pojedynczego pola; pageIndex kroku; parametry
      do opisu kroku). DataNormalizer z T2 przełączony na clampy z rejestru
      (jeśli w T2 była lokalna kopia — usuń ją).
- [ ] CompoundExpander wg sekcji 6.4: normalizacja params/gałęzi z defaultami
      + ekspansja 9 rodzajów W DOKŁADNEJ KOLEJNOŚCI (w tym kolejność kroków gałęzi);
      expandModeSteps; expandWithSource (mapowanie krok→indeks źródłowy dla UI).
- [ ] ModeStepValidator + ModeTemplates — pełny port.
- [ ] SessionState/Action/Reducer wg sekcji 6.5 — wszystkie 20 akcji z opisanymi
      subtelnościami (UPDATE_PARTIAL_STT poza listening ignorowana; CLEAR_ERROR nie
      zmienia statusu; REVEAL_* czyszczą peek; ADVANCE_CARD = pełny reset).
      Reducer = czysta funkcja, osobno testowalna.
- [ ] StepExecutor wg sekcji 6.4+6.5: wszystkie 17 kroków, warunki (skipped/error/
      none nie pasują), auto-complete gate'ów, no-op show_ratings po ocenie,
      przerwanie speak_all_pages, timingi (dynamic_pause len×60×mult; STT soft/hard),
      koniec listy = STOP.
- [ ] SessionEngine wg sekcji 6.5: start/restart/restartFailed (świeże karty po id),
      rate z semantyką ponownej oceny (ReviewTracker "próba:cardId", replace-from-base,
      bez podwójnej heatmapy, failed od nowa), skipToNextCard, goToPreviousCard
      (okno 3000 ms), replayCurrentCard, pause/resume/end/stop, holding jako
      StateFlow (advance czeka bez limitu), tap-handling wg TABELI z sekcji 6.5,
      peek (500 ms), anulacja Job-ami (anulowany łańcuch bez efektów), auto-run
      z blokadą lastExecutedCardIndex, pendingStepIndex konsumowany przed startem.
- [ ] BackgroundSubstitution wg sekcji 6.6 (hands-free check + substytucje + progi).
- [ ] SessionDeps jako interfejs; w testach fake'i + kotlinx-coroutines-test
      (wirtualny czas!).
- [ ] Testy: port CAŁYCH sessionEngine/sessionState/sessionUtils/sessionBackground
      + silnikowe scenariusze z useStudySession.test + testy z sekcji 6.5 wprost:
      "tap podczas TTS nie odsłania strony", "tap w STT → skipped → manual fallback",
      "hold wstrzymuje advance", "previous <3 s cofa / >=3 s restartuje",
      "re-rate zastępuje ocenę bez podwójnej heatmapy", "peek <500/>=500 ms".
**NIE WOLNO:** upraszczać semantyk; dodawać fallbacków, których stary runner nie miał;
używać czegokolwiek z Androida.

---

### T5 — Data: Room + repozytoria + purger + backup

**Cel:** Room jako źródło prawdy; repozytoria = operations w transakcjach.
**Czytaj:** sekcje 4, 5, 6.2; referencje: `src/store/useStoreActions.ts` (semantyka
akcji), `src/store/useArchivePurger.ts`, `src/features/settings/deckPageReorder.ts`
+ test, testy zachowań: `src/store/__tests__/{useStoreActionsCore,backupImport}.test.*`.

**Pliki:** `data/local/db/{RayCollDatabase,DeckEntity,CardEntity,StudyModeEntity,
ActivityDayEntity,DeckDao,CardDao,StudyModeDao,ActivityDao,Converters}.kt`,
`data/local/mappers/*`, `data/repo/{DeckRepository,StudyModeRepository,
ActivityRepository,BackupRepository,ArchivePurger}.kt`, `di/DatabaseModule.kt` + testy.

**Checklista DoD:**
- [ ] Room w version catalog + KSP. Schemat = WERSJA 1, zero klas Migration;
      `exportSchema=true` (schemas/ w repo).
- [ ] Encje: DeckEntity (PK id; pola sekcji 4.3 bez cards; pageNames/pageLanguages
      jako JSON), CardEntity (PK id; FK deckId z ON DELETE CASCADE + index; pages
      JSON; SrsState płasko), StudyModeEntity (steps JSON), ActivityDayEntity
      (PK dayKey; count). Konwertery kotlinx.serialization — TEN SAM format co
      backup (round-trip test).
- [ ] DAO (Flow): activeDecks (archivedAt IS NULL, kolejność wstawienia — rowid),
      archivedDecks (archivedAt DESC), deckWithCards, modes, heatmap; wszystkie
      mutacje zbiorcze @Transaction.
- [ ] Repozytoria: każda metoda = domain/operations + zapis W TRANSAKCJI;
      reviewCard/reviewCardAgain ATOMOWO karta+heatmapa; deleteDeck kaskadowo;
      movePage transakcyjnie (wszystkie karty); resetStudyMode; seed-on-empty
      (pierwsze otwarcie pustej bazy → seed przez normalizeStoreData);
      getSnapshot()/replaceAll(snapshot) dla backupu/resetu.
- [ ] BackupRepository: exportJson() = StoreSnapshot pretty print (żywe = wszystkie;
      nie ma tombstonów); importJson(text): parse → normalize → validate →
      transakcyjne zastąpienie CAŁEGO stanu; błąd → wyjątek z kluczem
      `app_settings.import_error`, stan NIETKNIĘTY (transakcja). Reset fabryczny =
      replaceAll(seed).
- [ ] ArchivePurger: purge(now) przy starcie + co 60 min (wpięcie w T7 przez
      ProcessLifecycle — tu sama logika + test).
- [ ] Testy (Robolectric, in-memory): DAO round-trip (w tym JSON kroków compound!),
      kaskada delete, atomowość reviewCard (symulowany błąd → rollback), seed-on-empty
      (dokładnie raz), backup export→import→stan identyczny, import błędnego JSON →
      stan nietknięty, purger 14 dni (fake Clock), movePage (port deckPageReorder.test),
      zachowania z useStoreActionsCore.test (część store'owa).
**NIE WOLNO:** zapisu poza transakcją tam, gdzie operacja jest wielotabelowa;
zmiany nazw pól JSON ustalonych w T2; migracji Room.

---

### T6 — Playback: TTS + STT + feedback + haptyka + holder

**Cel:** warstwa urządzeń audio (semantyka starej apki + wzmocnienia natywne).
**Czytaj:** sekcja 6.5 (deps), 6.6; referencje: `src/services/ttsService.ts`,
`src/services/{sttExpo,sttSessionHelpers,sttInactivityTimer,
sttTranscriptAccumulator,sttTypes}.ts` + testy, `src/services/{audioFeedback,
hapticFeedback}.ts`, `TensorDeck/assets/sounds/{success,error}.wav`,
`src/features/study/hooks/useStudyAudio.ts`.

**Pliki:** `playback/{TtsPlayer,SttRecognizer,SttTranscriptAccumulator,
SttSessionTimers,FeedbackSoundPlayer,Haptics,AudioFocusManager,StudySessionHolder}.kt`,
`di/PlaybackModule.kt`, `res/raw/{success,error}.wav` + testy JVM.

**Checklista DoD:**
- [ ] TtsPlayer (android.speech.tts.TextToSpeech): inicjalizacja async (suspend do
      onInit), suspend `speak(text, lang, rate)` przez UtteranceProgressListener;
      SEMANTYKA PRZERWANIA: cancel()/nowsza wypowiedź NIE jest błędem (kończy się
      normalnie); błąd tylko dla wciąż-aktywnej wypowiedzi; język per wypowiedź;
      `isLanguageAvailable` przed mową — brak głosu → wynik MissingVoice(lang)
      (UI: dialog + intent ACTION_INSTALL_TTS_DATA).
- [ ] SttRecognizer (android.speech.SpeechRecognizer, main thread): partial results
      → callback; akumulator transkryptu (finalne segmenty SKLEJANE, interim
      podmienia tylko ostatni segment — port 1:1); inactivity timer resetowany
      wynikami + hard timeout — oba kończą nasłuch Z WYNIKIEM; błędy no-speech/
      speech-timeout/aborted → wynik (pusty tekst), inne → błąd; EXTRA_PARTIAL_RESULTS,
      EXTRA_PREFER_OFFLINE=true; brak uprawnienia RECORD_AUDIO → PermissionDenied
      (UI poprosi o zgodę).
- [ ] INTERLOCK TTS↔STT: nasłuch NIE startuje, dopóki TTS mówi (await/cancel) —
      mikrofon nie może słyszeć lektora.
- [ ] AudioFocusManager: focus (TRANSIENT_MAY_DUCK) na czas TTS/STT, STREAM_MUSIC;
      utrata focusu → callback (silnik: pauza).
- [ ] FeedbackSoundPlayer: SoundPool, wav z res/raw (skopiowane ze starej apki);
      Haptics: wzorce per ocena 1–4 + success/error (port hapticFeedback.ts).
- [ ] StudySessionHolder (@Singleton): create/get/release aktywnego SessionEngine +
      metadane (deckId, nazwa talii); jedno źródło dla ekranu i serwisu (T8).
- [ ] Adapter SessionDeps (T4) na TtsPlayer/SttRecognizer/Feedback/Haptics.
- [ ] Testy JVM: akumulator (port CAŁEGO testu), timery (wirtualny czas), semantyka
      "cancel nie jest błędem" na fake'u.
**NIE WOLNO:** STT w trakcie TTS; zgłaszania przerwania jako błędu; blokowania
głównego wątku.

---

### T7 — UI: Dashboard + Browse + Stats + Archive

**Cel:** 4 ekrany przeglądania — zachowania DOKŁADNIE wg sekcji 3.1/3.3/3.7/3.8.
**Czytaj:** sekcje 3.1, 3.3, 3.7, 3.8, 6.2; referencje: `src/app/{index,stats,
archive}.tsx`, `src/app/browse/[groupId].tsx`, `src/components/dashboard/*`,
`src/features/{browse,flashcards,stats,archive}/*`,
`src/components/SegmentedProgressBar.tsx`.

**Pliki:** `ui/components/{SegmentedProgressBar,ConfirmDialog,EmptyState,
SnackbarHost…}.kt`, `ui/dashboard/*`, `ui/browse/*`, `ui/stats/*`, `ui/archive/*`
(Screen + ViewModel + podkomponenty wg potrzeb).

**Checklista DoD:**
- [ ] Dashboard 1:1 z sekcją 3.1 (grid, pasek segmentowy z SrsColors, liczba due,
      Study/Browse, menu trybu persystowane, menu talii, FAB→Import, wejścia
      Stats/Archive/AppSettings, empty state).
- [ ] Browse 1:1 z sekcją 3.3 (search po widocznych stronach, chipy z licznikami,
      edycja per widoczna strona z zachowaniem ukrytych, save disabled <2 pola,
      FAB→nowa karta→edycja, twarde delete z confirm, nagłówek ze statystykami).
- [ ] Stats 1:1 z sekcją 3.7 (heatmapa roczna z intensywnością, streak, sumy,
      lista talii z paskami).
- [ ] Archive 1:1 z sekcją 3.8 (sort, countdown 14 dni, restore, empty state);
      ArchivePurger wpięty: purge przy starcie apki + tick godzinny
      (ProcessLifecycleOwner).
- [ ] Wszystkie stringi z zasobów; ViewModele na Flow z repo (statystyki liczone
      selektorami w VM, NIE w composable); testy jednostkowe VM: filtr browse,
      liczniki chipów, countdown.
- [ ] `assembleDebug` zielony; nawigacja między ekranami działa.
**NIE WOLNO:** liczenia statystyk w composable; hardkodowania stringów; dodawania
ficzerów spoza sekcji 3.

---

### T8 — UI: Study (foreground) + nauka w tle (Media3)

**Cel:** kompletny ekran nauki + tło. Serce aplikacji. Zachowanie wg sekcji 3.2.
**Czytaj:** sekcje 3.2, 5, 6.5, 6.6; referencje: `src/app/study/[groupId].tsx`,
`src/features/study/components/*`, `src/features/study/hooks/*`,
`src/services/mediaSession.native.ts`, `src/services/registerPlaybackService.native.ts`,
`TensorDeck/docs/REGRESSION.md` sekcja Study (KAŻDY checkbox = wymaganie,
poza punktami STT-web).

**Pliki:** `ui/study/{StudyScreen,StudyViewModel,StudyCard,StudyControls,
StudyFinishedState,SttPanel}.kt`, `playback/{StudySessionService,
SessionPlayerAdapter,MediaNotification}.kt`, manifest (FGS mediaPlayback + service),
Media3 w version catalog.

**Checklista DoD (foreground):**
- [ ] StudyViewModel: silnik przez StudySessionHolder; kroki = expandModeSteps
      aktywnego trybu przefiltrowane z pageIndex/nextPageIndex >= activePageCount;
      dueCards z repo (filtr+kolejność talii); auto-start; onCardReviewed →
      repo.reviewCard / reviewCardAgain(replaceFromBase); exit-confirm (BackHandler
      + predictive back) gdy sesja aktywna; endedEarly.
- [ ] Ekran "brak kart na dziś" (0 due) i auto-start przy >0.
- [ ] StudyCard: strony wg revealedPages (strona 0 zawsze), peek overlay, auto-scroll
      do najnowszej strony, adaptacyjna czcionka (progi 70/140/260), wskaźnik audio
      per strona; GESTY: tap → engine.handleCardPress (tabela 6.5); hold →
      setHolding(true/false) + peek po 500 ms z semantyką puszczenia.
- [ ] StudyControls: 4 przyciski ocen (revealed only; kolory semantyczne; haptyka),
      SttPanel (partial live, percent/próg/status), przyciski poprzednia/następna.
- [ ] StudyFinishedState wg 3.2 (restart failed warunkowy, restart, back, endedEarly).
- [ ] Pasek postępu sesji (segmenty past/current/future w kolorach kategorii) + x/y.
- [ ] Błędy TTS/STT → snackbar/dialog z i18n; MissingVoice → dialog z intentem do
      ustawień TTS; CLEAR_ERROR po zamknięciu; sesja nie crashuje.
- [ ] Keep-screen-on podczas aktywnej sesji; wyjście z ekranu (potwierdzone) →
      engine.stop() + release holdera (gdy tło nieaktywne).
**Checklista DoD (tło):**
- [ ] StudySessionService: Media3 MediaSessionService, FGS mediaPlayback; aktywacja
      TYLKO gdy pref backgroundPlayback ON i tryb hands-free; player = adapter
      (SimpleBasePlayer odzwierciedlający sesję; fallback dozwolony: pętla cichego
      wav — wybór opisz w raporcie).
- [ ] Notyfikacja: tytuł = front karty, drugi wiersz = talia, licznik x/y;
      metadane aktualizowane przy KAŻDEJ zmianie karty; komendy play/pause/next/
      previous/stop → silnik (wg 6.6).
- [ ] Auto-pauza w tle (revealed/gate/error) → notyfikacja "wstrzymane"
      (klucz `study.background_paused`) + pauza playera.
- [ ] onTaskRemoved → stop serwisu i silnika (notyfikacja znika); koniec sesji →
      serwis zwolniony; powrót do apki po Stop → reaktywacja przy kolejnym zejściu.
- [ ] Zejście w tło przy trybie nie-hands-free → engine.pause() + stop audio;
      hold zwalniany ZAWSZE przy zejściu w tło.
- [ ] Audio focus (T6) wpięty: utrata → pauza; głośność = STREAM_MUSIC; systemowa
      pauza/wznowienie playera → dosynchronizowanie silnika.
- [ ] Raport z testu manualnego: sesja hands-free (opisz jak utworzyłeś tryb —
      np. speak+pauzy bez ocen), lock screen, sterowanie z notyfikacji, swipe
      z recents.
**NIE WOLNO:** wołać TTS/STT z composable; pokazywać ocen po ocenie karty; zostawić
FGS po sesji; łamać tabeli tapów z 6.5.

---

### T9 — UI: Deck settings + edytor trybów + Import + App settings

**Cel:** ekrany konfiguracji i danych wejściowych — pełny feature-set.
**Czytaj:** sekcje 3.4, 3.5, 3.6, 3.9, 6.4, 6.7; referencje:
`src/app/{settings/[groupId],study-modes/[modeId],import,app-settings}.tsx`,
`src/features/settings/*` + testy, `src/features/import/*` + testy,
`src/features/appSettings/*`, `src/components/settings/*`,
`src/components/dialogs/TextEntryDialog.tsx`, `src/constants/languages.ts`.

**Pliki:** `ui/decksettings/*`, `ui/modedetail/*` (w tym AddStepDialog,
CompoundStepDialog, StepListEditor), `ui/imports/*`, `ui/appsettings/*`.

**Checklista DoD:**
- [ ] Deck settings 1:1 z sekcją 3.4 (zapisy na blur vs od razu, stepper stron
      z zachowaniem ukrytych, movePage przez repo, filtr/kolejność/tryb, archiwizacja
      → dashboard, usunięcie z tokenem per język + await + błąd bez nawigacji).
- [ ] Edytor trybów 1:1 z sekcją 3.5 (lista: built-in reset/custom delete; nowy tryb
      z szablonów; edytor kroków: opisy z parametrami, reorder, delete, AddStepDialog
      driven by StepRegistry — kategorie, pola, defaulty, clampy, warunek;
      CompoundStepDialog z pełną edycją obu gałęzi; walidacja przed zapisem,
      błędy przy krokach).
- [ ] Import 1:1 z sekcją 3.6 (wklej + SAF; detekcje z ręcznym override; nazwy/języki
      stron edytowalne; podgląd dwustronnie spójny z tekstem; disabled states;
      sukces→dashboard; błąd→komunikat bez zapisu).
- [ ] App settings 1:1 z sekcją 3.9 (motyw, dynamic colors — ukryte <Android 12,
      język, ttsRate, tło; backup eksport SAF z nazwą wg wzorca + import z walidacją
      i nietkniętym stanem przy błędzie; reset fabryczny z confirm; wersja).
      ŻADNEJ sekcji konta/logowania.
- [ ] Testy VM: import draft (port importDraftReducer/Helpers — spójność
      tekst↔podgląd), walidacja trybu przed zapisem, deck settings (stepper/token).
**NIE WOLNO:** zapisu nazwy per znak; kasowania ukrytych stron; zapisu niepoprawnego
trybu; hardkodowania tokenów i języków.

---

### T10 — Polish + release: regresja, Auto Backup, R8, podpis

**Cel:** apka gotowa do wydania; wszystko z sekcji 12 przechodzi.
**Czytaj:** sekcje 3 (całość), 12; referencje: `TensorDeck/docs/REGRESSION.md`
(sekcje nie-sync/nie-web jako inspiracja).

**Checklista DoD:**
- [ ] Pełne przejście checklisty z sekcji 12 na urządzeniu/emulatorze — wynik
      punkt po punkcie w raporcie (każdy punkt: PASS/FAIL+fix/N/A z powodem).
- [ ] Audyt predictive back na 9 ekranach (w tym exit-confirm w Study i dialogi).
- [ ] Weryfikacja Auto Backup: `adb shell bmgr backupnow com.raycoll.app` →
      odinstalowanie → instalacja → dane wróciły (opisz procedurę i wynik).
- [ ] Release build: minify+R8 z regułami dla Room/kotlinx-serialization/Media3;
      `assembleRelease` zielony; apka z release APK uruchamia się i przechodzi
      smoke (dashboard→study→ocena→stats).
- [ ] Konfiguracja podpisu: signingConfig czytający keystore z properties
      (bez sekretów w repo); instrukcja generowania keystore w README sekcja Release.
- [ ] Ikona, splash, nazwa, wersja (versionName 1.0.0, versionCode 1) — spójne.
- [ ] Wydajność: talia 500 kart — browse scrolluje płynnie, wejście w study < 1 s
      (jeśli nie: napraw, opisz co).
- [ ] Finalny grep TODO/FIXME/@Ignore po CAŁYM app/src = pusto.
- [ ] README.md: jak zbudować, jak uruchomić testy, struktura projektu (1 strona).
**NIE WOLNO:** wyłączać testów; "naprawiać" regresji przez usuwanie funkcji.

---------------------------------------------------------------------------------------
## 11. LISTA TODO — TRACKING POSTĘPU
---------------------------------------------------------------------------------------

Odhaczaj po zamknięciu (build+testy+raport+diff zweryfikowane przez orkiestratora).

- [ ] **T1 Fundament** — projekt kompiluje się; motyw+dynamic colors; 9 tras
      z predictive back; strings.xml ×5 z testem spójności; DataStore prefs;
      Auto Backup rules; ikony.
- [ ] **T2 Domain-modele** — modele+serializacja (round-trip); FSRS z testami 1:1;
      SpeechMatcher; DataNormalizer; BackupValidator; SeedData.
- [ ] **T3 Domain-operacje** — operations (twarde delete) z testami; selektory
      z testami; import CSV z testami.
- [ ] **T4 SessionEngine** — rejestr/ekspansja/walidacja kroków; reducer; executor
      (17 kroków); silnik (rate/re-rate, prev/next, pauza, hold, peek, gate'y,
      substytucje tła); komplet testów sesji.
- [ ] **T5 Room** — encje+DAO+konwertery; repozytoria transakcyjne; seed-on-empty;
      backup export/import; purger; testy Robolectric.
- [ ] **T6 Playback** — TTS (interrupt≠error, voice check); STT (akumulator,
      timeouty, prefer-offline); interlock; audio focus; dźwięki+haptyka; holder.
- [ ] **T7 UI-przegląd** — Dashboard; Browse; Stats; Archive; purger wpięty;
      testy VM.
- [ ] **T8 Study+tło** — pełny ekran nauki (gesty, oceny, STT panel, finished);
      serwis Media3 z notyfikacją; auto-pauzy; onTaskRemoved; raport testu
      manualnego tła.
- [ ] **T9 UI-konfiguracja** — Deck settings; edytor trybów (oba dialogi);
      Import; App settings z backupem; testy VM.
- [ ] **T10 Release** — checklista sekcji 12 przechodzi; Auto Backup zweryfikowany;
      release build z R8; podpis; README.

---------------------------------------------------------------------------------------
## 12. FINALNA CHECKLISTA REGRESJI MANUALNEJ (akceptacja w T10)
---------------------------------------------------------------------------------------

Dashboard:
- [ ] Lista talii renderuje się; paski i liczniki zgodne ze statystykami.
- [ ] Study/Browse otwierają właściwą talię; wybór trybu na karcie talii przeżywa
      restart apki.
- [ ] Empty state dopiero po usunięciu/archiwizacji wszystkich talii.
Study:
- [ ] 0 due → ekran "brak kart"; >0 → auto-start.
- [ ] Tap odsłania strony; po wszystkich → 4 przyciski ocen; ocena → następna karta
      i heatmapa dziś +1 (sprawdź w Stats).
- [ ] Rating 1 → karta w failed; na końcu "Powtórz oblane" widoczne tylko gdy
      failed>0 i działa; "Od nowa" działa.
- [ ] Tryb ze speak_page czyta właściwym językiem i szybkością (ttsRate z ustawień).
- [ ] Tryb z listen_and_check: mikrofon działa, partial widać, próg decyduje
      o correct/incorrect; tap podczas nasłuchu → ręczne oceny (bez auto-fail).
- [ ] Tap podczas TTS/pauzy pomija krok, głos milknie natychmiast; tap na ocenach
      nic nie robi.
- [ ] Hold: <500 ms odsłania trwale; >=500 ms podgląd i powrót; trzymanie blokuje
      przejście do następnej karty.
- [ ] Poprzednia: zaraz po starcie karty cofa, później restartuje kartę; ponowna
      ocena cofniętej karty nadpisuje (heatmapa bez podwójnego +1).
- [ ] Back/gest → dialog przerwania; potwierdzenie → podsumowanie endedEarly.
- [ ] Bez internetu/mikrofonu sesja nie crashuje (komunikaty błędów).
Tło (tryb hands-free + toggle ON):
- [ ] Lock screen: sesja gra dalej; notyfikacja pokazuje kartę i x/y; play/pauza/
      następna/poprzednia/stop działają; po stop/końcu notyfikacja znika.
- [ ] Telefon/inna apka audio → sesja pauzuje; głośność zmienia media, nie dzwonek.
- [ ] Swipe apki z recents → cisza, brak martwej notyfikacji.
- [ ] Tryb z ocenami w tle → auto-pauza "wstrzymane" przy ocenie.
Browse:
- [ ] Search po wszystkich stronach; chipy z poprawnymi licznikami.
- [ ] Edycja: <2 pola → save disabled; zapis zmienia kartę; anuluj nie zmienia;
      ukryte strony przeżywają edycję (sprawdź zwiększając potem liczbę stron).
- [ ] FAB dodaje i otwiera edycję; delete z potwierdzeniem usuwa trwale.
Deck settings:
- [ ] Nazwa na blur; nazwy stron na blur; język od razu; przestawienie stron
      przestawia też treść kart (sprawdź w Browse).
- [ ] Stepper stron: zmniejszenie ukrywa bez kasowania, zwiększenie przywraca dane.
- [ ] Filtr/kolejność/tryb zapisują się; archiwizacja przenosi do archiwum;
      usunięcie wymaga tokenu w języku UI.
Edytor trybów:
- [ ] Add step każdego TYPU (przejdź wszystkie 17) buduje poprawny krok z clampami.
- [ ] Compound dialog buduje krok każdego RODZAJU (9); gałęzie edytowalne.
- [ ] Reorder/delete działają; niepoprawny tryb nie da się zapisać; reset built-in
      przywraca seed; nowy tryb staje się aktywny.
Import:
- [ ] CSV z ";", TSV z tabem, CSV z "," i "|" — separator wykryty; ręczny override
      działa.
- [ ] Nagłówek wykrywany (checkbox zaznaczony) dla pliku z nagłówkiem; języki
      i nazwy stron z nagłówka; edycja ręczna działa.
- [ ] Edycja/usunięcie wiersza w podglądzie ↔ tekst źródłowy spójne.
- [ ] Pusta nazwa talii / brak wierszy → Import disabled; po imporcie talia na
      dashboardzie ze wszystkimi kartami jako "new".
- [ ] Plik .csv/.txt/.md przez picker działa.
Stats/Archive:
- [ ] Heatmapa pokazuje dzisiejsze oceny; streak poprawny (dzień przerwy zeruje,
      dzisiejszy brak jeszcze nie); sumy zgadzają się z taliami.
- [ ] Archiwum: countdown maleje; restore przywraca na dashboard; po 14 dniach
      talia znika trwale (test z fake czasem lub zmianą zegara).
App settings:
- [ ] Motyw/dynamic colors/język działają natychmiast i po restarcie.
- [ ] ttsRate zmienia szybkość lektora w study.
- [ ] Eksport tworzy plik JSON; import z niego przywraca stan 1:1; błędny plik →
      komunikat, stan nietknięty; reset fabryczny → seedy.
System:
- [ ] Predictive back na każdym ekranie; obrót ekranu nie gubi stanu ekranów.
- [ ] Kill apki (force stop) → po ponownym uruchomieniu wszystkie dane są
      (talie, SRS, heatmapa, wybory trybów).
- [ ] Auto Backup: backupnow → reinstall → dane wróciły.

---------------------------------------------------------------------------------------
## 13. RYZYKA I PUŁAPKI IMPLEMENTACYJNE
---------------------------------------------------------------------------------------

1. **TTS zgłasza błąd przy przerwaniu** (Android emituje error na stop) — semantyka
   "interrupt ≠ error" z T6 jest OBOWIĄZKOWA, inaczej każdy skip pokaże błąd.
2. **Zombie-callbacki STT** po skip/advance — wynik przychodzący po zakończeniu
   kroku musi być ignorowany (reducer ignoruje UPDATE_PARTIAL_STT poza listening;
   anulowany Job nie dispatchuje).
3. **TextToSpeech init jest asynchroniczny** — pierwszy speak musi poczekać na
   onInit (suspend), inaczej pierwsza karta jest niema.
4. **SpeechRecognizer wymaga main thread** i bywa niedostępny na emulatorach —
   błąd ma być komunikatem, nie crashem.
5. **Wiszący hold**: zablokowanie ekranu w trakcie trzymania karty może nigdy nie
   dostarczyć "puszczono" — hold zwalniany przy każdym zejściu w tło/pauzie.
6. **Podwójne odpalenie karty**: ADVANCE_CARD + auto-run — blokada
   lastExecutedCardIndex jest krytyczna; szybkie wielokrotne tapy nie mogą podwójnie
   pominąć kroku (skip idempotentny per krok).
7. **kotlinx.serialization dyskryminatory**: `type` dla ModeStep i `kind` dla
   CompoundParams — @JsonClassDiscriminator per hierarchia; test round-trip łapie
   pomyłki. R8: reguły serializera (T10).
8. **Room + relacja deck→cards**: kaskada ON DELETE; mutacje wielotabelowe tylko
   @Transaction — inaczej kill apki w złym momencie zostawia sieroty.
9. **Predictive back vs dialog wyjścia ze study**: BackHandler musi być aktywny
   tylko gdy sesja aktywna, inaczej blokuje gest na finished/no-due.
10. **FGS na Androidzie 14+**: typ mediaPlayback wymaga deklaracji w manifeście
    i uzasadnienia (media session) — inaczej crash przy starcie serwisu.
11. **Compose: silnik ↔ UI**: stan silnika przez collectAsStateWithLifecycle;
    ŻADNYCH side-effectów w kompozycji (LaunchedEffect na zdarzenia).
12. **DataStore i język**: setApplicationLocales wywołane przy starcie zanim
    pierwsza kompozycja czyta stringi (inaczej miga domyślny język).
13. **Fisher-Yates z Random** — wstrzykuj Random do testowalności kolejności random.
14. **Duże talie**: browse na LazyColumn z kluczami (id kart), statystyki liczone
    w VM (nie w item composable).

---------------------------------------------------------------------------------------
## 14. FAZA 2 (OPCJONALNA, POZA ZAKRESEM v1): SYNC MIĘDZY URZĄDZENIAMI
---------------------------------------------------------------------------------------

Jeśli kiedyś wróci potrzeba kont/syncu — NIE zgaduj, tylko wróć do tej sekcji
i do starego kodu. Co trzeba będzie dodać:

1. **Tombstony**: pola `deletedAt` na Card/Deck/StudyMode + miękkie usuwanie +
   filtrowanie "żywych" + reguła wskrzeszenia (edycja PO usunięciu ożywia rekord).
2. **Merge** (spec: `TensorDeck/src/store/selectors/merge.ts` + testy merge/tombstones):
   karty — SRS never-regress (więcej repetitions wygrywa; remis → nowszy srsUpdatedAt,
   remis → strona chmury), treść po contentUpdatedAt; talie — winner po updatedAt,
   karty zawsze mergowane; tryby — wbudowane wskrzeszane; heatmapa — max per dzień.
   Pola `*UpdatedAt` już są stemplowane w v1 właśnie po to.
3. **Izolacja kont**: kolumna `namespace` we wszystkich tabelach (''=gość, uid=konto),
   przełączanie przy logowaniu, ciche przejęcie danych gościa przy pierwszym
   logowaniu na świeże konto (kopia, nie move).
4. **Backend**: Firestore (projekt już istnieje) — schema: `users/{uid}`,
   `users/{uid}/decks/{deckId}`, `users/{uid}/decks/{deckId}/cards/{cardId}`,
   `users/{uid}/studyModes/{modeId}`, `users/{uid}/activity/{yyyy-MM-dd}`;
   `firestore.rules` + indeksy w starym repo. Auth: Credential Manager → Firebase.
5. **Polityka zapisu do chmury** (spec: `useStorePersistence.ts` + `persistenceQueue.ts`
   + testy): debounce 1200 ms latest-wins single-in-flight z retry; w study próg
   10 ocen + flush na granicy sesji; immediate+await dla importów/usunięć (z
   rollbackiem lokalnym przy błędzie chmury); flush na zejściu w tło; diff-save po
   cache'owanym snapshocie chmury (spec: `firebasePersistence.ts`); bootstrap
   z timeoutem 8 s → merge → push; seedy nie-persystowane przy niedostępnej chmurze.
6. Poprzednia wersja tego planu (z pełnym rozpisaniem syncu na taski) jest w historii
   gita tego pliku.

--- KONIEC PLANU ---

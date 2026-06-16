# RayColl — Regression checklist

Praktyczna checklista regresji do odpalenia po każdym większym etapie refaktoryzacji. Sekcja **Commands** ma być zawsze zielona; **Manual smoke test** — przeklikany na web + co najmniej jednym natywnym buildzie (Android) dla większych zmian.

## Commands

Z roota projektu:

```bash
npm run lint
npm run typecheck
npm run test
npx expo export --platform web
```

Wszystkie cztery muszą przejść bez błędu. Lint może mieć tylko log ładowania `.env` (`env: load .env` + `env: export ...`) — to nie błąd.

## Manual smoke test

Każdą sekcję wykonujemy w dwóch kontekstach:

- **local-only** — uruchomione z pustym lub brakującym `.env`. Aplikacja musi działać, `auth === null`, `db === null`, brak cloud sync.
- **logged-in** — z poprawnym `.env`, po zalogowaniu Google. Firestore dokument `users/{uid}` aktualizuje się zgodnie ze [study persistence policy w CLAUDE.md](../CLAUDE.md) (nie per karta).

### Dashboard

- [ ] Lista talii renderuje się.
- [ ] Każda karta talii pokazuje `SegmentedProgressBar` z liczbami zgodnymi z `computeCardStats`.
- [ ] Przycisk **Study** otwiera study screen tej samej talii.
- [ ] Przycisk **Browse** otwiera browse tej samej talii.
- [ ] `StudyModeMenuButton` na karcie talii wybiera tryb i wybór zostaje po wejściu/wyjściu.
- [ ] Otwarcie Settings danej talii działa.
- [ ] Otwarcie Import i Stats działa.
- [ ] `EmptyDashboardState` pokazuje się dopiero po usunięciu wszystkich talii.

### Import

- [ ] Wklejony CSV z `;` — preview pokazuje wiersze, wykryty separator = `semicolon`.
- [ ] TSV (taby) — wykryty separator = `tab`.
- [ ] CSV z `,` i `|` — separator wykryty poprawnie.
- [ ] Wykryta liczba stron (`pageCount`) zgodna z pierwszą linią.
- [ ] Wykryte nazwy stron i języki w nagłówku (`Phrase`, `Translation`, …) — `detectLangFromHeader`.
- [ ] Upload pliku `.csv` / `.txt` / `.md` (DocumentPicker) działa na web i Android.
- [ ] Edycja jednego wiersza w preview zmienia rawText i preview pozostaje spójne.
- [ ] Usunięcie wiersza w preview zmniejsza listę i rawText.
- [ ] Pusta nazwa talii → przycisk Import disabled.
- [ ] Brak fiszek w preview → przycisk Import disabled.
- [ ] Po udanym imporcie wracamy na dashboard i talia tam jest.
- [ ] **Rollback:** po błędzie persystencji importu (np. odłączony internet przy zalogowanym) → Snackbar z błędem, lista talii bez nowej talii.

### Browse

- [ ] Lista kart renderuje się.
- [ ] Search filtruje po wszystkich `pages` karty.
- [ ] Chipsy filtra (`all` / `new` / `learning` / `review` / `mastered`) liczą zgodnie z `computeCardStats` + `getCardCategory`.
- [ ] Inline edit fiszki z `< 2` wypełnionymi polami — przycisk save jest disabled, nie można zapisać.
- [ ] Inline edit z poprawnymi polami zapisuje zmianę.
- [ ] Anulowanie edycji nie modyfikuje karty.
- [ ] FAB **+** dodaje nową kartę i od razu otwiera edycję.
- [ ] Dialog `DeleteFlashcardDialog` wymaga potwierdzenia i usuwa kartę.
- [ ] `SegmentedProgressBar` w nagłówku odzwierciedla aktualne statystyki po każdej zmianie.

### Settings decku

- [ ] Zmiana nazwy talii zapisuje się na **blur**, nie na każdy keystroke.
- [ ] Zmiana nazwy strony (col name) zapisuje się na **blur**.
- [ ] Zmiana języka strony (`AppSelect`) zapisuje się od razu po wyborze.
- [ ] Strzałki przesuwania strony zamieniają **nazwy + języki + zawartość kart** (sprawdź w Browse, że `pages` w kartach też się przesunęły).
- [ ] Zwiększenie/zmniejszenie `activePageCount` zmienia liczbę widocznych stron, **nie kasuje** zawartości ukrytych.
- [ ] Zmiana `studyFilter` (StudyScopeSection) zostaje.
- [ ] Wybór trybu nauki w `StudyModeSelector` zostaje (Dashboard pokazuje nowy aktywny tryb na karcie talii).
- [ ] Edycja kroków trybu: add / move (strzałki) / delete — zostają (dla nie-domyślnych trybów).
- [ ] `AddStepDialog` poprawnie buduje krok każdego typu: `show_page`, `speak_page`, `dynamic_pause`, `wait`, `listen_and_check`.
- [ ] Tworzenie nowego custom trybu (`CreateStudyModeSection`) zapisuje go i ustawia jako aktywny.
- [ ] `ArchiveDeckDialog`: archiwizuje talię, wraca na dashboard.

### Study

- [ ] Otwarcie study przy `dueCards.length === 0` pokazuje ekran "no due".
- [ ] Otwarcie study z kartami startuje sesję automatycznie.
- [ ] Tap na karcie odsłania kolejne strony (`waitingForTap`).
- [ ] Po odsłonięciu wszystkich aktywnych stron pokazują się 4 rating buttons.
- [ ] Rating 1 → karta trafia do `failed`, sesja idzie dalej, **heatmap dziś +1**.
- [ ] Rating 2/3/4 → FSRS przeliczone, sesja idzie dalej, **heatmap dziś +1**.
- [ ] Zakończenie sesji → ekran "bravo" z `SegmentedProgressBar`.
- [ ] **Restart failed** widoczny tylko jeśli `failedCount > 0` i działa.
- [ ] **Restart session** działa.
- [ ] **Back to panel** działa.
- [ ] Tryb z `speak_page` → TTS odtwarza tekst w prawidłowym języku (`pageLanguages[i]`).
- [ ] Tryb z `listen_and_check` → mikrofon się włącza, partial STT widać w `sttResultText`.
- [ ] `listen_and_check` ≥ `successThreshold` → auto-rating, ikona success, przejście dalej.
- [ ] `listen_and_check` < threshold → karta na `failed`, ewentualne korekcyjne TTS (jeśli `incorrectTtsPageIndex` ustawione).
- [ ] Press-and-hold na karcie blokuje przejście do następnej (`waitUntilReleased`).
- [ ] **TTS/STT no crash:** bez mikrofonu / bez głosów systemowych sesja nie wywala aplikacji (`SET_ERROR` reducer + komunikat).
- [ ] **Dotyk pomija TTS/pauzę:** kliknięcie karty podczas odtwarzania TTS lub pauzy przerywa krok i przechodzi dalej; głos TTS milknie natychmiast.
- [ ] **Dotyk pomija nasłuch:** kliknięcie podczas nasłuchu STT zatrzymuje mikrofon (ikonka przestaje pulsować), pojawia się ręczna ocena, karta nie jest oznaczona jako „nie umiem".
- [ ] **Dotyk na ekranie oceny:** kliknięcie nie robi nic (oceny nie pomijamy).
- [ ] **Przytrzymanie podgląda stronę:** naciśnięcie i przytrzymanie 500ms odsłania podgląd strony; puszczenie po <500ms trwale odsłania stronę; puszczenie po >=500ms cofa podgląd (strona znów ukryta).
- [ ] **Podgląd w trakcie nasłuchu:** przytrzymanie w trakcie STT odsłania stronę, mikrofon dalej słucha; po puszczeniu (>=1s) strona się chowa, logika nie ruszyła.
- [ ] **Stres — szybkie dotknięcia:** wielokrotne szybkie kliknięcia w trakcie odsłaniania — brak migotania układu i brak podwójnego pominięcia kroku.

### Stats

- [ ] Ekran `stats` otwiera się z dashboardu.
- [ ] Heatmap dni pokazuje dzisiejsze i wcześniejsze wpisy z `activityHeatmap`.
- [ ] Sumy statystyk zgodne z `computeCardStats` na wszystkich taliach.

### App settings

- [ ] Przełączenie theme (light / dark / system) działa natychmiast i jest persisted.
- [ ] Przełączenie języka i18n działa.
- [ ] Zmiana `ttsRate` wpływa na szybkość TTS w study.
- [ ] **Login Googlem** (przy poprawnym `.env`) → użytkownik pojawia się, sync zaczyna działać, dane lokalne mergują się z chmurą (`mergeUserData`).
- [ ] **Logout** — user znika, dane lokalne (`local-only` storage) zostają.
- [ ] Eksport backupu → JSON zawiera `groups`, `studyModes`, `activityHeatmap`.
- [ ] Import backupu z poprawnego JSON-a wczytuje dane i zastępuje stan (`validateBackupData`).
- [ ] Import backupu z niepoprawnego JSON-a → komunikat błędu, stan bez zmian.
- [ ] **Reset to defaults** zwraca seed groups i seed modes.

### Persistence / Firebase smoke

- [ ] **Local-only mode:** brak `.env` (lub puste klucze Firebase) — start, działa, `auth === null`, `db === null`, `signIn` console.warn ale brak crasha.
- [ ] **Login → merge:** wcześniejsze dane lokalne nadal widoczne po zalogowaniu (zgodnie z regułami z `mergeUserData`: więcej repetycji wygrywa, heatmap max per day, custom modes preserved).
- [ ] **Logout:** dane lokalne nadal widoczne na świeżo otwartym tabie / restarcie.
- [ ] **Restart aplikacji** (web: hard refresh; native: kill + relaunch) → talie, fiszki, SRS state, heatmap, wybrane tryby wracają.
- [ ] **AsyncStorage round-trip:** edycja w Browse → kill aplikacji → relaunch → edycja zachowana.
- [ ] **Firestore (po zalogowaniu):** zmiany trafiają do dokumentu `users/{uid}` w konsoli Firebase.
- [ ] **Study persistence policy:** sesja 12 ratingów → cloud `setDoc` widać **1× po 10 ratingach** i **1× na wyjściu/zakończeniu** (max 2), **nie** 12× per karta. Lokalnie (AsyncStorage) zapis może być częstszy.
- [ ] **Sync status:** w czasie zapisu `syncStatus` przechodzi `idle → saving → syncing → idle`; przy błędzie `error` + `lastSyncError` ustawione.
- [ ] **Critical ops zawsze immediate:** `importDeck`, `importState`, delete deck, `signOut` — cloud write potwierdzony przed kontynuacją lub błąd surface'owany w `lastPersistenceError` / `lastSyncError`.

#### Prompt 9 — kolejność zapisu i rollback (nowe)

- [ ] **Lokalna kolejność zapisu:** dodaj 3 karty pod rząd → kill aplikacji → relaunch → wszystkie 3 karty są (żaden zapis nie nadpisał nowszego).
- [ ] **Sesja 10 ratingów → restart:** postęp SRS i heatmap zachowane, heatmap dnia **nie jest podwojony** (jeden inkrement per oceniona karta).
- [ ] **Usunięcie zestawu jest awaitowane:** usuń talię → od razu zapisane lokalnie + cloud; restart → talia nie wraca.
- [ ] **importState rollback:** import backupu przy odłączonym/błędnym Firebase (zalogowany) → baner błędu, a po restarcie stan = sprzed importu (dysk i pamięć spójne, nie częściowy import).
- [ ] **Recovery baneru sync:** po transient cloud error kolejna udana synchronizacja czyści `lastSyncError` (baner znika).

#### Prompt 11 — product decisions regression (nowe)

- [ ] **First login new account → migration dialog:** guest user z danymi loguje się na nowe konto → dialog z 2 opcjami (Przenieś na konto / Zacznij od nowa). "Przenieś" → dane guest na koncie. "Od nowa" → seed dane na koncie, guest dane zachowane lokalnie. Anuluj/Wstecz → wylogowanie, powrót do guest.
- [ ] **Existing account → load cloud bez migracji:** user loguje się na konto z istniejącymi danymi w chmurze → dane cloud + merge, brak dialogu migracji.
- [ ] **Logout → guest state restored:** po wylogowaniu aplikacja wraca do lokalnego stanu guest (oddzielny od konta).
- [ ] **Delete on device A → nie wraca z device B:** usunięta talia/fiszka (tombstone z deletedAt) propaguje się przez sync i nie wraca po merge z chmurą.
- [ ] **activePageCount decrease/increase preserves hidden pages:** zmniejszenie liczby stron → ukryte strony zachowane. Zwiększenie → ukryte strony widoczne ponownie z danymi.
- [ ] **Mobile backup exports real .json file:** na Android/iOS eksport tworzy plik `raycoll-backup-YYYY-MM-DD-HH-mm.json` przez expo-file-system + expo-sharing, nie Share.share z tekstem.
- [ ] **Localized delete token per language:** token potwierdzenia usunięcia talii zależy od języka (PL: USUŃ, EN: DELETE, DE: LÖSCHEN, ES: BORRAR, IT: ELIMINA), nie hardcoded "DELETE".
- [ ] **Icon/cache web update smoke:** po deployu favicon.ico ma `no-cache`, hashed JS assety mają `immutable`, HTML shell ma `no-cache`.

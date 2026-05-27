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
- [ ] Inline edit fiszki z `< 2` wypełnionymi polami → karta zostaje **usunięta** (obecne zachowanie).
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
- [ ] `AddStepDialog` poprawnie buduje krok każdego typu: `show_page`, `speak_page`, `dynamic_pause`, `wait`, `listen_and_branch`.
- [ ] Tworzenie nowego custom trybu (`CreateStudyModeSection`) zapisuje go i ustawia jako aktywny.
- [ ] `DeleteDeckDialog`: usuwa dopiero po wpisaniu dokładnie `DELETE`, wraca na dashboard.

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
- [ ] Tryb z `listen_and_branch` → mikrofon się włącza, partial STT widać w `sttResultText`.
- [ ] `listen_and_branch` ≥ `successThreshold` → auto-rating, ikona success, przejście dalej.
- [ ] `listen_and_branch` < threshold → karta na `failed`, ewentualne korekcyjne TTS (jeśli `incorrectTtsPageIndex` ustawione).
- [ ] Press-and-hold na karcie blokuje przejście do następnej (`waitUntilReleased`).
- [ ] **TTS/STT no crash:** bez mikrofonu / bez głosów systemowych sesja nie wywala aplikacji (`SET_ERROR` reducer + komunikat).

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
- [ ] **Critical ops zawsze immediate:** `importDeck`, `importState`, `resetToDefault`, delete deck, `signOut` — cloud write potwierdzony przed kontynuacją lub błąd surface'owany w `lastPersistenceError` / `lastSyncError`.

# Samo-audyt checklisty regresji (sekcja 12 PORT_PLAN)

Weryfikacja **w kodzie** (bez urządzenia). Legenda:
- **OK** — logika zaimplementowana, wskazany `plik:linia`.
- **N-A** — zachowanie czysto runtime/UI/urządzeniowe (lock screen, mikrofon,
  obrót, reinstall); weryfikacja wymaga telefonu. Wskazana ścieżka kodu, która to obsługuje.
- **RYZYKO** — brak/wątpliwość w kodzie.

Data: 2026-07-13. Suite: 195 testów jednostkowych zielone.

> **Aktualizacja 2026‑07‑14:** głębszy red‑team 1:1 vs TensorDeck (patrz
> [`REDTEAM_AUDIT.md`](REDTEAM_AUDIT.md)) wykrył i naprawił **11 realnych błędów**,
> których ten samo‑audyt nie złapał (m.in. tasowanie „random" gubiące ostatnią kartę,
> wyciek statusu blokujący bramkę drugiej karty, cofanie zwiększenia liczby stron).
> Suita po naprawach: **206 testów zielonych**. Poniższa lista pozostaje aktualna co do
> ścieżek kodu; skreślone „nie znaleziono błędów" — patrz nowy audyt.

## Dashboard
- OK — Lista talii + paski/liczniki: [DashboardViewModel.kt](app/src/main/java/com/raycoll/app/ui/dashboard/DashboardViewModel.kt), statystyki z [CardStats.kt](app/src/main/java/com/raycoll/app/domain/selectors/CardStats.kt). Test: `DashboardViewModelTest`.
- OK — Wybór trybu przeżywa restart: `setActiveMode` → `deckRepository` (Room, persist) — `DashboardViewModelTest > setActiveMode`. Renderowanie/nawigacja: N-A (telefon).
- OK — Empty state po archiwizacji/usunięciu wszystkich: filtr `activeDecks` w VM; `DashboardViewModelTest > archiveDeck`.

## Study
- N-A — 0 due → "brak kart"; >0 → auto-start: logika due w [DueCards.kt](app/src/main/java/com/raycoll/app/domain/selectors/DueCards.kt), auto-run w [SessionEngine.kt:93](app/src/main/java/com/raycoll/app/domain/session/SessionEngine.kt).
- OK — Ocena → następna karta + heatmapa +1: rating w [SessionEngine.kt:141](app/src/main/java/com/raycoll/app/domain/session/SessionEngine.kt); zapis aktywności → Stats.
- OK — Rating 1 → failed; "Powtórz oblane" tylko gdy failed>0: [SessionEngine.kt:84](app/src/main/java/com/raycoll/app/domain/session/SessionEngine.kt) (`repeatFailed`), `failedCardIds` w [SessionReducer.kt:77](app/src/main/java/com/raycoll/app/domain/session/SessionReducer.kt).
- N-A — speak_page językiem/szybkością (ttsRate): [StepExecutor.kt](app/src/main/java/com/raycoll/app/domain/session/StepExecutor.kt) + TtsPlayer (playback); wymaga TTS na urządzeniu.
- N-A — listen_and_check próg correct/incorrect: [StepExecutor.kt:114](app/src/main/java/com/raycoll/app/domain/session/StepExecutor.kt) (`percent >= successThreshold`), [SpeechMatcher.kt:84](app/src/main/java/com/raycoll/app/domain/srs/SpeechMatcher.kt); mikrofon = telefon.
- N-A — Tap pomija krok, głos milknie; tap na ocenach nic nie robi: idempotentny skip [SessionEngine.kt:93](app/src/main/java/com/raycoll/app/domain/session/SessionEngine.kt); interrupt≠error obsłużone w playback.
- N-A — Hold <500/≥500 ms + blokada przejścia: logika hold w SessionEngine/StepExecutor; gest = telefon.
- OK — Poprzednia karta: cofnięcie + nadpisanie oceny bez podwójnego +1: `lastExecutedCardIndex` guard [SessionEngine.kt:93,195](app/src/main/java/com/raycoll/app/domain/session/SessionEngine.kt), komentarz o NADPISANIU [SessionEngine.kt:141](app/src/main/java/com/raycoll/app/domain/session/SessionEngine.kt).
- N-A — Back → dialog przerwania → `endedEarly`: flaga w SessionState; UI dialog = telefon.
- N-A — Bez internetu/mikrofonu bez crasha: błędy STT jako komunikaty (ryzyko #4 planu obsłużone w playback).

## Tło (hands-free)
- N-A — Lock screen gra dalej, notyfikacja x/y, kontrolki: [StudySessionService.kt](app/src/main/java/com/raycoll/app/playback/StudySessionService.kt) (Media3 MediaSessionService); wymaga urządzenia.
- N-A — Audio focus (telefon/inna apka → pauza; głośność media): [AudioFocusManager.kt](app/src/main/java/com/raycoll/app/playback/AudioFocusManager.kt).
- N-A — Swipe z recents → cisza, brak martwej notyfikacji: `onTaskRemoved` w StudySessionService.
- N-A — Auto-pauza przy ocenie w tle: obsługa w service + SessionEngine.

## Browse
- OK — Search po wszystkich stronach + chipy z licznikami: [BrowseViewModel.kt](app/src/main/java/com/raycoll/app/ui/browse/BrowseViewModel.kt); `BrowseViewModelTest`.
- OK — Edycja <2 pola → save disabled; ukryte strony przeżywają edycję: update w VM; `BrowseViewModelTest > updateCard`. Walidacja disabled = UI (N-A dla pikseli).
- OK — FAB dodaje i otwiera edycję; delete trwały: `addCard` emituje editRequest [BrowseViewModel](app/src/main/java/com/raycoll/app/ui/browse/BrowseViewModel.kt); `BrowseViewModelTest > addCard/deleteCard`.

## Deck settings
- OK — Nazwa/nazwy stron/język; przestawienie stron przestawia treść kart: `updatePageName`/`setPageLanguage`/`movePage` [DeckSettingsViewModel.kt:65](app/src/main/java/com/raycoll/app/ui/decksettings/DeckSettingsViewModel.kt); `DeckSettingsViewModelTest`.
- OK — Stepper stron ukrywa bez kasowania / przywraca: `activePageCount` (miękkie ukrycie); kolumny kart zachowane w Room.
- OK — Archiwizacja + usunięcie z tokenem w języku UI: delete onDone/onError w VM; `DeckSettingsViewModelTest`. Token UI = weryfikacja tekstu (N-A dla pikseli).

## Edytor trybów
- OK — Add step każdego typu z clampami: [StepRegistry.kt:44](app/src/main/java/com/raycoll/app/domain/steps/StepRegistry.kt) (`clampStepNumberField` → `coerceIn`); 31 wariantów `@SerialName` w [ModeStep.kt](app/src/main/java/com/raycoll/app/domain/model/ModeStep.kt).
- OK — Compound dialog + gałęzie edytowalne: [CompoundExpander.kt](app/src/main/java/com/raycoll/app/domain/steps/CompoundExpander.kt), [ModeStepValidator.kt](app/src/main/java/com/raycoll/app/domain/steps/ModeStepValidator.kt).
- OK — Reorder/delete; niepoprawny tryb nie zapisze; reset built-in; nowy aktywny: ModeStepValidator + [SeedModes.kt](app/src/main/java/com/raycoll/app/domain/srs/SeedModes.kt). Interakcje reorder = N-A (telefon).

## Import
- OK — Detekcja separatora (`;`/tab/`,`/`|`) + override: [CsvParser.kt](app/src/main/java/com/raycoll/app/domain/imports/CsvParser.kt) + [ImportViewModel.kt](app/src/main/java/com/raycoll/app/ui/imports/ImportViewModel.kt); `ImportViewModelTest > setSourceText`.
- OK — Nagłówek/języki/nazwy stron z nagłówka: [HeaderDetector.kt](app/src/main/java/com/raycoll/app/domain/imports/HeaderDetector.kt); `ImportViewModelTest`.
- OK — Edycja/usunięcie wiersza ↔ tekst źródłowy spójne: `updateCell` [ImportViewModel](app/src/main/java/com/raycoll/app/ui/imports/ImportViewModel.kt); `ImportViewModelTest > updateCell`.
- OK — Pusta nazwa/brak wierszy → disabled; po imporcie karty "new": walidacja `runImport` [ImportPayloadValidator.kt](app/src/main/java/com/raycoll/app/domain/imports/ImportPayloadValidator.kt); `ImportViewModelTest > runImport`.
- N-A — Picker .csv/.txt/.md: SAF/intent = telefon.

## Stats / Archive
- OK — Heatmapa dziś + streak (przerwa zeruje, dzisiejszy brak nie): [CardStats.kt](app/src/main/java/com/raycoll/app/domain/selectors/CardStats.kt) + [StatsViewModel.kt](app/src/main/java/com/raycoll/app/ui/stats/StatsViewModel.kt); `StatsViewModelTest > streak+heatmapa`.
- OK — Archiwum: countdown maleje, restore, po 14 dniach znika: [ArchiveCountdown.kt](app/src/main/java/com/raycoll/app/domain/selectors/ArchiveCountdown.kt) + `ArchivePurger` (sterowane wstrzykiwanym `Clock`).

## App settings
- OK — Motyw/dynamic colors/język natychmiast i po restarcie: `setTheme`/`setUiLanguage`/`setSystemColors` → [UserPreferencesRepository.kt](app/src/main/java/com/raycoll/app/data/local/prefs/UserPreferencesRepository.kt) (DataStore, persist); test `UserPreferencesRepositoryTest`.
- OK — ttsRate zmienia szybkość lektora: `setTtsRate` → DataStore, czytane w playback.
- OK — Eksport/import JSON 1:1; błędny plik → komunikat, stan nietknięty; reset → seedy: `exportJson`/`importJson`/`factoryReset` [BackupRepository.kt](app/src/main/java/com/raycoll/app/data/repository/BackupRepository.kt) + `try/catch → onError` w [AppSettingsViewModel.kt:87](app/src/main/java/com/raycoll/app/ui/appsettings/AppSettingsViewModel.kt). **RYZYKO(niski)**: brak testu VM dla import/export (dług — patrz KROK 2); logika repo istnieje i BackupValidator waliduje wejście.

## System
- OK — Predictive back na każdym ekranie: `android:enableOnBackInvokedCallback="true"` [AndroidManifest.xml:17](app/src/main/AndroidManifest.xml). Obrót bez utraty stanu: stan w StateFlow/VM (N-A dla faktycznego obrotu — telefon).
- N-A — Force stop → dane wracają: Room = źródło prawdy (trwały storage); wymaga urządzenia.
- N-A — Auto Backup (backupnow → reinstall): mechanizm Androida; wymaga urządzenia.

## Podsumowanie
Cała logika sterująca zachowaniami z checklisty jest obecna w kodzie i (tam gdzie
to możliwe bez urządzenia) pokryta testami jednostkowymi. Pozycje **N-A** wymagają
przejścia na fizycznym telefonie po instalacji `app-debug.apk`. Jedyne **RYZYKO**
to brak testu jednostkowego dla ścieżki backup import/export w `AppSettingsViewModel`
— logika repozytorium (`BackupRepository`) istnieje, ryzyko czysto testowe, nie
funkcjonalne. Nie wykryto błędów wymagających naprawy.

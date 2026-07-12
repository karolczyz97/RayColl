# Task #3 — Audyt UX 1:1 vs plan v4 — RAPORT

Data: 2026-07-12. Build + 156 testów zielone. 0 stubów/TODO/@Ignore w `app/src`.

## Co poprawiono ekran po ekranie (spec = sekcja 3 planu)

### Silnik sesji (rdzeń, sekcja 6.5–6.6) — przepisany
- **Peek/hold przeniesiony do silnika**: `pressDown()`/`pressUp()` — silnik sam mierzy czas trzymania (peek ≥500 ms podglądem następnej ukrytej strony przez `PeekSet`, tap <500 ms = zwykły tap). Wcześniej UI wołał `setHolding` bez peeka i bez progu.
- **Skip kroku ≠ karty**: `runSkippable {}` opakowuje TTS/czekanie/nasłuch; tap anuluje TYLKO bieżący krok (`skippableJob.cancel()`), łańcuch idzie dalej. `speak_all_pages` pomija pozostałe strony jednym skippable.
- **Ponowna ocena nadpisująca** (`submitReview`): tracking próby (`ReviewTracker`) + snapshot `baseSrsStates`; druga ocena tej samej karty liczy SRS od bazy sprzed pierwszej oceny (`reviewCardAgain`), heatmapa bez drugiego +1, `failed` ustawiane od nowa.
- **Błąd TTS**: `SpeakResult{SUCCESS,ERROR,MISSING_VOICE}` zamiast `Unit`. W tle → `onBackgroundAutoPause` (notyfikacja „wstrzymane"), na froncie → `SET_ERROR` z kluczem (osobny dla brakującego głosu → dialog z `ACTION_INSTALL_TTS_DATA`).
- **STT**: `listen` dostaje callback partiali (na żywo w panelu); skip tapem → `SKIPPED` bez liczenia dopasowania; błąd techniczny → `ERROR`; brak mowy (NO_MATCH/TIMEOUT) to NIE błąd. Twardy timeout via `withTimeout`, main-thread, `PREFER_OFFLINE`.
- **resume()**: replay bieżącej karty od kroku 0 (był no-op poza pending). **pause()**: zawsze zwalnia hold (pułapka 5).
- **Pułapka współbieżności**: `runCurrentCard` startuje Job `LAZY` i wpisuje do mapy PRZED `start()` — inaczej immediate dispatcher wykonuje ciało zanim referencja trafi do mapy i anulowanie chybia.

### SessionDepsAdapter — realny zapis
- `onCardReviewed` faktycznie pisze do repo (`reviewCard` / `reviewCardAgain`) na `@ApplicationScope`. Wcześniej był pusty komentarz „zapis w VM" — oceny nigdzie nie trafiały.

### Study (3.2)
Pasek postępu sesji (segment/karta: past/current/future, kolor=kategoria), licznik x/y w topbarze, adaptacyjna czcionka wg progów z sekcji 5, auto-scroll do najnowszej strony, panel STT z partialem+%+progiem+statusem, oceny 1–4 w kolorach semantycznych, prev/next, dialog przerwania→endedEarly, keep-screen-on tylko w aktywnej sesji, prośba o mikrofon dla trybu z STT, dialog braku głosu TTS, podsumowanie z paskiem kategorii + statystyką + „Powtórz oblane"/„Od nowa"/„Wróć".

### Dashboard (3.1)
Wybór aktywnego trybu na karcie talii (dropdown z RadioButton, zapis do talii), menu Archiwizuj z dialogiem, empty state z CTA.

### Browse (3.3)
Pasek statystyk talii w nagłówku, chipy filtrów z licznikami (all/new/learning/review/mastered), FAB dodaje kartę i od razu otwiera edycję, edycja tylko WIDOCZNYCH stron (ukryte nietknięte), etykiety=nazwy stron.

### Deck settings (3.4)
Nazwa na blur (`onFocusChanged`), strony: nazwa na blur + język (dropdown, zapis od razu) + strzałki zamiany (`movePage` transakcyjnie: nazwy+języki+treść kart), stepper 2..5, filtr/kolejność/tryb, archiwizacja, usunięcie z tokenem zależnym od języka + await + komunikat błędu.

### Import (3.6)
Pełny przepływ: pole tekstowe + plik SAF (csv/txt/md, UTF-8), auto-detekcja separatora (przełączalna), checkbox nagłówka (auto-sugestia), nazwy stron+języki z nagłówka (`HeaderDetector`, `LanguageDetector`), podgląd wierszy z edycją komórek i usuwaniem (sync z tekstem źródłowym), walidacja payloadu, Import disabled gdy pusto.

### Stats (3.7)
Heatmapa 140 dni (kolumny×7, intensywność wg liczby ocen, dziś z ramką) z realnego `activityHeatmapFlow`, streak, sumy globalne, lista talii z paskami. (Wcześniej streak liczony z `emptyMap()`.)

### Archive (3.8)
Sort najnowsze pierwsze, liczba kart (plurals), countdown „zniknie za X dni" / dziś / jutro. `ArchivePurger` wpięty w `Application` (start + co godzinę).

### App settings (3.9)
Dynamic colors ukryte <API 31, język UI (6 opcji przez per-app locale `AppCompatDelegate.setApplicationLocales`, `MainActivity`=`AppCompatActivity`, autoStoreLocales w manifeście), reset fabryczny z opisem, wersja aplikacji.

### Sync/bootstrap
- **BUG NAPRAWIONY**: `shouldTriggerMigration` był ODWRÓCONY (`hasUserLocalCache && guestHasData`) — migracja gościa nigdy by się nie odpaliła dla świeżego konta. Teraz `!hasUserLocalCache && guestHasData` (port z `useStoreBootstrap.ts`).
- `BootstrapLoader` czyta realny snapshot namespace gościa (`getSnapshotFor("")`) zamiast pustego `StoreData()`.
- `isUserAuthoredMode` porównuje edytowany built-in z seedem (nazwa/kroki).
- `validateBackupJson` parsuje i waliduje PRZED dekodowaniem/zapisem — błędny plik zostawia stan nietknięty.

## Scalone testy (z „RayColl - Kopie" i chudej wersji e5db3166)
MergeEngineTest (13), MigrationRulesTest (3), BackupValidatorTest (9), ImportParserTest (6), UserPreferencesRepositoryTest (Robolectric), nowy SessionEngineSemanticsTest (12: oceny/re-rate/skip/STT-skip/peek/TTS-fg-bg), realny I18nConsistencyTest (spójność 5 locale + zgodność argumentów formatu). Stringi: +13 kluczy ×5 języków (353 klucze/plik).

## Dług / poza zakresem tej sesji
Zobacz `docs/HANDOFF_PROMPT.md`.

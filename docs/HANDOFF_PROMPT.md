# ONESHOT PROMPT — dokończ RayColl do wydania (wklej to innemu agentowi)

Skopiuj wszystko poniżej linii do agenta w katalogu `C:\Users\Karol\.gemini\antigravity\scratch\RayColl`.

---

Jesteś inżynierem kończącym natywną apkę Android RayColl (Kotlin/Compose/Room/Media3/Firestore).
Katalog roboczy: `C:\Users\Karol\.gemini\antigravity\scratch\RayColl` (repo git na branchu main).
Powłoka: PowerShell + Bash tool. Build: `./gradlew.bat` przez Bash tool (JDK 23, SDK w `%LOCALAPPDATA%\Android\Sdk`).

KONTEKST: Cała logika (T1–T10 planu) + audyt UX są ZROBIONE. Build + 156 testów jednostkowych są zielone,
0 stubów/TODO. Working tree ma niezacommitowane zmiany audytu (~40 plików). Plan: `docs/PORT_PLAN.md`
(spec ekranów sekcja 3, checklista regresji sekcja 12). Raport audytu: `docs/AUDIT_UX_REPORT.md`.
Twoje zadanie: domknąć apkę do wydania, wykonując WSZYSTKO poniżej w jednym ciągu, bez pytań między krokami.
Punkt niewykonalny oznacz `BLOCKED(powód)` i rób resztę.

ZASADY (obowiązują bezwzględnie):
- Zero TODO/FIXME/@Ignore/stubów. Po każdej większej zmianie: `./gradlew.bat :app:testDebugUnitTest`
  i `./gradlew.bat :app:assembleDebug` muszą być zielone.
- Nie regresuj istniejących 156 testów. Nie zmieniaj stałych FSRS ani logiki silnika bez powodu z planu.
- Commituj po każdym ukończonym kroku osobnym commitem z sensownym opisem PL.
  Stopka commita: `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`.

KROK 1 — Commit audytu.
`git add -A` i zacommituj obecne zmiany working tree jako „Audyt UX 1:1 vs plan v4 + scalenie testów".
(To już gotowa praca — tylko ją utrwal.)

KROK 2 — Testy ViewModeli (dług z DoD T7/T9).
Dopisz testy jednostkowe (JUnit4 + kotlinx-coroutines-test, wzór: istniejące `*Test.kt` w `app/src/test`)
dla VM, które ich nie mają, używając fake'ów repozytoriów (interfejsy repo wołają Room — zrób cienkie fake'i
lub Robolectric jak w `UserPreferencesRepositoryTest`/`DaoTest`). Minimum:
- `DashboardViewModel`: seedIfEmpty przy starcie, setActiveMode, archiveDeck, filtr żywych trybów.
- `BrowseViewModel`: addCard emituje editRequest + ustawia liczbę stron z talii; update/delete.
- `DeckSettingsViewModel`: updatePageName/setPageLanguage/movePage aktualizują stan; delete onDone/onError.
- `ImportViewModel`: setSourceText auto-detekcja separatora/nagłówka/stron; updateCell↔tekst źródłowy;
  runImport walidacja (pusta nazwa/brak wierszy → errorMsg, poprawne → addDeckWithCards).
- `StatsViewModel`: streak+heatmapa z combine; DeckSummary per talia.
- `AppSettingsViewModel`: setTheme/setUiLanguage/setTtsRate/factoryReset; import/exportBackup happy+error path.
Cel: realne asercje zachowań ze spec, nie smoke-testy.

KROK 3 — Weryfikacja release build (R8/proguard).
`./gradlew.bat :app:assembleRelease`. Jeśli R8 wywali serializację/Room/Hilt/Media3/Firebase —
dopisz reguły do `app/proguard-rules.pro` (już zawiera bazowe) aż `assembleRelease` przejdzie.
Jeśli release wymaga podpisu i blokuje build — skonfiguruj w `app/build.gradle.kts` debugowy `signingConfig`
dla release (do smoke-testów) LUB `BLOCKED(brak keystore)` i zostaw unsigned. Nie commituj żadnych sekretów.
Po sukcesie: zweryfikuj że round-trip serializacji ModeStep/CompoundParams działa w release (test już to łapie na debug;
jeśli masz wątpliwość, dodaj instrukcję ręcznej weryfikacji do README).

KROK 4 — README.
Napisz `README.md` (PL): czym jest apka, stack, jak zbudować (`./gradlew.bat :app:assembleDebug`),
jak odpalić testy, wymóg SHA-1 w Firebase Console (patrz niżej), struktura pakietów (skrót z sekcji 7 planu),
status: v1 kompletne, chmura Firestore+Auth. Zacommituj.

KROK 5 — Samo-audyt względem checklisty regresji (sekcja 12 planu).
Przejdź checklistę sekcja 12 `docs/PORT_PLAN.md` pozycja po pozycji i dla każdej sprawdź w KODZIE
(nie na urządzeniu — nie masz go), czy zachowanie jest zaimplementowane. Wynik zapisz jako
`docs/REGRESSION_SELFCHECK.md`: każda pozycja = OK (plik:linia) / RYZYKO (opis) / N-A (wymaga telefonu).
Jeśli znajdziesz realny błąd logiki — napraw i dopisz test. Zacommituj.

KROK 6 — Raport końcowy w odpowiedzi (nie do pliku):
co zrobione, co BLOCKED, dług techniczny, oraz DOKŁADNIE ta instrukcja dla użytkownika:
„W Firebase Console projekt raycoll-io dodaj aplikacje Android `com.raycoll.app` i `com.raycoll.app.debug`
z SHA-1 debug keystore `35:80:D2:75:83:68:BB:CB:8C:3F:CD:B5:CF:01:BC:24:3B:F8:23:7C` — bez tego logowanie
Google rzuci DEVELOPER_ERROR; reszta apki działa lokalnie. Potem zainstaluj `app/build/outputs/apk/debug/app-debug.apk`
i przejdź checklistę regresji z sekcji 12 planu."

Zacznij od KROK 1. Pracuj do końca.

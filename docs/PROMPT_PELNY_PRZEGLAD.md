# Pełny przegląd RayColl — prompt do nowej sesji

Przeskanuj CAŁY kod aplikacji RayColl (`C:\Users\Karol\.gemini\antigravity\scratch\RayColl`, gałąź `przeglad-modernizacja`) i popraw wszystko, co odstaje od celów poniżej: UI, ruch, kolory, dane i synchronizację, nauka i dźwięk, import i eksport, testy i build. Każdy plik w `app/src/main` i `app/src/test`, plus `firestore.rules` i pliki Gradle.

## Kim jestem i jak ze mną pracować
- Nie jestem programistą. Decyzje techniczne podejmujesz sam. Pytasz mnie tylko o wygląd, zachowanie aplikacji i priorytety.
- Tłumacz prosto, po polsku, na przykładach.
- Zmiany zachowania lub wyglądu, które nie wynikają wprost z celów poniżej, najpierw opisz i poczekaj na moje „tak”.
- Często jestem na telefonie. Na końcu zbuduj release APK i wyślij mi go razem ze zrzutami zmienionych ekranów.

## Cele (co znaczy „dobrze”)
1. **Oryginalne komponenty Google zamiast własnych imitacji.**
   - Wszędzie, gdzie Material 3 (1.5 Expressive), Compose albo Navigation 3 ma gotowy element, używamy go zamiast własnego kodu.
   - Własny kod zostaje tylko tam, gdzie oryginału nie ma. Wtedy ma być zbudowany z prostych klocków Compose i wyglądać jak reszta.
   - Weryfikuj API w źródłach zainstalowanych wersji (sources jar z dl.google.com), nie z pamięci.
2. **Wygląd jak w Androidzie 16/17.**
   - Domyślne kształty, typografia i `motionScheme` Material 3, bez nadpisywania.
   - Ikony to Material Symbols Rounded jako `res/drawable/ic_*.xml` z repozytorium google/material-design-icons, bez `android:tint`.
   - Jedna ikona = jedno znaczenie w całej aplikacji. Ikony kategorii są w `ui/components/SrsCategoryIcons.kt`.
   - Ustawienia w stylu segmentowanych list: `SettingsGroup`, `SegmentedListItem`, tło `settingsBackground`.
   - Wybór jednej krótkiej opcji robi `SingleChoiceButtonGroup` (połączone `ToggleButton`), dłuższej opcji lista z `SettingsRadio`.
   - Okna dialogowe mają `blurredDialogProperties`.
3. **Spójność wszystkich ekranów.**
   - Te same odstępy (`Spacing`, `Spacing.screenContent()`, `Spacing.item`) i te same wzorce: usuwanie z „Cofnij” w snackbarze, FAB do tworzenia, edytory na cały ekran z X i „Zapisz”.
   - Animacje list i wysuwanych elementów przez `animateListItem` / `expandIn` / `shrinkOut`.
   - Żadnych ręcznie dobranych czasów animacji ani sprężyn tam, gdzie wystarczy `motionScheme`.
4. **Kolory poprawne w jasnym i ciemnym motywie oraz z dynamicznymi kolorami z tapety.**
   - Paski i wykresy używają `SrsColors.*Accent`, tekst na kafelkach par `*Fg`/`*Bg`.
   - Nic nie może wyglądać na „odwrócone”, kontrast minimum 3:1 dla grafiki.
5. **Dane i Firebase.**
   - Firestore to jedyne źródło danych. Nie dokładaj odczytów, nasłuchów ani zapisów bez potrzeby, bo nie chcę „zajechać” Firebase.
   - Liczenie lokalne jest lepsze niż ponowne czytanie z chmury.
   - Sprawdź zgodność ścieżek i pól z `firestore.rules` (reguła „nowsza zmiana wygrywa”, tombstony), działanie offline, wycieki nasłuchów, korutyny i cykl życia, obsługę błędów (nic nie może być połykane po cichu).
6. **Nauka.**
   - FSRS-6 (`domain/srs/SrsEngine.kt`, wagi z fsrs-rs) ma być poprawny.
   - Sesja, TTS/STT, słuchawki i praca w tle mają być stabilne.
   - Ekran nauki ma pokazywać jak najmniej wyskakujących napisów.
7. **Porządek.**
   - Martwy kod, nieużywane zasoby, stringi i importy, przestarzałe (deprecated) API, ostrzeżenia lint.
   - Brakujące testy zachowania.
   - Wersje bibliotek: Compose przez BOM-alpha i Navigation 3 alfa są dozwolone. Pozostałe stabilne, chyba że alfa daje coś widocznego dla użytkownika.

## Jak pracować
1. **Najpierw kontekst.** Przeczytaj pamięć projektu (`MEMORY.md` i `raycoll-kotlin-rewrite.md`) oraz `docs/`. Uruchom `git status`. W drzewie są NIEZACOMMITOWANE zmiany z poprzedniej sesji. To mój stan bazowy: nie cofaj ich i nie nadpisuj.
2. **Skan równoległy.** Kilku podagentów tylko do czytania, każdy na swój obszar:
   - ekrany UI (każdy plik w `ui/*`),
   - komponenty i motyw (`ui/components`, `ui/theme`, nawigacja),
   - dane i Firestore (`data/*`, `di/*`, `firestore.rules`),
   - nauka i dźwięk (`domain/session`, `playback/*`),
   - domena (`domain/srs`, `domain/imports`, operacje, seedy),
   - testy i build (Gradle, lint, ktlint).

   Każde znalezisko ma mieć: plik:linię, co jest nie tak, oryginalny zamiennik (zweryfikowane API) albo poprawkę, oraz wagę.
3. **Raport dla mnie** (krótko, po polsku, po ludzku):
   - co poprawisz od razu (błędy, niespójności, imitacje z oczywistym oryginałem),
   - co wymaga mojej decyzji (zmiany zachowania lub wyglądu), każde z jednym zdaniem „po co”.
4. **Poprawki** po kolei, w małych krokach. Po każdym pakiecie:
   - `./gradlew.bat :app:ktlintFormat`, potem `:app:ktlintCheck :app:testDebugUnitTest :app:lintDebug :app:assembleRelease` (przez narzędzie Bash na Windows);
   - wszystko musi być zielone;
   - nigdy nie osłabiaj ani nie usuwaj testów sprawdzających zachowanie. Zmieniaj asercje tylko tam, gdzie zachowanie zmieniło się celowo, i napisz to.
5. **Weryfikacja wyglądu** bez telefonu:
   - tymczasowy test Robolectric `@GraphicsMode(NATIVE)` z `captureToImage()` zapisujący do `app/build/shots`;
   - sprawdź jasny i ciemny motyw, PL i DE, szerokość 360 dp i szeroki ekran;
   - przed końcem usuń tymczasowe testy.
6. **Pułapki Windows:**
   - pliki mają mieszane końce linii LF/CRLF, więc zachowuj styl pliku;
   - skrypty Python pisz narzędziem Write do scratchpada (heredoki z cudzysłowami się sypią);
   - Gradle z katalogu głównego repo.
7. **Na koniec:**
   - podsumowanie (co zrobione, co inaczej niż planowano, czego celowo nie zrobiono, dług techniczny),
   - release APK i zrzuty wysłane do mnie,
   - pytanie, czy commitować.

   Bez mojej zgody nie commituj i nie pushuj.

# Plan porządków (wszystko poza Firestore)

Gałąź `przeglad-modernizacja`, stan wyjściowy: commit `25ec6c1c`. Data: 2026-10-15.
Status: **plan, nic nie wdrożone, gotowy do wdrożenia.** Drugi plan obok
`docs/PLAN_SYNC_FIRESTORE.md`. Tu jest wszystko, co nie dotyczy synchronizacji. Decyzje są
w sekcji 12.

## 0. W skrócie

- **Java 21 do budowania** zamiast 23. Koniec awarii Javy 23 w trakcie testów.
- **Najstarszy wspierany Android to 13** (dziś 8). Znika biblioteka AppCompat i sprawdzanie
  wersji Androida w 6 miejscach.
- **Biblioteki zamiast własnego kodu:** Apache Commons CSV (import) i Apache Commons Text
  (porównywanie mowy).
- **Wraca przełącznik „Tryb eksperta”** w edytorze trybów nauki. Zgubił się przy przepisywaniu
  aplikacji na Kotlin.
- **Sprzątanie:**
  - narzędzia po agentach AI, stare raporty z przeglądów i logi awarii idą do usunięcia;
  - repomix trafia do jednego folderu;
  - README zostaje poprawione.
- **Bez zmian:**
  - 5 języków;
  - `ScreenCompletion` i `PersistedTextField`;
  - własny FSRS (przejście na FSRS-6 to osobne zadanie).

## 1. Kolejność względem planu Firestore

| Etap | Co | Kiedy |
|---|---|---|
| 1 | Java 21 | **przed planem Firestore**: każde budowanie i każdy test w trakcie przebudowy idzie już na Javie 21 |
| 2 | Android 13 | przed planem Firestore; dotyka innych plików (start aplikacji, ustawienia języka, motyw) |
| 3 | biblioteki CSV i Levenshtein | w dowolnym momencie, niezależne od Firestore |
| 4 | tryb eksperta | w dowolnym momencie, niezależny od Firestore |
| 5 | sprzątanie, repomix, README | **po planie Firestore**: README ma opisywać stan po obu planach, a stare raporty przydają się do końca przebudowy |

Każdy etap kończy się zielonym `test`, `ktlintCheck`, `lint` i `assembleRelease`.

## 2. Java 21 do budowania (etap 1)

Awaria nie jest losowa. Log `app/hs_err_pid24024.log` pokazuje, że wywala się kompilator JIT
Javy 23.0.1 (C2), gdy w trakcie testów optymalizuje konkretną funkcję aplikacji (przepływ danych
w `StatsViewModel`). Java zaczyna optymalizować funkcję dopiero, gdy ta wykona się wystarczająco
wiele razy, a to zależy od przebiegu testów. Dlatego raz się wywala, a raz nie. To błąd samej
Javy 23, która jest wersją bez długiego wsparcia i nie dostaje już poprawek.

Naprawa:
- Gradle uruchamiany na Javie 21 (wersja z długim wsparciem, ta sama, którą dostarcza Android
  Studio);
- plik `gradle/gradle-daemon-jvm.properties` (`./gradlew updateDaemonJvm --jvm-version=21`) oraz
  wtyczka `foojay-resolver` w `settings.gradle.kts`. Gradle sam pobierze Javę 21, nic nie
  instalujesz ręcznie (na komputerze jest dziś tylko Java 23);
- aplikacja dalej kompiluje się pod Javę 17, bez zmian w kodzie;
- logi awarii w `app/` (2,4 MB, i tak w `.gitignore`) idą do usunięcia.

## 3. Najstarszy wspierany Android: 13 (etap 2)

Twój zakres to „12–13”. Wybieram **Android 13 (minSdk 33)**, bo dopiero od niego system sam
obsługuje osobny język aplikacji i można wyrzucić bibliotekę AppCompat. Android 12 dałby mniej
uproszczeń. Aplikacji nie da się wtedy zainstalować na Androidzie 8–12.

**Co znika:**
- **AppCompat:**
  - `MainActivity`: `AppCompatActivity` zamienione na zwykłe `ComponentActivity`;
  - wybór języka w ustawieniach: zamiast `AppCompatDelegate` systemowy `LocaleManager`.
    Na Androidzie 13+ AppCompat i tak zapisywał język w systemie, więc wybrany język zostaje;
  - motyw aplikacji: zamiast rodzica `Theme.AppCompat.DayNight.NoActionBar` motyw systemowy
    bez paska akcji (Compose i tak rysuje cały ekran sam);
  - manifest: usunięty serwis `AppLocalesMetadataHolderService`;
  - `backup_rules.xml` i `data_extraction_rules.xml`: usunięty plik języka AppCompat (system sam
    pamięta język aplikacji i obejmuje go kopią);
  - zależność `androidx.appcompat` w `libs.versions.toml` i `build.gradle.kts`.
- **Sprawdzanie wersji Androida w 6 miejscach** (po zmianie zawsze prawdziwe):
  - `HapticAndSoundPlayer`: nowy sposób wibracji;
  - `StudySessionService`: typ serwisu pierwszoplanowego;
  - `SettingsSections`: przełącznik kolorów z tapety, widoczny zawsze;
  - `Theme`: kolory z tapety zawsze dostępne;
  - `AppSettingsScreen` i `StudyScreen`: prośba o zgodę na powiadomienia zawsze.

**Co zostaje:**
- obejście `LocalDate.ofInstant` w `LocalDates`, bo ta funkcja jest dopiero od Androida 14;
- biblioteka `core-splashscreen`. Na Androidzie 12+ to cienka nakładka na system, a wyrzucenie
  jej wymaga przerobienia motywu ekranu startowego, więc zysk jest mały;
- przestarzała metoda `onError(String)` w `TtsPlayer`, bo Android nadal każe ją zaimplementować.

**Testy:** trzy pliki uruchamiają testy na starszych wersjach Androida w Robolectric
(`PlatformAudioFocusTest` i `StudySessionServiceTest` na `[32, 33]`, `SessionPermissionsTest`
na `[33]`). Wersje poniżej 33 trzeba usunąć, bo Robolectric nie uruchomi testu poniżej minSdk.

**Bilans:** jedna zależność mniej, około 40 linii mniej.

## 4. Biblioteki zamiast własnego kodu (etap 3)

- **CSV:** Apache Commons CSV zamiast ręcznego dzielenia na komórki (`parseRows`, `encodeCell`
  w `CsvParser.kt`).
  - Wykrywanie separatora, nagłówka i języka zostaje nasze, bo żadna biblioteka tego nie robi.
  - Ustawienia biblioteki muszą odtworzyć dzisiejsze zachowanie: przycięte komórki, pominięte
    puste wiersze, BOM, cudzysłowy `""`, koniec po N wierszach przy wykrywaniu separatora.
  - Testy `ImportParserTest` zostają bez zmian i muszą przejść.
- **Porównywanie mowy:** `LevenshteinDistance` z Apache Commons Text zamiast własnej funkcji
  (18 linii w `SpeechMatcher.kt`). Logika dopasowania słowo po słowie i próg zostają.
  Testy w `SrsEngineTest` zostają bez zmian.
- **Koszt:** licencja Apache 2.0, zgodna z resztą aplikacji. Commons CSV dociąga Commons IO
  i Commons Codec, a Commons Text dociąga Commons Lang. Nieużywany kod wycina R8 przy budowaniu
  wersji release, więc aplikacja urośnie o kilkadziesiąt KB.
- **Dlaczego Apache, a nie inne:**
  - FastCSV (MIT, bez zależności, najszybszy) wymaga Androida 14 (API 34), czyli powyżej
    Twojego zakresu;
  - kotlin-csv jest rzadko aktualizowany, a OpenCSV ciągnie wiele bibliotek;
  - java-string-similarity nie jest rozwijana od lat;
  - Apache rozwija fundacja, a nie jedna osoba.

## 5. „Tryb eksperta” w edytorze trybów (etap 4)

Wersja Expo miała przełącznik „Tryb eksperta”. Wyłączony: „Dodaj krok” pokazywał gotowe bloki.
Włączony: pojedyncze kroki. Przy przepisywaniu na Kotlin przełącznik zniknął, a obie listy
połączono w jedną. Przywracamy go:
- przełącznik „Tryb eksperta” nad listą kroków w edytorze trybu;
- wyłączony (domyślnie): „Dodaj krok” pokazuje tylko 9 gotowych bloków;
- włączony: bloki na górze, pod nimi 17 pojedynczych kroków (Twoja decyzja; w wersji Expo były
  wtedy same pojedyncze kroki);
- ustawienie zapamiętane na telefonie (DataStore, obok motywu i tempa mowy), wspólne dla
  wszystkich trybów;
- kroki, które już są w trybie (także pojedyncze kroki trybów wbudowanych), zawsze widoczne
  i edytowalne, niezależnie od przełącznika.

Około 30 linii, tekst w 5 językach, test ekranu (lista „Dodaj krok” przy wyłączonym
i włączonym przełączniku).

**Umowa dla wykonawcy:**
- `UserPreferences.expertMode: Boolean = false`, klucz DataStore
  `booleanPreferencesKey("expert_mode")`, `UserPreferencesRepository.setExpertMode(value)`;
- `ModeDetailViewModel` dostaje `UserPreferencesRepository`, wystawia
  `expertMode: StateFlow<Boolean>` i `setExpertMode(Boolean)`;
- `ModeDetailScreen`: wiersz z przełącznikiem nad listą kroków w edytorze (ten sam wygląd co
  przełączniki w ustawieniach). Teksty `mode_editor_expert_mode` „Tryb eksperta” i
  `mode_editor_expert_mode_desc` „Pokazuje też pojedyncze kroki w „Dodaj krok”” w 5 językach;
- `AddStepDialog(expert: Boolean, …)`: grupa gotowych bloków zawsze, pozostałe grupy tylko gdy
  `expert`. Kolejność grup bez zmian;
- w `ModeDetailViewModel` poprawić komentarz o „nieruszonym” trybie wbudowanym, który „dostaje nowe
  kroki”. Ta automatyczna aktualizacja znika w planie Firestore.

## 6. Sprzątanie, repomix, README (etap 5, po planie Firestore)

**Usuwane:**
- `tools/gradle_locked.py`, `tools/tests/test_gradle_lock.py` z `__pycache__` oraz wzmianka
  w README. Pilnowały, żeby kilku agentów AI naraz nie budowało projektu;
- `docs/przeglad-2026-10-14/` i `docs/przeglad-2026-10-15/` (45 plików, 1,6 MB). Opisują kod
  sprzed przebudowy, a historia w gicie je zachowuje;
- `app/hs_err_pid*.log`, `app/replay_pid*.log` (jeśli nie zniknęły już w etapie 1).

**Repomix w jednym folderze:**
- folder `repomix/` w katalogu głównym: `pack.ps1` (dawny `pack-repomix.ps1`) i cztery
  konfiguracje: `all.json` (dawny `repomix.config.json`), `part1-logic.json`, `part2-ui.json`,
  `part3-tests.json`;
- paczki trafiają do `repomix/out/`, a ten folder jest w `.gitignore` (zamiast dzisiejszego
  `/repomix*.xml`). Konfiguracje zostają w gicie, żeby nie zginęły;
- skrypt uruchamia repomix z katalogu głównego, więc ścieżki plików w konfiguracjach działają
  dalej. Zmieniają się tylko ścieżki wyjściowe;
- co pakować (np. wzmianki o Room w opisach paczek), aktualizujesz sam.

**README:** stos technologiczny po obu planach (Firestore zamiast Room, Android 13+, Java 21),
bez `gradle_locked`.

## 7. Zostaje bez zmian (sprawdzone, Twoje decyzje)

- **`ScreenCompletion`** (powrót z ekranu z wynikiem, np. „zaimportowano”) i
  **`PersistedTextField`** (pole zapisywane na bieżąco, którego nie nadpisuje echo zapisu).
  Żadna biblioteka nie robi tego za nas. Navigation 3 uprościłaby przekazywanie wyniku, ale
  wymaga przepisania całej nawigacji.
  *Zmiana 16.10.2026:* nawigacja przeszła na osobne Activity (systemowe cofanie gestem);
  `ScreenCompletion` zastąpiło Activity Result API — zob. `docs/PROTOTYP_NAWIGACJA.md`.
- **5 języków.**
- **Własny FSRS-4.5.** Gotowa biblioteka FsrsKt jest na licencji GPL-3 (kłóci się z zamkniętymi
  częściami Google w aplikacji). FSRS-6 napiszemy sami na podstawie implementacji
  referencyjnych na licencji MIT. Plan tego jest osobnym zadaniem (karta sesji „Plan przejścia
  z FSRS-4.5 na FSRS-6”).

## 8. Testy

- **Zmieniane:**
  - `@Config(sdk = ...)` w 3 plikach (etap 2);
  - test listy „Dodaj krok” przy obu stanach przełącznika (etap 4).
- **Muszą przejść bez zmian:** `ImportParserTest` (etap 3), testy dopasowania mowy
  w `SrsEngineTest` (etap 3), `SettingsSectionsTest`.
- `SettingsSectionsTest` może zakładać, że przełącznik kolorów z tapety bywa ukryty. Jeśli tak,
  ten jeden przypadek znika razem ze sprawdzaniem wersji.
- **Nowych testów poza trybem eksperta nie ma.** Zmiany 1–3 nie zmieniają zachowania, a pilnują
  go obecne testy.

## 9. Ryzyka

| # | Ryzyko | Skutek | Zabezpieczenie |
|---|---|---|---|
| 1 | Telefon z Androidem 8–12 (np. zapasowy) | aplikacji nie da się zainstalować | świadoma decyzja |
| 2 | Zmiana sposobu zapisu języka aplikacji | język mógłby wrócić do systemowego | na Androidzie 13+ AppCompat już zapisywał go w systemie; sprawdzenie na telefonie po etapie 2 |
| 3 | Commons CSV inaczej czyta nietypowy plik (np. cudzysłów w środku komórki) | inny podział komórek przy imporcie | `ImportParserTest`; każdą wykrytą różnicę dopisujemy jako przypadek testowy i ustawiamy bibliotekę tak, by działała jak dziś |
| 4 | Pierwsze budowanie po etapie 1 | Gradle pobiera Javę 21 (wymaga internetu, kilkaset MB) | jednorazowo |
| 5 | Usunięcie AppCompat a motyw ekranu startowego | zły kolor tła przy starcie | sprawdzenie na telefonie (jasny i ciemny motyw) |

## 10. Kolejność wdrożenia

1. Java 21, usunięcie logów awarii. Pełny `test` dwa razy pod rząd, bez awarii.
2. Android 13: minSdk, usunięcie AppCompat, sprawdzanie wersji, testy Robolectric. Telefon:
   start, zmiana języka, jasny i ciemny motyw, powiadomienie nauki w tle.
3. Commons CSV i Commons Text.
4. Tryb eksperta.
5. Po planie Firestore: usunięcie narzędzi i raportów, repomix w folderze, README.

## 11. Przegląd krytyczny: co poprawiłem

1. **Awaria Javy nie była losowa ani nie dotyczyła R8.** Log pokazuje błąd kompilatora JIT Javy
   23.0.1 w trakcie testów. Wcześniejszy wpis „losowy crash przy R8” był błędny. Naprawa przez
   Javę 21 zostaje, zmienia się uzasadnienie.
2. **Przełącznik „Tryb eksperta” to regresja, a nie nowa funkcja.** Był w wersji Expo, port go
   pominął.
3. **Java 21 musi być pierwsza.** Pierwotnie była na końcu porządków, a dotyczy każdego
   budowania w trakcie obu planów.
4. **FastCSV nadal odpada** mimo podniesienia minimalnego Androida, bo wymaga Androida 14,
   a Twój zakres to 12–13. Zostaje Apache.
5. **Testy Robolectric ze starszym SDK** (32) przestałyby się uruchamiać po podniesieniu
   minSdk. Dopisane do etapu 2.
6. **Usunięcie AppCompat to nie tylko zmiana klasy aktywności.** Dochodzi motyw (rodzic
   AppCompat), serwis w manifeście i wpisy w regułach kopii. Wszystko wypisane w sekcji 3.
7. **Android 12 kontra 13:** dopiero 13 pozwala wyrzucić AppCompat (systemowy język aplikacji).
   Na 12 musiałby zostać, więc wybór 13 daje więcej przy tym samym ryzyku dla Ciebie.
8. **Plan Firestore miał porządki w sekcji 14** i dublowałby ten plan. Sekcja przeniesiona tutaj,
   a w planie Firestore zostały tylko odnośniki (krok 1 i 7 kolejności wdrożenia, sekcja 13).
9. **Java 21 a testy:** sprawdzone, że wystarczy zmienić Javę, na której działa Gradle. Testy
   jednostkowe Androida uruchamiają się na tej samej Javie, bo projekt nie ustawia osobnej.
   Polecenie `gradlew` może dalej startować z Javy 23, bo Gradle sam uruchomi proces budowania
   na Javie 21.

## 12. Decyzje i pytania

**Twoje decyzje:**
- Usunąć `tools/gradle_locked.py` z testem i stare raporty z `docs/przeglad-*`.
- Repomix w osobnym folderze, wyniki w `.gitignore`, treść konfiguracji aktualizujesz sam.
- Zostaje 5 języków.
- Biblioteki zamiast własnego kodu tam, gdzie działają od ręki.
- `ScreenCompletion` i `PersistedTextField` zostają, bez Navigation 3.
- Najstarszy wspierany Android w zakresie 12–13.
- FSRS-6 (a może i 7) piszemy sami, jeśli nie ma dobrej biblioteki. Osobne zadanie.
- Tryb eksperta: wyłączony pokazuje bloki, włączony bloki i pod nimi pojedyncze kroki.
  Zapamiętywany na telefonie.

**Moje decyzje techniczne:**
- Java 21 do budowania;
- minSdk 33 (Android 13), bo pozwala wyrzucić AppCompat;
- Apache Commons CSV i Commons Text (uzasadnienie w sekcji 4).

**Opcjonalnie, domyślnie nie:** Android 14 zamiast 13 dałby dodatkowo FastCSV i jedno obejście
mniej (`LocalDates`). To poza Twoim zakresem 12–13.

**Poza planem:** przejście na FSRS-6 i 7 (osobne zadanie), Navigation 3.

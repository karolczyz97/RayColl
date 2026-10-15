# Wdrożenie pełnego przeglądu — 15.10.2026

Autoryzacja: „one shot wszystkiego, działaj”; następnie polecenie odłożenia Ponytail. Obowiązuje pełen skorygowany zakres red teamu, wraz z rekomendowanymi wariantami UX. Odrzucone podpunkty nie są wdrażane. Claude wdrożył wariant B wskaźnika audio (etykieta nad stroną, stałe miejsce); zachowano go w kontynuacji; motion i landscape zachowują rekomendowane zachowanie. Bez push/merge do main i wdrożenia Firebase.

## Kolejność i stan

- [x] 1. Dane: zbieżna kolejność, zastępowanie całego konta z migracją Room, retry, konto/migracja, DAO, walidacja i testy. Wspólne porządki nazw pozostają w pakiecie 7.
- [x] 2. Nawigacja po zapisie, operacje kopii zapasowej i testy. Doprecyzowanie tłumaczeń pozostaje w pakiecie 6.
- [x] 3. Audio/fokus, uprawnienia, błędy i kolejka ocen. Rozszerzenie testów samej usługi pozostaje w pakiecie 8.
- [x] 4. Import, edytor, przeglądanie i potwierdzenia operacji.
- [x] 5. Nauka, dostępność, animacje, listy, wspólne elementy.
- [x] 6. Teksty i tłumaczenia pięciu języków.
- [x] 7. Porządki domeny i struktury plików.
- [x] 8. Testy kontraktów i infrastruktury.
- [x] 9. Build, dokumentacja i narzędzia.
- [x] 10. Weryfikacja lokalna i rozliczenie wszystkich 166 identyfikatorów. Sprawdzenia urządzenia wymienione poniżej pozostają niewykonane.

## Ustalone rozwiązania

- Zastąpienie konta: nowa generacja danych porównywana deterministycznie, utrwalona w Room i chmurze. Stara generacja nie odtwarza fiszek, trybów ani aktywności po resecie/importowaniu kopii.
- Drugie poprawne podejście audio: HARD; własne tryby pozostają nietknięte.
- Logowanie: przepływ jawnego przycisku Google.
- Dodanie fiszki: komunikat, zachowane filtry.
- Import: potwierdzenie porzucenia/nadpisania i pionowy podgląd dla >2 kolumn, zachowane nazwane puste kolumny.
- Tryby: wstawienie kroku poniżej wybranego; komunikaty po zakończeniu na pulpicie; Cofnij wyłącznie archiwizację.
- Terminologia: fiszka / talia / dotknięcie. Debug: osobna etykieta aplikacji.

## Weryfikacja

Baza przed zmianami: 533 testy, ktlint zielony (red team). Testy wykonywać kolejno, JDK `C:/Program Files/Java/jdk-23`, jeden Gradle naraz. Wyniki i ograniczenia uzupełniane w trakcie.

- Pakiety 1–2: pełne 564 testy, 0 błędów/pominięć, ktlint zielony.
- Pakiety 1–4: pełne 585 testów, 0 błędów/pominięć, ktlint zielony. Import: przypadki 2/3/5/20/>20, duża czcionka, dialog po restoration, ręczny separator i nazwane puste kolumny.
- Audio przed ostatnią poprawką zamykania komunikatów: pełne 571 testów, 0 błędów/pominięć, ktlint zielony. Testy fizycznego routingu i ograniczeń tła wymagają urządzenia.
- Room 1 → 2: test na rzeczywistym schemacie v1 zachowuje obie przestrzenie, SRS, archiwum, tombstony, tryby i aktywność; stara kopia JSON pozostaje czytelna.
- Lokalny JDK 23.0.1 rozbił się w kompilatorze C2 podczas testu statystyk. Kolejne przebiegi używają lokalnego `JAVA_TOOL_OPTIONS=-XX:TieredStopAtLevel=1`; bez zmiany konfiguracji projektu/globalnego środowiska.
- Znacznik zastępowania wymaga aktualnej aplikacji na synchronizujących urządzeniach. Dawna wersja nie zna tego kontraktu; nie obiecujemy zgodnego resetowania przy mieszaniu starych i nowych klientów.

## Kontynuacja po Claude

Przeczytano rozmowę `1384a04a-7de5-404f-91a6-ae2b293c5a86` oraz cztery zakończone audyty Claude’a. Dokumenty `audit-*.md` są historycznym wejściem do poprawek, a nie bieżącą listą braków.

- Zachowano wariant B i poprawki konfiguracji/narzędzi Claude’a. Oceny przy dużej czcionce przechodzą na dwa rzędy; nie zmniejszają wszystkich etykiet do drobnego tekstu.
- Migracja gościa zeruje znacznik zastąpienia: nie przenosi polecenia resetu na konto.
- Firestore: każdy commit sprawdza wersję bazy w transakcji i zapisuje własny token. Nie ma pobierania i przejmowania obcego znacznika po zapisie. Dokumenty również mają generację: przerwana duża wysyłka nie przywraca starych fiszek/trybów/aktywności. Diff przepisuje wszystkie zachowane dokumenty po zmianie generacji, a przy ponowieniu sprząta pozostałości starej generacji. Całe duże konto nadal nie jest jedną transakcją chmurową; kolejne porcje pojawiają się stopniowo, a lokalna podmiana jest atomowa.
- Kopie: testy błędnego pliku, niedostępnego pliku, błędu bazy z rollbackiem, ukrytych stron i tombstonów. Dawna pusta nazwa rozpoznanego trybu wbudowanego jest odtwarzana z seeda; błędne nazwy własnych danych nadal blokują przywrócenie.
- Błąd zapisu ocen ma trwały przycisk ponowienia. Zniknięcie snackbara lub powrót z tła nie zostawia użytkownika bez wyjścia.
- Usługa: prawdziwy lifecycle z Hilt, powiadomienie/kanał, FINISHED, zwolnienie wake locka i sesji multimedialnej na API 32/33. Credential Manager i błędy Firebase testowane przez produkcyjne AuthRepository.
- Preferencje testów mają oddzielne pliki i zamykane zakresy. Testy zegara pulpitu przesuwają czas, testy repozytorium sprawdzają sygnały, a test przełączania aplikacji czeka na koniec konkretnego zadania zamiast sleep.
- Emulator Firestore: 10/10 (anonimowy/właściciel/inny UID dla roota i podkolekcji, odrzucenie nieaktualnej transakcji). To lokalny projekt `demo-raycoll`; reguły produkcyjne nie były wdrażane.
- Blokada Gradle: 2/2 testy wieloprocesowe (serializacja, zwolnienie po awarii procesu), bez kasowania blokad po wieku/PID. Windows PowerShell 5.1: Repomix zatrzymuje się po pierwszym błędzie z kodem 23, bez komunikatu sukcesu; polskie teksty mają poprawne kodowanie.
- Guard Firebase: testy generacji przechodzą z pustym kluczem; requireFirebaseApiKey odrzuca pusty klucz. Pakowanie obu wariantów zależy od tego zadania. Nie zmieniono local.properties.
- Oficjalna [suma Gradle 9.8.1-bin](https://services.gradle.org/distributions/gradle-9.8.1-bin.zip.sha256) potwierdzona: `dce76f55f8e251a3a1f130eb120f30b3d271de2b76c9b0729d316b5a1b6dc01f`.

## Pozostające sprawdzenia urządzenia

`adb devices -l` nie wykazał urządzenia; brak skonfigurowanego AVD. Nie wykonano: fizycznego TTS/STT, Pixel Buds/media buttons, odłączenia Bluetooth, rozmowy/nawigacji przy audio, przywrócenia Android Backup z WAL ani instalacji podpisanej aktualizacji release. Robolectric i emulator Firestore nie zastępują tych sprawdzeń. Przed instalacją na istniejących danych: synchronizacja i kopia JSON; powrót do wersji z bazą v1 po migracji v2 wymaga osobnego planu, nie downgrade APK.

Wszystkie synchronizujące urządzenia muszą obsługiwać generacje. Dawny klient tego kontraktu nie zna. Nie wykonano push, merge do main, wdrożenia Firebase ani usuwania cudzych worktree.

## Końcowe środowisko budowania

- JDK 17 jest zalecany w README. Pełny `check` i release z R8/profilami ART zostały na nim wykonane. JDK 23.0.1 powodował awarię C2 w testach, a później `NoSuchMethodException` w przetwarzaniu profili ART. Nie wyłączono optymalizacji, profili ani kontroli lintu jako obejścia.
- Poprawiono kolejność generatorów Hilt i lintu testów (udokumentowany w logu błąd FileNotFoundException generowanego źródła release). Zwiększono limit metaspace z 1 do 2 GiB po ostrzeżeniu daemona podczas pełnego sprawdzenia obu wariantów.
- `dependencyInsight`: AndroidX test ext 1.3.0 rozstrzyga zależność 1.1.5 wciąganą przez Compose UI test. Bezpośredni wpis został świadomie zachowany (TB-N27).
- Druga poprawna próba Audio: HARD jest zarówno zapisany, jak i wyświetlony w podpowiedzi; panel uwzględnia też kolor HARD. Test wykonuje rzeczywisty seed Audio z błędną pierwszą i poprawną drugą odpowiedzią.
- Lint: 0 błędów, 6 ostrzeżeń — informacja o nowszym patchu Firebase BOM i 5 heurystyk PluralsCandidate. Są to numery porządkowe oraz zakresy lub liczba kolumn >2, a nie dynamiczne liczniki 1/n wymagające plurals. Nie wyłączono globalnie żadnej kontroli.

## Wynik ostatniego pełnego przebiegu

`check + assembleDebug + assembleRelease` na JDK 17: **BUILD SUCCESSFUL**, 643 testy, 0 błędów i 0 pominięć. Ktlint zielony, Android lint 0 błędów (6 opisanych ostrzeżeń), release z R8 i profilami ART. Debug ma poprawny podpis; release pozostaje niepodpisany (zweryfikowane apksigner). Etykiety i packageId obu APK sprawdzone przez aapt2.

166 identyfikatorów: **155 domkniętych zakresów, 11 świadomie zachowanych**; szczegóły w [ZAKRES.md](ZAKRES.md). Polecenia, sumy SHA-256 APK i liczby testów: [verification.json](verification.json).

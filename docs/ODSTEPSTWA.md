# Odstępstwa od planu porządków

## Etap 2 — konfiguracja testów Androida

- **Co:** zmieniono wersję SDK w dwóch plikach testów, a nie w trzech wymienionych w planie.
- **Dlaczego:** `SessionPermissionsTest` już miał `@Config(sdk = [33])`, więc nie wymagał zmiany.
- **Gdzie:** `PlatformAudioFocusTest.kt` i `StudySessionServiceTest.kt` — usunięto SDK 32;
  `SessionPermissionsTest.kt` pozostawiono bez zmian.

Nie zmienia to zakresu ani zachowania przewidzianego w planie.

## Firestore — granice etapów 1–3 i 5

- **Co:** etap 1 dodaje kontrakt magazynu, implementację Firestore i atrapę. Przełączenie
  repozytoriów, usunięcie Room i zależnego kodu synchronizacji następuje razem z kontem
  w etapie 2. Podstawowe operacje kopii potrzebne kontu są również w etapie 2; etap 3
  domyka ich walidację i testy. Etap 5 kończy pozostałe porządki.
- **Dlaczego:** obecne logowanie, kopie i synchronizacja współdzielą LocalStore oraz Room.
  Usunięcie samego Room w etapie 1 pozostawiłoby niekompilujący się projekt, zamiast
  wymaganego działającego commitu po każdym etapie.
- **Gdzie:** `data/firestore`, `data/account`, `data/repository`, DI i testowy harness.

## Etap 3 — opcjonalna adnotacja Commons CSV

- **Co:** dodano jedną regułę R8: `-dontwarn edu.umd.cs.findbugs.annotations.SuppressFBWarnings`.
- **Dlaczego:** Commons CSV 1.14.1 zawiera odwołanie do opcjonalnej adnotacji SpotBugs,
  której nie dostarcza jako zależności. Testy przechodziły, ale R8 blokował paczkę release.
  Adnotacja służy analizie kodu biblioteki i nie jest potrzebna aplikacji. Zastosowano dokładną
  regułę wygenerowaną przez R8, bez dodawania kolejnej zależności i bez wyłączania innych ostrzeżeń.
- **Gdzie:** `app/proguard-rules.pro`; biblioteka w `gradle/libs.versions.toml`.
- Problem opisany przez Apache: [CSV-320](https://issues.apache.org/jira/browse/CSV-320).

# Odstępstwa od planu porządków

## Etap 2 — konfiguracja testów Androida

- **Co:** zmieniono wersję SDK w dwóch plikach testów, a nie w trzech wymienionych w planie.
- **Dlaczego:** `SessionPermissionsTest` już miał `@Config(sdk = [33])`, więc nie wymagał zmiany.
- **Gdzie:** `PlatformAudioFocusTest.kt` i `StudySessionServiceTest.kt` — usunięto SDK 32;
  `SessionPermissionsTest.kt` pozostawiono bez zmian.

Nie zmienia to zakresu ani zachowania przewidzianego w planie.

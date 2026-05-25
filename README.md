# 🧠 TensorDeck

TensorDeck to nowoczesna, w pełni responsywna aplikacja do nauki za pomocą fiszek, wykorzystująca zaawansowany algorytm spaced repetition **FSRS** (Free Spaced Repetition Scheduler), asystenta głosowego **TTS & STT** oraz interaktywny design **Material Design 3 (Expressive)**. 

Aplikacja działa w pełni offline (dane w localStorage) z opcjonalną synchronizacją w chmurze (Firebase Firestore + Google Login) i jest dostępna jako strona internetowa, aplikacja desktopowa (Tauri) oraz aplikacja mobilna na Androida (Capacitor).

---

## 🌟 Kluczowe Funkcje

*   **📈 Algorytm FSRS (wersja 4.5):** Optymalizacja powtórek oparta na najnowszych badaniach nad pamięcią (naukowy odpowiednik silnika Anki).
*   **🗣️ Asystent Głosowy (TTS & STT):** 
    *   **Text-to-Speech (TTS):** Czytanie zawartości fiszek na głos w wybranym języku (np. wymowa słówek).
    *   **Speech-to-Text (STT):** Rozpoznawanie mowy użytkownika w celu weryfikacji wymowy (automatyczne sprawdzanie odpowiedzi z progiem tolerancji dopasowania).
*   **🔊 Dźwięki mikro-feedbacku:** Sygnały dźwiękowe wygenerowane przez Web Audio API przy włączaniu mikrofonu, udanej weryfikacji oraz błędzie.
*   **🌐 Wbudowana Lokalizacja (i18n):** Pełna obsługa 5 języków interfejsu: **Polski**, **Angielski**, **Niemiecki**, **Hiszpański** oraz **Włoski** (z automatycznym wykrywaniem języka przeglądarki).
*   **🎨 Stylistyka Material Design 3 (Material You):**
    *   Dynamiczna ekstrakcja koloru systemowego (Windows Accent Color / Android Dynamic Color) i wstrzykiwanie go jako koloru wiodącego (`primary`).
    *   Pełna obsługa motywu jasnego, ciemnego oraz systemowego (z zabezpieczeniem przed migotaniem tła).
    *   Responsywny grid grup, zaokrąglenia MD3 (28px dla dialogów, 12px dla pól, 8px dla chipów).
*   **📐 Zaawansowana Karta Fiszki (`StudyPage`):**
    *   Podział wysokości karty na równe części `1/N` dla `N` stron w zestawie (koniec z dynamicznym skakaniem i przesuwaniem się układu).
    *   **Indywidualne animacje 3D Flip (obrót w pionie):** Każda zablokowana sekcja wyświetla nazwę strony fiszki, a po odsłonięciu wykonuje trójwymiarowy obrót `rotateX(180deg)`, pokazując czystą treść.
    *   **Fizyczny efekt wciśnięcia (Framer Motion):** Karta delikatnie ugina się o 5% przy kliknięciu lub przytrzymaniu (zapobiegającemu automatycznemu przejściu).
*   **📊 Ekran Końcowy i Statystyki:**
    *   Podsumowanie zakończonej sesji nauki za pomocą wielokolorowego paska postępu `SegmentedProgressBar` z legendą.
    *   Śledzenie aktywności w czasie za pomocą interaktywnej siatki heatmapy aktywności (w stylu GitHub contributions).
*   **🔐 Bezpieczne Logowanie i Rozdzielność Danych:**
    *   Niezalogowani użytkownicy korzystają z lokalnej bazy `fiszki-local-groups`.
    *   Zalogowani użytkownicy synchronizują dane w Firestore pod kluczem `fiszki-user-groups-${uid}` – wylogowanie natychmiast przywraca lokalną bazę bez utraty danych.

---

## 🛠️ Stos Technologiczny

*   **Frontend Core:** React 19, TypeScript, Vite
*   **Stylizacja i UI:** Material UI (MUI v9) z tokenami Material Design 3, Vanilla CSS
*   **Animacje:** Framer Motion
*   **Baza danych i Auth:** Firebase Firestore & Firebase Authentication (Google Login)
*   **Multiplatformowość:**
    *   **Desktop:** Tauri (Rust) do kompilacji lekkiej aplikacji natywnej na PC
    *   **Mobile:** Capacitor do opakowania w natywną aplikację na Androida

---

## 🧠 Silnik Spaced Repetition (FSRS)

Algorytm FSRS oblicza optymalny czas kolejnej powtórki na podstawie 4 ocen trudności:
1.  **Again (Powtórz):** Całkowite zapomnienie karty (reset stabilności).
2.  **Hard (Trudna):** Karta zapamiętana z trudem (krótki interwał).
3.  **Good (Dobrze):** Standardowe zapamiętanie (optymalne wydłużenie interwału).
4.  **Easy (Łatwa):** Pełne i szybkie zapamiętanie (maksymalne wydłużenie interwału).

Aplikacja mapuje również procent dopasowania mowy (STT) bezpośrednio na powyższą skalę ocen:
*   `≥85%` dopasowania → **Easy**
*   `≥60%` dopasowania → **Good**
*   `≥40%` dopasowania → **Hard**
*   `<40%` dopasowania → **Again** (automatyczne uruchomienie korekty lektora TTS)

---

## 🚀 Uruchomienie Lokalne

### Wymagania wstępne
*   Node.js (wersja 22+)
*   Narzędzia NPM

### Instalacja i uruchomienie dev
```bash
# 1. Sklonuj repozytorium i wejdź do katalogu
git clone https://github.com/mkowalczyk111/tensordeck.git
cd tensordeck

# 2. Zainstaluj zależności npm
npm install

# 3. Uruchom serwer developerski
npm run dev
```

### Konfiguracja Firebase (opcjonalnie)
Utwórz plik `.env` w głównym katalogu projektu:
```env
VITE_FIREBASE_API_KEY=TwojApiKey
VITE_FIREBASE_AUTH_DOMAIN=twoj-projekt.firebaseapp.com
VITE_FIREBASE_PROJECT_ID=twoj-projekt
VITE_FIREBASE_STORAGE_BUCKET=twoj-projekt.appspot.com
VITE_FIREBASE_MESSAGING_SENDER_ID=IDNadawcy
VITE_FIREBASE_APP_ID=IDApp
```
*Uwaga: Bez pliku `.env` aplikacja automatycznie i bezbłędnie przełącza się w pełen tryb offline (dane zapisywane są wyłącznie w `localStorage`).*

---

## 📱 Kompilacja i Deploy (Android & Desktop)

Wszystkie procesy kompilacji i wdrażania są obsługiwane automatycznie w chmurze za pomocą **GitHub Actions** (`.github/workflows/`):

### 1. Wersja Web (Firebase Hosting)
Każdy push na gałąź `main` automatycznie buduje aplikację i wdraża jej najnowszą wersję na Firebase Hosting za pomocą akcji `deploy-web.yml`.

### 2. Aplikacje Mobilne i Desktopowe (APK & EXE)
Dzięki akcji `build-apps.yml` (uruchamianej ręcznie w zakładce **Actions** na GitHubie poprzez `workflow_dispatch`), kompilacja odbywa się **w pełni automatycznie i równolegle**:
*   **Windows EXE (Tauri):** Buduje natywny instalator na Windowsa (`.exe` i `.msi`).
*   **Android APK (Capacitor):** Uruchamia kompilację Androida i generuje plik debugujący `.apk`.
*   **Automatyczne wydanie (Releases):** Akcja automatycznie pobiera oba te pliki, po czym wstawia je jako załączniki do jednego szkicu wydania (**Draft Release**) w Twojej zakładce *Releases* na GitHubie. Aby udostępnić wersję, wystarczy kliknąć **Publish release** w zakładce wydań.

#### Kompilacja Androida lokalnie (opcjonalnie)
Jeśli posiadasz zainstalowane Android SDK i JDK 21, możesz skompilować plik APK na swoim komputerze:
```bash
npm run build
npx cap sync android
cd android
./gradlew assembleDebug
```
Gotowy plik APK znajdziesz w lokalizacji: `android/app/build/outputs/apk/debug/app-debug.apk`.

---

## 📂 Struktura Katalogów

*   `src/components/` — Współdzielone komponenty UI (PageHeader, SegmentedProgressBar, GroupNotFound itp.)
*   `src/hooks/` — Customowe hooki obsługujące stan sesji, autoryzację i bazę danych (`useStudySession`, `useFlashcardStore`, `useLocalStorage`)
*   `src/i18n/` — System internacjonalizacji z plikami tłumaczeń (pl, en, de, es, it)
*   `src/pages/` — Strony aplikacji (Dashboard, Study, Browse, Settings, AppSettings, Stats)
*   `src/services/` — Usługi głosowe TTS/STT, dźwięki Web Audio, Firebase i instancje SDK
*   `src/srs/` — Matematyka algorytmuspaced repetition (FSRS Engine)
*   `src/theme.ts` — Konfiguracja motywu Material Design 3 z obsługą kolorów systemowych
*   `src-tauri/` — Konfiguracja i kod Rust dla środowiska desktopowego Tauri
*   `android/` — Wygenerowany projekt natywny dla systemu Android (Capacitor)

# 🧠 TensorDeck

TensorDeck is a modern, fully responsive flashcard learning application featuring the advanced **FSRS** (Free Spaced Repetition Scheduler) algorithm, an integrated **TTS & STT** voice assistant, and a sleek **Material Design 3 (Expressive)** UI.

The application operates fully offline (storing data in localStorage) with optional cloud synchronization (Firebase Firestore + Google Login). It is compiled for multiple targets: Web (PWA), Desktop (Tauri for Windows), and Mobile (Capacitor for Android).

---

## 🌟 Key Features

*   **📈 FSRS Algorithm (v4.5):** Scientific spaced repetition scheduling based on the latest memory retention research (identical to Anki's latest scheduler engine).
*   **🗣️ Voice Assistant (TTS & STT):** 
    *   **Text-to-Speech (TTS):** Auditory card pronunciation in the target language.
    *   **Speech-to-Text (STT):** Advanced speech recognition to verify user pronunciation against the card answers, using custom thresholds for match validation.
*   **🔊 Micro-feedback Sound Effects:** High-quality micro-sounds synthesized via the Web Audio API indicating microphone activation, successful matching, and pronunciation errors.
*   **🌐 Built-in Localization (i18n):** Full localization support for 5 languages: **Polish**, **English**, **German**, **Spanish**, and **Italian** (with automatic browser locale detection).
*   **🎨 Material Design 3 (Material You):**
    *   Dynamic extraction of system colors (Windows Accent Color / Android Dynamic Color) injected as the primary palette color.
    *   Fully integrated Dark, Light, and System themes (with pre-rendered dark styles to prevent flash-of-white loading glitches).
    *   Clean layout spacing conforming to MD3 specifications (28px corner radius for dialogs, 12px for input fields, 8px for chips).
*   **📐 Advanced Card Layout (`StudyPage`):**
    *   The flashcard height occupies the full available height (`flex-grow: 1`) and divides its space symmetrically into `1/N` segments for `N` pages in a deck, preventing any layout shifts during study sessions.
    *   **Individual 3D Page Flips:** Hidden card pages show their labels (e.g., "QUESTION", "ANSWER") and perform a smooth trójwymiarowy vertical flip (`rotateX(180deg)`) when revealed to display only the raw content.
    *   **Tactile Spring Interactions (Framer Motion):** Cards shrink slightly by 5% (`whileTap={{ scale: 0.95 }}`) on tap/hold, providing micro-feedback during hold-to-block actions.
*   **📊 End-of-Session Summaries & Analytics:**
    *   Visual session overview displaying a multi-colored `SegmentedProgressBar` with a detailed legend (New, Learning, Review, Mastered cards count).
    *   Yearly activity tracker featuring a GitHub-style SVG contributions heatmap.
*   **🔐 Secure Login & Data Isolation:**
    *   Anonymous data is isolated in `fiszki-local-groups`.
    *   Authenticated users sync their cards to `fiszki-user-groups-${uid}` in Firestore. Logging out instantly restores the local profile without data pollution.

---

## 🛠️ Technology Stack

*   **Frontend Core:** React 19, TypeScript, Vite
*   **Styling & UI:** Material UI (MUI v9) with MD3 tokens, Vanilla CSS
*   **Animations:** Framer Motion
*   **Backend & Auth:** Firebase Firestore & Firebase Authentication (Google Login)
*   **Platforms:**
    *   **Desktop:** Tauri (Rust) for building native, lightweight Windows apps (~10MB)
    *   **Mobile:** Capacitor for building native Android APKs

---

## 🧠 Spaced Repetition (FSRS) Math

The FSRS engine schedules subsequent card reviews based on 4 ratings:
1.  **Again:** Complete memory lapse (resets stability).
2.  **Hard:** Recalled with significant effort (short interval).
3.  **Good:** Correctly recalled (standard interval increase).
4.  **Easy:** Recalled effortlessly (maximum interval increase).

Speech recognition (STT) match percentages map directly to these review ratings:
*   `≥85%` match → **Easy** (Rating 4)
*   `≥60%` match → **Good** (Rating 3)
*   `≥40%` match → **Hard** (Rating 2)
*   `<40%` match → **Again** (Rating 1) + automatic TTS correction playback

---

## 🚀 Local Development

### Prerequisites
*   Node.js (version 22+)
*   NPM package manager

### Getting Started
```bash
# 1. Clone the repository and enter the directory
git clone https://github.com/mkowalczyk111/tensordeck.git
cd tensordeck

# 2. Install dependencies
npm install

# 3. Run the development server
npm run dev
```

### Firebase Config (Optional)
Create a `.env` file in the root directory:
```env
VITE_FIREBASE_API_KEY=YourApiKey
VITE_FIREBASE_AUTH_DOMAIN=your-project.firebaseapp.com
VITE_FIREBASE_PROJECT_ID=your-project
VITE_FIREBASE_STORAGE_BUCKET=your-project.appspot.com
VITE_FIREBASE_MESSAGING_SENDER_ID=SenderId
VITE_FIREBASE_APP_ID=AppId
```
*Note: Without a `.env` file, the app automatically runs in local-only offline mode (data saved in `localStorage`).*

---

## 📱 Build & CI/CD Pipelines

All builds and deployments are managed in the cloud using **GitHub Actions** (`.github/workflows/`):

### 1. Web Deployment (Firebase Hosting)
Pushes to the `main` branch trigger `deploy-web.yml`, which automatically builds web assets and deploys them to Firebase Hosting.

### 2. Desktop & Mobile Compilation (Tauri EXE & Capacitor APK)
Triggering the `Build Mobile and Desktop Apps` (`build-apps.yml`) workflow manually via the **Actions** tab triggers **parallel builds**:
*   **Windows EXE:** Compiles the Tauri app into `.exe` and `.msi` installers.
*   **Android APK:** Syncs Capacitor and compiles the native Android app into a debug `.apk`.
*   **Automated Release Upload:** The workflow automatically creates a **Draft Release** (e.g., `TensorDeck v0.0.0`) and uploads the Windows installers and the renamed Android package (`TensorDeck-Android-Debug.apk`) as assets.

#### Compiling Android locally (Optional)
If you have Android SDK and JDK 21 configured, you can build the APK locally:
```bash
npm run build
npx cap sync android
cd android
./gradlew assembleDebug
```
The resulting package will be generated at `android/app/build/outputs/apk/debug/app-debug.apk`.

---

## 📂 Project Structure

*   `src/components/` — Shared UI components (PageHeader, SegmentedProgressBar, GroupNotFound)
*   `src/hooks/` — Custom hooks managing session states, Firestore data, and auth
*   `src/i18n/` — Translation configurations and files (pl, en, de, es, it)
*   `src/pages/` — Page components (Dashboard, Study, Browse, Settings, AppSettings, Stats)
*   `src/services/` — Audio feedback generator, TTS/STT interfaces, and Firebase client configurations
*   `src/srs/` — Free Spaced Repetition Scheduling (FSRS) engine math
*   `src/theme.ts` — Material Design 3 theme system with accent color extraction
*   `src-tauri/` — Rust Tauri configurations
*   `android/` — Native Android project structure (Capacitor wrapper)

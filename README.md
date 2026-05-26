# RayColl 🚀

RayColl is an advanced, cross-platform flashcard study application built with **React Native**, **Expo**, **TypeScript**, and **React Native Paper (MD3/Material You)**. It supports smart spaced repetition using a custom implementation of the **FSRS (Free Spaced Repetition Scheduler)** algorithm.

## Features

- **Spaced Repetition (FSRS)**: Modern intervals scheduling algorithm that optimizes memory retention.
- **Custom Study Modes**: Define custom card review flows by ordering steps like TTS, STT verification, Wait, or Show Page.
- **TTS (Text-to-Speech)**: Multi-page auto-voice output using native voices or browser synth.
- **STT (Speech-to-Text)**: Pronunciation check with customizable match thresholds for multiple languages.
- **CSV/TSV Import**: Import whole decks of cards via pasted files with automatic separator, header language, and page count detection.
- **Material You Theme**: Supports device dynamic accent colors on compatible Android 12+ systems.
- **Local & Cloud Sync**: Local-first architecture (AsyncStorage & SQLite cache) with optional Firebase Firestore sync.

## Project Structure

```
src/
├── app/                  # File-based routing pages (Expo Router)
│   ├── browse/           # Card browsing & editing per group
│   ├── settings/         # Deck configuration (pages, languages, modes)
│   ├── study/            # Study/review sessions using useStudySession
│   ├── _layout.tsx       # Root layout provider
│   ├── app-settings.tsx  # Global configuration (theme, tts rate, backup)
│   ├── index.tsx         # Dashboard layout
│   └── stats.tsx         # Streak & learning analytics
├── components/           # Shared UI components
│   ├── browse/           # Browse screen subcomponents
│   ├── dashboard/        # Dashboard screen subcomponents
│   ├── import/           # Import screen subcomponents
│   └── settings/         # Deck settings subcomponents
├── constants/            # Configuration constants
├── contexts/             # Context providers (ThemeContext)
├── hooks/                # Custom React hooks (useStudySession, etc.)
├── i18n/                 # Localization (EN, PL, DE, ES, IT)
├── import/               # CSV/TSV parser & autodetect utilities
├── services/             # Audio, Firebase, TTS & STT services
├── srs/                  # Spaced repetition engine & FSRS math definitions
├── store/                # Central app state storage
└── types/                # TypeScript models and auth overrides
```

## Technology Stack

- **Framework**: React Native with Expo (SDK 56)
- **UI Library**: React Native Paper v5 (Material Design 3 / MD3 Expressive)
- **State Management**: Local context-based reactivity with persistence layer
- **Styling**: React Native StyleSheet with custom HSL palette mapping
- **Database / Cloud Sync**: Firebase (Auth & Firestore)
- **Audio Feedback**: Native speech synthesizer and microphone speech recognition services

## Getting Started

### Prerequisites

Make sure you have Node.js installed on your machine.

### Installation

1. Clone the repository and install dependencies:
   ```bash
   npm install
   ```

2. Generate or verify the native voice patches:
   ```bash
   npm run postinstall
   ```

### Running Locally

- **Start Expo CLI**:
  ```bash
  npm run start
  ```
- **Web App**:
  ```bash
  npm run web
  ```
- **Android Dev**:
  ```bash
  npm run android
  ```
- **iOS Dev**:
  ```bash
  npm run ios
  ```

## CSV / TSV Import Format

Decks can be imported using standard tabular text formats. Example:
```csv
Word;Translation;Example
hello;cześć;Hello, world!
goodbye;pożegnanie;Goodbye, friend.
```
During copy-paste, the parser automatically detects:
- Field delimiter (Tab, Semicolon, Comma, Pipe)
- Column names & languages based on header content
- Total page (columns) count per card

## Configuration & Environment

To configure Google Sign-In and Firestore Sync, create a `.env` file in the root directory:

```env
EXPO_PUBLIC_FIREBASE_API_KEY=your_api_key
EXPO_PUBLIC_FIREBASE_AUTH_DOMAIN=your_auth_domain
EXPO_PUBLIC_FIREBASE_PROJECT_ID=your_project_id
EXPO_PUBLIC_FIREBASE_STORAGE_BUCKET=your_storage_bucket
EXPO_PUBLIC_FIREBASE_MESSAGING_SENDER_ID=your_sender_id
EXPO_PUBLIC_FIREBASE_APP_ID=your_app_id
EXPO_PUBLIC_GOOGLE_CLIENT_ID=your_google_client_id
```

If these environment variables are absent, the application automatically runs in **Local Mode** with localStorage cache enabled.

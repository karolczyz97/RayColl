# RayColl

RayColl is a cross-platform flashcard study app built with Expo, React Native,
TypeScript, expo-router, React Native Paper, Firebase, AsyncStorage, and a custom
FSRS-style spaced repetition engine.

The app is local-first: decks, study modes, review progress, and activity data
are persisted on-device and can be synced to Firebase when the user signs in.

## Features

- Multi-page flashcard decks with configurable page names and languages.
- FSRS-inspired spaced repetition scheduling for new, learning, review, and mastered cards.
- Custom study modes made from show, speak, wait, pause, and listen-and-branch steps.
- Text-to-speech and speech-to-text study flows with pronunciation matching.
- CSV, TSV, semicolon, comma, and pipe-separated deck import.
- Dashboard, browse, study, settings, import, and statistics screens via expo-router.
- Material Design 3 UI using React Native Paper.
- Firebase Auth and Firestore sync for signed-in users.
- Static web export suitable for Firebase Hosting.

## Tech Stack

- Expo SDK 56
- React Native 0.85
- React 19
- TypeScript strict mode
- expo-router
- React Native Paper v5 / MD3
- Firebase Auth and Firestore
- AsyncStorage
- expo-speech and `@react-native-voice/voice`
- React Native Reanimated

## Development

Install dependencies:

```bash
npm install
```

Start Expo:

```bash
npm run start
```

Run on web:

```bash
npm run web
```

Run native development builds:

```bash
npm run android
npm run ios
```

Check code quality:

```bash
npm run lint
npm run typecheck
npm run test
```

Refactor regression baseline:

```bash
npm run lint
npm run typecheck
npm run test
```

Use [scripts/regression-checklist.md](/C:/Users/Karol/.gemini/antigravity/scratch/TensorDeck/scripts/regression-checklist.md)
after each refactor step for the manual functional baseline.

Create a static web export:

```bash
npx expo export --platform web
```

## Environment

Create a `.env` file with Firebase and Google sign-in configuration:

```env
EXPO_PUBLIC_FIREBASE_API_KEY=your_api_key
EXPO_PUBLIC_FIREBASE_AUTH_DOMAIN=your_auth_domain
EXPO_PUBLIC_FIREBASE_PROJECT_ID=your_project_id
EXPO_PUBLIC_FIREBASE_STORAGE_BUCKET=your_storage_bucket
EXPO_PUBLIC_FIREBASE_MESSAGING_SENDER_ID=your_sender_id
EXPO_PUBLIC_FIREBASE_APP_ID=your_app_id
EXPO_PUBLIC_GOOGLE_CLIENT_ID=your_google_client_id
```

When Firebase values are missing, RayColl continues to work in local-only mode.

## Import Format

Decks are imported from pasted tabular text. Supported separators are tab,
semicolon, comma, and pipe.

Example:

```csv
Phrase;Translation;Example
hello;czesc;Hello, world!
goodbye;do widzenia;Goodbye, friend.
```

The import screen detects the separator and page count, suggests page names and
languages from the first row, previews parsed rows, then creates the deck in one
store action. Import validation rejects empty deck names, malformed rows, empty
imports, invalid page counts, and invalid non-empty language codes. If
persistence fails, the newly-created deck is rolled back.

## Spaced Repetition

The SRS engine lives in `src/srs/srsEngine.ts`. Each flashcard stores an `SrsState`
with difficulty, stability, repetition count, FSRS state, last review timestamp,
and next review timestamp. Ratings are mapped to intervals through the FSRS
calculation helpers, while selectors categorize cards as new, learning, review,
or mastered.

## Sync And Persistence

AsyncStorage is the local source for guest data and the signed-in user cache.
Firebase Firestore stores the signed-in user's cloud copy. On sign-in, local and
cloud data are merged defensively:

- groups are merged by id,
- cards are merged by id,
- the card with more SRS repetitions wins when both sides have the same card,
- custom study modes are preserved,
- activity heatmap counts keep the maximum value per day.

Store persistence exposes `syncStatus`, `lastSyncError`, `lastPersistenceError`,
and `lastStoreError` so failures are visible instead of silently swallowed.

## Architecture

```text
src/
  app/                  expo-router screens
  components/           reusable Paper-focused UI components
  constants/            app constants and storage keys
  contexts/             theme context
  hooks/                app hooks, including study session orchestration
  import/               import parsing and validation
  services/             Firebase, TTS, STT, and audio feedback
  srs/                  FSRS/SRS domain logic
  store/                state provider, actions, selectors, persistence, seed data
  theme/                MD3 theme and semantic color helpers
  types/                shared TypeScript models
```

Business logic should live in selectors, actions, services, or SRS/import helpers,
not inside screen components.

Runtime store data should be canonical. Normalize local storage, Firebase data,
backup imports, and merge results at the boundary before they reach screens or
selectors. Prefer a single normalization layer in `src/store/storeDataNormalization.ts`
over repeating `|| default` and `?? fallback` across the app.

## Quality Rules

- Screens and presentational components may call store actions only. They must not call Firebase services directly.
- High-frequency changes should use queued persistence. Critical operations should use immediate flush.
- Route files in `src/app` should stay thin and move business logic into `features/*`, `store/*`, `services/*`, `selectors/*`, or `actions/*`.
- Prefer shared UI primitives such as `AppTextInput`, `SectionCard`, `AppScreen`, `AnimatedSection`, `MetricGrid`, `ConfirmDialog`, `DangerDialog`, `AppSnackbar`, and `SyncStatusBanner`.
- Avoid new magic inline styles like `outlineStyle={{ borderRadius: 12 }}` in screens. Use `TOKENS` and base components instead.
- Prefer canonical runtime fields over optional domain config when the app already has a real default. For deck config, normalize `studyFilter`, `activePageCount`, `pageNames`, and `pageLanguages` once instead of handling missing values ad hoc in UI code.

## MD3 And Icon Policy

RayColl uses React Native Paper as the app-level UI and icon API. Application code
should not import icon sets directly from `@expo/vector-icons`,
`react-native-vector-icons`, or `@react-native-vector-icons/*`.

Use Paper APIs instead:

- `Icon`
- `IconButton`
- `Button icon`
- `FAB icon`
- `List.Icon`
- `Avatar.Icon`
- `TextInput.Icon`
- `Chip icon`
- `src/components/AppIcon.tsx`

Icon names should be passed as strings such as `plus`, `delete-outline`,
`cards-outline`, or `chart-box-outline`. ESLint blocks direct app-level vector
icon imports.

## Web Hosting

`firebase.json` serves the static `dist` export and rewrites application routes to
`index.html`. Hashed JavaScript, CSS, font, and image assets receive long-lived
cache headers; font files also include CORS headers so Paper icon fonts load
correctly after deployment.

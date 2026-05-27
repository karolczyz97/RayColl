@AGENTS.md

# RayColl - working rules for Claude Code

Practical, project-specific rules. Read before every edit. If something here conflicts with a user instruction in the current session, ask.

## Project rules

- Project name: **RayColl** (working dir may be `TensorDeck`). `package.json#name === "raycoll"`.
- Stack: Expo SDK 56, React Native 0.85, React 19, TypeScript strict, expo-router, React Native Paper v5 (MD3), Firebase 12, AsyncStorage, Reanimated 4.
- App is **local-first**. Firebase is sync-only and may be unconfigured (`isConfigured = false` -> store still works).
- Treat the README "Architecture" section as the source of truth for directory roles.
- When you need Expo docs, use **v56** (`https://docs.expo.dev/versions/v56.0.0/`).
- Business logic belongs in `selectors/`, `actions/`, `services/`, `srs/`, `import/`. Screens orchestrate; they do not compute.

## Refactor rules

Before every refactor step:
1. Read AGENTS.md.
2. Preserve behavior unless the step explicitly says visual unification.
3. Run lint, typecheck, test.
4. Use `scripts/regression-checklist.md` for manual verification.

- No big-bang refactors. Work in **small, committable steps**; the app must compile after each.
- After each step run:
  - `npm run lint`
  - `npm run typecheck`
  - `npm run test`
- Preserve behavior unless the change is explicitly about behavior. Visual unification is intentional, not opportunistic restyling.
- Do not delete code by guesswork. Verify with grep and typecheck. If something might be needed indirectly (Expo, web, Paper), mark it "to audit" instead of removing it blindly.
- Every change needs a manual regression checklist. Passing TypeScript does not mean the feature works.
- Do not introduce abstractions for hypothetical future needs.

## UI / MD3 rules

- Use existing primitives. Do not reinvent:
  - `AppCard`, `AppSelect`, `AppSplitButton`, `AppMenuButton`, `AppIcon`, `PageHeader`, `GroupNotFound`, `SegmentedProgressBar`
  - `AppTextInput`, `SectionCard`, `AppScreen`, `AnimatedSection`, `MetricGrid`
  - `ConfirmDialog`, `DangerDialog`, `AppSnackbar`, `SyncStatusBanner`
- Spacing, radius, control height, motion durations, and layout breakpoints come from **`TOKENS`** in `src/theme/tokens.ts`. No magic numbers for those values in `StyleSheet.create`.
- Colors come from `theme.colors.*` and `src/theme/semanticColors.ts`. No raw hex in screens unless there is a strong reason and it belongs in the theme layer.
- Dialogs should use `dialogStyles.dialog` from `src/theme/dialogStyles.ts` and be wrapped in `<Portal>`.
- Animations should use Reanimated and shared motion tokens.
- Icons must never be imported directly from `@expo/vector-icons`, `react-native-vector-icons`, or `@react-native-vector-icons/*`. Use Paper icon APIs or `AppIcon`.
- Inline `style={{ ... }}` is only for dynamic values such as theme color overrides or computed widths. Static values should live in `StyleSheet` or a base component.
- Do not add ad-hoc styles like `outlineStyle={{ borderRadius: 12 }}` directly in screens when a base component already exists.
- Web parity matters. `AppCard` adds web hover and `_layout.tsx` adds a web shell; do not break either path during native polish.

## Firebase / persistence rules

- Components must never call `setDoc`, `getDoc`, or Firebase auth APIs directly.
- Flow should always be:
  - `services/firebase.ts` for low-level Firebase access
  - `store/persistence/*` for persistence boundaries
  - `FlashcardStoreContext` for orchestration
  - screens call `store.<action>`
- Screens and presentational components may call store actions only. They must not call Firebase services directly.
- High-frequency changes should use queued persistence.
- Critical operations should use immediate flush.
- During study, local store and AsyncStorage update immediately per rating, while Firestore flushes on session boundaries, every 10 reviewed cards, sign-out, background/unmount, and web `beforeunload`.
- `saveCloudData` is best-effort: it must surface errors through `syncStatus`, `lastSyncError`, `lastPersistenceError`, and `lastStoreError`.
- When Firebase is unconfigured, cloud calls must short-circuit gracefully and preserve local-only mode.
- Sign-in merge logic lives in `store/selectors/merge.ts`. Do not duplicate merge rules elsewhere.
- Firestore schema is now v2-backed. Prefer deck/card/study-mode/activity document updates over rewriting a monolithic root payload whenever action scope is known.

## Testing rules

- `npm run test` runs `scripts/run-tests.mjs`; keep that runner the single entry point for unit tests.
- Add or extend a unit test whenever you touch:
  - SRS engine
  - import parser
  - merge logic
  - selectors
  - store actions
  - persistence boundaries
- For screens and UI, always include a manual regression checklist in the response.
- Do not rely on snapshot tests for layout.

## Dependency rules

- Do not install new packages without explicit approval.
- If a new dependency is unavoidable, explain what it does, what it replaces, install size, native module impact, and web compatibility first.
- Do not remove dependencies by inspection only. Confirm with grep, typecheck, tests, and a real build or export.
- `@expo/vector-icons` may still be needed transitively by Paper or Expo even if app code cannot import it directly.
- Pin to Expo SDK 56-compatible versions unless the user explicitly asks for an upgrade.
- Preserve `postinstall: patch-voice-gradle.js` when changing install flow.

## File size / structure rules

- Route files in `src/app` should stay around 180 lines or less whenever practical.
- If a route starts accumulating stateful business logic, move it into `features/*` hooks/components or `store/*`.
- Prefer `useStoreState` and `useStoreActions` for action-only consumers when it helps avoid unnecessary rerenders.

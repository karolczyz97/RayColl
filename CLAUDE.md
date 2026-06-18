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
4. Use `docs/REGRESSION.md` for manual verification.

- No big-bang refactors. Work in **small, committable steps**; the app must compile after each.
- After each step run:
  - `npm run lint`
  - `npm run typecheck`
  - `npm run test`
- Preserve behavior unless the change is explicitly about behavior. Visual unification is intentional, not opportunistic restyling.
- Do not delete code by guesswork. Verify with grep and typecheck. If something might be needed indirectly (Expo, web, Paper), mark it "to audit" instead of removing it blindly.
- UI / behavior / navigation / sync changes need a manual regression checklist; pure-logic changes fully covered by unit tests do not. Passing TypeScript does not mean the feature works.
- Do not introduce abstractions for hypothetical future needs.
- A split or extraction must **reduce** coupling across its boundary. If pulling logic into a separate file/hook forces you to pass most of the parent's state back into it (many setters/refs/props), that boundary is fake — the two pieces are really one cohesive unit. Prefer one honest file over a split held together by a firehose of parameters. If a long file genuinely must be split, make the seam real first (e.g. a single `dispatch`/reducer or a small typed value object) instead of threading individual setters.

## UI / MD3 rules

- Use existing primitives. Do not reinvent:
  - `AppCard`, `AppSelect`, `AppSplitButton`, `AppMenuButton`, `AppIcon`, `GroupNotFound`, `SegmentedProgressBar`
  - `AppTextInput`, `SectionCard`, `AppScreen`, `AnimatedSection`, `MetricGrid`
  - `ConfirmDialog`, `AppSnackbar`, `SyncStatusBanner`, `ChangelogDialog`
- Spacing, radius, control height, motion durations, and layout breakpoints come from **`TOKENS`** in `src/theme/tokens.ts`. No magic numbers for those values in `StyleSheet.create`. Use a `TOKENS` entry when the value belongs to the design scale **or** is shared by ≥2 places; for a genuine one-off in a single component, a named local `const` with a `// why` comment beats bloating the global scale.
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
- Normalize data at the boundary, not in screens. Local load, cloud load, backup import, and merge should return canonical runtime data before it reaches the store.
- Do not spread `|| default` and `?? fallback` for domain fields across UI and selectors when the field is meant to be stable. Prefer one canonical source of truth in `store/storeDataNormalization.ts`.
- For `FlashcardGroup`, treat deck configuration as canonical runtime state:
  - `studyFilter` should always be present in runtime data.
  - `activePageCount` should always be present in runtime data.
  - `pageNames` and `pageLanguages` should be normalized to match `activePageCount`.
- If a field is business-required in normal app flow, prefer normalizing it once over allowing `undefined` and patching callers individually.

## Testing rules

- `npm run test` runs `jest` (parallel); keep that the single entry point for unit tests.
- When a test fails after an **intentional** behavior change, update the test to the new expected behavior — do **not** revert the feature to make a stale test pass. Treat a failing test as a blocker only when it reveals an **unintended** regression. If unsure whether the change was intentional, ask instead of guessing.
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

- Line count is a **smell, not a target**. A long file is a prompt to ask "is there a real seam here?", not an order to cut. Never split a file just to hit a number — a 400-line cohesive unit beats two files that share most of their state (see the abstraction-boundary rule above).
- Route files in `src/app` are best kept lean (~180 lines) by moving **business logic** out — not by slicing the file at an arbitrary line. The win is "screens orchestrate, they don't compute", not the line count itself.
- If a route accumulates stateful business logic, move that logic into `features/*` hooks/components or `store/*` — but only when the extracted piece has a clear, narrow boundary. If it would need the whole screen's state passed back in, keep it inline.
- Prefer `useStoreState` and `useStoreActions` for action-only consumers when it helps avoid unnecessary rerenders.

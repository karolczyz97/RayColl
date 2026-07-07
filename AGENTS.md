# Commit message convention

Commit message is part of the product — the first line and body become the in-app changelog shown to users after each deploy.

**Required format for any commit that triggers a deploy:**

```
<short title — becomes dialog heading for users>

- <user-facing change>
- <user-facing change>
```

Rules enforced by the deploy workflow (hard fail if violated):
- First line must be non-empty.
- Body must contain at least one bullet point starting with `- ` or `* `.

Write bullet points as if addressing the user, not as internal dev notes.

Good: `- Fixed card flip animation not responding on first tap`
Bad: `- refactor: extract useCardFlip hook`

# Deploy process

Deploy is **automatic** — pushing to the `main` branch triggers the CI/CD deploy workflow automatically.
For manual deploys or retries, the workflow can also be triggered via GitHub Actions.

## Steps every deploy

1. Make sure all changes are committed with the correct commit message format (see above).
2. `git push` to `main`. This triggers the deployment to Firebase Hosting and publishes an OTA update to Expo EAS.

## One-time setup (before first EAS Android build after adding remote versioning)

If there are existing published Android builds, sync the EAS version counter once:
```
eas build:version:set --platform android
```
Enter the current versionCode when prompted. Skip if no Android builds have been published yet.

## What the workflow does

- Validates commit message format (fails if no bullet points in body).
- Generates release metadata (webBuild, commitTitle, notes) and embeds it in the JS bundle.
- Deploys web to Firebase Hosting (`raycoll-io` project, `live` channel).
- Writes `version.json` to the hosted dist for inspection.
- Runs a non-blocking smoke test checking cache headers on the live URL.
- Publishes OTA update to Expo EAS using the same release metadata.

# Expo HAS CHANGED

Read the exact versioned docs at https://docs.expo.dev/versions/v56.0.0/ before writing any code.

Normalize data at the boundary, not in screens.

- Local storage load, Firebase load, backup import, and merge logic should return canonical runtime data before it reaches the store or UI.
- Do not spread `|| default` and `?? fallback` for stable domain fields across screens and selectors when the value can be normalized once.
- For deck runtime data, prefer a canonical model where config like `studyFilter`, `activePageCount`, `pageNames`, and `pageLanguages` is already normalized.

# Implementation quality rules

- For behavior changes, do not stop at making the UI show the new feature. Verify the full path: source data -> state/store -> UI -> persisted output, where applicable.
- Prefer extracting pure helpers for parsing, normalization, and state transitions, then test those helpers instead of burying fragile logic inside screens.
- When a task mentions tests or fixes a regression, add coverage for the real user flow or failure mode, not only for a tiny helper introduced during the change.
- For import, storage, sync, and Firestore changes, normalize data at the boundary before it reaches screens or store state.
- Avoid leaving untracked support files after a task. If changed code imports or depends on a file, that file must be included in the final diff/commit.
- At the end of substantial tasks, state which requested points are fully covered, partially covered, or intentionally left out.

# Required workflow for non-trivial changes

Use this workflow for changes that affect behavior, data, persistence, sync, import/export, study sessions, navigation, or shared components. Keep it proportional: cover realistic risks and known regressions, not imaginary edge cases.

Before editing:
- Read the relevant existing code path end-to-end before deciding on the implementation.
- Identify the user-visible behavior, the data flow, and any persistence/sync impact.
- Look for existing normalization, selectors, reducers, helpers, and tests before adding new patterns.
- Before creating new components, hooks, utilities, or patterns, check whether an existing one already fits. Use the existing piece when it fits cleanly; create a new one when reusing the old one would make the code more confusing or force a bad abstraction.

During implementation:
- Fix the root cause rather than patching symptoms with local fallbacks or UI-only workarounds.
- Keep changes scoped to the requested behavior; do not bundle unrelated cleanup unless it directly reduces risk.
- Prefer small, boring, maintainable logic over clever hacks. If a workaround is unavoidable, explain why in code or in the final response.
- Add tests for realistic failure modes: the original bug, manual overrides, data migration/normalization boundaries, or state transitions that could regress.
- Do not add tests for cases that cannot occur through supported app flows unless protecting against old persisted data or external input.

Before final response:
- Run the full local validation suite using `npm run validate` (which runs lint, typecheck, tests, check-ui, check-assets, and a dry-run web build export), or clearly explain why a command was skipped.
- Do a logic review of the implemented flow: walk through the main user scenario, one realistic edge case, and the relevant state/data transitions to catch bugs that typecheck and lint cannot catch.
- Review the final diff for unrelated changes, missing/untracked required files, accidental behavior changes, and duplicated fallback logic.
- State what was verified and call out any remaining realistic risks or partially covered areas.

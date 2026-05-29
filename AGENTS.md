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

Deploy is **manual** — pushing to git does not trigger it automatically.

## Steps every deploy

1. Make sure all changes are committed with the correct commit message format (see above).
2. `git push`
3. Go to GitHub → Actions → "CI/CD – Deploy Web to Firebase & OTA Update to Expo EAS" → "Run workflow" → "Run workflow".
4. This deploys web to Firebase Hosting AND publishes an OTA update to Expo EAS in one run.

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

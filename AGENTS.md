# Expo HAS CHANGED

Read the exact versioned docs at https://docs.expo.dev/versions/v56.0.0/ before writing any code.

Normalize data at the boundary, not in screens.

- Local storage load, Firebase load, backup import, and merge logic should return canonical runtime data before it reaches the store or UI.
- Do not spread `|| default` and `?? fallback` for stable domain fields across screens and selectors when the value can be normalized once.
- For deck runtime data, prefer a canonical model where config like `studyFilter`, `activePageCount`, `pageNames`, and `pageLanguages` is already normalized.

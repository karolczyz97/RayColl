# Fix instructions (15.10.2026, shared by all fixers)

You implement fixes for RayColl (Android, Kotlin, Jetpack Compose M3, Room, Firestore, Media3). You work inside your OWN git worktree (path and branch given in your prompt), created from `przeglad-modernizacja`. Other fixers work in parallel in other worktrees on other files. Always `cd` into your worktree path; never edit files in the main checkout `C:\Users\Karol\.gemini\antigravity\scratch\RayColl` itself.

## Setup (do first)
1. `local.properties` was already copied into your worktree (gitignored). Never commit it.
2. Confirm `git status` is clean and `git log --oneline -1` shows your branch.

## What to fix
Read your verdict file `docs/przeglad-2026-10-15/verdict-<area>.md` (and the original finding in `findings-<area>.md` for context). Implement every item with verdict CONFIRMED or PARTIAL (only the confirmed part), following its fix spec unless, after re-reading the code, you find the spec wrong — then do the right thing and note it. Skip WRONG / INTENTIONAL / EDGE / NOT_WORTH / DECISION. The owner wants even the smallest confirmed improvements done — do not skip small items.
Also implement the extra items the orchestrator lists in your prompt, and do NOT implement items the orchestrator assigns to someone else.

## File ownership (strict)
Only edit files in your ownership list (given in your prompt). If a fix truly needs a change in a file you don't own, do NOT edit it — write the needed change into your report under "HANDOFF" with exact file, location and code. Exceptions: new files you create, and tests for code you changed.
strings.xml: you MAY ADD new keys (never change or delete existing ones — translation fixes belong to a later pass). Add them to ALL 5 locales (values, values-pl, values-de, values-es, values-it) with real translations (Polish natural and gender-neutral; German/Spanish/Italian natural; Polish uses "talia" for deck). Put new keys at the END of each file inside a comment block `<!-- <AREA> -->` (your area code) to keep merges simple. I18nConsistencyTest enforces parity.

## Code style
- Match surrounding code: Kotlin idioms, existing naming, Polish comments (short, same density as nearby code). Don't add comments narrating the change ("naprawa X", "fix for review"). Don't reference finding IDs in code.
- Keep public APIs used by files you don't own unchanged (or keep the old signature delegating).
- Serialized formats (Firestore field names, backup JSON, Room schema) must stay compatible with existing data. Avoid Room schema changes.
- Add/adjust unit tests for behaviour you change (Robolectric/JUnit4 like existing tests). Prefer `stringResource`/`R.string` lookups over hardcoded English in NEW UI tests.

## Verification (mandatory)
Gradle must not run in parallel across worktrees (RAM). ALWAYS run Gradle through the lock script, from your worktree root:
  bash "C:/Users/Karol/.gemini/antigravity/scratch/RayColl/docs/przeglad-2026-10-15/gradle-locked.sh" :app:compileDebugUnitTestKotlin
It may wait for the lock; that's normal. Use Bash timeout 600000. Batch your edits; avoid many tiny builds.
Final check before committing: `gradle-locked.sh :app:testDebugUnitTest :app:ktlintCheck` must pass (baseline: 533 tests green, ktlint clean). Run `:app:ktlintFormat` first if formatting fails.

## Commit
Commit on your worktree branch (several logical commits are fine). Message in Polish, imperative, describing the user-visible effect, ending with:
Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Do not push. Do not touch other branches.

## Report (return to orchestrator, concise)
- Branch name and commit hashes.
- Implemented IDs (one line each, what changed).
- Skipped IDs with reason (incl. ones you found invalid).
- HANDOFF: changes needed in files you don't own (exact).
- Anything needing a device check (audio, background, visuals) — be specific.
- Test count after your changes.

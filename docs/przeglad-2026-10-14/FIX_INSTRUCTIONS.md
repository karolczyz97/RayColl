# Fix instructions (shared by all fixers)

You implement fixes for RayColl (Android, Kotlin, Jetpack Compose M3, Room, Firestore, Media3). You run inside your OWN git worktree (a copy of the repo on its own branch). Other fixers work in parallel in other worktrees on other files.

Review dir: C:\Users\Karol\AppData\Local\Temp\claude\C--Users-Karol--gemini-antigravity-scratch-RayColl\3dcb1293-b81c-4b3b-88f3-9cfde2526c53\scratchpad\review\

## Setup (do first)
1. Copy `C:/Users/Karol/.gemini/antigravity/scratch/RayColl/local.properties` into your worktree root (it is gitignored; without it Gradle fails). Never commit it.
2. Confirm `git status` is clean and note your branch name.

## What to fix
Read your verdict file(s). Implement every item with verdict CONFIRMED or PARTIAL (only the confirmed part), following its fix spec unless you find, after reading the code, that the spec is wrong — then do the right thing and note it. Skip WRONG / INTENTIONAL / EDGE / NOT_WORTH. Before changing anything, re-read the code: if a finding turns out invalid, skip it and record why.
Also implement the extra items the orchestrator lists in your prompt, and do NOT implement items the orchestrator assigns to someone else.

## File ownership (strict)
Only edit files in your ownership list (given in your prompt). If a fix truly needs a change in a file you don't own, do NOT edit it — write the needed change into your report under "HANDOFF" with exact file, location and code. Exception: new files you create, and tests for code you changed.
strings.xml: if you own strings, add new keys to ALL 5 locales (values, values-pl, values-de, values-es, values-it) with real translations (Polish natural and gender-neutral; German/Spanish/Italian natural). I18nConsistencyTest enforces parity. Put new keys at the END of each file inside a comment block `<!-- <your area> -->` to reduce merge conflicts.

## Code style
- Match surrounding code: Kotlin idioms, existing naming, Polish comments (short, same density as nearby code). Don't add comments that narrate the change ("naprawa X", "fix for review"). Don't reference finding IDs in code.
- Keep public APIs used by files you don't own unchanged (or keep the old signature delegating).
- Serialized formats (Firestore field names, backup JSON, Room schema) must stay compatible with existing data. Room schema changes need a migration — avoid unless the spec says so.
- Add/adjust unit tests for behaviour you change (Robolectric/JUnit4 like existing tests).

## Verification (mandatory)
Gradle must not run in parallel across worktrees (RAM). ALWAYS run Gradle through the lock script, from your worktree root:
  bash "C:/Users/Karol/AppData/Local/Temp/claude/C--Users-Karol--gemini-antigravity-scratch-RayColl/3dcb1293-b81c-4b3b-88f3-9cfde2526c53/scratchpad/review/gradle-locked.sh" :app:compileDebugKotlin
It may wait for the lock; that's normal. Run with Bash tool timeout 600000 and in the background if needed. Batch your edits; avoid many tiny builds.
Final check before committing: `... gradle-locked.sh :app:testDebugUnitTest :app:ktlintCheck` must pass (baseline: 435 tests green, ktlint clean). If ktlint complains about formatting, run `:app:ktlintFormat` if such task exists, else fix by hand.

## Commit
Commit on your worktree branch (several logical commits are fine). Message in Polish, imperative, describing user-visible effect, ending with:
Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Do not push. Do not touch main.

## Report (return to orchestrator, concise)
- Branch name and commit hashes.
- Implemented IDs (one line each, what changed).
- Skipped IDs with reason (incl. ones you found invalid).
- HANDOFF: changes needed in files you don't own (exact).
- Anything needing a device check (audio, background, visuals) — be specific.
- Test count after your changes.

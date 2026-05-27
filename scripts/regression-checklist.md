# RayColl Regression Checklist

## Commands

- `npm run lint`
- `npm run typecheck`
- `npm run test`

## Manual functional flows

1. App starts in local-only mode when Firebase env vars are missing.
2. Dashboard shows seed decks.
3. Login button does not crash when Firebase is configured/missing.
4. Import screen:
   - paste CSV/TSV text,
   - separator is detected,
   - page count is detected,
   - preview renders,
   - card can be edited/deleted before import,
   - import creates a deck.
5. Browse screen:
   - search filters cards locally,
   - filter chips work,
   - add card opens edit state,
   - save with fewer than 2 filled pages deletes the empty card,
   - delete dialog works.
6. Settings screen:
   - deck name edits locally while typing,
   - deck name persists only on blur/save,
   - page names persist only on blur/save,
   - language selection persists,
   - page reorder preserves card page content order,
   - study filter persists,
   - custom study mode can be created,
   - step add/move/delete works,
   - delete deck requires DELETE.
7. Study screen:
   - classic mode starts,
   - tap reveal works,
   - rating buttons update SRS,
   - failed-card flow works,
   - TTS/STT modes do not crash.
8. Stats screen:
   - global stats render,
   - heatmap renders,
   - deck progress renders.
9. App settings:
   - language switch works,
   - theme switch works,
   - dynamic colors switch works,
   - TTS speed persists,
   - export/import backup works,
   - reset works.

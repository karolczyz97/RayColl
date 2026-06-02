import { shouldTriggerMigration, getGuestHasData } from '../migrationLogic';
import type { FlashcardGroup, StudyMode } from '../../../types/models';

function assertEqual<T>(actual: T, expected: T, message: string) {
  if (actual !== expected) {
    throw new Error(`${message}: expected ${String(expected)}, got ${String(actual)}`);
  }
}

function makeGuestData(groups: FlashcardGroup[]): { groups: FlashcardGroup[]; studyModes: StudyMode[]; activityHeatmap: Record<string, number> } {
  return { groups, studyModes: [], activityHeatmap: {} };
}

export async function runTests() {
  console.log('\n--- Running Migration Logic Tests ---');

  // shouldTriggerMigration
  assertEqual(shouldTriggerMigration(false, true), true, 'No user-local cache + guest has data → trigger');
  assertEqual(shouldTriggerMigration(true, true), false, 'Has user-local cache + guest has data → no trigger');
  assertEqual(shouldTriggerMigration(false, false), false, 'No cache + no guest data → no trigger');
  assertEqual(shouldTriggerMigration(true, false), false, 'Has cache + no guest data → no trigger');

  // getGuestHasData
  assertEqual(getGuestHasData(null), false, 'null guest data → false');
  assertEqual(getGuestHasData(makeGuestData([])), false, 'Empty guest groups → false');
  assertEqual(
    getGuestHasData(makeGuestData([{ id: 'g1', name: 'Test', cards: [], activeModeId: '', studyFilter: 'all', pageLanguages: [], pageNames: [], activePageCount: 0 }])),
    true,
    'Guest with at least one group → true',
  );

  console.log('Migration logic tests passed');
}

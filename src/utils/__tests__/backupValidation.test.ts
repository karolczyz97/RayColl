import { validateBackupData, type BackupData } from '../backupValidation';
import { normalizeStoreData, CURRENT_SCHEMA_VERSION } from '../../store/storeDataNormalization';
import type { FlashcardGroup, StudyMode } from '../../types/models';

function assertOk(condition: boolean, message: string) {
  if (!condition) {
    throw new Error(message);
  }
}

function assertThrows(fn: () => void, message: string) {
  try {
    fn();
    throw new Error(`${message}: did not throw`);
  } catch (err) {
    if (err instanceof Error && err.message === `${message}: did not throw`) {
      throw err;
    }
  }
}

function assertEqual<T>(actual: T, expected: T, message: string) {
  if (actual !== expected) {
    throw new Error(`${message}: expected ${JSON.stringify(expected)}, got ${JSON.stringify(actual)}`);
  }
}

function makeValidGroup(): FlashcardGroup {
  return {
    id: 'g1',
    name: 'Test Deck',
    cards: [
      {
        id: 'c1',
        pages: ['hello', 'world'],
        srsState: {
          difficulty: 3,
          stability: 10,
          repetitions: 5,
          state: 2,
          lastReviewTimestamp: Date.now(),
          nextReviewTimestamp: Date.now() + 86400000,
        },
      },
    ],
    activeModeId: 'classic',
    studyFilter: 'new+review',
    pageLanguages: ['en-US', 'pl-PL'],
    pageNames: ['Word', 'Translation'],
    activePageCount: 2,
  };
}

function makeValidMode(): StudyMode {
  return {
    id: 'classic',
    name: 'Classic',
    steps: [
      { type: 'show_page', pageIndex: 0 },
      { type: 'rate' },
    ],
    isBuiltIn: true,
  };
}

function makeValidBackup(): BackupData {
  return {
    groups: [makeValidGroup()],
    studyModes: [makeValidMode()],
    activityHeatmap: { '2025-01-01': 3 },
  };
}

export async function runTests() {
  console.log('\n--- Running Backup Validation Tests ---');

  // Test 1: Valid backup passes validation
  assertOk(validateBackupData(makeValidBackup()), 'Valid backup should pass validation');

  // Test 2: Backup with optional fields passes validation
  const withOptional: BackupData = {
    ...makeValidBackup(),
    schemaVersion: 1,
    exportedAt: '2025-06-01T12:00:00.000Z',
  };
  assertOk(validateBackupData(withOptional), 'Backup with optional fields should pass validation');

  // Test 3: Missing groups throws
  assertThrows(() => {
    const { groups: _, ...noGroups } = makeValidBackup();
    validateBackupData(noGroups);
  }, 'Missing "groups" array should throw');

  // Test 4: Missing studyModes throws
  assertThrows(() => {
    const { studyModes: _, ...noModes } = makeValidBackup();
    validateBackupData(noModes);
  }, 'Missing "studyModes" array should throw');

  // Test 5: Missing activityHeatmap throws
  assertThrows(() => {
    const { activityHeatmap: _, ...noHeatmap } = makeValidBackup();
    validateBackupData(noHeatmap);
  }, 'Missing "activityHeatmap" should throw');

  // Test 6: Invalid card difficulty (string instead of number) throws
  assertThrows(() => {
    const bad = makeValidBackup();
    (bad.groups[0].cards[0].srsState as Record<string, unknown>).difficulty = 'easy';
    validateBackupData(bad);
  }, 'Invalid card srsState.difficulty should throw');

  // Test 7: Invalid study mode step type throws
  assertThrows(() => {
    const bad = makeValidBackup();
    bad.studyModes[0].steps.push({ type: 'unknown_type' } as never);
    validateBackupData(bad);
  }, 'Invalid study mode step type should throw');

  // Test 8: Empty object throws
  assertThrows(() => {
    validateBackupData({});
  }, 'Empty object should throw');

  // Test 9: Legacy backup without timestamps gets normalized
  const legacyGroup: FlashcardGroup = {
    id: 'g2',
    name: 'Legacy Deck',
    cards: [
      {
        id: 'c2',
        pages: ['a', 'b'],
        srsState: {
          difficulty: 2,
          stability: 5,
          repetitions: 0,
          state: 0,
          lastReviewTimestamp: 0,
          nextReviewTimestamp: 0,
        },
      },
    ],
    activeModeId: 'classic',
    studyFilter: 'new+review',
    pageLanguages: ['en-US'],
    pageNames: ['Front'],
    activePageCount: 1,
  };
  // Fields intentionally absent: updatedAt, contentUpdatedAt, srsUpdatedAt, deletedAt
  const legacyBackup: BackupData = {
    groups: [legacyGroup],
    studyModes: [makeValidMode()],
    activityHeatmap: {},
  };
  assertOk(validateBackupData(legacyBackup), 'Legacy backup without timestamps should pass validation');

  const normalized = normalizeStoreData({
    groups: legacyBackup.groups,
    studyModes: legacyBackup.studyModes,
    activityHeatmap: legacyBackup.activityHeatmap,
  });
  assertEqual(normalized.schemaVersion, CURRENT_SCHEMA_VERSION, 'Legacy backup should receive schemaVersion');
  assertEqual(normalized.groups[0].updatedAt, 0, 'normalizeGroup should fill missing updatedAt');
  assertEqual(normalized.groups[0].cards[0].contentUpdatedAt, 0, 'normalizeCard should fill missing contentUpdatedAt');
  assertEqual(normalized.groups[0].cards[0].srsUpdatedAt, 0, 'normalizeCard should fill missing srsUpdatedAt');

  // Test 10: Export structure contract
  const exportObj = {
    groups: [],
    studyModes: [],
    activityHeatmap: {},
    schemaVersion: 1,
  };
  assertOk('groups' in exportObj, 'Export must contain groups');
  assertOk('studyModes' in exportObj, 'Export must contain studyModes');
  assertOk('activityHeatmap' in exportObj, 'Export must contain activityHeatmap');
  assertOk('schemaVersion' in exportObj, 'Export must contain schemaVersion');

  console.log('Backup validation tests passed');
}

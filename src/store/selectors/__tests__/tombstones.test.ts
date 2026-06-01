import { selectLiveStudyModes, selectActiveGroups, selectArchivedGroups, isArchived } from '../tombstones';
import { DEFAULT_STUDY_FILTER } from '../../storeDataNormalization';
import { createNewSrsState } from '../../../srs/srsEngine';

function assertEqual<T>(actual: T, expected: T, message: string) {
  if (actual !== expected) {
    throw new Error(`${message}: expected ${expected}, got ${actual}`);
  }
}

function assertOk(condition: boolean, message: string) {
  if (!condition) {
    throw new Error(message);
  }
}

export async function runTests() {
  console.log('\n--- Running Tombstone Selector Tests ---');

  const now = Date.now();
  const liveCard = {
    id: 'c1',
    pages: ['a', 'b'],
    srsState: createNewSrsState(),
  };
  const tombstoneCard = {
    id: 'c2',
    pages: ['x', 'y'],
    srsState: createNewSrsState(),
    deletedAt: now,
  };

  const group = {
    id: 'g1',
    name: 'Test',
    cards: [liveCard, tombstoneCard],
    activeModeId: 'classic',
    studyFilter: DEFAULT_STUDY_FILTER,
    pageLanguages: ['en-US', 'en-US'],
    pageNames: ['Front', 'Back'],
    activePageCount: 2,
  };

  const tombstoneGroup = {
    id: 'g2',
    name: 'Deleted',
    cards: [],
    activeModeId: 'classic',
    studyFilter: DEFAULT_STUDY_FILTER,
    pageLanguages: ['en-US', 'en-US'],
    pageNames: ['Front', 'Back'],
    activePageCount: 2,
    deletedAt: now,
  };

  const liveMode = { id: 'm1', name: 'Live', steps: [], isBuiltIn: false };
  const tombstoneMode = { id: 'm2', name: 'Dead', steps: [], isBuiltIn: false, deletedAt: now };
  const modeResult = selectLiveStudyModes([liveMode, tombstoneMode]);
  assertEqual(modeResult.length, 1, 'selectLiveStudyModes should filter out tombstone modes');
  assertEqual(modeResult[0].id, 'm1', 'selectLiveStudyModes should keep live modes');

  const emptyModes = selectLiveStudyModes([tombstoneMode]);
  assertEqual(emptyModes.length, 0, 'selectLiveStudyModes should return empty when all modes are tombstoned');

  // isArchived
  assertOk(!isArchived(group), 'Group without archivedAt should not be archived');
  const archivedGroup = { ...group, archivedAt: now };
  assertOk(isArchived(archivedGroup), 'Group with archivedAt should be archived');
  assertOk(!isArchived({ ...group, archivedAt: 0 as unknown as number | null | undefined }), 'archivedAt=0 should not be archived');
  assertOk(!isArchived({ ...group, archivedAt: null as unknown as number | null }), 'archivedAt=null should not be archived');

  // selectActiveGroups: excludes deletedAt AND archivedAt
  const permGroup = { ...group, id: 'g3', deletedAt: now };
  const activeResult = selectActiveGroups([group, archivedGroup, tombstoneGroup, permGroup]);
  assertEqual(activeResult.length, 1, 'selectActiveGroups should return only fully active groups');
  assertEqual(activeResult[0].id, 'g1', 'selectActiveGroups should keep only non-archived non-deleted');

  // selectArchivedGroups: only archivedAt and no deletedAt
  const archivedWithDeleted = { ...group, id: 'g4', archivedAt: now, deletedAt: now };
  const archivedResult = selectArchivedGroups([archivedGroup, group, tombstoneGroup, archivedWithDeleted]);
  assertEqual(archivedResult.length, 1, 'selectArchivedGroups should return only archived (not deleted) groups');
  assertEqual(archivedResult[0].id, 'g1', 'selectArchivedGroups should keep archived group');

  // selectArchivedGroups: sorted by archivedAt descending
  const oldArchived = { ...group, id: 'g5', archivedAt: now - 1000 };
  const newerArchived = { ...group, id: 'g6', archivedAt: now };
  const sortedResult = selectArchivedGroups([oldArchived, newerArchived]);
  assertEqual(sortedResult[0].id, 'g6', 'selectArchivedGroups should sort newest first');

  console.log('Tombstone selector tests passed');
}

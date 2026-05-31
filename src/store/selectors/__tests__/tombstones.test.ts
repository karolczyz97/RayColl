import { selectLiveGroups, selectLiveStudyModes } from '../tombstones';
import { DEFAULT_STUDY_FILTER } from '../../storeDataNormalization';
import { createNewSrsState } from '../../../srs/srsEngine';

function assertEqual<T>(actual: T, expected: T, message: string) {
  if (actual !== expected) {
    throw new Error(`${message}: expected ${expected}, got ${actual}`);
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

  const liveResult = selectLiveGroups([group, tombstoneGroup]);
  assertEqual(liveResult.length, 1, 'selectLiveGroups should filter out groups with deletedAt');
  assertEqual(liveResult[0].cards.length, 1, 'selectLiveGroups should filter out tombstone cards');
  assertEqual(liveResult[0].cards[0].id, 'c1', 'selectLiveGroups should keep live cards');

  const tombstoneOnlyGroup = [tombstoneGroup];
  const emptyResult = selectLiveGroups(tombstoneOnlyGroup);
  assertEqual(emptyResult.length, 0, 'selectLiveGroups should return empty when all groups are tombstoned');

  const liveMode = { id: 'm1', name: 'Live', steps: [], isBuiltIn: false };
  const tombstoneMode = { id: 'm2', name: 'Dead', steps: [], isBuiltIn: false, deletedAt: now };
  const modeResult = selectLiveStudyModes([liveMode, tombstoneMode]);
  assertEqual(modeResult.length, 1, 'selectLiveStudyModes should filter out tombstone modes');
  assertEqual(modeResult[0].id, 'm1', 'selectLiveStudyModes should keep live modes');

  const emptyModes = selectLiveStudyModes([tombstoneMode]);
  assertEqual(emptyModes.length, 0, 'selectLiveStudyModes should return empty when all modes are tombstoned');

  const noDeletedGroups = selectLiveGroups([group]);
  assertEqual(noDeletedGroups.length, 1, 'selectLiveGroups should keep groups without deletedAt');

  console.log('Tombstone selector tests passed');
}

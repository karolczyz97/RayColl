import { createNewSrsState } from '../../../srs/srsEngine';
import {
  mergeCards,
  mergeGroups,
  mergeStudyModes,
  mergeUserData,
  getLatestEdit,
  isDeleted,
  mergeSrsStateNeverRegress,
  mergeActivityHeatmap,
} from '../merge';
import { DEFAULT_STUDY_FILTER } from '../../storeDataNormalization';

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
  console.log('\n--- Running Merge Selector Tests ---');

  const now = Date.now();

  // Test 1: Per-field merge — content last-write-wins, SRS never-regress
  const localCard = {
    id: 'c1',
    pages: ['local', 'card'],
    srsState: { ...createNewSrsState(), repetitions: 4 },
    contentUpdatedAt: now - 2000,
    srsUpdatedAt: now,
  };
  const cloudCard = {
    id: 'c1',
    pages: ['cloud', 'card'],
    srsState: { ...createNewSrsState(), repetitions: 1 },
    contentUpdatedAt: now - 1000,
    srsUpdatedAt: 0,
  };

  // Direct mergeCards test
  const directResult = mergeCards([localCard], [cloudCard]);
  assertEqual(directResult[0].pages[0], 'cloud', 'Direct: content last-write-wins');
  assertEqual(directResult[0].srsState.repetitions, 4, 'Direct: SRS never-regress');

  // Full mergeUserData test
  const merged = mergeUserData(
    {
      groups: [
        {
          id: 'g1',
          name: 'Local deck',
          cards: [localCard],
          activeModeId: 'custom-mode',
          studyFilter: 'new+review',
          pageLanguages: ['en-US', 'pl-PL'],
          pageNames: ['Front', 'Back'],
          activePageCount: 2,
        },
      ],
      studyModes: [{ id: 'custom-mode', name: 'Custom', steps: [], isBuiltIn: false }],
      activityHeatmap: { '2026-05-27': 3 },
    },
    {
      groups: [
        {
          id: 'g1',
          name: 'Cloud deck',
          cards: [cloudCard],
          activeModeId: '',
          studyFilter: DEFAULT_STUDY_FILTER,
          pageLanguages: ['en-US', 'pl-PL'],
          pageNames: ['Front', 'Back'],
          activePageCount: 2,
        },
      ],
      studyModes: [
        {
          id: 'classic',
          name: 'Classic',
          steps: [],
          isBuiltIn: true,
          builtInSourceId: 'classic',
        },
      ],
      activityHeatmap: { '2026-05-27': 1 },
    },
  );

  assertEqual(merged.studyModes.some((mode) => mode.id === 'custom-mode'), true, 'Merge should preserve custom modes');
  assertEqual(
    merged.groups[0].cards[0].pages[0],
    'cloud',
    'Content last-write-wins: cloud has newer contentUpdatedAt',
  );
  assertEqual(
    merged.groups[0].cards[0].srsState.repetitions,
    4,
    'SRS never-regress: local has more repetitions',
  );
  assertEqual(merged.activityHeatmap['2026-05-27'], 3, 'Merge should keep the maximum activity count');

  // Test 2: Legacy timestamps (0) → cloud wins
  const legacyCard = {
    id: 'c2',
    pages: ['legacy', 'card'],
    srsState: createNewSrsState(),
  };
  const cloudCard2 = {
    id: 'c2',
    pages: ['cloud2', 'card'],
    srsState: { ...createNewSrsState(), repetitions: 2 },
    contentUpdatedAt: 0,
    srsUpdatedAt: 0,
  };

  const merged2 = mergeUserData(
    {
      groups: [
        {
          id: 'g2',
          name: 'Legacy Deck',
          cards: [legacyCard],
          activeModeId: 'classic',
          studyFilter: DEFAULT_STUDY_FILTER,
          pageLanguages: ['en-US', 'pl-PL'],
          pageNames: ['Front', 'Back'],
          activePageCount: 2,
        },
      ],
      studyModes: [],
      activityHeatmap: {},
    },
    {
      groups: [
        {
          id: 'g2',
          name: 'Cloud Wins',
          cards: [cloudCard2],
          activeModeId: 'classic',
          studyFilter: DEFAULT_STUDY_FILTER,
          pageLanguages: ['en-US', 'pl-PL'],
          pageNames: ['Front', 'Back'],
          activePageCount: 2,
        },
      ],
      studyModes: [],
      activityHeatmap: {},
    },
  );

  assertEqual(
    merged2.groups[0].name,
    'Cloud Wins',
    'Legacy (0) vs cloud: cloud should win group fields on tie',
  );
  assertEqual(
    merged2.groups[0].cards[0].pages[0],
    'cloud2',
    'Legacy (0) vs cloud: cloud cards should win on tie',
  );

  // Test 3: Tombstone propagation — deleted card stays deleted
  const deletedCard = {
    id: 'c3',
    pages: ['gone', 'card'],
    srsState: createNewSrsState(),
    contentUpdatedAt: now,
    srsUpdatedAt: 0,
    deletedAt: now,
  };
  const liveCard = {
    id: 'c3',
    pages: ['alive', 'card'],
    srsState: { ...createNewSrsState(), repetitions: 1 },
    contentUpdatedAt: now - 5000,
    srsUpdatedAt: 0,
  };

  const merged3 = mergeUserData(
    {
      groups: [
        {
          id: 'g3',
          name: 'Tombstone local',
          cards: [deletedCard],
          activeModeId: 'classic',
          studyFilter: DEFAULT_STUDY_FILTER,
          pageLanguages: ['en-US', 'pl-PL'],
          pageNames: ['Front', 'Back'],
          activePageCount: 2,
        },
      ],
      studyModes: [],
      activityHeatmap: {},
    },
    {
      groups: [
        {
          id: 'g3',
          name: 'Tombstone cloud',
          cards: [liveCard],
          activeModeId: 'classic',
          studyFilter: DEFAULT_STUDY_FILTER,
          pageLanguages: ['en-US', 'pl-PL'],
          pageNames: ['Front', 'Back'],
          activePageCount: 2,
        },
      ],
      studyModes: [],
      activityHeatmap: {},
    },
  );

  assertOk(
    merged3.groups[0].cards[0].deletedAt != null,
    'Tombstone should propagate: card deleted locally stays deleted after merge',
  );

  // Test 4: Edit newer than deletion → card revived
  const revivedCard = {
    id: 'c4',
    pages: ['revived', 'card'],
    srsState: createNewSrsState(),
    contentUpdatedAt: now,
    srsUpdatedAt: 0,
    deletedAt: now - 10000,
  };

  const merged4 = mergeUserData(
    {
      groups: [
        {
          id: 'g4',
          name: 'Revive test',
          cards: [revivedCard],
          activeModeId: 'classic',
          studyFilter: DEFAULT_STUDY_FILTER,
          pageLanguages: ['en-US', 'pl-PL'],
          pageNames: ['Front', 'Back'],
          activePageCount: 2,
        },
      ],
      studyModes: [],
      activityHeatmap: {},
    },
    {
      groups: [],
      studyModes: [],
      activityHeatmap: {},
    },
  );

  assertOk(
    merged4.groups[0].cards[0].deletedAt == null,
    'Edit newer than deletion: card with contentUpdatedAt > deletedAt should be alive',
  );

  // Test 5: mergeStudyModes direct test
  const localModes = [
    { id: 'custom', name: 'Custom Local', steps: [{ type: 'rate' as const }], isBuiltIn: false, updatedAt: now },
  ];
  const cloudModes = [
    { id: 'classic', name: 'Classic Cloud', steps: [], isBuiltIn: true, builtInSourceId: 'classic', updatedAt: now - 5000 },
  ];
  const mergedModes = mergeStudyModes(localModes, cloudModes);
  assertEqual(mergedModes.length, 2, 'mergeStudyModes should combine both local and cloud modes');
  assertOk(
    mergedModes.some((m) => m.id === 'custom'),
    'mergeStudyModes should preserve local-only modes',
  );
  assertOk(
    mergedModes.some((m) => m.id === 'classic'),
    'mergeStudyModes should preserve cloud-only modes',
  );

  // Test 6: mergeStudyModes — conflict resolved by updatedAt
  const localModeConflict = { id: 'conflict', name: 'Local Wins', steps: [{ type: 'rate' as const }], isBuiltIn: false, updatedAt: now };
  const cloudModeConflict = { id: 'conflict', name: 'Cloud Older', steps: [], isBuiltIn: false, updatedAt: now - 5000 };
  const mergedConflict = mergeStudyModes([localModeConflict], [cloudModeConflict]);
  assertEqual(mergedConflict.length, 1, 'mergeStudyModes should deduplicate by id');
  assertEqual(mergedConflict[0].name, 'Local Wins', 'mergeStudyModes: newer updatedAt should win');

  // Test 7: mergeStudyModes — tombstone propagation
  const localTombstoneMode = { id: 'tomb', name: 'Deleted', steps: [], isBuiltIn: false, updatedAt: now, deletedAt: now };
  const cloudLiveMode = { id: 'tomb', name: 'Alive', steps: [], isBuiltIn: false, updatedAt: now - 5000 };
  const mergedTombstone = mergeStudyModes([localTombstoneMode], [cloudLiveMode]);
  assertEqual(mergedTombstone.length, 1, 'mergeStudyModes should keep tombstoned mode');
  assertOk(
    mergedTombstone[0].deletedAt != null,
    'mergeStudyModes: tombstone should propagate to merged result',
  );

  // Test 8: mergeGroups — tombstone propagation
  const localGroupTomb = {
    id: 'gt',
    name: 'Local Deleted',
    cards: [],
    activeModeId: 'classic',
    studyFilter: DEFAULT_STUDY_FILTER,
    pageLanguages: ['en-US'],
    pageNames: ['Front'],
    activePageCount: 1,
    updatedAt: now,
    deletedAt: now,
  };
  const cloudGroupLive = {
    id: 'gt',
    name: 'Cloud Alive',
    cards: [],
    activeModeId: 'classic',
    studyFilter: DEFAULT_STUDY_FILTER,
    pageLanguages: ['en-US'],
    pageNames: ['Front'],
    activePageCount: 1,
    updatedAt: now - 5000,
  };
  const mergedGroupTomb = mergeGroups([localGroupTomb], [cloudGroupLive]);
  assertEqual(mergedGroupTomb.length, 1, 'mergeGroups should keep tombstoned group');
  assertOk(
    mergedGroupTomb[0].deletedAt != null,
    'mergeGroups: tombstone should propagate to merged result',
  );

  // Test 9: getLatestEdit helper
  {
    const card = {
      id: 'hlp1',
      pages: ['a', 'b'],
      srsState: createNewSrsState(),
      contentUpdatedAt: 1000,
      srsUpdatedAt: 2000,
    };
    assertEqual(getLatestEdit(card), 2000, 'getLatestEdit returns max of content and srs timestamps');

    const cardMissing = {
      id: 'hlp2',
      pages: ['a'],
      srsState: createNewSrsState(),
      contentUpdatedAt: undefined,
      srsUpdatedAt: undefined,
    };
    assertEqual(getLatestEdit(cardMissing), 0, 'getLatestEdit returns 0 when both timestamps missing');
  }

  // Test 10: isDeleted helper
  {
    assertOk(!isDeleted({}), 'isDeleted returns false for no deletedAt');
    assertOk(!isDeleted({ deletedAt: undefined }), 'isDeleted returns false for undefined');
    assertOk(!isDeleted({ deletedAt: null }), 'isDeleted returns false for null');
    assertOk(!isDeleted({ deletedAt: 0 }), 'isDeleted returns false for 0');
    assertOk(!isDeleted({ deletedAt: -1 }), 'isDeleted returns false for negative');
    assertOk(isDeleted({ deletedAt: 1 }), 'isDeleted returns true for positive');
  }

  // Test 11: mergeSrsStateNeverRegress helper
  {
    // Higher reps wins
    assertEqual(
      mergeSrsStateNeverRegress(5, 100, 1, 200),
      'local',
      'mergeSrsStateNeverRegress: higher local reps wins regardless of srsAt',
    );
    assertEqual(
      mergeSrsStateNeverRegress(1, 100, 5, 200),
      'cloud',
      'mergeSrsStateNeverRegress: higher cloud reps wins',
    );
    // Tie on reps → higher srsAt wins
    assertEqual(
      mergeSrsStateNeverRegress(3, 500, 3, 300),
      'local',
      'mergeSrsStateNeverRegress: tie on reps, local has higher srsAt',
    );
    assertEqual(
      mergeSrsStateNeverRegress(3, 300, 3, 500),
      'cloud',
      'mergeSrsStateNeverRegress: tie on reps, cloud has higher srsAt',
    );
    // Full tie
    assertEqual(
      mergeSrsStateNeverRegress(3, 300, 3, 300),
      'cloud',
      'mergeSrsStateNeverRegress: full tie defaults to cloud',
    );
  }

  // Test 12: mergeActivityHeatmap helper
  {
    const local = { '2026-05-27': 5, '2026-05-28': 2 };
    const cloud = { '2026-05-27': 3, '2026-05-29': 7 };
    const merged = mergeActivityHeatmap(local, cloud);
    assertEqual(merged['2026-05-27'], 5, 'mergeActivityHeatmap keeps max count (local higher)');
    assertEqual(merged['2026-05-28'], 2, 'mergeActivityHeatmap preserves local-only dates');
    assertEqual(merged['2026-05-29'], 7, 'mergeActivityHeatmap preserves cloud-only dates');
  }

  // Test 13: mergeStudyModes — built-in mode ignores tombstone
  {
    const localTomb = {
      id: 'classic',
      name: 'Deleted classic',
      steps: [],
      isBuiltIn: true,
      builtInSourceId: 'classic',
      updatedAt: now,
      deletedAt: now,
    };
    const cloudLive = {
      id: 'classic',
      name: 'Cloud classic',
      steps: [{ type: 'rate' as const }],
      isBuiltIn: true,
      builtInSourceId: 'classic',
      updatedAt: now - 5000,
    };
    const merged = mergeStudyModes([localTomb], [cloudLive]);
    assertEqual(merged.length, 1, 'mergeStudyModes: built-in tombstone should still produce 1 mode');
    assertOk(
      merged[0].deletedAt == null,
      'mergeStudyModes: built-in mode ignores tombstone, deletedAt should be undefined',
    );
    assertEqual(
      merged[0].name,
      'Deleted classic',
      'mergeStudyModes: built-in winner name from higher updatedAt',
    );
  }

  // Test 14: mergeStudyModes — built-in mode on cloud side also ignores tombstone
  {
    const localLive = {
      id: 'classic',
      name: 'Local classic',
      steps: [],
      isBuiltIn: true,
      builtInSourceId: 'classic',
      updatedAt: now - 1000,
    };
    const cloudTomb = {
      id: 'classic',
      name: 'Deleted cloud classic',
      steps: [{ type: 'rate' as const }],
      isBuiltIn: true,
      builtInSourceId: 'classic',
      updatedAt: now,
      deletedAt: now,
    };
    const merged = mergeStudyModes([localLive], [cloudTomb]);
    assertEqual(merged.length, 1, 'mergeStudyModes: built-in cloud tombstone should still produce 1 mode');
    assertOk(
      merged[0].deletedAt == null,
      'mergeStudyModes: built-in mode ignores cloud tombstone, deletedAt should be undefined',
    );
    assertEqual(
      merged[0].name,
      'Deleted cloud classic',
      'mergeStudyModes: built-in winner from cloud (higher updatedAt)',
    );
  }

  console.log('Merge selector tests passed');
}

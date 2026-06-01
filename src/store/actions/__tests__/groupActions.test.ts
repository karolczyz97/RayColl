import { setVisiblePageCountAction, deleteGroupAction, updateGroupAction, archiveGroupAction, restoreGroupAction, purgeExpiredArchivesAction } from '../groupActions';
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
  console.log('\n--- Running Group Actions Tests ---');

  const group = {
    id: 'g1',
    name: 'Deck',
    cards: [
      {
        id: 'c1',
        pages: ['front', 'back', 'extra'],
        srsState: createNewSrsState(),
      },
    ],
    activeModeId: 'classic',
    studyFilter: DEFAULT_STUDY_FILTER,
    pageLanguages: ['en-US', 'pl-PL', 'en-US'],
    pageNames: ['Front', 'Back', 'Extra'],
    activePageCount: 3,
  };

  // setVisiblePageCount: decrease should NOT truncate arrays
  const decreased = setVisiblePageCountAction([group], 'g1', 2)[0];
  assertEqual(decreased.pageNames.length, 3, 'Decreasing visible pages must not truncate pageNames');
  assertEqual(decreased.pageLanguages.length, 3, 'Decreasing visible pages must not truncate pageLanguages');
  assertEqual(decreased.pageNames[2], 'Extra', 'Hidden page names must be preserved');
  assertEqual(decreased.activePageCount, 2, 'activePageCount should reflect new visible count');

  // setVisiblePageCount: increase should recover hidden pages
  const increased = setVisiblePageCountAction([decreased], 'g1', 3)[0];
  assertEqual(increased.activePageCount, 3, 'Increasing visible pages should restore count');
  assertEqual(increased.pageNames[2], 'Extra', 'Previously hidden page names should be recovered');

  // setVisiblePageCount: expand beyond existing
  const expanded = setVisiblePageCountAction([group], 'g1', 4)[0];
  assertEqual(expanded.pageNames[0], 'Front', 'Existing page names should stay in place');
  assertEqual(expanded.pageNames[1], 'Back', 'Existing page names should preserve order');
  assertEqual(expanded.pageLanguages[0], 'en-US', 'Existing page languages should stay in place');
  assertEqual(expanded.pageLanguages[1], 'pl-PL', 'Existing page languages should preserve order');
  assertEqual(expanded.pageNames[2], 'Extra', 'Existing extra page names should be kept');
  assertEqual(expanded.pageNames[3], 'Page 4', 'New page names should be generated for added pages');
  assertEqual(expanded.pageLanguages[3], 'en-US', 'New page languages should default to en-US');

  // deleteGroupAction: soft delete
  const delResult = deleteGroupAction([group], 'g1');
  assertEqual(delResult.length, 1, 'Soft-deleted group should still be in the array');
  assertOk(delResult[0].deletedAt != null, 'Soft-deleted group must have deletedAt set');

  // updateGroupAction: preserves tombstoned cards
  const now = Date.now();
  const canonGroup = {
    ...group,
    cards: [
      { id: 'c1', pages: ['a', 'b', 'c'], srsState: createNewSrsState() },
      { id: 'c2', pages: ['x', 'y', 'z'], srsState: createNewSrsState(), deletedAt: now },
    ],
  };
  const uiGroup = {
    ...group,
    cards: [
      { id: 'c1', pages: ['updated', 'b', 'c'], srsState: createNewSrsState() },
    ],
  };
  const updated = updateGroupAction([canonGroup], uiGroup)[0];
  assertEqual(updated.cards.length, 2, 'updateGroup should preserve tombstoned cards from canon');
  assertOk(updated.cards.some((c) => c.id === 'c2'), 'Tombstoned card should be in result');
  assertEqual(
    updated.cards.find((c) => c.id === 'c1')!.pages[0],
    'updated',
    'Live card updates should be preserved',
  );

  // archiveGroupAction: archivedAt + updatedAt set
  const archived = archiveGroupAction([group], 'g1');
  assertEqual(archived.length, 1, 'Archived group should still be in the array');
  assertOk(archived[0].archivedAt != null && archived[0].archivedAt > 0, 'Archived group must have archivedAt set');
  assertOk(archived[0].updatedAt != null && archived[0].updatedAt > 0, 'Archiving must bump updatedAt');
  assertEqual(archived[0].deletedAt ?? 0, 0, 'Archived group must NOT have deletedAt');

  // restoreGroupAction: clears archivedAt, bumps updatedAt
  const restored = restoreGroupAction(archived, 'g1');
  assertOk(!restored[0].archivedAt, 'Restored group must have archivedAt cleared');
  assertOk(restored[0].updatedAt != null && restored[0].updatedAt > 0, 'Restoring must bump updatedAt');

  // purgeExpiredArchivesAction: 13 days — no purge
  {
    const now = Date.now();
    const recentGroup = { ...group, archivedAt: now - 13 * 24 * 60 * 60 * 1000 };
    const purgedRecent = purgeExpiredArchivesAction([recentGroup], now, 14 * 24 * 60 * 60 * 1000);
    assertOk(!purgedRecent.changed, 'Purge must not fire before retention period');
    assertOk(!purgedRecent.groups[0].deletedAt, 'Recent archive must not get deletedAt');

    // purgeExpiredArchivesAction: 14 days — purge
    const oldGroup = { ...group, archivedAt: now - 14 * 24 * 60 * 60 * 1000 };
    const purgedOld = purgeExpiredArchivesAction([oldGroup], now, 14 * 24 * 60 * 60 * 1000);
    assertOk(purgedOld.changed, 'Purge must fire after retention period');
    assertOk(purgedOld.groups[0].deletedAt != null && purgedOld.groups[0].deletedAt > 0, 'Expired archive must get deletedAt');

    // purgeExpiredArchivesAction: already deleted — skip
    const alreadyDeleted = { ...group, archivedAt: now - 20 * 24 * 60 * 60 * 1000, deletedAt: now };
    const purgedDeleted = purgeExpiredArchivesAction([alreadyDeleted], now, 14 * 24 * 60 * 60 * 1000);
    assertOk(!purgedDeleted.changed, 'Purge must skip already-deleted groups');
    assertOk(purgedDeleted.groups[0].deletedAt === now, 'Already-deleted must not change deletedAt');

    // purgeExpiredArchivesAction: no archivedAt — skip
    const purgedNone = purgeExpiredArchivesAction([group], now, 14 * 24 * 60 * 60 * 1000);
    assertOk(!purgedNone.changed, 'Purge must skip groups without archivedAt');
  }

  console.log('Group actions tests passed');
}

import { createNewSrsState } from '../../../srs/srsEngine';
import {
  addFlashcardsBulkAction,
  deleteFlashcardAction,
  reviewFlashcardAction,
  updateFlashcardAction,
} from '../cardActions';
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
  console.log('\n--- Running Card Actions Tests ---');

  const originalCard = {
    id: 'c1',
    pages: ['front', 'back'],
    srsState: createNewSrsState(),
    contentUpdatedAt: 0,
    srsUpdatedAt: 0,
  };
  const originalGroup = {
    id: 'g1',
    name: 'Deck',
    cards: [originalCard],
    activeModeId: 'classic',
    studyFilter: DEFAULT_STUDY_FILTER,
    pageLanguages: ['en-US', 'pl-PL'],
    pageNames: ['Front', 'Back'],
    activePageCount: 2,
  };
  const groups = [originalGroup];

  // updateFlashcardAction: sets contentUpdatedAt, preserves srsUpdatedAt
  const updatedCard = {
    ...originalCard,
    pages: ['updated front', 'updated back'],
  };
  const updatedGroups = updateFlashcardAction(groups, 'g1', updatedCard);
  assertEqual(originalGroup.cards[0].pages[0], 'front', 'Update action must not mutate the source card pages');
  assertEqual(updatedGroups[0].cards[0].pages[0], 'updated front', 'Update action should replace the target card');
  assertOk(
    (updatedGroups[0].cards[0].contentUpdatedAt ?? 0) > 0,
    'updateFlashcardAction should set contentUpdatedAt',
  );
  assertEqual(
    updatedGroups[0].cards[0].srsUpdatedAt ?? 0,
    0,
    'updateFlashcardAction should NOT change srsUpdatedAt',
  );

  // reviewFlashcardAction: sets srsUpdatedAt, preserves contentUpdatedAt
  const reviewedCard = {
    ...originalCard,
    srsState: { ...originalCard.srsState, repetitions: 1 },
    contentUpdatedAt: 1000,
    srsUpdatedAt: 0,
  };
  const reviewedGroups = reviewFlashcardAction(groups, 'g1', reviewedCard);
  assertOk(
    (reviewedGroups[0].cards[0].srsUpdatedAt ?? 0) > 0,
    'reviewFlashcardAction should set srsUpdatedAt',
  );
  assertEqual(
    reviewedGroups[0].cards[0].contentUpdatedAt ?? 0,
    1000,
    'reviewFlashcardAction should preserve contentUpdatedAt',
  );

  // deleteFlashcardAction: soft delete
  const deletedGroups = deleteFlashcardAction(groups, 'g1', 'c1');
  assertEqual(deletedGroups[0].cards.length, 1, 'Soft-deleted card should remain in the array');
  assertOk(
    deletedGroups[0].cards[0].deletedAt != null,
    'Soft-deleted card must have deletedAt set',
  );

  // addFlashcardsBulkAction: timestamps
  const bulkGroups = addFlashcardsBulkAction(groups, 'g1', [
    { id: 'c2', pages: ['one', 'two'], srsState: createNewSrsState() },
  ]);
  assertEqual(originalGroup.cards.length, 1, 'Bulk add must not mutate the source cards array');
  assertEqual(bulkGroups[0].cards.length, 2, 'Bulk add should append the new cards');
  assertOk(
    (bulkGroups[0].cards[1].contentUpdatedAt ?? 0) > 0,
    'Bulk-added cards should have contentUpdatedAt set',
  );
  assertEqual(
    bulkGroups[0].cards[1].srsUpdatedAt ?? 1,
    0,
    'Bulk-added cards should have srsUpdatedAt = 0',
  );

  console.log('Card actions tests passed');
}

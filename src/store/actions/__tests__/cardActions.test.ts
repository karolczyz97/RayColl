import { createNewSrsState } from '../../../srs/srsEngine';
import { addFlashcardsBulkAction, updateFlashcardAction } from '../cardActions';

function assertEqual<T>(actual: T, expected: T, message: string) {
  if (actual !== expected) {
    throw new Error(`${message}: expected ${expected}, got ${actual}`);
  }
}

export async function runTests() {
  console.log('\n--- Running Card Actions Tests ---');

  const originalCard = {
    id: 'c1',
    pages: ['front', 'back'],
    srsState: createNewSrsState(),
  };
  const originalGroup = {
    id: 'g1',
    name: 'Deck',
    cards: [originalCard],
    activeModeId: 'classic',
    pageLanguages: ['en-US', 'pl-PL'],
    pageNames: ['Front', 'Back'],
    activePageCount: 2,
  };
  const groups = [originalGroup];
  const updatedCard = {
    ...originalCard,
    pages: ['updated front', 'updated back'],
  };

  const updatedGroups = updateFlashcardAction(groups, 'g1', updatedCard);
  assertEqual(originalGroup.cards[0].pages[0], 'front', 'Update action must not mutate the source card pages');
  assertEqual(updatedGroups[0].cards[0].pages[0], 'updated front', 'Update action should replace the target card');

  const bulkGroups = addFlashcardsBulkAction(groups, 'g1', [
    { id: 'c2', pages: ['one', 'two'], srsState: createNewSrsState() },
  ]);
  assertEqual(originalGroup.cards.length, 1, 'Bulk add must not mutate the source cards array');
  assertEqual(bulkGroups[0].cards.length, 2, 'Bulk add should append the new cards');

  console.log('Card actions tests passed');
}

import { reorderDeckPages } from '../deckPageReorder';
import { DEFAULT_STUDY_FILTER } from '@/store/storeDataNormalization';
import { createNewSrsState } from '@/srs/srsEngine';
import type { FlashcardGroup } from '@/types/models';

function makeGroup(): FlashcardGroup {
  return {
    id: 'deck-1',
    name: 'Deck',
    activeModeId: 'classic',
    activePageCount: 3,
    studyFilter: DEFAULT_STUDY_FILTER,
    pageNames: ['Front', 'Back', 'Example', 'Note', 'Hint'],
    pageLanguages: ['en-US', 'pl-PL', 'es-ES', 'de-DE', 'it-IT'],
    cards: [
      {
        id: 'card-1',
        pages: ['front', 'back', 'example', 'note', 'hint'],
        srsState: createNewSrsState(),
      },
    ],
  };
}

describe('reorderDeckPages', () => {
  it.each([
    ['active to active', 0, 1, ['Back', 'Front', 'Example', 'Note', 'Hint'], ['back', 'front', 'example', 'note', 'hint']],
    ['active to hidden', 2, 3, ['Front', 'Back', 'Note', 'Example', 'Hint'], ['front', 'back', 'note', 'example', 'hint']],
    ['hidden to active', 3, 2, ['Front', 'Back', 'Note', 'Example', 'Hint'], ['front', 'back', 'note', 'example', 'hint']],
    ['hidden to hidden', 3, 4, ['Front', 'Back', 'Example', 'Hint', 'Note'], ['front', 'back', 'example', 'hint', 'note']],
  ])('%s swaps metadata and card pages together', (_label, index, target, expectedNames, expectedPages) => {
    const reordered = reorderDeckPages(makeGroup(), index, target);

    expect(reordered.pageNames).toEqual(expectedNames);
    expect(reordered.cards[0].pages).toEqual(expectedPages);
  });

  it('keeps page languages aligned with names', () => {
    const reordered = reorderDeckPages(makeGroup(), 2, 3);

    expect(reordered.pageLanguages).toEqual(['en-US', 'pl-PL', 'de-DE', 'es-ES', 'it-IT']);
  });
});

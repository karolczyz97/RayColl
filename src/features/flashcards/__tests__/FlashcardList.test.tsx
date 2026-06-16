import type { Flashcard } from '@/types/models';
import { pairFlashcardRows } from '../FlashcardList';

function makeCard(id: string): Flashcard {
  return {
    id,
    pages: [id],
    srsState: {
      difficulty: 1,
      stability: 1,
      repetitions: 0,
      state: 0,
      lastReviewTimestamp: 0,
      nextReviewTimestamp: 0,
    },
    contentUpdatedAt: 0,
    srsUpdatedAt: 0,
  };
}

describe('pairFlashcardRows', () => {
  it('keeps cards paired by row', () => {
    const cards = [makeCard('a'), makeCard('b'), makeCard('c'), makeCard('d')];

    expect(pairFlashcardRows(cards)).toEqual([
      { id: 'row-a', rowIndex: 0, cards: [cards[0], cards[1]] },
      { id: 'row-c', rowIndex: 1, cards: [cards[2], cards[3]] },
    ]);
  });

  it('keeps the final odd card in a half-width row', () => {
    const cards = [makeCard('a'), makeCard('b'), makeCard('c')];

    expect(pairFlashcardRows(cards)).toEqual([
      { id: 'row-a', rowIndex: 0, cards: [cards[0], cards[1]] },
      { id: 'row-c', rowIndex: 1, cards: [cards[2], undefined] },
    ]);
  });
});

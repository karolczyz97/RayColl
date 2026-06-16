import { describe, expect, it, jest } from '@jest/globals';
import { CARD_ORDERS } from '@/constants/cardOrder';
import type { Flashcard } from '@/types/models';
import { getDueCards } from '@/store/selectors/cardSelectors';

function makeCard(id: string, difficulty: number, nextReviewTimestamp = 0): Flashcard {
  return {
    id,
    pages: [id],
    contentUpdatedAt: 0,
    srsUpdatedAt: 0,
    srsState: {
      difficulty,
      stability: 1,
      repetitions: 1,
      state: 2,
      lastReviewTimestamp: 0,
      nextReviewTimestamp,
    },
  };
}

describe('getDueCards ordering', () => {
  const cards = [
    makeCard('easy', 2, 20),
    makeCard('hardest', 9, 30),
    makeCard('hard-oldest', 9, 10),
  ];

  it('keeps deck order by default', () => {
    expect(getDueCards(cards, 'all').map((card) => card.id)).toEqual([
      'easy',
      'hardest',
      'hard-oldest',
    ]);
  });

  it('orders hardest cards first and keeps a stable tie-breaker', () => {
    expect(getDueCards(cards, 'all', CARD_ORDERS.hardest).map((card) => card.id)).toEqual([
      'hard-oldest',
      'hardest',
      'easy',
    ]);
  });

  it('shuffles only when random order is selected', () => {
    const randomSpy = jest.spyOn(Math, 'random').mockReturnValueOnce(0).mockReturnValueOnce(0);
    expect(getDueCards(cards, 'all', CARD_ORDERS.random).map((card) => card.id)).toEqual([
      'hardest',
      'hard-oldest',
      'easy',
    ]);
    randomSpy.mockRestore();
  });
});

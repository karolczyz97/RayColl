import { describe, expect, it } from '@jest/globals';
import type { Flashcard, FlashcardGroup } from '@/types/models';
import { groupHasHiddenPages, getAllPages } from '@/store/selectors/pages';

function makeMockGroup(activePageCount: number, pageNames: string[]): FlashcardGroup {
  return {
    id: 'g1',
    name: 'Test Group',
    cards: [],
    activeModeId: 'm1',
    studyFilter: 'all',
    cardOrder: 'sequential',
    pageLanguages: Array(pageNames.length).fill('en'),
    pageNames,
    activePageCount,
    updatedAt: 0,
  };
}

function makeMockCard(pages: string[]): Flashcard {
  return {
    id: 'c1',
    pages,
    contentUpdatedAt: 0,
    srsUpdatedAt: 0,
    srsState: {
      difficulty: 0,
      stability: 0,
      repetitions: 0,
      state: 0,
      lastReviewTimestamp: 0,
      nextReviewTimestamp: 0,
    },
  };
}

describe('pages selectors', () => {
  describe('groupHasHiddenPages', () => {
    it('returns true if the group has more page names than active pages', () => {
      const group = makeMockGroup(2, ['Front', 'Back', 'Extra']);
      expect(groupHasHiddenPages(group)).toBe(true);
    });

    it('returns false if active page count equals total page names', () => {
      const group = makeMockGroup(2, ['Front', 'Back']);
      expect(groupHasHiddenPages(group)).toBe(false);
    });
  });

  describe('getAllPages', () => {
    it('pads pages array up to pageNames.length if card has fewer pages', () => {
      const group = makeMockGroup(3, ['Front', 'Back', 'Extra']);
      const card = makeMockCard(['front_val', 'back_val']);
      expect(getAllPages(card, group)).toEqual(['front_val', 'back_val', '']);
    });

    it('does not truncate if card has more pages than group pageNames.length', () => {
      const group = makeMockGroup(2, ['Front', 'Back']);
      const card = makeMockCard(['front_val', 'back_val', 'extra_val']);
      expect(getAllPages(card, group)).toEqual(['front_val', 'back_val', 'extra_val']);
    });
  });
});

import { describe, it, expect } from '@jest/globals';

import { createNewSrsState } from '../../../srs/srsEngine';
import {
  addFlashcardsBulkAction,
  deleteFlashcardAction,
  reviewCardAction,
  updateFlashcardAction,
} from '../cardActions';
import { DEFAULT_STUDY_FILTER } from '../../storeDataNormalization';

describe('cardActions', () => {
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

  describe('updateFlashcardAction', () => {
    it('sets contentUpdatedAt and preserves srsUpdatedAt', () => {
      const updatedCard = { ...originalCard, pages: ['updated front', 'updated back'] };
      const updatedGroups = updateFlashcardAction(groups, 'g1', updatedCard);

      expect(originalGroup.cards[0].pages[0]).toBe('front');
      expect(updatedGroups[0].cards[0].pages[0]).toBe('updated front');
      expect(updatedGroups[0].cards[0].contentUpdatedAt ?? 0).toBeGreaterThan(0);
      expect(updatedGroups[0].cards[0].srsUpdatedAt ?? 0).toBe(0);
    });
  });

  describe('deleteFlashcardAction', () => {
    it('soft-deletes by setting deletedAt', () => {
      const deletedGroups = deleteFlashcardAction(groups, 'g1', 'c1');
      expect(deletedGroups[0].cards.length).toBe(1);
      expect(deletedGroups[0].cards[0].deletedAt).toBeTruthy();
    });
  });

  describe('addFlashcardsBulkAction', () => {
    it('appends cards with contentUpdatedAt set', () => {
      const bulkGroups = addFlashcardsBulkAction(groups, 'g1', [
        { id: 'c2', pages: ['one', 'two'], srsState: createNewSrsState() },
      ]);

      expect(originalGroup.cards.length).toBe(1);
      expect(bulkGroups[0].cards.length).toBe(2);
      expect(bulkGroups[0].cards[1].contentUpdatedAt ?? 0).toBeGreaterThan(0);
      expect(bulkGroups[0].cards[1].srsUpdatedAt ?? 0).toBe(0);
    });
  });

  describe('reviewCardAction', () => {
    it('recomputes srsState from the stored card, stamps srsUpdatedAt, bumps heatmap once', () => {
      const heatmapBefore: Record<string, number> = {};
      const reviewResult = reviewCardAction(groups, 'g1', 'c1', 3, heatmapBefore);

      const reviewed = reviewResult.nextGroups[0].cards[0];
      expect(reviewed.srsUpdatedAt ?? 0).toBeGreaterThan(0);
      // FSRS ran on the stored card (repetitions 0 -> 1), not on a passed-in snapshot.
      expect(reviewed.srsState.repetitions).toBe(1);
      expect(reviewResult.nextHeatmap[reviewResult.todayKey]).toBe(1);
      expect(Object.keys(heatmapBefore).length).toBe(0);
      expect(originalCard.srsState.repetitions).toBe(0); // source not mutated
    });

    it('preserves live pages/contentUpdatedAt/deletedAt — a stale snapshot cannot clobber them', () => {
      // Card as currently stored: content edited and soft-deleted *after* a session
      // snapshot would have been taken. Review must touch only the SRS fields.
      const liveCard = {
        id: 'c1',
        pages: ['edited front', 'edited back'],
        srsState: { ...createNewSrsState(), repetitions: 2 },
        contentUpdatedAt: 12345,
        srsUpdatedAt: 0,
        deletedAt: 67890,
      };
      const liveGroups = [{ ...originalGroup, cards: [liveCard] }];

      const reviewed = reviewCardAction(liveGroups, 'g1', 'c1', 4, {}).nextGroups[0].cards[0];

      expect(reviewed.pages).toEqual(['edited front', 'edited back']);
      expect(reviewed.contentUpdatedAt).toBe(12345);
      expect(reviewed.deletedAt).toBe(67890);
      expect(reviewed.srsState.repetitions).toBe(3); // 2 -> 3
      expect(reviewed.srsUpdatedAt ?? 0).toBeGreaterThan(0);
    });
  });
});

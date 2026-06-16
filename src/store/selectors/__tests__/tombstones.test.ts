import { describe, it, expect } from '@jest/globals';

import { selectLiveStudyModes, selectActiveGroups, selectArchivedGroups, isArchived } from '@/store/selectors/liveSelectors';
import { DEFAULT_STUDY_FILTER } from '@/store/storeDataNormalization';
import { createNewSrsState } from '@/srs/srsEngine';

describe('tombstones', () => {
  const now = Date.now();
  const liveCard = { id: 'c1', pages: ['a', 'b'], srsState: createNewSrsState(), contentUpdatedAt: 0, srsUpdatedAt: 0 };
  const tombstoneCard = { id: 'c2', pages: ['x', 'y'], srsState: createNewSrsState(), contentUpdatedAt: 0, srsUpdatedAt: 0, deletedAt: now };

  const group = {
    id: 'g1',
    name: 'Test',
    cards: [liveCard, tombstoneCard],
    activeModeId: 'classic',
    studyFilter: DEFAULT_STUDY_FILTER,
    cardOrder: 'sequential' as const,
    pageLanguages: ['en-US', 'en-US'],
    pageNames: ['Front', 'Back'],
    activePageCount: 2,
    updatedAt: 0,
  };

  const tombstoneGroup = {
    id: 'g2',
    name: 'Deleted',
    cards: [],
    activeModeId: 'classic',
    studyFilter: DEFAULT_STUDY_FILTER,
    cardOrder: 'sequential' as const,
    pageLanguages: ['en-US', 'en-US'],
    pageNames: ['Front', 'Back'],
    activePageCount: 2,
    updatedAt: 0,
    deletedAt: now,
  };

  describe('selectLiveStudyModes', () => {
    const liveMode = { id: 'm1', name: 'Live', steps: [], isBuiltIn: false, updatedAt: 0 };
    const tombstoneMode = { id: 'm2', name: 'Dead', steps: [], isBuiltIn: false, updatedAt: 0, deletedAt: now };

    it('filters out tombstone modes', () => {
      const result = selectLiveStudyModes([liveMode, tombstoneMode]);
      expect(result).toHaveLength(1);
      expect(result[0].id).toBe('m1');
    });

    it('returns empty when all modes are tombstoned', () => {
      expect(selectLiveStudyModes([tombstoneMode])).toHaveLength(0);
    });
  });

  describe('isArchived', () => {
    it('returns false for group without archivedAt', () => {
      expect(isArchived(group)).toBe(false);
    });

    it('returns true for group with archivedAt', () => {
      expect(isArchived({ ...group, archivedAt: now })).toBe(true);
    });

    it('returns false for archivedAt=0', () => {
      expect(isArchived({ ...group, archivedAt: 0 as unknown as number | null | undefined })).toBe(false);
    });

    it('returns false for archivedAt=null', () => {
      expect(isArchived({ ...group, archivedAt: null as unknown as number | null })).toBe(false);
    });
  });

  describe('selectActiveGroups', () => {
    it('returns only fully active groups', () => {
      const archivedGroup = { ...group, archivedAt: now };
      const permGroup = { ...group, id: 'g3', deletedAt: now };
      const result = selectActiveGroups([group, archivedGroup, tombstoneGroup, permGroup]);
      expect(result).toHaveLength(1);
      expect(result[0].id).toBe('g1');
    });

    it('preserves the group reference when no card is tombstoned (memo identity)', () => {
      const cleanGroup = { ...group, cards: [liveCard] };
      const result = selectActiveGroups([cleanGroup]);
      expect(result[0]).toBe(cleanGroup);
    });

    it('clones the group only when a tombstoned card is filtered out', () => {
      const result = selectActiveGroups([group]);
      expect(result[0]).not.toBe(group);
      expect(result[0].cards).toEqual([liveCard]);
      // Input is left untouched.
      expect(group.cards).toHaveLength(2);
    });
  });

  describe('selectArchivedGroups', () => {
    it('returns only archived (not deleted) groups', () => {
      const archivedGroup = { ...group, archivedAt: now };
      const archivedWithDeleted = { ...group, id: 'g4', archivedAt: now, deletedAt: now };
      const result = selectArchivedGroups([archivedGroup, group, tombstoneGroup, archivedWithDeleted]);
      expect(result).toHaveLength(1);
      expect(result[0].id).toBe('g1');
    });

    it('sorts newest archivedAt first', () => {
      const oldArchived = { ...group, id: 'g5', archivedAt: now - 1000 };
      const newerArchived = { ...group, id: 'g6', archivedAt: now };
      const result = selectArchivedGroups([oldArchived, newerArchived]);
      expect(result[0].id).toBe('g6');
    });
  });
});

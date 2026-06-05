import { describe, it, expect, jest } from '@jest/globals';
import { shouldTriggerMigration, getGuestHasData } from '../../useStoreBootstrap';
import type { FlashcardGroup, StudyMode } from '../../../types/models';

jest.mock('@/services/firebase', () => ({
  onAuthChange: jest.fn(),
}));

jest.mock('../../persistence/firebasePersistence', () => ({
  loadCloudData: jest.fn(),
}));

jest.mock('../../persistence/localPersistence', () => ({
  getSeedVersion: jest.fn(),
  loadLocalData: jest.fn(),
  setSeedVersion: jest.fn(),
}));

function makeGuestData(groups: FlashcardGroup[]): { groups: FlashcardGroup[]; studyModes: StudyMode[]; activityHeatmap: Record<string, number> } {
  return { groups, studyModes: [], activityHeatmap: {} };
}

describe('migrationLogic', () => {
  describe('shouldTriggerMigration', () => {
    it('triggers when no user-local cache and guest has data', () => {
      expect(shouldTriggerMigration(false, true)).toBe(true);
    });

    it('does not trigger when user-local cache exists', () => {
      expect(shouldTriggerMigration(true, true)).toBe(false);
    });

    it('does not trigger when no cache and no guest data', () => {
      expect(shouldTriggerMigration(false, false)).toBe(false);
    });

    it('does not trigger when has cache and no guest data', () => {
      expect(shouldTriggerMigration(true, false)).toBe(false);
    });
  });

  describe('getGuestHasData', () => {
    it('returns false for null guest data', () => {
      expect(getGuestHasData(null)).toBe(false);
    });

    it('returns false for empty guest groups', () => {
      expect(getGuestHasData(makeGuestData([]))).toBe(false);
    });

    it('returns true when guest has at least one group', () => {
      expect(
        getGuestHasData(makeGuestData([{ id: 'g1', name: 'Test', cards: [], activeModeId: '', studyFilter: 'all', pageLanguages: [], pageNames: [], activePageCount: 0 }])),
      ).toBe(true);
    });
  });
});

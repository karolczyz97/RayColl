import { describe, it, expect } from '@jest/globals';

import { createNewSrsState } from '../../../srs/srsEngine';
import {
  mergeCards,
  mergeGroups,
  mergeStudyModes,
  mergeUserData,
  getLatestEdit,
  mergeSrsStateNeverRegress,
  mergeActivityHeatmap,
} from '../merge';
import { DEFAULT_STUDY_FILTER } from '../../storeDataNormalization';

describe('merge', () => {
  const now = Date.now();

  describe('mergeCards', () => {
    it('content last-write-wins, SRS never-regress in direct mergeCards', () => {
      const localCard = {
        id: 'c1',
        pages: ['local', 'card'],
        srsState: { ...createNewSrsState(), repetitions: 4 },
        contentUpdatedAt: now - 2000,
        srsUpdatedAt: now,
      };
      const cloudCard = {
        id: 'c1',
        pages: ['cloud', 'card'],
        srsState: { ...createNewSrsState(), repetitions: 1 },
        contentUpdatedAt: now - 1000,
        srsUpdatedAt: 0,
      };
      const result = mergeCards([localCard], [cloudCard]);
      expect(result[0].pages[0]).toBe('cloud');
      expect(result[0].srsState.repetitions).toBe(4);
    });
  });

  describe('mergeUserData', () => {
    it('content last-write-wins, SRS never-regress, max activity count', () => {
      const localCard = {
        id: 'c1',
        pages: ['local', 'card'],
        srsState: { ...createNewSrsState(), repetitions: 4 },
        contentUpdatedAt: now - 2000,
        srsUpdatedAt: now,
      };
      const cloudCard = {
        id: 'c1',
        pages: ['cloud', 'card'],
        srsState: { ...createNewSrsState(), repetitions: 1 },
        contentUpdatedAt: now - 1000,
        srsUpdatedAt: 0,
      };

      const merged = mergeUserData(
        {
          groups: [
            {
              id: 'g1',
              name: 'Local deck',
              cards: [localCard],
              activeModeId: 'custom-mode',
              studyFilter: 'new+review',
              pageLanguages: ['en-US', 'pl-PL'],
              pageNames: ['Front', 'Back'],
              activePageCount: 2,
            },
          ],
          studyModes: [{ id: 'custom-mode', name: 'Custom', steps: [], isBuiltIn: false }],
          activityHeatmap: { '2026-05-27': 3 },
        },
        {
          groups: [
            {
              id: 'g1',
              name: 'Cloud deck',
              cards: [cloudCard],
              activeModeId: '',
              studyFilter: DEFAULT_STUDY_FILTER,
              pageLanguages: ['en-US', 'pl-PL'],
              pageNames: ['Front', 'Back'],
              activePageCount: 2,
            },
          ],
          studyModes: [
            { id: 'classic', name: 'Classic', steps: [], isBuiltIn: true, builtInSourceId: 'classic' },
          ],
          activityHeatmap: { '2026-05-27': 1 },
        },
      );

      expect(merged.studyModes.some((mode) => mode.id === 'custom-mode')).toBe(true);
      expect(merged.groups[0].cards[0].pages[0]).toBe('cloud');
      expect(merged.groups[0].cards[0].srsState.repetitions).toBe(4);
      expect(merged.activityHeatmap['2026-05-27']).toBe(3);
    });

    it('legacy timestamps (0) yield to cloud on tie', () => {
      const legacyCard = { id: 'c2', pages: ['legacy', 'card'], srsState: createNewSrsState() };
      const cloudCard2 = {
        id: 'c2',
        pages: ['cloud2', 'card'],
        srsState: { ...createNewSrsState(), repetitions: 2 },
        contentUpdatedAt: 0,
        srsUpdatedAt: 0,
      };

      const merged = mergeUserData(
        {
          groups: [
            {
              id: 'g2',
              name: 'Legacy Deck',
              cards: [legacyCard],
              activeModeId: 'classic',
              studyFilter: DEFAULT_STUDY_FILTER,
              pageLanguages: ['en-US', 'pl-PL'],
              pageNames: ['Front', 'Back'],
              activePageCount: 2,
            },
          ],
          studyModes: [],
          activityHeatmap: {},
        },
        {
          groups: [
            {
              id: 'g2',
              name: 'Cloud Wins',
              cards: [cloudCard2],
              activeModeId: 'classic',
              studyFilter: DEFAULT_STUDY_FILTER,
              pageLanguages: ['en-US', 'pl-PL'],
              pageNames: ['Front', 'Back'],
              activePageCount: 2,
            },
          ],
          studyModes: [],
          activityHeatmap: {},
        },
      );

      expect(merged.groups[0].name).toBe('Cloud Wins');
      expect(merged.groups[0].cards[0].pages[0]).toBe('cloud2');
    });

    it('tombstone propagation: deleted card stays deleted', () => {
      const deletedCard = {
        id: 'c3',
        pages: ['gone', 'card'],
        srsState: createNewSrsState(),
        contentUpdatedAt: now,
        srsUpdatedAt: 0,
        deletedAt: now,
      };
      const liveCard = {
        id: 'c3',
        pages: ['alive', 'card'],
        srsState: { ...createNewSrsState(), repetitions: 1 },
        contentUpdatedAt: now - 5000,
        srsUpdatedAt: 0,
      };

      const merged = mergeUserData(
        {
          groups: [
            {
              id: 'g3',
              name: 'Tombstone local',
              cards: [deletedCard],
              activeModeId: 'classic',
              studyFilter: DEFAULT_STUDY_FILTER,
              pageLanguages: ['en-US', 'pl-PL'],
              pageNames: ['Front', 'Back'],
              activePageCount: 2,
            },
          ],
          studyModes: [],
          activityHeatmap: {},
        },
        {
          groups: [
            {
              id: 'g3',
              name: 'Tombstone cloud',
              cards: [liveCard],
              activeModeId: 'classic',
              studyFilter: DEFAULT_STUDY_FILTER,
              pageLanguages: ['en-US', 'pl-PL'],
              pageNames: ['Front', 'Back'],
              activePageCount: 2,
            },
          ],
          studyModes: [],
          activityHeatmap: {},
        },
      );

      expect(merged.groups[0].cards[0].deletedAt).toBeTruthy();
    });

    it('edit newer than deletion revives card', () => {
      const revivedCard = {
        id: 'c4',
        pages: ['revived', 'card'],
        srsState: createNewSrsState(),
        contentUpdatedAt: now,
        srsUpdatedAt: 0,
        deletedAt: now - 10000,
      };

      const merged = mergeUserData(
        {
          groups: [
            {
              id: 'g4',
              name: 'Revive test',
              cards: [revivedCard],
              activeModeId: 'classic',
              studyFilter: DEFAULT_STUDY_FILTER,
              pageLanguages: ['en-US', 'pl-PL'],
              pageNames: ['Front', 'Back'],
              activePageCount: 2,
            },
          ],
          studyModes: [],
          activityHeatmap: {},
        },
        { groups: [], studyModes: [], activityHeatmap: {} },
      );

      expect(merged.groups[0].cards[0].deletedAt).toBeFalsy();
    });
  });

  describe('mergeStudyModes', () => {
    it('combines both local and cloud modes', () => {
      const localModes = [
        { id: 'custom', name: 'Custom Local', steps: [{ type: 'rate' as const }], isBuiltIn: false, updatedAt: now },
      ];
      const cloudModes = [
        { id: 'classic', name: 'Classic Cloud', steps: [], isBuiltIn: true, builtInSourceId: 'classic', updatedAt: now - 5000 },
      ];
      const merged = mergeStudyModes(localModes, cloudModes);
      expect(merged.length).toBe(2);
      expect(merged.some((m) => m.id === 'custom')).toBe(true);
      expect(merged.some((m) => m.id === 'classic')).toBe(true);
    });

    it('deduplicates by id, newer updatedAt wins', () => {
      const localMode = { id: 'conflict', name: 'Local Wins', steps: [{ type: 'rate' as const }], isBuiltIn: false, updatedAt: now };
      const cloudMode = { id: 'conflict', name: 'Cloud Older', steps: [], isBuiltIn: false, updatedAt: now - 5000 };
      const merged = mergeStudyModes([localMode], [cloudMode]);
      expect(merged.length).toBe(1);
      expect(merged[0].name).toBe('Local Wins');
    });

    it('tombstone propagates to merged result', () => {
      const localTomb = { id: 'tomb', name: 'Deleted', steps: [], isBuiltIn: false, updatedAt: now, deletedAt: now };
      const cloudLive = { id: 'tomb', name: 'Alive', steps: [], isBuiltIn: false, updatedAt: now - 5000 };
      const merged = mergeStudyModes([localTomb], [cloudLive]);
      expect(merged.length).toBe(1);
      expect(merged[0].deletedAt).toBeTruthy();
    });

    it('built-in mode ignores tombstone on local side', () => {
      const localTomb = {
        id: 'classic',
        name: 'Deleted classic',
        steps: [],
        isBuiltIn: true,
        builtInSourceId: 'classic',
        updatedAt: now,
        deletedAt: now,
      };
      const cloudLive = {
        id: 'classic',
        name: 'Cloud classic',
        steps: [{ type: 'rate' as const }],
        isBuiltIn: true,
        builtInSourceId: 'classic',
        updatedAt: now - 5000,
      };
      const merged = mergeStudyModes([localTomb], [cloudLive]);
      expect(merged.length).toBe(1);
      expect(merged[0].deletedAt).toBeFalsy();
      expect(merged[0].name).toBe('Deleted classic');
    });

    it('built-in mode ignores tombstone on cloud side', () => {
      const localLive = {
        id: 'classic',
        name: 'Local classic',
        steps: [],
        isBuiltIn: true,
        builtInSourceId: 'classic',
        updatedAt: now - 1000,
      };
      const cloudTomb = {
        id: 'classic',
        name: 'Deleted cloud classic',
        steps: [{ type: 'rate' as const }],
        isBuiltIn: true,
        builtInSourceId: 'classic',
        updatedAt: now,
        deletedAt: now,
      };
      const merged = mergeStudyModes([localLive], [cloudTomb]);
      expect(merged.length).toBe(1);
      expect(merged[0].deletedAt).toBeFalsy();
      expect(merged[0].name).toBe('Deleted cloud classic');
    });
  });

  describe('mergeGroups', () => {
    it('tombstone propagates to merged result', () => {
      const localTomb = {
        id: 'gt',
        name: 'Local Deleted',
        cards: [],
        activeModeId: 'classic',
        studyFilter: DEFAULT_STUDY_FILTER,
        pageLanguages: ['en-US'],
        pageNames: ['Front'],
        activePageCount: 1,
        updatedAt: now,
        deletedAt: now,
      };
      const cloudLive = {
        id: 'gt',
        name: 'Cloud Alive',
        cards: [],
        activeModeId: 'classic',
        studyFilter: DEFAULT_STUDY_FILTER,
        pageLanguages: ['en-US'],
        pageNames: ['Front'],
        activePageCount: 1,
        updatedAt: now - 5000,
      };
      const merged = mergeGroups([localTomb], [cloudLive]);
      expect(merged.length).toBe(1);
      expect(merged[0].deletedAt).toBeTruthy();
    });
  });

  describe('getLatestEdit', () => {
    it('returns max of content and srs timestamps', () => {
      const card = { id: 'hlp1', pages: ['a', 'b'], srsState: createNewSrsState(), contentUpdatedAt: 1000, srsUpdatedAt: 2000 };
      expect(getLatestEdit(card)).toBe(2000);
    });

    it('returns 0 when both timestamps missing', () => {
      const card = { id: 'hlp2', pages: ['a'], srsState: createNewSrsState(), contentUpdatedAt: undefined, srsUpdatedAt: undefined };
      expect(getLatestEdit(card)).toBe(0);
    });
  });

  describe('mergeSrsStateNeverRegress', () => {
    it('higher local reps wins regardless of srsAt', () => {
      expect(mergeSrsStateNeverRegress(5, 100, 1, 200)).toBe('local');
    });

    it('higher cloud reps wins', () => {
      expect(mergeSrsStateNeverRegress(1, 100, 5, 200)).toBe('cloud');
    });

    it('tie on reps, local has higher srsAt wins', () => {
      expect(mergeSrsStateNeverRegress(3, 500, 3, 300)).toBe('local');
    });

    it('tie on reps, cloud has higher srsAt wins', () => {
      expect(mergeSrsStateNeverRegress(3, 300, 3, 500)).toBe('cloud');
    });

    it('full tie defaults to cloud', () => {
      expect(mergeSrsStateNeverRegress(3, 300, 3, 300)).toBe('cloud');
    });
  });

  describe('mergeActivityHeatmap', () => {
    it('keeps max count and preserves all dates', () => {
      const local = { '2026-05-27': 5, '2026-05-28': 2 };
      const cloud = { '2026-05-27': 3, '2026-05-29': 7 };
      const merged = mergeActivityHeatmap(local, cloud);
      expect(merged['2026-05-27']).toBe(5);
      expect(merged['2026-05-28']).toBe(2);
      expect(merged['2026-05-29']).toBe(7);
    });
  });
});

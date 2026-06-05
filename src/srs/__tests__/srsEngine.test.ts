import { describe, it, expect } from '@jest/globals';

import {
  createNewSrsState,
  calculateFsrs,
  matchSpeech,
  mapMatchToRating,
  getCardCategory,
} from '../srsEngine';
import {
  getVisiblePages,
  getVisiblePageNames,
} from '../../store/selectors/pages';
import { filterCards } from '../../store/selectors/cardSelectors';
import { DEFAULT_STUDY_FILTER, normalizeStoreData } from '../../store/storeDataNormalization';
import { validateBackupData } from '../../utils/backupValidation';
import type { Flashcard, FlashcardGroup } from '../../types/models';

describe('srsEngine', () => {
  describe('createNewSrsState', () => {
    it('sets difficulty=5, stability=0, repetitions=0, state=0', () => {
      const state = createNewSrsState();
      expect(state.difficulty).toBe(5);
      expect(state.stability).toBe(0);
      expect(state.repetitions).toBe(0);
      expect(state.state).toBe(0);
    });
  });

  describe('getCardCategory', () => {
    const state = createNewSrsState();

    it('returns "new" for state=0', () => {
      expect(getCardCategory(state)).toBe('new');
    });

    it('returns "learning" for state=1', () => {
      expect(getCardCategory({ ...state, state: 1 as const })).toBe('learning');
    });

    it('returns "learning" for state=3 (relearning)', () => {
      expect(getCardCategory({ ...state, state: 3 as const })).toBe('learning');
    });

    it('returns "review" for state=2 with repetitions=2', () => {
      expect(getCardCategory({ ...state, state: 2 as const, repetitions: 2 })).toBe('review');
    });

    it('returns "mastered" for state=2 with repetitions=3', () => {
      expect(getCardCategory({ ...state, state: 2 as const, repetitions: 3 })).toBe('mastered');
    });
  });

  describe('matchSpeech', () => {
    it('exact match case/punctuation insensitive', () => {
      expect(matchSpeech('hello world', 'Hello World!')).toBe(100);
    });

    it('partial match', () => {
      expect(matchSpeech('hello', 'hello world')).toBe(50);
    });

    it('empty match', () => {
      expect(matchSpeech('', '')).toBe(100);
    });
  });

  describe('mapMatchToRating', () => {
    it('90% -> 4 (Easy)', () => { expect(mapMatchToRating(90)).toBe(4); });
    it('70% -> 3 (Good)', () => { expect(mapMatchToRating(70)).toBe(3); });
    it('50% -> 2 (Hard)', () => { expect(mapMatchToRating(50)).toBe(2); });
    it('20% -> 1 (Again)', () => { expect(mapMatchToRating(20)).toBe(1); });
  });

  describe('calculateFsrs', () => {
    it('increments repetitions, increases stability, recalculates difficulty, changes state to Review', () => {
      const state = createNewSrsState();
      const result = calculateFsrs(state, 3);
      expect(result.repetitions).toBe(1);
      expect(result.stability).toBeGreaterThan(0);
      expect(result.difficulty).toBeLessThan(5);
      expect(result.state).toBe(2);
    });

    it('handles extreme values without infinity', () => {
      const dayMs = 24 * 60 * 60 * 1000;
      const extremeState = {
        difficulty: 100,
        stability: 1_000_000,
        repetitions: 999,
        state: 2 as const,
        lastReviewTimestamp: Date.now() - 730 * dayMs,
        nextReviewTimestamp: Date.now() - 365 * dayMs,
      };
      const result = calculateFsrs(extremeState, 4);
      expect(Number.isFinite(result.nextReviewTimestamp)).toBe(true);
      expect(result.difficulty).toBeLessThanOrEqual(10);
      expect(result.stability).toBeLessThanOrEqual(3650);
      expect(result.nextReviewTimestamp - result.lastReviewTimestamp).toBeLessThanOrEqual(3650 * dayMs + 1000);
    });
  });
});

describe('pageConfig', () => {
  const mockGroup: FlashcardGroup = {
    id: 'g1',
    name: 'Mock Deck',
    cards: [],
    activeModeId: 'classic',
    studyFilter: DEFAULT_STUDY_FILTER,
    pageNames: ['Front', 'Back', 'Example'],
    pageLanguages: ['en-US', 'pl-PL', 'en-US'],
    activePageCount: 2,
  };

  const mockCard: Flashcard = {
    id: 'c1',
    pages: ['hello', 'cześć', 'hello world'],
    srsState: createNewSrsState(),
  };

  describe('getVisiblePages', () => {
    it('slices card pages to activePageCount', () => {
      expect(getVisiblePages(mockCard, mockGroup)).toEqual(['hello', 'cześć']);
    });

    it('pads visible pages to activePageCount if card lacks slots', () => {
      const shortCard: Flashcard = { id: 'c2', pages: ['hello'], srsState: createNewSrsState() };
      expect(getVisiblePages(shortCard, mockGroup)).toEqual(['hello', '']);
    });
  });

  describe('getVisiblePageNames', () => {
    it('slices pageNames to activePageCount', () => {
      expect(getVisiblePageNames(mockGroup)).toEqual(['Front', 'Back']);
    });
  });

  describe('normalizeStoreData edge cases', () => {
    it('handles backup with NaN activePageCount and overflow pages', () => {
      const backup = {
        groups: [
          {
            id: 'g2',
            name: 'Unnormalized',
            cards: [],
            activeModeId: 'classic',
            studyFilter: DEFAULT_STUDY_FILTER,
            activePageCount: Number.NaN,
            pageNames: ['Front', 'Back', 'Extra', 'Overflow', 'Too much', 'Ignored'],
            pageLanguages: ['en-US'],
          },
        ],
        studyModes: [],
        activityHeatmap: {},
      };
      expect(() => validateBackupData(backup)).not.toThrow();
      const normalized = normalizeStoreData(backup);
      expect(normalized.groups[0].activePageCount).toBe(5);
      expect(normalized.groups[0].pageNames.length).toBe(6);
      expect(normalized.groups[0].pageLanguages.length).toBe(6);
    });

    it('preserves edited built-in mode name but marks isBuiltIn', () => {
      const result = normalizeStoreData({
        groups: [],
        studyModes: [
          { id: 'classic', name: 'Edited Classic', steps: [{ type: 'wait', ms: 250 }] },
        ],
        activityHeatmap: {},
      } as unknown as Parameters<typeof normalizeStoreData>[0]);
      expect(result.studyModes[0].isBuiltIn).toBe(true);
      expect(result.studyModes[0].builtInSourceId).toBe('classic');
      expect(result.studyModes[0].name).toBe('Edited Classic');
      expect(result.studyModes[0].steps).toEqual([{ type: 'wait', ms: 250 }]);
      expect(result.studyModes.some((mode) => mode.id === 'listen-speak')).toBe(true);
    });
  });
});

describe('pageCountModification', () => {
  const myCard: Flashcard = {
    id: 'c_test',
    pages: ['Word', 'Tłumaczenie', 'Hidden Example Note'],
    srsState: createNewSrsState(),
  };

  const myGroup: FlashcardGroup = {
    id: 'g_test',
    name: 'Test Deck',
    cards: [myCard],
    activeModeId: 'classic',
    studyFilter: DEFAULT_STUDY_FILTER,
    pageNames: ['Front', 'Back', 'Example'],
    pageLanguages: ['en-US', 'pl-PL', 'en-US'],
    activePageCount: 3,
  };

  it('shows all pages when activePageCount=3', () => {
    expect(getVisiblePages(myCard, myGroup)).toEqual(['Word', 'Tłumaczenie', 'Hidden Example Note']);
  });

  it('slices visible pages to 2 when activePageCount reduced', () => {
    const groupReduced = { ...myGroup, activePageCount: 2 };
    expect(getVisiblePages(myCard, groupReduced)).toEqual(['Word', 'Tłumaczenie']);
  });

  it('does NOT truncate physical card.pages array', () => {
    const groupReduced = { ...myGroup, activePageCount: 2 };
    getVisiblePages(myCard, groupReduced);
    expect(myCard.pages).toEqual(['Word', 'Tłumaczenie', 'Hidden Example Note']);
  });

  it('fully recovers hidden pages when activePageCount restored', () => {
    const groupRestored = { ...myGroup, activePageCount: 3 };
    expect(getVisiblePages(myCard, groupRestored)).toEqual(['Word', 'Tłumaczenie', 'Hidden Example Note']);
  });
});

describe('cardFiltering', () => {
  const now = Date.now();
  const newCard: Flashcard = { id: 'c_new', pages: ['New', 'Nowy'], srsState: { ...createNewSrsState(), state: 0 } };
  const dueCard: Flashcard = { id: 'c_due', pages: ['Due', 'Należny'], srsState: { ...createNewSrsState(), state: 2, nextReviewTimestamp: now - 1000 } };
  const notDueCard: Flashcard = { id: 'c_not_due', pages: ['Not Due', 'Nienależny'], srsState: { ...createNewSrsState(), state: 2, nextReviewTimestamp: now + 500000 } };
  const allCards = [newCard, dueCard, notDueCard];

  it('filter=all returns all cards', () => {
    expect(filterCards(allCards, 'all').length).toBe(3);
  });

  it('filter=new returns only new cards', () => {
    expect(filterCards(allCards, 'new')).toEqual([newCard]);
  });

  it('filter=review returns only due review cards', () => {
    expect(filterCards(allCards, 'review')).toEqual([dueCard]);
  });

  it('filter=new+review returns new + due cards', () => {
    expect(filterCards(allCards, 'new+review')).toEqual([newCard, dueCard]);
  });
});

describe('backupValidation', () => {
  const validBackup = {
    groups: [
      {
        id: 'g_backup',
        name: 'Backup Group',
        cards: [{ id: 'c_backup', pages: ['A', 'B'], srsState: createNewSrsState() }],
        activeModeId: 'classic',
        pageLanguages: ['en-US', 'pl-PL'],
        pageNames: ['Front', 'Back'],
      },
    ],
    studyModes: [{ id: 'classic', name: 'Classic', steps: [{ type: 'show_page', pageIndex: 0 }] }],
    activityHeatmap: { '2026-05-26': 1 },
  };

  it('accepts valid backup', () => {
    expect(() => validateBackupData(validBackup)).not.toThrow();
  });

  it('throws on null', () => {
    expect(() => validateBackupData(null)).toThrow('Backup data is not a valid JSON object.');
  });

  it('throws on missing groups', () => {
    expect(() => validateBackupData({})).toThrow('Backup data must contain a "groups" array.');
  });

  it('throws on missing studyModes', () => {
    expect(() => validateBackupData({ groups: [] })).toThrow('Backup data must contain a "studyModes" array.');
  });

  it('throws on missing activityHeatmap', () => {
    expect(() => validateBackupData({ groups: [], studyModes: [] })).toThrow(
      'Backup data must contain an "activityHeatmap" object.',
    );
  });

  it('throws on null groups entry', () => {
    expect(() => validateBackupData({ groups: [null], studyModes: [], activityHeatmap: {} })).toThrow(
      'Each group must be a valid object.',
    );
  });

  it('throws on non-string group id', () => {
    expect(() => validateBackupData({ groups: [{ id: 123 }], studyModes: [], activityHeatmap: {} })).toThrow(
      'Each group must have a string id.',
    );
  });

  it('throws on invalid study mode step', () => {
    expect(() =>
      validateBackupData({
        groups: [],
        studyModes: [{ id: 'broken', name: 'Broken', steps: [{ type: 'show_page' }] }],
        activityHeatmap: {},
      }),
    ).toThrow('invalid pageIndex');
  });
});

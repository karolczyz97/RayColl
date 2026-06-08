import { normalizeStoreData, normalizeGroup, normalizeStudyMode } from '../storeDataNormalization';
import type { Flashcard, FlashcardGroup, StudyMode } from '@/types/models';

describe('normalizeStoreData', () => {
  it('normalizes invalid activity heatmap values to a safe object', () => {
    const normalized = normalizeStoreData({
      groups: [],
      studyModes: [],
      activityHeatmap: null as unknown as Record<string, number>,
    });

    expect(normalized.activityHeatmap).toEqual({});
  });

  it('keeps only finite numeric activity heatmap counts', () => {
    const normalized = normalizeStoreData({
      groups: [],
      studyModes: [],
      activityHeatmap: {
        good: 3,
        badString: '4',
        badNaN: Number.NaN,
      } as unknown as Record<string, number>,
    });

    expect(normalized.activityHeatmap).toEqual({ good: 3 });
  });
});

const srsState = {
  difficulty: 1,
  stability: 1,
  repetitions: 0,
  state: 0 as const,
  lastReviewTimestamp: 0,
  nextReviewTimestamp: 0,
};

function makeCard(overrides?: Partial<Flashcard>): Flashcard {
  return {
    id: 'c1',
    pages: ['front'],
    srsState,
    ...overrides,
  };
}

function makeGroup(overrides?: Partial<FlashcardGroup>): FlashcardGroup {
  return {
    id: 'g1',
    name: 'Test',
    cards: [],
    activeModeId: 'classic',
    studyFilter: 'all',
    pageLanguages: ['en-US'],
    pageNames: ['Front'],
    activePageCount: 1,
    ...overrides,
  };
}

describe('normalizeGroup', () => {
  it('does not produce deletedAt own-key when input has none', () => {
    const group = makeGroup({ cards: [makeCard()] });
    const normalized = normalizeGroup(group);
    expect('deletedAt' in normalized.cards[0]).toBe(false);
  });

  it('preserves deletedAt own-key when input has one', () => {
    const group = makeGroup({ cards: [makeCard({ deletedAt: 9999 })] });
    const normalized = normalizeGroup(group);
    expect('deletedAt' in normalized.cards[0]).toBe(true);
    expect(normalized.cards[0].deletedAt).toBe(9999);
  });

  it('does not produce archivedAt own-key when input has none', () => {
    const group = makeGroup();
    const normalized = normalizeGroup(group);
    expect('archivedAt' in normalized).toBe(false);
  });

  it('preserves archivedAt own-key when input has one', () => {
    const group = makeGroup({ archivedAt: 8888 });
    const normalized = normalizeGroup(group);
    expect('archivedAt' in normalized).toBe(true);
    expect(normalized.archivedAt).toBe(8888);
  });
});

describe('normalizeStudyMode', () => {
  function makeMode(overrides?: Partial<StudyMode>): StudyMode {
    return {
      id: 'custom-1',
      name: 'Custom',
      steps: [],
      isBuiltIn: false,
      ...overrides,
    };
  }

  it('does not produce deletedAt own-key when input has none', () => {
    const normalized = normalizeStudyMode(makeMode());
    expect('deletedAt' in normalized).toBe(false);
  });

  it('preserves deletedAt own-key when input has one', () => {
    const normalized = normalizeStudyMode(makeMode({ deletedAt: 7777 }));
    expect('deletedAt' in normalized).toBe(true);
    expect(normalized.deletedAt).toBe(7777);
  });
});

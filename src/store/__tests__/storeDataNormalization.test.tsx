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

  it('migrates legacy speak_page extraPauseMs to pauseMultiplier (>0 -> 1, 0 -> 0)', () => {
    const legacySteps = [
      { type: 'speak_page', pageIndex: 0, extraPauseMs: 500 },
      { type: 'speak_page', pageIndex: 1, extraPauseMs: 0 },
    ] as unknown as StudyMode['steps'];
    const normalized = normalizeStudyMode(makeMode({ steps: legacySteps }));
    expect(normalized.steps).toEqual([
      { type: 'speak_page', pageIndex: 0, pauseMultiplier: 1 },
      { type: 'speak_page', pageIndex: 1, pauseMultiplier: 0 },
    ]);
  });

  it('defaults missing speak_page pauseMultiplier to 1 and clamps to 0–5', () => {
    const steps = [
      { type: 'speak_page', pageIndex: 0 },
      { type: 'speak_page', pageIndex: 1, pauseMultiplier: 9 },
      { type: 'speak_page', pageIndex: 2, pauseMultiplier: -3 },
    ] as unknown as StudyMode['steps'];
    const normalized = normalizeStudyMode(makeMode({ steps }));
    expect(normalized.steps).toEqual([
      { type: 'speak_page', pageIndex: 0, pauseMultiplier: 1 },
      { type: 'speak_page', pageIndex: 1, pauseMultiplier: 5 },
      { type: 'speak_page', pageIndex: 2, pauseMultiplier: 0 },
    ]);
  });

  it('keeps next_card wherever the user placed it', () => {
    const steps = [
      { type: 'next_card' },
      { type: 'show_page', pageIndex: 0 },
    ] as unknown as StudyMode['steps'];
    const normalized = normalizeStudyMode(makeMode({ steps }));
    expect(normalized.steps).toEqual([
      { type: 'next_card' },
      { type: 'show_page', pageIndex: 0 },
    ]);
  });

  it('preserves valid step conditions and drops invalid ones', () => {
    const steps = [
      { type: 'speak_page', pageIndex: 0, pauseMultiplier: 1, condition: 'correct' },
      { type: 'next_card', condition: 'wrong' },
      { type: 'show_page', pageIndex: 0, condition: 'nonsense' },
    ] as unknown as StudyMode['steps'];
    const normalized = normalizeStudyMode(makeMode({ steps }));
    expect(normalized.steps).toEqual([
      { type: 'speak_page', pageIndex: 0, pauseMultiplier: 1, condition: 'correct' },
      { type: 'next_card', condition: 'wrong' },
      { type: 'show_page', pageIndex: 0 },
    ]);
  });

  it('migrates dynamic_pause to pauseMultiplier (legacy extraPauseMs dropped, missing -> 1)', () => {
    const legacySteps = [
      { type: 'dynamic_pause', nextPageIndex: 1, extraPauseMs: 500 },
      { type: 'dynamic_pause', nextPageIndex: 0 },
      { type: 'dynamic_pause', nextPageIndex: 2, pauseMultiplier: 3 },
    ] as unknown as StudyMode['steps'];
    const normalized = normalizeStudyMode(makeMode({ steps: legacySteps }));
    expect(normalized.steps).toEqual([
      { type: 'dynamic_pause', nextPageIndex: 1, pauseMultiplier: 1 },
      { type: 'dynamic_pause', nextPageIndex: 0, pauseMultiplier: 1 },
      { type: 'dynamic_pause', nextPageIndex: 2, pauseMultiplier: 3 },
    ]);
  });
});

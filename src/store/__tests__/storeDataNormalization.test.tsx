import {
  DEFAULT_CARD_ORDER,
  normalizeStoreData,
  normalizeGroup,
  normalizeStudyMode,
} from '../storeDataNormalization';
import type { Flashcard, FlashcardGroup, StudyMode } from '@/types/models';
import { defaultCompoundParams } from '@/features/settings/compoundSteps';

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
    contentUpdatedAt: 0,
    srsUpdatedAt: 0,
    ...overrides,
  } as Flashcard;
}

function makeGroup(overrides?: Partial<FlashcardGroup>): FlashcardGroup {
  return {
    id: 'g1',
    name: 'Test',
    cards: [],
    activeModeId: 'classic',
    studyFilter: 'all',
    cardOrder: DEFAULT_CARD_ORDER,
    pageLanguages: ['en-US'],
    pageNames: ['Front'],
    activePageCount: 1,
    updatedAt: 0,
    ...overrides,
  } as FlashcardGroup;
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

  it('normalizes missing or invalid cardOrder to the default', () => {
    expect(normalizeGroup(makeGroup()).cardOrder).toBe(DEFAULT_CARD_ORDER);
    expect(normalizeGroup(makeGroup({ cardOrder: 'broken' as never })).cardOrder).toBe(
      DEFAULT_CARD_ORDER,
    );
  });
});

describe('normalizeStudyMode', () => {
  function makeMode(overrides?: Partial<StudyMode>): StudyMode {
    return {
      id: 'custom-1',
      name: 'Custom',
      steps: [],
      isBuiltIn: false,
      updatedAt: 0,
      ...overrides,
    } as StudyMode;
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

  it('normalizes speak_page to the canonical runtime shape', () => {
    const steps = [
      { type: 'speak_page', pageIndex: 0, unsupportedField: 500 },
      { type: 'speak_page', pageIndex: 1, debugLabel: 'front' },
    ] as unknown as StudyMode['steps'];
    const normalized = normalizeStudyMode(makeMode({ steps }));
    expect(normalized.steps).toEqual([
      { type: 'speak_page', pageIndex: 0 },
      { type: 'speak_page', pageIndex: 1 },
    ]);
  });

  it('clamps auto_rate_fixed rating to 1–4 and defaults invalid values', () => {
    const steps = [
      { type: 'auto_rate_fixed', rating: 9 },
      { type: 'auto_rate_fixed', rating: 0 },
      { type: 'auto_rate_fixed' },
    ] as unknown as StudyMode['steps'];
    const normalized = normalizeStudyMode(makeMode({ steps }));
    expect(normalized.steps).toEqual([
      { type: 'auto_rate_fixed', rating: 4 },
      { type: 'auto_rate_fixed', rating: 1 },
      { type: 'auto_rate_fixed', rating: 3 },
    ]);
  });

  it('keeps next_card wherever the user placed it', () => {
    const steps = [
      { type: 'next_card' },
      { type: 'show_page', pageIndex: 0 },
    ] as unknown as StudyMode['steps'];
    const normalized = normalizeStudyMode(makeMode({ steps }));
    expect(normalized.steps).toEqual([{ type: 'next_card' }, { type: 'show_page', pageIndex: 0 }]);
  });

  it('strips unknown step types the runner does not understand', () => {
    const steps = [
      { type: 'show_page', pageIndex: 0 },
      { type: 'wait_for_tap_to_reveal_next' },
      { type: 'unsupported_reveal' },
      { type: 'unsupported_rate' },
      { type: 'unsupported_branch', pageIndex: 1, successThreshold: 70 },
      { type: 'show_ratings' },
    ] as unknown as StudyMode['steps'];
    const normalized = normalizeStudyMode(makeMode({ steps }));
    expect(normalized.steps).toEqual([
      { type: 'show_page', pageIndex: 0 },
      { type: 'wait_for_tap_to_reveal_next' },
      { type: 'show_ratings' },
    ]);
  });

  it('preserves valid step conditions and drops invalid ones', () => {
    const steps = [
      { type: 'speak_page', pageIndex: 0, condition: 'correct' },
      { type: 'next_card', condition: 'wrong' },
      { type: 'show_page', pageIndex: 0, condition: 'nonsense' },
    ] as unknown as StudyMode['steps'];
    const normalized = normalizeStudyMode(makeMode({ steps }));
    expect(normalized.steps).toEqual([
      { type: 'speak_page', pageIndex: 0, condition: 'correct' },
      { type: 'next_card', condition: 'wrong' },
      { type: 'show_page', pageIndex: 0 },
    ]);
  });

  it('normalizes dynamic_pause pauseMultiplier', () => {
    const steps = [
      { type: 'dynamic_pause', nextPageIndex: 1 },
      { type: 'dynamic_pause', nextPageIndex: 0 },
      { type: 'dynamic_pause', nextPageIndex: 2, pauseMultiplier: 3 },
      { type: 'dynamic_pause', nextPageIndex: 3, pauseMultiplier: Number.NaN },
    ] as unknown as StudyMode['steps'];
    const normalized = normalizeStudyMode(makeMode({ steps }));
    expect(normalized.steps).toEqual([
      { type: 'dynamic_pause', nextPageIndex: 1, pauseMultiplier: 1 },
      { type: 'dynamic_pause', nextPageIndex: 0, pauseMultiplier: 1 },
      { type: 'dynamic_pause', nextPageIndex: 2, pauseMultiplier: 3 },
      { type: 'dynamic_pause', nextPageIndex: 3, pauseMultiplier: 1 },
    ]);
  });

  it('normalizes compound steps idempotently and drops unknown compound kinds', () => {
    const steps = [
      {
        id: 'temp-id',
        type: 'compound',
        version: 99,
        params: { kind: 'auto_pass_next', rating: 99 },
      },
      {
        type: 'compound',
        version: 1,
        params: { kind: 'mystery' },
      },
    ] as unknown as StudyMode['steps'];

    const normalized = normalizeStudyMode(makeMode({ steps }));
    expect(normalized.steps).toEqual([
      { type: 'compound', version: 1, params: { kind: 'auto_pass_next', rating: 4 } },
    ]);
    expect(normalizeStudyMode(makeMode({ steps: normalized.steps })).steps).toEqual(
      normalized.steps,
    );
  });

  it('preserves a valid listen_grade compound through normalization', () => {
    const steps = [
      { type: 'compound', version: 1, params: defaultCompoundParams('listen_grade') },
    ] as StudyMode['steps'];

    expect(normalizeStudyMode(makeMode({ steps })).steps).toEqual(steps);
  });
});

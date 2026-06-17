import { MAX_PAUSE_MULTIPLIER } from '@/store/storeDataNormalization';
import type { BuildModeStepForm } from '@/features/settings/buildModeStep';
import { buildModeStep, ATOMIC_STEP_TYPE_ORDER, ATOMIC_STEP_LABEL_KEYS } from '@/features/settings/buildModeStep';

function makeForm(overrides: Partial<BuildModeStepForm> = {}): BuildModeStepForm {
  return {
    newStepType: 'show_page',
    pageCount: 3,
    newPageIdx: 1,
    newMs: 750,
    newPauseMultiplier: 2,
    newThreshold: 80,
    newRating: 3,
    newCondition: 'always',
    ...overrides,
  };
}

describe('buildModeStep', () => {
  it('has consistent ATOMIC_STEP constants', () => {
    expect(Object.keys(ATOMIC_STEP_LABEL_KEYS)).toHaveLength(ATOMIC_STEP_TYPE_ORDER.length);
    expect(Object.keys(ATOMIC_STEP_LABEL_KEYS).sort()).toEqual([...ATOMIC_STEP_TYPE_ORDER].sort());
  });

  it.each([
    ['show_page', { type: 'show_page', pageIndex: 1 }],
    ['show_all_pages', { type: 'show_all_pages' }],
    ['wait_for_tap_to_reveal_next', { type: 'wait_for_tap_to_reveal_next' }],
    ['wait_for_tap_to_reveal', { type: 'wait_for_tap_to_reveal' }],
    ['show_ratings', { type: 'show_ratings' }],
    ['speak_page', { type: 'speak_page', pageIndex: 1 }],
    [
      'dynamic_pause',
      { type: 'dynamic_pause', nextPageIndex: 1, pauseMultiplier: 2 },
    ],
    ['wait', { type: 'wait', ms: 750 }],
    [
      'listen_and_check',
      { type: 'listen_and_check', pageIndex: 1, successThreshold: 80 },
    ],
    ['feedback_success', { type: 'feedback_success' }],
    ['feedback_error', { type: 'feedback_error' }],
    ['auto_rate_from_answer', { type: 'auto_rate_from_answer' }],
    ['auto_rate_fixed', { type: 'auto_rate_fixed', rating: 3 }],
    ['mark_failed', { type: 'mark_failed' }],
    ['next_card', { type: 'next_card' }],
  ])('builds %s steps', (newStepType, expected) => {
    expect(buildModeStep(makeForm({ newStepType }))).toEqual(expected);
  });

  it('adds a condition only when the step is not always executed', () => {
    expect(buildModeStep(makeForm({ newCondition: 'correct' }))).toEqual({
      type: 'show_page',
      pageIndex: 1,
      condition: 'correct',
    });

    expect(buildModeStep(makeForm())).not.toHaveProperty('condition');
  });

  it('clamps page indexes to the available page range', () => {
    expect(buildModeStep(makeForm({ newPageIdx: -2 }))).toEqual({
      type: 'show_page',
      pageIndex: 0,
    });

    expect(buildModeStep(makeForm({ newPageIdx: 99 }))).toEqual({
      type: 'show_page',
      pageIndex: 2,
    });
  });

  it('clamps and truncates pause multipliers', () => {
    expect(buildModeStep(makeForm({ newStepType: 'dynamic_pause', newPauseMultiplier: -4 }))).toEqual({
      type: 'dynamic_pause',
      nextPageIndex: 1,
      pauseMultiplier: 0,
    });

    expect(buildModeStep(makeForm({ newStepType: 'dynamic_pause', newPauseMultiplier: 99 }))).toEqual({
      type: 'dynamic_pause',
      nextPageIndex: 1,
      pauseMultiplier: MAX_PAUSE_MULTIPLIER,
    });

    expect(buildModeStep(makeForm({ newStepType: 'dynamic_pause', newPauseMultiplier: 3.8 }))).toEqual({
      type: 'dynamic_pause',
      nextPageIndex: 1,
      pauseMultiplier: 3,
    });
  });

  it('clamps auto-rate fixed rating to 1–4', () => {
    expect(buildModeStep(makeForm({ newStepType: 'auto_rate_fixed', newRating: 0 }))).toEqual({
      type: 'auto_rate_fixed',
      rating: 1,
    });

    expect(buildModeStep(makeForm({ newStepType: 'auto_rate_fixed', newRating: 9 }))).toEqual({
      type: 'auto_rate_fixed',
      rating: 4,
    });
  });

  it('returns null for unknown step types', () => {
    expect(buildModeStep(makeForm({ newStepType: 'mystery' }))).toBeNull();
  });
});

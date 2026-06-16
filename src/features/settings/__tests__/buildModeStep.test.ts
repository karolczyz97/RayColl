import { MAX_PAUSE_MULTIPLIER } from '@/store/storeDataNormalization';
import type { BuildModeStepForm } from '@/features/settings/buildModeStep';
import { buildModeStep } from '@/features/settings/buildModeStep';

function makeForm(overrides: Partial<BuildModeStepForm> = {}): BuildModeStepForm {
  return {
    id: 'step-1',
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
  it.each([
    ['show_page', { id: 'step-1', type: 'show_page', pageIndex: 1 }],
    ['show_all_pages', { id: 'step-1', type: 'show_all_pages' }],
    ['wait_for_tap_to_reveal_next', { id: 'step-1', type: 'wait_for_tap_to_reveal_next' }],
    ['wait_for_tap_to_reveal', { id: 'step-1', type: 'wait_for_tap_to_reveal' }],
    ['show_ratings', { id: 'step-1', type: 'show_ratings' }],
    // speak_page nie ma już pauseMultiplier — TTS tylko mówi.
    ['speak_page', { id: 'step-1', type: 'speak_page', pageIndex: 1 }],
    [
      'dynamic_pause',
      { id: 'step-1', type: 'dynamic_pause', nextPageIndex: 1, pauseMultiplier: 2 },
    ],
    ['wait', { id: 'step-1', type: 'wait', ms: 750 }],
    [
      'listen_and_check',
      { id: 'step-1', type: 'listen_and_check', pageIndex: 1, successThreshold: 80 },
    ],
    ['feedback_success', { id: 'step-1', type: 'feedback_success' }],
    ['feedback_error', { id: 'step-1', type: 'feedback_error' }],
    ['auto_rate_from_answer', { id: 'step-1', type: 'auto_rate_from_answer' }],
    ['auto_rate_fixed', { id: 'step-1', type: 'auto_rate_fixed', rating: 3 }],
    ['mark_failed', { id: 'step-1', type: 'mark_failed' }],
    ['next_card', { id: 'step-1', type: 'next_card' }],
  ])('builds %s steps', (newStepType, expected) => {
    expect(buildModeStep(makeForm({ newStepType }))).toEqual(expected);
  });

  it('adds a condition only when the step is not always executed', () => {
    expect(buildModeStep(makeForm({ newCondition: 'correct' }))).toEqual({
      id: 'step-1',
      type: 'show_page',
      pageIndex: 1,
      condition: 'correct',
    });

    expect(buildModeStep(makeForm())).not.toHaveProperty('condition');
  });

  it('clamps page indexes to the available page range', () => {
    expect(buildModeStep(makeForm({ newPageIdx: -2 }))).toEqual({
      id: 'step-1',
      type: 'show_page',
      pageIndex: 0,
    });

    expect(buildModeStep(makeForm({ newPageIdx: 99 }))).toEqual({
      id: 'step-1',
      type: 'show_page',
      pageIndex: 2,
    });
  });

  it('clamps and truncates pause multipliers', () => {
    expect(buildModeStep(makeForm({ newStepType: 'dynamic_pause', newPauseMultiplier: -4 }))).toEqual({
      id: 'step-1',
      type: 'dynamic_pause',
      nextPageIndex: 1,
      pauseMultiplier: 0,
    });

    expect(buildModeStep(makeForm({ newStepType: 'dynamic_pause', newPauseMultiplier: 99 }))).toEqual({
      id: 'step-1',
      type: 'dynamic_pause',
      nextPageIndex: 1,
      pauseMultiplier: MAX_PAUSE_MULTIPLIER,
    });

    expect(buildModeStep(makeForm({ newStepType: 'dynamic_pause', newPauseMultiplier: 3.8 }))).toEqual({
      id: 'step-1',
      type: 'dynamic_pause',
      nextPageIndex: 1,
      pauseMultiplier: 3,
    });
  });

  it('clamps auto-rate fixed rating to 1–4', () => {
    expect(buildModeStep(makeForm({ newStepType: 'auto_rate_fixed', newRating: 0 }))).toEqual({
      id: 'step-1',
      type: 'auto_rate_fixed',
      rating: 1,
    });

    expect(buildModeStep(makeForm({ newStepType: 'auto_rate_fixed', newRating: 9 }))).toEqual({
      id: 'step-1',
      type: 'auto_rate_fixed',
      rating: 4,
    });
  });

  it('returns null for unknown step types', () => {
    expect(buildModeStep(makeForm({ newStepType: 'mystery' }))).toBeNull();
  });
});

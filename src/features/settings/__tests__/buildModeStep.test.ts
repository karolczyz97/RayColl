import { MAX_PAUSE_MULTIPLIER } from '@/store/storeDataNormalization';
import type { BuildModeStepForm } from '../buildModeStep';
import { buildModeStep } from '../buildModeStep';

function makeForm(overrides: Partial<BuildModeStepForm> = {}): BuildModeStepForm {
  return {
    id: 'step-1',
    newStepType: 'show_page',
    pageCount: 3,
    newPageIdx: 1,
    newMs: 750,
    newPauseMultiplier: 2,
    newThreshold: 80,
    newCondition: 'always',
    ...overrides,
  };
}

describe('buildModeStep', () => {
  it.each([
    ['show_page', { id: 'step-1', type: 'show_page', pageIndex: 1 }],
    [
      'speak_page',
      { id: 'step-1', type: 'speak_page', pageIndex: 1, pauseMultiplier: 2 },
    ],
    [
      'dynamic_pause',
      { id: 'step-1', type: 'dynamic_pause', nextPageIndex: 1, pauseMultiplier: 2 },
    ],
    ['wait', { id: 'step-1', type: 'wait', ms: 750 }],
    [
      'listen_and_branch',
      { id: 'step-1', type: 'listen_and_branch', pageIndex: 1, successThreshold: 80 },
    ],
    [
      'listen_and_check',
      { id: 'step-1', type: 'listen_and_check', pageIndex: 1, successThreshold: 80 },
    ],
    ['reveal_on_tap', { id: 'step-1', type: 'reveal_on_tap' }],
    ['rate', { id: 'step-1', type: 'rate' }],
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
    expect(buildModeStep(makeForm({ newStepType: 'speak_page', newPauseMultiplier: -4 }))).toEqual({
      id: 'step-1',
      type: 'speak_page',
      pageIndex: 1,
      pauseMultiplier: 0,
    });

    expect(buildModeStep(makeForm({ newStepType: 'dynamic_pause', newPauseMultiplier: 99 }))).toEqual({
      id: 'step-1',
      type: 'dynamic_pause',
      nextPageIndex: 1,
      pauseMultiplier: MAX_PAUSE_MULTIPLIER,
    });

    expect(buildModeStep(makeForm({ newStepType: 'speak_page', newPauseMultiplier: 3.8 }))).toEqual({
      id: 'step-1',
      type: 'speak_page',
      pageIndex: 1,
      pauseMultiplier: 3,
    });
  });

  it('returns null for unknown step types', () => {
    expect(buildModeStep(makeForm({ newStepType: 'mystery' }))).toBeNull();
  });
});

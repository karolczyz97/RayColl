import type { AtomicStep, CompoundStep } from '@/types/models';
import { createSeedModes } from '@/store/seed/seedModes';
import {
  defaultBranch,
  defaultCompoundParams,
  expandCompound,
  expandModeSteps,
  expandWithSource,
  normalizeCompoundStep,
} from '@/features/settings/compoundSteps';

function compound(step: CompoundStep['params']): CompoundStep {
  return { type: 'compound', version: 1, params: step };
}

describe('compoundSteps', () => {
  it('expands default classic compounds to the classic seed steps', () => {
    const expanded = expandModeSteps([
      compound(defaultCompoundParams('present_front')),
      compound(defaultCompoundParams('flip_reveal')),
    ]);
    const classic = createSeedModes().find((mode) => mode.id === 'classic');

    expect(expanded).toEqual(classic?.steps);
  });

  it('expands default audio compounds to the audio seed steps', () => {
    const expanded = expandModeSteps([
      compound(defaultCompoundParams('present_front')),
      compound(defaultCompoundParams('listen_grade')),
    ]);
    const audio = createSeedModes().find((mode) => mode.id === 'listen-speak');

    expect(expanded).toEqual(audio?.steps);
  });

  it('keeps atomic steps untouched while tracking source indexes', () => {
    const atom: AtomicStep = { type: 'show_ratings' };

    expect(expandModeSteps([atom])).toEqual([atom]);
    expect(expandWithSource([atom, compound({ kind: 'fail_next' })])).toEqual([
      { step: atom, sourceIndex: 0 },
      { step: { type: 'mark_failed' }, sourceIndex: 1 },
      { step: { type: 'auto_rate_fixed', rating: 1 }, sourceIndex: 1 },
      { step: { type: 'next_card' }, sourceIndex: 1 },
    ]);
  });

  it('expands fixed and dynamic branch pauses to the matching atomic steps', () => {
    expect(
      expandCompound(
        compound({
          kind: 'grade_after_listen',
          onCorrect: {
            ...defaultBranch('correct'),
            feedback: false,
            pause: { kind: 'fixed', ms: 1000 },
            revealAll: false,
            rate: 'none',
            advance: false,
          },
          onWrong: {
            ...defaultBranch('wrong'),
            feedback: false,
            pause: { kind: 'dynamic', page: 2, multiplier: 3 },
            revealAll: false,
            markFailed: false,
            rate: 'none',
            advance: false,
          },
          manualFallback: false,
        }),
      ),
    ).toEqual([
      { type: 'wait', ms: 1000, condition: 'correct' },
      { type: 'dynamic_pause', nextPageIndex: 2, pauseMultiplier: 3, condition: 'wrong' },
    ]);
  });

  it('normalizes compound params idempotently and rejects unknown kinds', () => {
    const raw = {
      type: 'compound',
      version: 99,
      params: {
        kind: 'auto_pass_next',
        rating: 99,
      },
    };

    const normalized = normalizeCompoundStep(raw);
    expect(normalized).toEqual({
      type: 'compound',
      version: 1,
      params: { kind: 'auto_pass_next', rating: 4 },
    });
    expect(normalizeCompoundStep(normalized)).toEqual(normalized);
    expect(normalizeCompoundStep({ type: 'compound', params: { kind: 'mystery' } })).toBeNull();
  });
});

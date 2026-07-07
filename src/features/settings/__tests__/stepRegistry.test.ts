import { MAX_PAUSE_MULTIPLIER } from '@/constants/studySteps';
import { en } from '@/i18n/locales/en';
import {
  ATOMIC_STEP_REGISTRY,
  ATOMIC_STEP_TYPE_ORDER,
  buildAtomicStep,
  clampStepNumberField,
  getStepPageIndex,
  getStepSummaryParams,
  isAtomicStepType,
} from '@/features/settings/stepRegistry';

const CTX = { pageCount: 3 };

const DEFAULT_VALUES: Record<string, number> = {
  pageIndex: 1,
  nextPageIndex: 1,
  pauseMultiplier: 2,
  ms: 750,
  successThreshold: 80,
  rating: 3,
};

function build(type: string, overrides: Record<string, number> = {}) {
  return buildAtomicStep(type, { ...DEFAULT_VALUES, ...overrides }, CTX, 'always');
}

describe('stepRegistry', () => {
  describe('registry invariants', () => {
    it('has 15 unique entries in the approved picker order', () => {
      expect(ATOMIC_STEP_REGISTRY).toHaveLength(15);
      expect(new Set(ATOMIC_STEP_TYPE_ORDER).size).toBe(ATOMIC_STEP_TYPE_ORDER.length);
      expect(ATOMIC_STEP_TYPE_ORDER).toEqual([
        'show_page',
        'show_all_pages',
        'wait_for_tap_to_reveal_next',
        'wait_for_tap_to_reveal',
        'speak_page',
        'listen_and_check',
        'feedback_success',
        'feedback_error',
        'dynamic_pause',
        'wait',
        'show_ratings',
        'auto_rate_from_answer',
        'auto_rate_fixed',
        'mark_failed',
        'next_card',
      ]);
    });

    it('references only i18n keys defined in the en locale', () => {
      for (const def of ATOMIC_STEP_REGISTRY) {
        expect(en[def.labelKey]).toBeDefined();
        expect(en[def.summaryKey]).toBeDefined();
        expect(en['step.category.' + def.category]).toBeDefined();
      }
    });
  });

  describe('buildAtomicStep', () => {
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
    ])('builds %s steps', (type, expected) => {
      expect(build(type)).toEqual(expected);
    });

    it('adds a condition only when the step is not always executed', () => {
      expect(buildAtomicStep('show_page', DEFAULT_VALUES, CTX, 'correct')).toEqual({
        type: 'show_page',
        pageIndex: 1,
        condition: 'correct',
      });

      expect(build('show_page')).not.toHaveProperty('condition');
    });

    it('clamps page indexes to the available page range', () => {
      expect(build('show_page', { pageIndex: -2 })).toEqual({
        type: 'show_page',
        pageIndex: 0,
      });

      expect(build('show_page', { pageIndex: 99 })).toEqual({
        type: 'show_page',
        pageIndex: 2,
      });
    });

    it('clamps and truncates pause multipliers', () => {
      expect(build('dynamic_pause', { pauseMultiplier: -4 })).toEqual({
        type: 'dynamic_pause',
        nextPageIndex: 1,
        pauseMultiplier: 0,
      });

      expect(build('dynamic_pause', { pauseMultiplier: 99 })).toEqual({
        type: 'dynamic_pause',
        nextPageIndex: 1,
        pauseMultiplier: MAX_PAUSE_MULTIPLIER,
      });

      expect(build('dynamic_pause', { pauseMultiplier: 3.8 })).toEqual({
        type: 'dynamic_pause',
        nextPageIndex: 1,
        pauseMultiplier: 3,
      });
    });

    it('clamps auto-rate fixed rating to 1–4', () => {
      expect(build('auto_rate_fixed', { rating: 0 })).toEqual({
        type: 'auto_rate_fixed',
        rating: 1,
      });

      expect(build('auto_rate_fixed', { rating: 9 })).toEqual({
        type: 'auto_rate_fixed',
        rating: 4,
      });
    });

    it('falls back to field defaults for missing values', () => {
      expect(buildAtomicStep('wait', {}, CTX, 'always')).toEqual({ type: 'wait', ms: 500 });
      expect(buildAtomicStep('listen_and_check', {}, CTX, 'always')).toEqual({
        type: 'listen_and_check',
        pageIndex: 0,
        successThreshold: 70,
      });
    });

    it('returns null for unknown step types', () => {
      expect(build('mystery')).toBeNull();
      expect(isAtomicStepType('mystery')).toBe(false);
    });
  });

  describe('clampStepNumberField', () => {
    it('matches the legacy pause multiplier normalization', () => {
      expect(clampStepNumberField('dynamic_pause', 'pauseMultiplier', undefined)).toBe(1);
      expect(clampStepNumberField('dynamic_pause', 'pauseMultiplier', Number.NaN)).toBe(1);
      expect(clampStepNumberField('dynamic_pause', 'pauseMultiplier', -4)).toBe(0);
      expect(clampStepNumberField('dynamic_pause', 'pauseMultiplier', 99)).toBe(MAX_PAUSE_MULTIPLIER);
      expect(clampStepNumberField('dynamic_pause', 'pauseMultiplier', 3.8)).toBe(3);
    });

    it('matches the legacy fixed rating clamp', () => {
      expect(clampStepNumberField('auto_rate_fixed', 'rating', undefined)).toBe(3);
      expect(clampStepNumberField('auto_rate_fixed', 'rating', 0)).toBe(1);
      expect(clampStepNumberField('auto_rate_fixed', 'rating', 9)).toBe(4);
    });

    it('leaves unbounded fields uncapped above the minimum', () => {
      expect(clampStepNumberField('wait', 'ms', -5)).toBe(0);
      expect(clampStepNumberField('wait', 'ms', 123456)).toBe(123456);
      expect(clampStepNumberField('wait', 'ms', 'oops')).toBe(500);
    });
  });

  describe('getStepPageIndex', () => {
    it('returns the page field value or null', () => {
      expect(getStepPageIndex({ type: 'show_page', pageIndex: 2 })).toBe(2);
      expect(getStepPageIndex({ type: 'speak_page', pageIndex: 1 })).toBe(1);
      expect(
        getStepPageIndex({ type: 'listen_and_check', pageIndex: 0, successThreshold: 70 }),
      ).toBe(0);
      expect(
        getStepPageIndex({ type: 'dynamic_pause', nextPageIndex: 2, pauseMultiplier: 1 }),
      ).toBe(2);
      expect(getStepPageIndex({ type: 'wait', ms: 500 })).toBeNull();
      expect(getStepPageIndex({ type: 'next_card' })).toBeNull();
    });
  });

  describe('getStepSummaryParams', () => {
    it('maps page fields to a 1-based index and numeric fields to summary params', () => {
      expect(getStepSummaryParams({ type: 'show_page', pageIndex: 1 })).toEqual({ index: 2 });
      expect(
        getStepSummaryParams({ type: 'dynamic_pause', nextPageIndex: 1, pauseMultiplier: 3 }),
      ).toEqual({ index: 2, multiplier: 3 });
      expect(
        getStepSummaryParams({ type: 'listen_and_check', pageIndex: 0, successThreshold: 70 }),
      ).toEqual({ index: 1, threshold: 70 });
      expect(getStepSummaryParams({ type: 'wait', ms: 500 })).toEqual({ ms: 500 });
      expect(getStepSummaryParams({ type: 'auto_rate_fixed', rating: 4 })).toEqual({ rating: 4 });
      expect(getStepSummaryParams({ type: 'show_all_pages' })).toBeUndefined();
    });
  });
});

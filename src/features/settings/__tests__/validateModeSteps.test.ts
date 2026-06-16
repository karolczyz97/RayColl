import { describe, it, expect } from '@jest/globals';
import type { AtomicStep } from '@/types/models';
import { hasBlockingStepIssue, validateModeSteps } from '../validateModeSteps';

describe('validateModeSteps', () => {
  it('flags a page index out of range as a blocking error', () => {
    const steps: AtomicStep[] = [
      { type: 'show_page', pageIndex: 5 },
      { type: 'show_ratings' },
    ];
    const issues = validateModeSteps(steps, 3);
    expect(
      issues.some(
        (i) =>
          i.severity === 'error' && i.messageKey === 'settings.validation.step_page_out_of_range',
      ),
    ).toBe(true);
    expect(hasBlockingStepIssue(issues)).toBe(true);
  });

  it('accepts in-range page indexes with a terminal', () => {
    const steps: AtomicStep[] = [
      { type: 'show_page', pageIndex: 0 },
      { type: 'listen_and_check', pageIndex: 1, successThreshold: 70 },
      { type: 'show_ratings' },
    ];
    expect(validateModeSteps(steps, 3)).toEqual([]);
  });

  it('warns (non-blocking) when there is no terminal step', () => {
    const steps: AtomicStep[] = [{ type: 'show_all_pages' }];
    const issues = validateModeSteps(steps, 3);
    expect(issues).toEqual([
      { severity: 'warning', messageKey: 'settings.validation.no_terminal' },
    ]);
    expect(hasBlockingStepIssue(issues)).toBe(false);
  });

  it('treats next_card as a valid terminal', () => {
    const steps: AtomicStep[] = [{ type: 'show_all_pages' }, { type: 'next_card' }];
    expect(validateModeSteps(steps, 3)).toEqual([]);
  });

  it('checks dynamic_pause nextPageIndex against the page range', () => {
    const steps: AtomicStep[] = [
      { type: 'dynamic_pause', nextPageIndex: 9, pauseMultiplier: 1 },
      { type: 'next_card' },
    ];
    expect(hasBlockingStepIssue(validateModeSteps(steps, 3))).toBe(true);
  });

  it('returns no issues for an empty list', () => {
    expect(validateModeSteps([], 3)).toEqual([]);
  });
});

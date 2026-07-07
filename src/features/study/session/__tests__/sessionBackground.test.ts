import { describe, expect, it } from '@jest/globals';
import type { AtomicStep } from '@/types/models';
import { isModeHandsFreeCapable } from '@/features/study/session/sessionBackground';

describe('isModeHandsFreeCapable', () => {
  it('returns true for TTS-only auto-rate mode', () => {
    const steps: AtomicStep[] = [
      { type: 'speak_all_pages' },
      { type: 'dynamic_pause', nextPageIndex: 1, pauseMultiplier: 1 },
      { type: 'show_all_pages' },
      { type: 'auto_rate_fixed', rating: 3 },
      { type: 'next_card' },
    ];
    expect(isModeHandsFreeCapable(steps)).toBe(true);
  });

  it('returns false when show_ratings is present', () => {
    const steps: AtomicStep[] = [
      { type: 'speak_all_pages' },
      { type: 'show_all_pages' },
      { type: 'show_ratings' },
    ];
    expect(isModeHandsFreeCapable(steps)).toBe(false);
  });

  it('returns false when listen_and_check is present', () => {
    const steps: AtomicStep[] = [
      { type: 'speak_page', pageIndex: 0 },
      { type: 'listen_and_check', pageIndex: 1, successThreshold: 70 },
      { type: 'show_all_pages' },
      { type: 'show_ratings' },
    ];
    expect(isModeHandsFreeCapable(steps)).toBe(false);
  });

  it('returns false for empty steps', () => {
    expect(isModeHandsFreeCapable([])).toBe(false);
  });

  it('returns true for pure speak-and-pause mode', () => {
    const steps: AtomicStep[] = [
      { type: 'speak_page', pageIndex: 0 },
      { type: 'dynamic_pause', nextPageIndex: 0, pauseMultiplier: 2 },
      { type: 'speak_page', pageIndex: 1 },
      { type: 'show_all_pages' },
      { type: 'next_card' },
    ];
    expect(isModeHandsFreeCapable(steps)).toBe(true);
  });
});

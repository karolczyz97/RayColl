import { describe, it, expect } from '@jest/globals';

import { createSeedModes } from '../seedModes';

describe('createSeedModes', () => {
  it('makes the classic preset reveal all pages explicitly before ratings', () => {
    const classic = createSeedModes().find((mode) => mode.id === 'classic');

    expect(classic?.steps).toEqual([
      { type: 'show_page', pageIndex: 0 },
      { type: 'speak_page', pageIndex: 0 },
      { type: 'wait_for_tap_to_reveal' },
      { type: 'show_all_pages' },
      { type: 'show_ratings' },
    ]);
  });

  it('returns fresh clones so callers cannot mutate the shared seed', () => {
    const first = createSeedModes();
    const second = createSeedModes();

    expect(first).not.toBe(second);
    expect(first[0]).not.toBe(second[0]);
    expect(first[0].steps).not.toBe(second[0].steps);

    const originalStepCount = second[0].steps.length;
    first[0].name = 'mutated';
    first[0].steps.push({ type: 'wait', ms: 12345 });

    expect(second[0].name).not.toBe('mutated');
    expect(second[0].steps).toHaveLength(originalStepCount);
    expect(second[0].steps).not.toContainEqual({ type: 'wait', ms: 12345 });
  });
});

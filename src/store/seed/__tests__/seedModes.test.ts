import { describe, it, expect } from '@jest/globals';

import { createSeedModes } from '../seedModes';

describe('createSeedModes', () => {
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

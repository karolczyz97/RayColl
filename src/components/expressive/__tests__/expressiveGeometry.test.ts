import assert from 'node:assert/strict';

import {
  createShapeSurfacePath,
  getProgressAccessibilityValue,
  getProgressRatio,
} from '../expressiveGeometry';

export async function runTests() {
  assert.equal(getProgressRatio({ value: 50, max: 100 }), 0.5);
  assert.equal(getProgressRatio({ value: -1, max: 100 }), 0);
  assert.equal(getProgressRatio({ value: 150, max: 100 }), 1);
  assert.equal(getProgressRatio({ value: 10, max: 0 }), 0);

  assert.deepEqual(getProgressAccessibilityValue({ value: 150, max: 100 }), {
    min: 0,
    max: 100,
    now: 100,
  });

  // Shape-surface paths stay as closed SVG curves (the only remaining SVG geometry).
  for (const variant of ['soft', 'outline', 'blob'] as const) {
    const path = createShapeSurfacePath(variant);
    assert.match(path, /^M/);
    assert.match(path, /Z$/);
    assert.equal(path.includes('C'), true);
  }
}

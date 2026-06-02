import assert from 'node:assert/strict';

import { TOKENS, getTokenMotionEnterDelay } from '../tokens';
import { getElevationStyle } from '../elevation';

export async function runTests() {
  assert.equal(getTokenMotionEnterDelay(-1), 0);
  assert.equal(getTokenMotionEnterDelay(0), 0);
  assert.equal(getTokenMotionEnterDelay(2), TOKENS.motion.enter.delayStep * 2);
  assert.equal(getTokenMotionEnterDelay(999), TOKENS.motion.enter.delayMax);

  const webElevation = getElevationStyle(TOKENS.elevation.level2, '#000000', 'web');
  assert.deepEqual(webElevation, { boxShadow: '0px 2px 10px rgba(0, 0, 0, 0.06)' });

  const nativeElevation = getElevationStyle(TOKENS.elevation.level2, '#000000', 'native');
  assert.deepEqual(nativeElevation, {
    shadowColor: '#000000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.06,
    shadowRadius: 10,
    elevation: 2,
  });
}

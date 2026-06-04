import { describe, it, expect } from '@jest/globals';

import { TOKENS, getTokenMotionEnterDelay } from '../tokens';
import { getElevationStyle } from '../elevation';

describe('tokens', () => {
  it('clamps the motion enter delay', () => {
    expect(getTokenMotionEnterDelay(-1)).toBe(0);
    expect(getTokenMotionEnterDelay(0)).toBe(0);
    expect(getTokenMotionEnterDelay(2)).toBe(TOKENS.motion.enter.delayStep * 2);
    expect(getTokenMotionEnterDelay(999)).toBe(TOKENS.motion.enter.delayMax);
  });

  it('builds the web elevation style', () => {
    expect(getElevationStyle(TOKENS.elevation.level2, '#000000', 'web')).toEqual({
      boxShadow: '0px 2px 10px rgba(0, 0, 0, 0.06)',
    });
  });

  it('builds the native elevation style', () => {
    expect(getElevationStyle(TOKENS.elevation.level2, '#000000', 'native')).toEqual({
      shadowColor: '#000000',
      shadowOffset: { width: 0, height: 2 },
      shadowOpacity: 0.06,
      shadowRadius: 10,
      elevation: 2,
    });
  });
});

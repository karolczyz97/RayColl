import { describe, expect, it } from '@jest/globals';

import {
  createShapeSurfacePath,
  getProgressAccessibilityValue,
  getProgressRatio,
  getStopDotOpacity,
  STOP_DOT_SIZE,
  TRACK_END_GAP,
} from '../expressiveGeometry';

describe('expressiveGeometry', () => {
  it('getProgressRatio', () => {
    expect(getProgressRatio({ value: 50, max: 100 })).toBe(0.5);
    expect(getProgressRatio({ value: -1, max: 100 })).toBe(0);
    expect(getProgressRatio({ value: 150, max: 100 })).toBe(1);
    expect(getProgressRatio({ value: 10, max: 0 })).toBe(0);
  });

  it('getProgressAccessibilityValue', () => {
    expect(getProgressAccessibilityValue({ value: 150, max: 100 })).toEqual({
      min: 0,
      max: 100,
      now: 100,
    });
  });

  it('getStopDotOpacity', () => {
    expect(getStopDotOpacity(0)).toBe(0);
    expect(getStopDotOpacity(0.5)).toBe(1);
    expect(getStopDotOpacity(1)).toBe(1);
  });

  it('STOP_DOT_SIZE and TRACK_END_GAP are positive', () => {
    expect(STOP_DOT_SIZE).toBeGreaterThan(0);
    expect(TRACK_END_GAP).toBeGreaterThan(0);
  });

  it('createShapeSurfacePath', () => {
    for (const variant of ['soft', 'outline', 'blob'] as const) {
      const path = createShapeSurfacePath(variant);
      expect(path).toMatch(/^M/);
      expect(path).toMatch(/Z$/);
      expect(path.includes('C')).toBe(true);
    }
  });
});

import { describe, it, expect } from '@jest/globals';
import { computeStreak } from '../stats';
import { getLocalDateString } from '@/utils/date';

function daysAgo(n: number): string {
  const d = new Date();
  d.setDate(d.getDate() - n);
  return getLocalDateString(d);
}

function makeHeatmap(dates: number[]): Record<string, number> {
  const map: Record<string, number> = {};
  for (const offset of dates) {
    map[daysAgo(offset)] = 1;
  }
  return map;
}

describe('computeStreak', () => {
  it('returns 0 for empty heatmap', () => {
    expect(computeStreak({})).toBe(0);
  });

  it('counts consecutive days including today', () => {
    const heatmap = makeHeatmap([0, 1, 2]);
    expect(computeStreak(heatmap)).toBe(3);
  });

  it('breaks streak at first missing day before today', () => {
    const heatmap = makeHeatmap([0, 1, 3]); // missing day 2
    expect(computeStreak(heatmap)).toBe(2);
  });

  it('preserves streak when today is missing but yesterday is present', () => {
    const heatmap = makeHeatmap([1, 2, 3]); // no today, but yesterday exists
    expect(computeStreak(heatmap)).toBe(3);
  });

  it('returns 0 when both today and yesterday are missing', () => {
    const heatmap = makeHeatmap([2, 3, 4]); // neither today nor yesterday
    expect(computeStreak(heatmap)).toBe(0);
  });

  it('counts today only as streak of 1', () => {
    const heatmap = makeHeatmap([0]);
    expect(computeStreak(heatmap)).toBe(1);
  });

  it('handles streak starting today with gap after', () => {
    const heatmap = makeHeatmap([0, 2, 3]);
    expect(computeStreak(heatmap)).toBe(1);
  });

  it('handles streak starting yesterday without today', () => {
    const heatmap = makeHeatmap([1, 2]);
    expect(computeStreak(heatmap)).toBe(2);
  });
});

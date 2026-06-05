import { clampActivePageCount, MIN_PAGE_COUNT } from '../pages';

describe('clampActivePageCount', () => {
  it('clamps below MIN_PAGE_COUNT', () => {
    expect(clampActivePageCount(0, 5)).toBe(MIN_PAGE_COUNT);
    expect(clampActivePageCount(1, 5)).toBe(MIN_PAGE_COUNT);
    expect(clampActivePageCount(-1, 5)).toBe(MIN_PAGE_COUNT);
  });

  it('clamps above upperBound', () => {
    expect(clampActivePageCount(10, 5)).toBe(5);
    expect(clampActivePageCount(6, 5)).toBe(5);
  });

  it('returns the candidate when within bounds', () => {
    expect(clampActivePageCount(3, 5)).toBe(3);
    expect(clampActivePageCount(MIN_PAGE_COUNT, 5)).toBe(MIN_PAGE_COUNT);
  });

  it('floors the candidate', () => {
    expect(clampActivePageCount(3.7, 5)).toBe(3);
    expect(clampActivePageCount(4.1, 5)).toBe(4);
  });

  it('handles edge case where upperBound is below MIN_PAGE_COUNT', () => {
    expect(clampActivePageCount(3, 1)).toBe(MIN_PAGE_COUNT);
  });
});

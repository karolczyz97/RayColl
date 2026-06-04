import { clamp } from '../math';

describe('clamp', () => {
  it('clamps finite values', () => {
    expect(clamp(12, 0, 10)).toBe(10);
    expect(clamp(-2, 0, 10)).toBe(0);
    expect(clamp(5, 0, 10)).toBe(5);
  });

  it('returns the lower bound for NaN input', () => {
    expect(clamp(Number.NaN, 0, 10)).toBe(0);
  });
});

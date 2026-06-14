import { getRailIndicatorY, RailItemLayout } from '../navigationRailUtils';

const TOUCH_COMPACT = 40;

function makeLayouts(count: number, itemHeight = 56, startY = 32): RailItemLayout[] {
  return Array.from({ length: count }, (_, i) => ({
    y: startY + i * itemHeight,
    height: itemHeight,
  }));
}

describe('getRailIndicatorY', () => {
  it('centres the indicator within the active item', () => {
    const layouts = makeLayouts(4);
    const y = getRailIndicatorY(2, layouts);
    const expected = 32 + 2 * 56 + (56 - TOUCH_COMPACT) / 2;
    expect(y).toBe(expected);
  });

  it('returns null when the active index is out of bounds', () => {
    const layouts = makeLayouts(3);
    expect(getRailIndicatorY(3, layouts)).toBeNull();
    expect(getRailIndicatorY(-1, layouts)).toBeNull();
  });

  it('returns null when layout array is empty', () => {
    expect(getRailIndicatorY(0, [])).toBeNull();
  });

  it('handles uneven item heights', () => {
    const layouts: RailItemLayout[] = [
      { y: 0, height: 48 },
      { y: 48, height: 64 },
      { y: 112, height: 52 },
    ];
    expect(getRailIndicatorY(1, layouts)).toBe(48 + (64 - TOUCH_COMPACT) / 2);
  });
});

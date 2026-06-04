import { describe, it, expect } from '@jest/globals';

import { TOKENS } from '../../theme/tokens';
import {
  getDeterministicContainerWidth,
  getGridColumns,
  getGridGap,
  getGridItemWidth,
} from '../gridLayout';

describe('gridLayout', () => {
  it('clamps the grid gap across widths', () => {
    expect(getGridGap(320)).toBe(TOKENS.layout.minGap);
    expect(getGridGap(768)).toBe(20);
    expect(getGridGap(1600)).toBe(TOKENS.layout.maxGap);
  });

  it('accounts for all padding layers in the container width', () => {
    expect(getDeterministicContainerWidth(1600, TOKENS.layout.maxWidth, true, 0)).toBe(1168);
    expect(
      getDeterministicContainerWidth(900, TOKENS.layout.maxWidth, true, TOKENS.layout.railWidth),
    ).toBe(772);
  });

  it('expands columns exactly at the three-column threshold', () => {
    const gap = getGridGap(850);
    expect(getGridColumns(883, 8, TOKENS.layout.minCardWidth, TOKENS.layout.maxCols, gap)).toBe(2);
    expect(getGridColumns(884, 8, TOKENS.layout.minCardWidth, TOKENS.layout.maxCols, gap)).toBe(3);
    expect(getGridItemWidth(884, 3, gap)).toBe(280);
  });
});

import { describe, it, expect } from '@jest/globals';

import {
  getNavigationRailWidth,
  getWindowSizeClass,
  isExpandedWindowSize,
} from '../windowSizeClass';
import { TOKENS } from '../../theme/tokens';

describe('windowSizeClass', () => {
  it('maps width to the correct size class', () => {
    expect(getWindowSizeClass(0)).toBe('compact');
    expect(getWindowSizeClass(599)).toBe('compact');
    expect(getWindowSizeClass(600)).toBe('medium');
    expect(getWindowSizeClass(839)).toBe('medium');
    expect(getWindowSizeClass(840)).toBe('expanded');
    expect(getWindowSizeClass(1239)).toBe('expanded');
    expect(getWindowSizeClass(1240)).toBe('expanded');
  });

  it('flags expanded sizes', () => {
    expect(isExpandedWindowSize(839)).toBe(false);
    expect(isExpandedWindowSize(840)).toBe(true);
  });

  it('derives navigation rail width from window width', () => {
    expect(getNavigationRailWidth(599)).toBe(0);
    expect(getNavigationRailWidth(600)).toBe(TOKENS.layout.railWidth);
    expect(getNavigationRailWidth(840)).toBe(TOKENS.layout.railWidth);
  });
});

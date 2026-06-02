import assert from 'node:assert/strict';

import {
  getNavigationRailWidth,
  getWindowSizeClass,
  isExpandedWindowSize,
} from '../windowSizeClass';
import { TOKENS } from '../../theme/tokens';

export async function runTests() {
  assert.equal(getWindowSizeClass(0), 'compact');
  assert.equal(getWindowSizeClass(599), 'compact');
  assert.equal(getWindowSizeClass(600), 'medium');
  assert.equal(getWindowSizeClass(839), 'medium');
  assert.equal(getWindowSizeClass(840), 'expanded');
  assert.equal(getWindowSizeClass(1239), 'expanded');
  assert.equal(getWindowSizeClass(1240), 'expanded');

  assert.equal(isExpandedWindowSize(839), false);
  assert.equal(isExpandedWindowSize(840), true);

  assert.equal(getNavigationRailWidth(599), 0);
  assert.equal(getNavigationRailWidth(600), TOKENS.layout.railWidth);
  assert.equal(getNavigationRailWidth(840), TOKENS.layout.railWidth);
}

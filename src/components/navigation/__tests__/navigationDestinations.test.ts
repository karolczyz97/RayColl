import assert from 'node:assert/strict';

import {
  getActiveDestination,
  isImmersivePathname,
} from '../navigationDestinations';

export async function runTests() {
  assert.equal(getActiveDestination('/'), 'dashboard');
  assert.equal(getActiveDestination('/study/deck-1'), 'dashboard');
  assert.equal(getActiveDestination('/browse/deck-1'), 'dashboard');
  assert.equal(getActiveDestination('/settings/deck-1'), 'dashboard');

  assert.equal(getActiveDestination('/stats'), 'stats');
  assert.equal(getActiveDestination('/app-settings'), 'settings');
  assert.equal(getActiveDestination('/import'), null);

  assert.equal(isImmersivePathname('/'), false);
  assert.equal(isImmersivePathname('/stats'), false);
  assert.equal(isImmersivePathname('/app-settings'), false);
  assert.equal(isImmersivePathname('/import'), true);
  assert.equal(isImmersivePathname('/study/deck-1'), true);
  assert.equal(isImmersivePathname('/browse/deck-1'), true);
  assert.equal(isImmersivePathname('/settings/deck-1'), true);
}

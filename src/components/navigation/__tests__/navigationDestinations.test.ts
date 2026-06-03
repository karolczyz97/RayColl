import assert from 'node:assert/strict';

import {
  getActiveDestination,
} from '../navigationDestinations';

export async function runTests() {
  assert.equal(getActiveDestination('/'), 'dashboard');
  assert.equal(getActiveDestination('/study/deck-1'), 'dashboard');
  assert.equal(getActiveDestination('/browse/deck-1'), 'dashboard');
  assert.equal(getActiveDestination('/settings/deck-1'), 'dashboard');

  assert.equal(getActiveDestination('/stats'), 'stats');
  assert.equal(getActiveDestination('/app-settings'), 'settings');
  assert.equal(getActiveDestination('/import'), null);
}

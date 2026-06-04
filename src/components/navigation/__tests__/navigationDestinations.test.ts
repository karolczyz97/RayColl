import { describe, expect, it } from '@jest/globals';

import { getActiveDestination } from '../navigationDestinations';

describe('navigationDestinations', () => {
  it('getActiveDestination', () => {
    expect(getActiveDestination('/')).toBe('dashboard');
    expect(getActiveDestination('/study/deck-1')).toBe('dashboard');
    expect(getActiveDestination('/browse/deck-1')).toBe('dashboard');
    expect(getActiveDestination('/settings/deck-1')).toBe('dashboard');

    expect(getActiveDestination('/stats')).toBe('stats');
    expect(getActiveDestination('/app-settings')).toBe('settings');
    expect(getActiveDestination('/import')).toBeNull();
  });
});

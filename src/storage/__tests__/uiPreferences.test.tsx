import AsyncStorage from '@react-native-async-storage/async-storage';

import { STORAGE_KEYS } from '../../constants/storageKeys';
import {
  clearUiPreferenceCache,
  loadUiPreferences,
  setUiPreference,
} from '../uiPreferences';

describe('uiPreferences', () => {
  beforeEach(async () => {
    jest.clearAllMocks();
    await AsyncStorage.clear();
    clearUiPreferenceCache();
  });

  it('keeps the cache aligned with persisted storage when writes fail', async () => {
    jest.spyOn(AsyncStorage, 'setItem').mockRejectedValueOnce(new Error('storage unavailable'));

    await expect(setUiPreference(STORAGE_KEYS.NAV_RAIL_VISIBLE, 'false')).rejects.toThrow(
      'storage unavailable',
    );

    const preferences = await loadUiPreferences();

    expect(preferences[STORAGE_KEYS.NAV_RAIL_VISIBLE]).toBeNull();
  });
});

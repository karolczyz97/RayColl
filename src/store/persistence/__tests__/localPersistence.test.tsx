import AsyncStorage from '@react-native-async-storage/async-storage';

import { STORAGE_KEYS } from '@/constants/storageKeys';
import { loadLocalData } from '../localPersistence';

describe('localPersistence', () => {
  beforeEach(async () => {
    jest.restoreAllMocks();
    await AsyncStorage.clear();
  });

  it('keeps valid local slices when another slice contains invalid JSON', async () => {
    const errorSpy = jest.spyOn(console, 'error').mockImplementation(() => undefined);

    await AsyncStorage.setItem(STORAGE_KEYS.LOCAL_GROUPS, '{bad json');
    await AsyncStorage.setItem(STORAGE_KEYS.LOCAL_MODES, JSON.stringify([]));
    await AsyncStorage.setItem(STORAGE_KEYS.LOCAL_HEATMAP, JSON.stringify({ '2026-06-04': 3 }));

    const loaded = await loadLocalData();

    expect(loaded).not.toBeNull();
    expect(loaded?.groups).toEqual([]);
    expect(loaded?.activityHeatmap).toEqual({ '2026-06-04': 3 });
    expect(errorSpy).toHaveBeenCalledWith('Failed to parse local groups:', expect.any(SyntaxError));
  });

  it('keeps valid local slices when an item in another slice is corrupt', async () => {
    const errorSpy = jest.spyOn(console, 'error').mockImplementation(() => undefined);

    await AsyncStorage.setItem(STORAGE_KEYS.LOCAL_GROUPS, JSON.stringify([null]));
    await AsyncStorage.setItem(STORAGE_KEYS.LOCAL_MODES, JSON.stringify([]));
    await AsyncStorage.setItem(STORAGE_KEYS.LOCAL_HEATMAP, JSON.stringify({ '2026-06-04': 3 }));

    const loaded = await loadLocalData();

    expect(loaded).not.toBeNull();
    expect(loaded?.groups).toEqual([]);
    expect(loaded?.activityHeatmap).toEqual({ '2026-06-04': 3 });
    expect(errorSpy).toHaveBeenCalledWith('Failed to normalize local groups item 0:', expect.any(Error));
  });
});

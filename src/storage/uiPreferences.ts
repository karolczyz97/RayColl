import AsyncStorage from '@react-native-async-storage/async-storage';

import { STORAGE_KEYS } from '@/constants/storageKeys';

export const UI_PREFERENCE_STORAGE_KEYS = [
  STORAGE_KEYS.THEME_PREF,
  STORAGE_KEYS.USE_SYSTEM_COLORS,
  STORAGE_KEYS.TTS_RATE,
  STORAGE_KEYS.NAV_RAIL_VISIBLE,
  STORAGE_KEYS.NAV_RAIL_EXPANDED,
] as const;

type UiPreferenceStorageKey = (typeof UI_PREFERENCE_STORAGE_KEYS)[number];
type UiPreferenceSnapshot = Record<UiPreferenceStorageKey, string | null>;

const uiPreferenceCache = new Map<UiPreferenceStorageKey, string | null>();

export async function loadUiPreferences(): Promise<UiPreferenceSnapshot> {
  const missingKeys = UI_PREFERENCE_STORAGE_KEYS.filter((key) => !uiPreferenceCache.has(key));

  if (missingKeys.length > 0) {
    const entries = await AsyncStorage.multiGet(missingKeys);
    entries.forEach(([key, value]) => {
      if (isUiPreferenceStorageKey(key)) {
        uiPreferenceCache.set(key, value);
      }
    });
  }

  return UI_PREFERENCE_STORAGE_KEYS.reduce((snapshot, key) => {
    snapshot[key] = uiPreferenceCache.get(key) ?? null;
    return snapshot;
  }, {} as UiPreferenceSnapshot);
}

export async function setUiPreference(
  key: UiPreferenceStorageKey,
  value: string,
): Promise<void> {
  await AsyncStorage.setItem(key, value);
  uiPreferenceCache.set(key, value);
}

export function clearUiPreferenceCache() {
  uiPreferenceCache.clear();
}

function isUiPreferenceStorageKey(key: string): key is UiPreferenceStorageKey {
  return UI_PREFERENCE_STORAGE_KEYS.includes(key as UiPreferenceStorageKey);
}

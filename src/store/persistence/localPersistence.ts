import AsyncStorage from '@react-native-async-storage/async-storage';
import { STORAGE_KEYS } from '@/constants/storageKeys';
import type { StoreData } from '@/types/models';
import {
  normalizeActivityHeatmap,
  normalizeGroup,
  normalizeStoreData,
  normalizeStudyMode,
} from '@/store/storeDataNormalization';
import { validateBackupData, validateGroup, validateStudyMode } from '@/utils/backupValidation';

export type { StoreData };

function parseStoredItems<T>(
  raw: string | null,
  label: string,
  normalizeItem: (value: T) => T,
  validateItem: (value: T) => void,
): T[] {
  if (!raw) {
    return [];
  }

  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch (err) {
    console.error(`Failed to parse local ${label}:`, err);
    return [];
  }

  if (!Array.isArray(parsed)) {
    return [];
  }

  return parsed.flatMap((item, index) => {
    try {
      const normalized = normalizeItem(item as T);
      validateItem(normalized);
      return [normalized];
    } catch (err) {
      console.error(`Failed to normalize local ${label} item ${index}:`, err);
      return [];
    }
  });
}

function parseStoredHeatmap(raw: string | null): Record<string, number> {
  if (!raw) {
    return {};
  }

  try {
    return normalizeActivityHeatmap(JSON.parse(raw));
  } catch (err) {
    console.error('Failed to parse local activity heatmap:', err);
    return {};
  }
}

export async function loadLocalData(userId?: string): Promise<StoreData | null> {
  try {
    const keys = userId
      ? {
          groups: STORAGE_KEYS.USER_GROUPS(userId),
          modes: STORAGE_KEYS.USER_MODES(userId),
          heatmap: STORAGE_KEYS.USER_HEATMAP(userId),
        }
      : {
          groups: STORAGE_KEYS.LOCAL_GROUPS,
          modes: STORAGE_KEYS.LOCAL_MODES,
          heatmap: STORAGE_KEYS.LOCAL_HEATMAP,
        };

    const [storedGroups, storedModes, storedHeatmap] = await Promise.all([
      AsyncStorage.getItem(keys.groups),
      AsyncStorage.getItem(keys.modes),
      AsyncStorage.getItem(keys.heatmap),
    ]);

    // Nothing stored at all -> no local data (caller seeds).
    if (storedGroups == null && storedModes == null && storedHeatmap == null) {
      return null;
    }

    // Assemble from whatever exists so a partial write (e.g. groups saved but
    // modes missing) does not discard everything. Missing pieces are filled in
    // by normalizeStoreData (built-in modes, empty heatmap).
    const data: StoreData = {
      groups: parseStoredItems(
        storedGroups,
        'groups',
        normalizeGroup,
        validateGroup,
      ),
      studyModes: parseStoredItems(
        storedModes,
        'study modes',
        normalizeStudyMode,
        validateStudyMode,
      ),
      activityHeatmap: parseStoredHeatmap(storedHeatmap),
    };

    // Normalize first so data that normalization can repair is not rejected by
    // validation; then validate the canonical result.
    const normalized = normalizeStoreData(data);
    validateBackupData(normalized);
    return normalized;
  } catch (err) {
    console.error('Failed to load local data:', err);
  }
  return null;
}

export async function saveLocalData(userId: string | undefined, data: StoreData): Promise<void> {
  try {
    const entries: [string, string][] = userId
      ? [
          [STORAGE_KEYS.USER_GROUPS(userId), JSON.stringify(data.groups)],
          [STORAGE_KEYS.USER_MODES(userId), JSON.stringify(data.studyModes)],
          [STORAGE_KEYS.USER_HEATMAP(userId), JSON.stringify(data.activityHeatmap)],
        ]
      : [
          [STORAGE_KEYS.LOCAL_GROUPS, JSON.stringify(data.groups)],
          [STORAGE_KEYS.LOCAL_MODES, JSON.stringify(data.studyModes)],
          [STORAGE_KEYS.LOCAL_HEATMAP, JSON.stringify(data.activityHeatmap)],
        ];

    await AsyncStorage.multiSet(entries);
  } catch (err) {
    console.error('Failed to save local data:', err);
    throw err;
  }
}

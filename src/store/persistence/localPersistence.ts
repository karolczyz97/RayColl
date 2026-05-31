import AsyncStorage from '@react-native-async-storage/async-storage';
import { STORAGE_KEYS } from '../../constants/storageKeys';
import { FlashcardGroup, StudyMode } from '../../types/models';
import { normalizeStoreData } from '../storeDataNormalization';
import { validateBackupData } from '../../utils/backupValidation';

export interface StoreData {
  groups: FlashcardGroup[];
  studyModes: StudyMode[];
  activityHeatmap: Record<string, number>;
  schemaVersion?: number;
  lastSyncedAt?: number;
}

export async function loadLocalData(userId?: string): Promise<StoreData | null> {
  try {
    let data: StoreData | null = null;
    if (userId) {
      const cachedGroups = await AsyncStorage.getItem(STORAGE_KEYS.USER_GROUPS(userId));
      const cachedModes = await AsyncStorage.getItem(STORAGE_KEYS.USER_MODES(userId));
      const cachedHeatmap = await AsyncStorage.getItem(STORAGE_KEYS.USER_HEATMAP(userId));

      if (cachedGroups && cachedModes) {
        data = {
          groups: JSON.parse(cachedGroups),
          studyModes: JSON.parse(cachedModes),
          activityHeatmap: cachedHeatmap ? JSON.parse(cachedHeatmap) : {},
        };
      }
    } else {
      const storedGroups = await AsyncStorage.getItem(STORAGE_KEYS.LOCAL_GROUPS);
      const storedModes = await AsyncStorage.getItem(STORAGE_KEYS.LOCAL_MODES);
      const storedHeatmap = await AsyncStorage.getItem(STORAGE_KEYS.LOCAL_HEATMAP);

      if (storedGroups && storedModes) {
        data = {
          groups: JSON.parse(storedGroups),
          studyModes: JSON.parse(storedModes),
          activityHeatmap: storedHeatmap ? JSON.parse(storedHeatmap) : {},
        };
      }
    }

    if (data) {
      validateBackupData(data);
      return normalizeStoreData(data);
    }
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
export async function getSeedVersion(): Promise<number> {
  try {
    const ver = await AsyncStorage.getItem(STORAGE_KEYS.SEED_VERSION);
    return ver ? Number(ver) : 0;
  } catch (err) {
    console.error('Failed to read seed version:', err);
    return 0;
  }
}

export async function setSeedVersion(version: number): Promise<void> {
  try {
    await AsyncStorage.setItem(STORAGE_KEYS.SEED_VERSION, String(version));
  } catch (err) {
    console.error('Failed to save seed version:', err);
    throw err;
  }
}

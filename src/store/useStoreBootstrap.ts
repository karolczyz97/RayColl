import { useEffect } from 'react';
import type { Dispatch, SetStateAction } from 'react';
import type { User } from 'firebase/auth';
import type { FlashcardGroup, StudyMode } from '../types/models';
import { onAuthChange } from '../services/firebase';
import { loadCloudData } from './persistence/firebasePersistence';
import { createSeedGroups, SEED_VERSION } from './seed/seedGroups';
import { createSeedModes } from './seed/seedModes';
import { mergeUserData } from './selectors/merge';
import { getSeedVersion, loadLocalData, setSeedVersion } from './persistence/localPersistence';
import type { StoreData } from './persistence/localPersistence';
import { FIRESTORE_SCHEMA_VERSION } from './persistence/firestoreSchema';
import { normalizeStoreData, normalizeStudyModes } from './storeDataNormalization';
import { getErrorMessage } from '../utils/errors';

interface UseStoreBootstrapParams {
  user: User | null;
  setUser: Dispatch<SetStateAction<User | null>>;
  setIsLoading: Dispatch<SetStateAction<boolean>>;
  setLastSyncError: Dispatch<SetStateAction<string | null>>;
  setLastPersistenceError: Dispatch<SetStateAction<string | null>>;
  setLastStoreError: Dispatch<SetStateAction<string | null>>;
  setMigrationPending: Dispatch<SetStateAction<boolean>>;
  setPendingGuestSnapshot: Dispatch<SetStateAction<StoreData | null>>;
  applySnapshot: (snapshot: StoreData) => void;
  persistLocalSnapshot: (snapshot: StoreData & { uid: string | null }) => Promise<void>;
  persistNow: (snapshot: StoreData & { uid: string | null }) => Promise<void>;
}

export function useStoreBootstrap({
  user,
  setUser,
  setIsLoading,
  setLastSyncError,
  setLastPersistenceError,
  setLastStoreError,
  setMigrationPending,
  setPendingGuestSnapshot,
  applySnapshot,
  persistLocalSnapshot,
  persistNow,
}: UseStoreBootstrapParams) {
  useEffect(() => {
    return onAuthChange((nextUser) => {
      setUser(nextUser);
    });
  }, [setUser]);

  useEffect(() => {
    let active = true;
    const targetUid = user ? user.uid : null;

    async function finishBootstrap(
      groups: FlashcardGroup[],
      modes: StudyMode[],
      heatmap: Record<string, number>,
      seedVer: number,
      uid: string | null,
      cloudSynced: boolean,
      cloudLoadFailed: boolean,
    ) {
      if (seedVer < SEED_VERSION) {
        if (seedVer === 0 && groups.length === 0) {
          groups = createSeedGroups();
        }

        modes = normalizeStudyModes(modes);

        await setSeedVersion(SEED_VERSION).catch((err) => {
          setLastPersistenceError(getErrorMessage(err));
        });
      }

      if (groups.length === 0) {
        groups = createSeedGroups();
      }
      if (modes.length === 0) {
        modes = createSeedModes();
      }

      if (active) {
        const snapshot = normalizeStoreData({
          groups,
          studyModes: modes,
          activityHeatmap: heatmap,
          schemaVersion: FIRESTORE_SCHEMA_VERSION,
          ...(cloudSynced ? { lastSyncedAt: Date.now() } : {}),
        });
        applySnapshot(snapshot);
        if (cloudLoadFailed) {
          await persistLocalSnapshot({ uid, ...snapshot });
          setLastStoreError(null);
        } else {
          await persistNow({ uid, ...snapshot });
        }
      }
    }

    async function loadData() {
      setIsLoading(true);
      try {
        const seedVer = await getSeedVersion();
        let loadedGroups: FlashcardGroup[] = [];
        let loadedModes: StudyMode[] = [];
        let loadedHeatmap: Record<string, number> = {};

        const localCache = await loadLocalData(targetUid || undefined);
        if (localCache) {
          loadedGroups = localCache.groups;
          loadedModes = localCache.studyModes;
          loadedHeatmap = localCache.activityHeatmap;
        }

        if (!targetUid) {
          await finishBootstrap(loadedGroups, loadedModes, loadedHeatmap, seedVer, null, false, false);
          return;
        }

        let cloudLoadFailed = false;
        let cloudSynced = false;
        let cloudData: StoreData | null = null;
        try {
          cloudData = await loadCloudData(targetUid);
          setLastSyncError(null);
        } catch (err) {
          cloudLoadFailed = true;
          const message = getErrorMessage(err);
          setLastSyncError(message);
          setLastStoreError(message);
        }

        if (cloudData) {
          const merged = mergeUserData(
            { groups: loadedGroups, studyModes: loadedModes, activityHeatmap: loadedHeatmap },
            cloudData,
          );
          loadedGroups = merged.groups;
          loadedModes = merged.studyModes;
          loadedHeatmap = merged.activityHeatmap;
          cloudSynced = true;
        } else if (cloudLoadFailed) {
          // network error — use user-local cache (already in loaded vars)
        } else {
          // cloudData == null — NEW ACCOUNT
          if (loadedGroups.length === 0) {
            const guestData = await loadLocalData();

            if (guestData && guestData.groups.length > 0) {
              setMigrationPending(true);
              setPendingGuestSnapshot(guestData);
              return;
            }
          }
        }

        await finishBootstrap(
          loadedGroups,
          loadedModes,
          loadedHeatmap,
          seedVer,
          targetUid,
          cloudSynced,
          cloudLoadFailed,
        );
      } catch (err) {
        console.error('Failed to initialize flashcard store:', err);
        setLastStoreError(getErrorMessage(err));
      } finally {
        if (active) {
          setIsLoading(false);
        }
      }
    }

    void loadData();

    return () => {
      active = false;
    };
  }, [
    applySnapshot,
    persistLocalSnapshot,
    persistNow,
    setIsLoading,
    setLastPersistenceError,
    setLastStoreError,
    setLastSyncError,
    setMigrationPending,
    setPendingGuestSnapshot,
    user,
  ]);
}

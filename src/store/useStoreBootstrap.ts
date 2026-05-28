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
import { normalizeStoreData } from './storeDataNormalization';

function getErrorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

interface UseStoreBootstrapParams {
  user: User | null;
  setUser: Dispatch<SetStateAction<User | null>>;
  setIsLoading: Dispatch<SetStateAction<boolean>>;
  setLastPersistenceError: Dispatch<SetStateAction<string | null>>;
  setLastStoreError: Dispatch<SetStateAction<string | null>>;
  applySnapshot: (snapshot: StoreData) => void;
  persistNow: (snapshot: StoreData & { uid: string | null }) => Promise<void>;
}

export function useStoreBootstrap({
  user,
  setUser,
  setIsLoading,
  setLastPersistenceError,
  setLastStoreError,
  applySnapshot,
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

        if (targetUid) {
          const cloudData = await loadCloudData(targetUid);
          if (cloudData) {
            const merged = mergeUserData(
              { groups: loadedGroups, studyModes: loadedModes, activityHeatmap: loadedHeatmap },
              cloudData,
            );
            loadedGroups = merged.groups;
            loadedModes = merged.studyModes;
            loadedHeatmap = merged.activityHeatmap;
          }
        }

        if (seedVer < SEED_VERSION) {
          if (seedVer === 0 && loadedGroups.length === 0) {
            loadedGroups = createSeedGroups();
          }

          const defaultModes = createSeedModes();
          const customModes = loadedModes.filter(
            (mode) => mode.id !== 'classic' && mode.id !== 'listen-speak',
          );
          loadedModes = [...defaultModes, ...customModes];

          await setSeedVersion(SEED_VERSION).catch((err) => {
            setLastPersistenceError(getErrorMessage(err));
          });
        }

        if (loadedGroups.length === 0) {
          loadedGroups = createSeedGroups();
        }
        if (loadedModes.length === 0) {
          loadedModes = createSeedModes();
        }

        if (active) {
          const snapshot = normalizeStoreData({
            groups: loadedGroups,
            studyModes: loadedModes,
            activityHeatmap: loadedHeatmap,
          });
          applySnapshot(snapshot);
          await persistNow({ uid: targetUid, ...snapshot });
        }
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
  }, [applySnapshot, persistNow, setIsLoading, setLastPersistenceError, setLastStoreError, user]);
}

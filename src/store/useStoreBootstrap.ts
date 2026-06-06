import { useEffect } from 'react';
import type { Dispatch, SetStateAction } from 'react';
import type { User } from 'firebase/auth';
import type { FlashcardGroup, StudyMode, StoreData, ModeStep } from '@/types/models';
import { onAuthChange } from '@/services/firebase';
import { loadCloudData } from './persistence/firebasePersistence';
import { createSeedGroups, SEED_VERSION } from './seed/seedGroups';
import { createSeedModes } from './seed/seedModes';
import { deepEqual } from '@/utils/deepEqual';
import { mergeUserData } from './selectors/merge';
import { getSeedVersion, loadLocalData, setSeedVersion } from './persistence/localPersistence';
import { normalizeStoreData, normalizeStudyModes } from './storeDataNormalization';
import { getErrorMessage } from '@/utils/errors';
import { purgeExpiredArchivesAction } from './actions/groupActions';
import { ARCHIVE_RETENTION_MS } from '@/constants/archive';

export function shouldTriggerMigration(
  hasUserLocalCache: boolean,
  guestHasData: boolean,
): boolean {
  return !hasUserLocalCache && guestHasData;
}

function stepsWithoutIds(steps: ModeStep[]): Omit<ModeStep, 'id'>[] {
  return steps.map(({ id: _id, ...rest }) => rest);
}

/** A custom mode, or a built-in whose name/steps the user edited, counts as data. */
function isUserAuthoredMode(mode: StudyMode): boolean {
  if (!mode.isBuiltIn) return true;
  const sourceId = mode.builtInSourceId ?? mode.id;
  const seed = createSeedModes().find(
    (seedMode) => (seedMode.builtInSourceId ?? seedMode.id) === sourceId,
  );
  if (!seed) return true;
  // Step ids are generated during normalization, so compare without them.
  return mode.name !== seed.name || !deepEqual(stepsWithoutIds(mode.steps), stepsWithoutIds(seed.steps));
}

export function getGuestHasData(guestData: StoreData | null): boolean {
  if (!guestData) return false;
  if ((guestData.groups?.length ?? 0) > 0) return true;
  if (Object.keys(guestData.activityHeatmap ?? {}).length > 0) return true;
  return (guestData.studyModes ?? []).some(isUserAuthoredMode);
}

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
      const hadNoLocalGroups = groups.length === 0;
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

      groups = purgeExpiredArchivesAction(groups, Date.now(), ARCHIVE_RETENTION_MS).groups;

      if (active) {
        const snapshot = normalizeStoreData({
          groups,
          studyModes: modes,
          activityHeatmap: heatmap,
          ...(cloudSynced ? { lastSyncedAt: Date.now() } : {}),
        });
        applySnapshot(snapshot);
        // A signed-in user whose cloud load FAILED and who has no local data is shown
        // seed decks only as an ephemeral fallback. Persisting them as the user's
        // local cache would shadow the real (unreachable) cloud data and risk
        // duplicate seeds on the next successful merge — keep them in memory only.
        if (cloudLoadFailed && uid !== null && hadNoLocalGroups) {
          return;
        }
        if (cloudLoadFailed) {
          // Best-effort local fallback: keep the cloud failure visible via
          // lastSyncError/lastStoreError instead of clearing it here.
          await persistLocalSnapshot({ uid, ...snapshot });
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
          if (active) setLastSyncError(null);
        } catch (err) {
          cloudLoadFailed = true;
          if (active) {
            const message = getErrorMessage(err);
            setLastSyncError(message);
            setLastStoreError(message);
          }
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
            if (active && shouldTriggerMigration(false, getGuestHasData(guestData))) {
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

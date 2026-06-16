import { useEffect, useState } from 'react';
import type { Dispatch, SetStateAction } from 'react';
import type { User } from 'firebase/auth';
import type { FlashcardGroup, StudyMode, StoreData, ModeStep } from '@/types/models';
import { onAuthChange } from '@/services/firebase';
import { loadCloudData } from './persistence/firebasePersistence';
import { createSeedGroups } from './seed/seedGroups';
import { createSeedModes } from './seed/seedModes';
import { deepEqual } from '@/utils/deepEqual';
import { mergeUserData } from './selectors/merge';
import { loadLocalData } from './persistence/localPersistence';
import { normalizeStoreData } from './storeDataNormalization';
import { getErrorMessage } from '@/utils/errors';
import { withTimeout } from '@/utils/withTimeout';
import { purgeExpiredArchivesAction } from './actions/groupActions';
import { ARCHIVE_RETENTION_MS } from '@/constants/archive';

// Bound the startup cloud load so a stalled/unreachable Firestore connection
// cannot hold the spinner hostage. Signed-in startup waits for the cloud so the
// first render is already the merged, canonical data; on timeout/error we fall
// back to the local cache and surface the error via lastSyncError.
const CLOUD_LOAD_TIMEOUT_MS = 8000;

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
  const sourceId = mode.builtInSourceId;
  if (!sourceId) return true;
  const seed = createSeedModes().find(
    (seedMode) => seedMode.builtInSourceId === sourceId,
  );
  if (!seed) return true;
  // Step ids are stripped during normalization, so compare without them.
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
  // Gate the data load on the first auth state emission. onAuthChange always fires:
  // synchronously (with null) when Firebase is unconfigured, and from local auth
  // persistence (no network required) on native otherwise. This prevents the app
  // from briefly showing seed/guest decks before the persisted sign-in is restored.
  const [authResolved, setAuthResolved] = useState(false);

  useEffect(() => {
    return onAuthChange((nextUser) => {
      setUser(nextUser);
      setAuthResolved(true); // React 18 batches both setStates → one re-render
    });
  }, [setUser]);

  useEffect(() => {
    if (!authResolved) return; // spinner stays up until auth state is known

    let active = true;
    const targetUid = user ? user.uid : null;

    // Apply first-run seed defaults and purge expired archives. Pure transform —
    // no persistence side effects.
    function prepareCollections(
      groups: FlashcardGroup[],
      modes: StudyMode[],
    ): { groups: FlashcardGroup[]; modes: StudyMode[] } {
      let nextGroups = groups;
      let nextModes = modes;
      if (nextGroups.length === 0) {
        nextGroups = createSeedGroups();
      }
      if (nextModes.length === 0) {
        nextModes = createSeedModes();
      }
      nextGroups = purgeExpiredArchivesAction(nextGroups, Date.now(), ARCHIVE_RETENTION_MS).groups;
      return { groups: nextGroups, modes: nextModes };
    }

    function buildSnapshot(
      groups: FlashcardGroup[],
      modes: StudyMode[],
      heatmap: Record<string, number>,
      cloudSynced: boolean,
    ): StoreData {
      return normalizeStoreData({
        groups,
        studyModes: modes,
        activityHeatmap: heatmap,
        ...(cloudSynced ? { lastSyncedAt: Date.now() } : {}),
      });
    }

    async function loadData() {
      setIsLoading(true);
      try {
        const localCache = await loadLocalData(targetUid || undefined);
        let loadedGroups: FlashcardGroup[] = localCache?.groups ?? [];
        let loadedModes: StudyMode[] = localCache?.studyModes ?? [];
        let loadedHeatmap: Record<string, number> = localCache?.activityHeatmap ?? {};
        const hadNoLocalGroups = loadedGroups.length === 0;

        // Guest: no cloud. Apply + persist locally and we are done.
        if (!targetUid) {
          const prepared = prepareCollections(loadedGroups, loadedModes);
          const snapshot = buildSnapshot(prepared.groups, prepared.modes, loadedHeatmap, false);
          if (!active) return;
          applySnapshot(snapshot);
          setIsLoading(false);
          await persistNow({ uid: null, ...snapshot });
          return;
        }

        // Signed in → wait for the cloud (bounded by the timeout) so the first
        // render already shows the merged, canonical data. On failure/timeout
        // the local cache below is shown instead and the error is surfaced.
        let cloudLoadFailed = false;
        let cloudSynced = false;
        let cloudData: StoreData | null = null;
        try {
          cloudData = await withTimeout(loadCloudData(targetUid), CLOUD_LOAD_TIMEOUT_MS, 'Cloud load');
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
          // network error / timeout — keep the local cache (already shown above)
        } else {
          // cloudData == null — NEW ACCOUNT
          if (hadNoLocalGroups) {
            const guestData = await loadLocalData();
            if (active && shouldTriggerMigration(false, getGuestHasData(guestData))) {
              setMigrationPending(true);
              setPendingGuestSnapshot(guestData);
              return;
            }
          }
        }

        const prepared = prepareCollections(loadedGroups, loadedModes);
        const finalSnapshot = buildSnapshot(prepared.groups, prepared.modes, loadedHeatmap, cloudSynced);

        if (!active) return;

        applySnapshot(finalSnapshot);

        // Data is on screen now — drop the spinner before persisting so the
        // (un-timed) cloud save can never hold startup hostage.
        setIsLoading(false);

        // A signed-in user whose cloud load FAILED and who has no local data is
        // shown seed decks only as an ephemeral fallback. Persisting them as the
        // user's local cache would shadow the real (unreachable) cloud data and
        // risk duplicate seeds on the next successful merge — keep them in memory.
        if (cloudLoadFailed && hadNoLocalGroups) {
          return;
        }
        if (cloudLoadFailed) {
          // Best-effort local fallback: keep the cloud failure visible via
          // lastSyncError/lastStoreError instead of clearing it here.
          await persistLocalSnapshot({ uid: targetUid, ...finalSnapshot });
        } else {
          await persistNow({ uid: targetUid, ...finalSnapshot });
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
  }, [
    authResolved,
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

import { useCallback, useEffect, useMemo, useRef } from 'react';
import { AppState, Platform } from 'react-native';
import type { User } from 'firebase/auth';
import type { Dispatch, MutableRefObject, SetStateAction } from 'react';
import type { FlashcardGroup, StudyMode, StoreData } from '@/types/models';
import { saveLocalData } from './persistence/localPersistence';
import { saveCloudData } from './persistence/firebasePersistence';
import { usePersistenceQueue, type PersistenceSnapshot } from './persistence/persistenceQueue';
import type { PersistOptions, SyncStatus } from './FlashcardStoreTypes';
import { createWebPersistenceHandlers } from './persistence/webLifecycle';
import { getErrorMessage } from '@/utils/errors';
import { withTimeout } from '@/utils/withTimeout';

// Cap how long a single local write may block the serialized write chain. If
// AsyncStorage hangs (e.g. a stuck native lock), the timeout rejects so the
// next queued snapshot is not blocked forever; the underlying write may still
// resolve later in the background.
const LOCAL_WRITE_TIMEOUT_MS = 8000;

interface UseStorePersistenceParams {
  groupsRef: MutableRefObject<FlashcardGroup[]>;
  studyModesRef: MutableRefObject<StudyMode[]>;
  heatmapRef: MutableRefObject<Record<string, number>>;
  userRef: MutableRefObject<User | null>;
  setGroups: Dispatch<SetStateAction<FlashcardGroup[]>>;
  setStudyModes: Dispatch<SetStateAction<StudyMode[]>>;
  setHeatmap: Dispatch<SetStateAction<Record<string, number>>>;
  setSyncStatus: Dispatch<SetStateAction<SyncStatus>>;
  setLastSyncError: Dispatch<SetStateAction<string | null>>;
  setLastPersistenceError: Dispatch<SetStateAction<string | null>>;
  setLastStoreError: Dispatch<SetStateAction<string | null>>;
}

export function useStorePersistence({
  groupsRef,
  studyModesRef,
  heatmapRef,
  userRef,
  setGroups,
  setStudyModes,
  setHeatmap,
  setSyncStatus,
  setLastSyncError,
  setLastPersistenceError,
  setLastStoreError,
}: UseStorePersistenceParams) {
  const studyReviewCountRef = useRef(0);
  const pendingStudySnapshotRef = useRef<PersistenceSnapshot | null>(null);
  const localWriteSeqRef = useRef<Promise<void>>(Promise.resolve());

  const getCurrentUid = useCallback(() => userRef.current?.uid ?? null, [userRef]);

  const applySnapshot = useCallback(
    (snapshot: StoreData) => {
      groupsRef.current = snapshot.groups;
      studyModesRef.current = snapshot.studyModes;
      heatmapRef.current = snapshot.activityHeatmap;
      setGroups(snapshot.groups);
      setStudyModes(snapshot.studyModes);
      setHeatmap(snapshot.activityHeatmap);
    },
    [groupsRef, heatmapRef, setGroups, setHeatmap, setStudyModes, studyModesRef],
  );

  const getSnapshot = useCallback(
    (uid = getCurrentUid()): PersistenceSnapshot => ({
      uid,
      groups: groupsRef.current,
      studyModes: studyModesRef.current,
      activityHeatmap: heatmapRef.current,
    }),
    [getCurrentUid, groupsRef, heatmapRef, studyModesRef],
  );

  const persistLocalSnapshot = useCallback(
    async (snapshot: PersistenceSnapshot): Promise<void> => {
      setSyncStatus('saving');
      setLastPersistenceError(null);

      const { uid, ...payload } = snapshot;

      // Serialize local writes so a fire-and-forget snapshot cannot land out of order
      // with an awaited one (e.g. review snapshot racing a deleteGroup flush).
      const doWrite = () =>
        withTimeout(saveLocalData(uid || undefined, payload), LOCAL_WRITE_TIMEOUT_MS, 'Local persistence');
      const writePromise = localWriteSeqRef.current.then(doWrite, doWrite);
      localWriteSeqRef.current = writePromise.catch(() => {});

      try {
        await writePromise;
        setSyncStatus('idle');
      } catch (err) {
        console.error('Local persistence failed:', err);
        setLastPersistenceError(getErrorMessage(err));
        setSyncStatus('error');
        throw err;
      }
    },
    [setLastPersistenceError, setSyncStatus],
  );

  const persistCloudSnapshot = useCallback(
    async (snapshot: PersistenceSnapshot): Promise<void> => {
      const { uid, ...payload } = snapshot;

      if (!uid) {
        setSyncStatus('idle');
        return;
      }

      setLastSyncError(null);

      try {
        setSyncStatus('syncing');
        await saveCloudData(uid, payload);
        setLastStoreError(null);
        setSyncStatus('idle');
      } catch (err) {
        console.error('Cloud sync failed:', err);
        setLastSyncError(getErrorMessage(err));
        setSyncStatus('error');
        throw err;
      }
    },
    [setLastSyncError, setLastStoreError, setSyncStatus],
  );

  const handleQueueSaving = useCallback(() => setSyncStatus('saving'), [setSyncStatus]);
  const handleQueueSynced = useCallback(() => {
    setSyncStatus('idle');
    setLastSyncError(null);
  }, [setLastSyncError, setSyncStatus]);
  const handleQueueError = useCallback(
    (err: unknown) => {
      setLastSyncError(getErrorMessage(err));
      setSyncStatus('error');
    },
    [setLastSyncError, setSyncStatus],
  );

  const { enqueue: enqueueCloudSnapshot, flush: flushCloudQueue } = usePersistenceQueue({
    delayMs: 1200,
    persistNow: persistCloudSnapshot,
    onSaving: handleQueueSaving,
    onSynced: handleQueueSynced,
    onError: handleQueueError,
  });

  const persistNow = useCallback(
    async (snapshot: PersistenceSnapshot) => {
      pendingStudySnapshotRef.current = null;
      studyReviewCountRef.current = 0;
      await persistLocalSnapshot(snapshot);
      await persistCloudSnapshot(snapshot);
    },
    [persistCloudSnapshot, persistLocalSnapshot],
  );

  const persistCurrentSnapshot = useCallback(
    (options?: PersistOptions) => {
      const snapshot = getSnapshot();

      void persistLocalSnapshot(snapshot).catch((err: unknown) => {
        console.error('Background local persistence failed:', err);
      });

      if (options?.immediate) {
        pendingStudySnapshotRef.current = null;
        studyReviewCountRef.current = 0;
        enqueueCloudSnapshot(snapshot, { immediate: true });
        return;
      }

      if (!snapshot.uid) {
        setSyncStatus('idle');
        return;
      }

      switch (options?.cloudMode) {
        case 'none':
          return;
        case 'study':
          pendingStudySnapshotRef.current = snapshot;
          studyReviewCountRef.current += 1;

          if (studyReviewCountRef.current >= 10) {
            studyReviewCountRef.current = 0;
            const reviewSnapshot = pendingStudySnapshotRef.current;
            pendingStudySnapshotRef.current = null;

            if (reviewSnapshot) {
              enqueueCloudSnapshot(reviewSnapshot, { immediate: true });
            }
          }
          return;
        case 'debounced':
        default:
          enqueueCloudSnapshot(snapshot);
      }
    },
    [enqueueCloudSnapshot, getSnapshot, persistLocalSnapshot, setSyncStatus],
  );

  const flushPersistence = useCallback(async () => {
    const snapshot = getSnapshot();

    await persistLocalSnapshot(snapshot);

    pendingStudySnapshotRef.current = null;
    studyReviewCountRef.current = 0;

    if (snapshot.uid) {
      enqueueCloudSnapshot(snapshot, { immediate: true });
    }

    await flushCloudQueue();
  }, [enqueueCloudSnapshot, flushCloudQueue, getSnapshot, persistLocalSnapshot]);

  useEffect(() => {
    const subscription = AppState.addEventListener('change', (nextState) => {
      if (nextState !== 'active') {
        void flushPersistence();
      }
    });

    return () => {
      subscription.remove();
    };
  }, [flushPersistence]);

  useEffect(() => {
    if (Platform.OS !== 'web' || typeof window === 'undefined') {
      return;
    }

    const { handleBeforeUnload, handlePageHide, handleVisibilityChange } =
      createWebPersistenceHandlers(flushPersistence, () => document.visibilityState);

    window.addEventListener('beforeunload', handleBeforeUnload);
    window.addEventListener('pagehide', handlePageHide);
    document.addEventListener('visibilitychange', handleVisibilityChange);
    return () => {
      window.removeEventListener('beforeunload', handleBeforeUnload);
      window.removeEventListener('pagehide', handlePageHide);
      document.removeEventListener('visibilitychange', handleVisibilityChange);
    };
  }, [flushPersistence]);

  useEffect(() => {
    return () => {
      void flushPersistence();
    };
  }, [flushPersistence]);

  const commitGroups = useCallback(
    (nextGroups: FlashcardGroup[], options?: PersistOptions) => {
      groupsRef.current = nextGroups;
      setGroups(nextGroups);
      persistCurrentSnapshot(options);
    },
    [groupsRef, persistCurrentSnapshot, setGroups],
  );

  const commitStudyModes = useCallback(
    (nextStudyModes: StudyMode[], options?: PersistOptions) => {
      studyModesRef.current = nextStudyModes;
      setStudyModes(nextStudyModes);
      persistCurrentSnapshot(options);
    },
    [persistCurrentSnapshot, setStudyModes, studyModesRef],
  );

  const commitHeatmap = useCallback(
    (nextHeatmap: Record<string, number>, options?: PersistOptions) => {
      heatmapRef.current = nextHeatmap;
      setHeatmap(nextHeatmap);
      persistCurrentSnapshot(options);
    },
    [heatmapRef, persistCurrentSnapshot, setHeatmap],
  );

  return useMemo(
    () => ({
      getCurrentUid,
      applySnapshot,
      getSnapshot,
      persistNow,
      persistLocalSnapshot,
      persistCurrentSnapshot,
      flushPersistence,
      commitGroups,
      commitStudyModes,
      commitHeatmap,
    }),
    [
      getCurrentUid,
      applySnapshot,
      getSnapshot,
      persistNow,
      persistLocalSnapshot,
      persistCurrentSnapshot,
      flushPersistence,
      commitGroups,
      commitStudyModes,
      commitHeatmap,
    ],
  );
}

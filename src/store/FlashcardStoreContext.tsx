import React, { createContext, useContext, useState, useEffect, useCallback, useRef } from 'react';
import type { User } from 'firebase/auth';
import type { FlashcardGroup, Flashcard, StudyMode } from '../types/models';
import { onAuthChange, signInWithGoogle, signOutUser } from '../services/firebase';
import { CardFilter } from '../constants/cardFilters';
import { validateBackupData } from '../utils/backupValidation';
import type {
  ImportDeckPayload,
  ImportDeckResult,
  NormalizedImportDeckPayload,
} from '../import/importDeck';
import { validateImportDeckPayload } from '../import/importDeck';

import {
  addGroupAction,
  updateGroupAction,
  deleteGroupAction,
  setVisiblePageCountAction,
  setStudyFilterAction,
  setActiveStudyModeAction,
  addGroupWithCardsAction,
} from './actions/groupActions';

import {
  addFlashcardAction,
  updateFlashcardAction,
  deleteFlashcardAction,
  addFlashcardsBulkAction,
} from './actions/cardActions';

import {
  addStudyModeAction,
  updateStudyModeAction,
  deleteStudyModeAction,
} from './actions/studyModeActions';

import { recordActivityAction } from './actions/activityActions';

import {
  loadLocalData,
  saveLocalData,
  getSeedVersion,
  setSeedVersion,
} from './persistence/localPersistence';
import type { StoreData } from './persistence/localPersistence';

import { loadCloudData, saveCloudData } from './persistence/firebasePersistence';
import { createSeedGroups, SEED_VERSION } from './seed/seedGroups';
import { createSeedModes } from './seed/seedModes';
import { getDueCards as selectDueCards } from './selectors/dueCards';
import { getGroupProgress as selectGroupProgress } from './selectors/progress';
import { mergeUserData } from './selectors/merge';

export interface FlashcardStore {
  groups: FlashcardGroup[];
  studyModes: StudyMode[];
  activityHeatmap: Record<string, number>;
  isLoading: boolean;
  user: User | null;
  syncStatus: 'idle' | 'loading' | 'saving' | 'syncing' | 'error';
  lastSyncError: string | null;
  lastPersistenceError: string | null;
  lastStoreError: string | null;
  signIn: () => Promise<void>;
  signOut: () => Promise<void>;
  addGroup: (name: string, languages: string[], pageNames: string[]) => string;
  addGroupWithCards: (
    name: string,
    languages: string[],
    pageNames: string[],
    cards: Omit<Flashcard, 'id' | 'srsState'>[],
  ) => string;
  importDeck: (payload: ImportDeckPayload) => Promise<ImportDeckResult>;
  updateGroup: (group: FlashcardGroup) => void;
  deleteGroup: (groupId: string) => void;
  addFlashcard: (groupId: string, pages: string[]) => string;
  updateFlashcard: (groupId: string, card: Flashcard) => void;
  deleteFlashcard: (groupId: string, cardId: string) => void;
  addStudyMode: (mode: StudyMode) => void;
  updateStudyMode: (mode: StudyMode) => void;
  deleteStudyMode: (modeId: string) => void;
  resetToDefault: () => void;
  recordActivity: () => void;
  getDueCards: (groupId: string) => Flashcard[];
  getGroupProgress: (groupId: string) => number;
  importState: (json: string) => void;
  exportState: () => string;
  setVisiblePageCount: (groupId: string, count: number) => void;
  setStudyFilter: (groupId: string, filter: CardFilter) => void;
  setActiveStudyMode: (groupId: string, modeId: string) => void;
  addFlashcardsBulk: (groupId: string, cards: Flashcard[]) => void;
}

const FlashcardStoreContext = createContext<FlashcardStore | undefined>(undefined);

function getErrorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

export function useFlashcardStore(): FlashcardStore {
  const store = useContext(FlashcardStoreContext);
  if (!store) {
    throw new Error('useFlashcardStore must be used within a FlashcardStoreProvider');
  }
  return store;
}
export { validateBackupData };

export function FlashcardStoreProvider({ children }: { children: React.ReactNode }) {
  const [groups, setGroups] = useState<FlashcardGroup[]>([]);
  const [studyModes, setStudyModes] = useState<StudyMode[]>([]);
  const [heatmap, setHeatmap] = useState<Record<string, number>>({});
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  // Sync status and error state
  const [syncStatus, setSyncStatus] = useState<FlashcardStore['syncStatus']>('idle');
  const [lastSyncError, setLastSyncError] = useState<string | null>(null);
  const [lastPersistenceError, setLastPersistenceError] = useState<string | null>(null);
  const [lastStoreError, setLastStoreError] = useState<string | null>(null);

  // Keep references to latest states
  const groupsRef = useRef(groups);
  const studyModesRef = useRef(studyModes);
  const heatmapRef = useRef(heatmap);
  const userRef = useRef(user);

  useEffect(() => {
    groupsRef.current = groups;
  }, [groups]);
  useEffect(() => {
    studyModesRef.current = studyModes;
  }, [studyModes]);
  useEffect(() => {
    heatmapRef.current = heatmap;
  }, [heatmap]);
  useEffect(() => {
    userRef.current = user;
  }, [user]);

  const getCurrentUid = useCallback(() => userRef.current?.uid ?? null, []);

  const applySnapshot = useCallback((snapshot: StoreData) => {
    groupsRef.current = snapshot.groups;
    studyModesRef.current = snapshot.studyModes;
    heatmapRef.current = snapshot.activityHeatmap;
    setGroups(snapshot.groups);
    setStudyModes(snapshot.studyModes);
    setHeatmap(snapshot.activityHeatmap);
  }, []);

  const persistAsync = useCallback(
    async (
      g: FlashcardGroup[],
      m: StudyMode[],
      h: Record<string, number>,
      uid: string | null,
    ): Promise<void> => {
      setSyncStatus('saving');
      setLastPersistenceError(null);
      setLastSyncError(null);

      const payload = { groups: g, studyModes: m, activityHeatmap: h };

      try {
        await saveLocalData(uid || undefined, payload);
      } catch (err) {
        console.error('Local persistence failed:', err);
        setLastPersistenceError(getErrorMessage(err));
        setSyncStatus('error');
        throw err;
      }

      if (!uid) {
        setSyncStatus('idle');
        return;
      }

      try {
        setSyncStatus('syncing');
        await saveCloudData(uid, payload);
        setSyncStatus('idle');
      } catch (err) {
        console.error('Cloud sync failed:', err);
        setLastSyncError(getErrorMessage(err));
        setSyncStatus('error');
        throw err;
      }
    },
    [],
  );

  const persist = useCallback(
    (g: FlashcardGroup[], m: StudyMode[], h: Record<string, number>, uid: string | null) => {
      void persistAsync(g, m, h, uid).catch(() => undefined);
    },
    [persistAsync],
  );

  const persistCurrentSnapshot = useCallback(
    (uid = getCurrentUid()) => {
      persist(groupsRef.current, studyModesRef.current, heatmapRef.current, uid);
    },
    [getCurrentUid, persist],
  );

  const commitGroups = useCallback(
    (nextGroups: FlashcardGroup[]) => {
      groupsRef.current = nextGroups;
      setGroups(nextGroups);
      persistCurrentSnapshot();
    },
    [persistCurrentSnapshot],
  );

  const commitStudyModes = useCallback(
    (nextStudyModes: StudyMode[]) => {
      studyModesRef.current = nextStudyModes;
      setStudyModes(nextStudyModes);
      persistCurrentSnapshot();
    },
    [persistCurrentSnapshot],
  );

  const commitHeatmap = useCallback(
    (nextHeatmap: Record<string, number>) => {
      heatmapRef.current = nextHeatmap;
      setHeatmap(nextHeatmap);
      persistCurrentSnapshot();
    },
    [persistCurrentSnapshot],
  );

  // Sync auth state listener
  useEffect(() => {
    return onAuthChange((u) => {
      setUser(u);
    });
  }, []);

  // Centralized async loader triggered by changes in user
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

        // Load local cache (either for user or guest)
        const localCache = await loadLocalData(targetUid || undefined);
        if (localCache) {
          loadedGroups = localCache.groups;
          loadedModes = localCache.studyModes;
          loadedHeatmap = localCache.activityHeatmap;
        }

        // If user logged in, load and merge Firebase cloud data
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

        // Apply seeding logic if needed
        if (seedVer < SEED_VERSION) {
          if (seedVer === 0 && loadedGroups.length === 0) {
            loadedGroups = createSeedGroups();
          }
          const defaultModes = createSeedModes();
          const customModes = loadedModes.filter(
            (m) => m.id !== 'classic' && m.id !== 'listen-speak',
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
          applySnapshot({
            groups: loadedGroups,
            studyModes: loadedModes,
            activityHeatmap: loadedHeatmap,
          });
          persist(loadedGroups, loadedModes, loadedHeatmap, targetUid);
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

    loadData();

    return () => {
      active = false;
    };
  }, [user, persist, applySnapshot]);

  const addGroup = useCallback(
    (name: string, languages: string[], pageNames: string[]) => {
      const { nextGroups, newGroupId } = addGroupAction(
        groupsRef.current,
        name,
        languages,
        pageNames,
      );
      commitGroups(nextGroups);
      return newGroupId;
    },
    [commitGroups],
  );

  const addGroupWithCards = useCallback(
    (
      name: string,
      languages: string[],
      pageNames: string[],
      cards: Omit<Flashcard, 'id' | 'srsState'>[],
    ) => {
      const { nextGroups, newGroupId } = addGroupWithCardsAction(
        groupsRef.current,
        name,
        languages,
        pageNames,
        cards,
      );
      commitGroups(nextGroups);
      return newGroupId;
    },
    [commitGroups],
  );

  const importDeck = useCallback(
    async (payload: ImportDeckPayload): Promise<ImportDeckResult> => {
      let normalized: NormalizedImportDeckPayload;
      try {
        normalized = validateImportDeckPayload(payload);
      } catch (err) {
        return { ok: false, error: getErrorMessage(err) };
      }

      const previousSnapshot: StoreData = {
        groups: groupsRef.current,
        studyModes: studyModesRef.current,
        activityHeatmap: heatmapRef.current,
      };
      const currentUid = getCurrentUid();
      const { nextGroups, newGroupId } = addGroupWithCardsAction(
        previousSnapshot.groups,
        normalized.name,
        normalized.languages,
        normalized.pageNames,
        normalized.cards,
      );

      groupsRef.current = nextGroups;
      setGroups(nextGroups);

      try {
        await persistAsync(
          nextGroups,
          previousSnapshot.studyModes,
          previousSnapshot.activityHeatmap,
          currentUid,
        );
        return {
          ok: true,
          groupId: newGroupId,
          importedCards: normalized.cards.length,
        };
      } catch (err) {
        applySnapshot(previousSnapshot);
        try {
          await persistAsync(
            previousSnapshot.groups,
            previousSnapshot.studyModes,
            previousSnapshot.activityHeatmap,
            currentUid,
          );
        } catch {
          // The original persistence error is kept below for the UI.
        }
        const message = getErrorMessage(err);
        setLastPersistenceError(message);
        setLastStoreError(message);
        setSyncStatus('error');
        return { ok: false, error: message };
      }
    },
    [applySnapshot, getCurrentUid, persistAsync],
  );

  const updateGroup = useCallback(
    (group: FlashcardGroup) => {
      commitGroups(updateGroupAction(groupsRef.current, group));
    },
    [commitGroups],
  );

  const deleteGroup = useCallback(
    (groupId: string) => {
      commitGroups(deleteGroupAction(groupsRef.current, groupId));
    },
    [commitGroups],
  );

  const setVisiblePageCount = useCallback(
    (groupId: string, count: number) => {
      commitGroups(setVisiblePageCountAction(groupsRef.current, groupId, count));
    },
    [commitGroups],
  );

  const setStudyFilter = useCallback(
    (groupId: string, filter: CardFilter) => {
      commitGroups(setStudyFilterAction(groupsRef.current, groupId, filter));
    },
    [commitGroups],
  );

  const setActiveStudyMode = useCallback(
    (groupId: string, modeId: string) => {
      commitGroups(setActiveStudyModeAction(groupsRef.current, groupId, modeId));
    },
    [commitGroups],
  );

  const addFlashcard = useCallback(
    (groupId: string, pages: string[]) => {
      const { nextGroups, newCardId } = addFlashcardAction(groupsRef.current, groupId, pages);
      commitGroups(nextGroups);
      return newCardId;
    },
    [commitGroups],
  );

  const updateFlashcard = useCallback(
    (groupId: string, card: Flashcard) => {
      commitGroups(updateFlashcardAction(groupsRef.current, groupId, card));
    },
    [commitGroups],
  );

  const deleteFlashcard = useCallback(
    (groupId: string, cardId: string) => {
      commitGroups(deleteFlashcardAction(groupsRef.current, groupId, cardId));
    },
    [commitGroups],
  );

  const addFlashcardsBulk = useCallback(
    (groupId: string, cards: Flashcard[]) => {
      commitGroups(addFlashcardsBulkAction(groupsRef.current, groupId, cards));
    },
    [commitGroups],
  );

  const addStudyMode = useCallback(
    (mode: StudyMode) => {
      commitStudyModes(addStudyModeAction(studyModesRef.current, mode));
    },
    [commitStudyModes],
  );

  const updateStudyMode = useCallback(
    (mode: StudyMode) => {
      commitStudyModes(updateStudyModeAction(studyModesRef.current, mode));
    },
    [commitStudyModes],
  );

  const deleteStudyMode = useCallback(
    (modeId: string) => {
      commitStudyModes(deleteStudyModeAction(studyModesRef.current, modeId));
    },
    [commitStudyModes],
  );

  const resetToDefault = useCallback(() => {
    const currentUid = getCurrentUid();
    const g = createSeedGroups();
    const m = createSeedModes();
    const h: Record<string, number> = {};
    applySnapshot({ groups: g, studyModes: m, activityHeatmap: h });
    setSeedVersion(SEED_VERSION).catch((err) => {
      setLastPersistenceError(getErrorMessage(err));
    });
    persist(g, m, h, currentUid);
  }, [applySnapshot, getCurrentUid, persist]);

  const recordActivity = useCallback(() => {
    const { nextHeatmap } = recordActivityAction(heatmapRef.current);
    commitHeatmap(nextHeatmap);
  }, [commitHeatmap]);

  const getDueCards = useCallback((groupId: string): Flashcard[] => {
    const group = groupsRef.current.find((g) => g.id === groupId);
    if (!group) return [];
    return selectDueCards(group.cards, group.studyFilter || 'new+review');
  }, []);

  const getGroupProgress = useCallback((groupId: string): number => {
    const group = groupsRef.current.find((g) => g.id === groupId);
    if (!group) return 0;
    return selectGroupProgress(group.cards);
  }, []);

  const signIn = useCallback(async () => {
    await signInWithGoogle();
  }, []);

  const signOut = useCallback(async () => {
    await signOutUser();
  }, []);

  const exportState = useCallback(() => {
    return JSON.stringify(
      {
        groups: groupsRef.current,
        studyModes: studyModesRef.current,
        activityHeatmap: heatmapRef.current,
      },
      null,
      2,
    );
  }, []);

  const importState = useCallback(
    (json: string) => {
      const currentUid = getCurrentUid();
      const data = JSON.parse(json);

      // Explicit validation that throws descriptive errors
      validateBackupData(data);

      const g = data.groups;
      const m = data.studyModes;
      const h = data.activityHeatmap;

      applySnapshot({ groups: g, studyModes: m, activityHeatmap: h });
      persist(g, m, h, currentUid);
    },
    [applySnapshot, getCurrentUid, persist],
  );

  return React.createElement(
    FlashcardStoreContext.Provider,
    {
      value: {
        groups,
        studyModes,
        activityHeatmap: heatmap,
        isLoading,
        user,
        syncStatus,
        lastSyncError,
        lastPersistenceError,
        lastStoreError,
        signIn,
        signOut,
        addGroup,
        addGroupWithCards,
        importDeck,
        updateGroup,
        deleteGroup,
        addFlashcard,
        updateFlashcard,
        deleteFlashcard,
        addStudyMode,
        updateStudyMode,
        deleteStudyMode,
        resetToDefault,
        recordActivity,
        getDueCards,
        getGroupProgress,
        importState,
        exportState,
        setVisiblePageCount,
        setStudyFilter,
        setActiveStudyMode,
        addFlashcardsBulk,
      },
    },
    children,
  );
}

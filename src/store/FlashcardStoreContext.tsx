import React, { createContext, useContext, useState, useEffect, useCallback, useRef } from 'react';
import type { User } from 'firebase/auth';
import type { FlashcardGroup, Flashcard, StudyMode } from '../types/models';
import { onAuthChange, signInWithGoogle, signOutUser } from '../services/firebase';
import { CardFilter } from '../constants/cardFilters';
import { validateBackupData } from '../utils/backupValidation';

import {
  addGroupAction,
  updateGroupAction,
  deleteGroupAction,
  setVisiblePageCountAction,
  setStudyFilterAction,
  setActiveStudyModeAction,
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
  signIn: () => Promise<void>;
  signOut: () => Promise<void>;
  addGroup: (name: string, languages: string[], pageNames: string[]) => string;
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

  // Keep references to latest states
  const groupsRef = useRef(groups);
  const studyModesRef = useRef(studyModes);
  const heatmapRef = useRef(heatmap);
  const userRef = useRef(user);

  useEffect(() => { groupsRef.current = groups; }, [groups]);
  useEffect(() => { studyModesRef.current = studyModes; }, [studyModes]);
  useEffect(() => { heatmapRef.current = heatmap; }, [heatmap]);
  useEffect(() => { userRef.current = user; }, [user]);

  // Unified persistence function
  const persist = useCallback((
    g: FlashcardGroup[],
    m: StudyMode[],
    h: Record<string, number>,
    uid: string | null
  ) => {
    if (uid) {
      saveCloudData(uid, { groups: g, studyModes: m, activityHeatmap: h }).catch(() => {});
      saveLocalData(uid, { groups: g, studyModes: m, activityHeatmap: h }).catch(() => {});
    } else {
      saveLocalData(undefined, { groups: g, studyModes: m, activityHeatmap: h }).catch(() => {});
    }
  }, []);

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
              cloudData
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
            (m) => m.id !== 'classic' && m.id !== 'listen-speak'
          );
          loadedModes = [...defaultModes, ...customModes];
          
          await setSeedVersion(SEED_VERSION);
        }

        if (loadedGroups.length === 0) {
          loadedGroups = createSeedGroups();
        }
        if (loadedModes.length === 0) {
          loadedModes = createSeedModes();
        }

        if (active) {
          setGroups(loadedGroups);
          setStudyModes(loadedModes);
          setHeatmap(loadedHeatmap);
          // Sync changes immediately to local storage
          persist(loadedGroups, loadedModes, loadedHeatmap, targetUid);
        }
      } catch (err) {
        console.error('Failed to initialize flashcard store:', err);
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
  }, [user, persist]);

  const addGroup = useCallback((name: string, languages: string[], pageNames: string[]) => {
    const currentUid = userRef.current ? userRef.current.uid : null;
    let newId = '';
    setGroups((prev) => {
      const { nextGroups, newGroupId } = addGroupAction(prev, name, languages, pageNames);
      newId = newGroupId;
      persist(nextGroups, studyModesRef.current, heatmapRef.current, currentUid);
      return nextGroups;
    });
    return newId;
  }, [persist]);

  const updateGroup = useCallback((group: FlashcardGroup) => {
    const currentUid = userRef.current ? userRef.current.uid : null;
    setGroups((prev) => {
      const next = updateGroupAction(prev, group);
      persist(next, studyModesRef.current, heatmapRef.current, currentUid);
      return next;
    });
  }, [persist]);

  const deleteGroup = useCallback((groupId: string) => {
    const currentUid = userRef.current ? userRef.current.uid : null;
    setGroups((prev) => {
      const next = deleteGroupAction(prev, groupId);
      persist(next, studyModesRef.current, heatmapRef.current, currentUid);
      return next;
    });
  }, [persist]);

  const setVisiblePageCount = useCallback((groupId: string, count: number) => {
    const currentUid = userRef.current ? userRef.current.uid : null;
    setGroups((prev) => {
      const next = setVisiblePageCountAction(prev, groupId, count);
      persist(next, studyModesRef.current, heatmapRef.current, currentUid);
      return next;
    });
  }, [persist]);

  const setStudyFilter = useCallback((groupId: string, filter: CardFilter) => {
    const currentUid = userRef.current ? userRef.current.uid : null;
    setGroups((prev) => {
      const next = setStudyFilterAction(prev, groupId, filter);
      persist(next, studyModesRef.current, heatmapRef.current, currentUid);
      return next;
    });
  }, [persist]);

  const setActiveStudyMode = useCallback((groupId: string, modeId: string) => {
    const currentUid = userRef.current ? userRef.current.uid : null;
    setGroups((prev) => {
      const next = setActiveStudyModeAction(prev, groupId, modeId);
      persist(next, studyModesRef.current, heatmapRef.current, currentUid);
      return next;
    });
  }, [persist]);

  const addFlashcard = useCallback((groupId: string, pages: string[]) => {
    const currentUid = userRef.current ? userRef.current.uid : null;
    let newId = '';
    setGroups((prev) => {
      const { nextGroups, newCardId } = addFlashcardAction(prev, groupId, pages);
      newId = newCardId;
      persist(nextGroups, studyModesRef.current, heatmapRef.current, currentUid);
      return nextGroups;
    });
    return newId;
  }, [persist]);

  const updateFlashcard = useCallback((groupId: string, card: Flashcard) => {
    const currentUid = userRef.current ? userRef.current.uid : null;
    setGroups((prev) => {
      const next = updateFlashcardAction(prev, groupId, card);
      persist(next, studyModesRef.current, heatmapRef.current, currentUid);
      return next;
    });
  }, [persist]);

  const deleteFlashcard = useCallback((groupId: string, cardId: string) => {
    const currentUid = userRef.current ? userRef.current.uid : null;
    setGroups((prev) => {
      const next = deleteFlashcardAction(prev, groupId, cardId);
      persist(next, studyModesRef.current, heatmapRef.current, currentUid);
      return next;
    });
  }, [persist]);

  const addFlashcardsBulk = useCallback((groupId: string, cards: Flashcard[]) => {
    const currentUid = userRef.current ? userRef.current.uid : null;
    setGroups((prev) => {
      const next = addFlashcardsBulkAction(prev, groupId, cards);
      persist(next, studyModesRef.current, heatmapRef.current, currentUid);
      return next;
    });
  }, [persist]);

  const addStudyMode = useCallback((mode: StudyMode) => {
    const currentUid = userRef.current ? userRef.current.uid : null;
    setStudyModes((prev) => {
      const next = addStudyModeAction(prev, mode);
      persist(groupsRef.current, next, heatmapRef.current, currentUid);
      return next;
    });
  }, [persist]);

  const updateStudyMode = useCallback((mode: StudyMode) => {
    const currentUid = userRef.current ? userRef.current.uid : null;
    setStudyModes((prev) => {
      const next = updateStudyModeAction(prev, mode);
      persist(groupsRef.current, next, heatmapRef.current, currentUid);
      return next;
    });
  }, [persist]);

  const deleteStudyMode = useCallback((modeId: string) => {
    const currentUid = userRef.current ? userRef.current.uid : null;
    setStudyModes((prev) => {
      const next = deleteStudyModeAction(prev, modeId);
      persist(groupsRef.current, next, heatmapRef.current, currentUid);
      return next;
    });
  }, [persist]);

  const resetToDefault = useCallback(() => {
    const currentUid = userRef.current ? userRef.current.uid : null;
    const g = createSeedGroups();
    const m = createSeedModes();
    const h = {};
    setGroups(g);
    setStudyModes(m);
    setHeatmap(h);
    setSeedVersion(SEED_VERSION).catch(() => {});
    persist(g, m, h, currentUid);
  }, [persist]);

  const recordActivity = useCallback(() => {
    const currentUid = userRef.current ? userRef.current.uid : null;
    setHeatmap((prev) => {
      const { nextHeatmap } = recordActivityAction(prev);
      persist(groupsRef.current, studyModesRef.current, nextHeatmap, currentUid);
      return nextHeatmap;
    });
  }, [persist]);

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

  const importState = useCallback((json: string) => {
    const currentUid = userRef.current ? userRef.current.uid : null;
    const data = JSON.parse(json);
    
    // Explicit validation that throws descriptive errors
    validateBackupData(data);
    
    const g = data.groups;
    const m = data.studyModes;
    const h = data.activityHeatmap;

    setGroups(g);
    setStudyModes(m);
    setHeatmap(h);
    persist(g, m, h, currentUid);
  }, [persist]);

  return React.createElement(
    FlashcardStoreContext.Provider,
    {
      value: {
        groups,
        studyModes,
        activityHeatmap: heatmap,
        isLoading,
        user,
        signIn,
        signOut,
        addGroup,
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

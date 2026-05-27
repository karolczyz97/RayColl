import React, { createContext, useContext, useEffect, useMemo, useRef, useState } from 'react';
import type { User } from 'firebase/auth';
import type { FlashcardGroup, StudyMode } from '../types/models';
import { signInWithGoogle, signOutUser } from '../services/firebase';
import { validateBackupData } from '../utils/backupValidation';
import type {
  FlashcardStore,
  FlashcardStoreActions,
  FlashcardStoreState,
  SyncStatus,
} from './FlashcardStoreTypes';
import { StoreActionsContext } from './StoreActionsContext';
import { StoreStateContext } from './StoreStateContext';
import { useStorePersistence } from './useStorePersistence';
import { useStoreBootstrap } from './useStoreBootstrap';
import { useStoreActions } from './useStoreActions';

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
  const [syncStatus, setSyncStatus] = useState<SyncStatus>('idle');
  const [lastSyncError, setLastSyncError] = useState<string | null>(null);
  const [lastPersistenceError, setLastPersistenceError] = useState<string | null>(null);
  const [lastStoreError, setLastStoreError] = useState<string | null>(null);

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

  const persistence = useStorePersistence({
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
  });
  const { flushPersistence } = persistence;

  useStoreBootstrap({
    user,
    setUser,
    setIsLoading,
    setLastPersistenceError,
    setLastStoreError,
    applySnapshot: persistence.applySnapshot,
    persistNow: persistence.persistNow,
  });

  const actions = useStoreActions({
    groupsRef,
    studyModesRef,
    heatmapRef,
    setGroups,
    setHeatmap,
    setSyncStatus,
    setLastPersistenceError,
    setLastStoreError,
    getCurrentUid: persistence.getCurrentUid,
    applySnapshot: persistence.applySnapshot,
    persistNow: persistence.persistNow,
    persistCurrentSnapshot: persistence.persistCurrentSnapshot,
    flushPersistence: persistence.flushPersistence,
    commitGroups: persistence.commitGroups,
    commitStudyModes: persistence.commitStudyModes,
    commitHeatmap: persistence.commitHeatmap,
  });

  const signIn = React.useCallback(async () => {
    await signInWithGoogle();
  }, []);

  const signOut = React.useCallback(async () => {
    await flushPersistence();
    await signOutUser();
  }, [flushPersistence]);

  const stateValue = useMemo<FlashcardStoreState>(
    () => ({
      groups,
      studyModes,
      activityHeatmap: heatmap,
      isLoading,
      user,
      syncStatus,
      lastSyncError,
      lastPersistenceError,
      lastStoreError,
    }),
    [
      groups,
      heatmap,
      isLoading,
      lastPersistenceError,
      lastStoreError,
      lastSyncError,
      studyModes,
      syncStatus,
      user,
    ],
  );

  const actionsValue = useMemo<FlashcardStoreActions>(
    () => ({
      signIn,
      signOut,
      flushPersistence: flushPersistence,
      addGroup: actions.addGroup,
      addGroupWithCards: actions.addGroupWithCards,
      importDeck: actions.importDeck,
      updateGroup: actions.updateGroup,
      deleteGroup: actions.deleteGroup,
      addFlashcard: actions.addFlashcard,
      updateFlashcard: actions.updateFlashcard,
      deleteFlashcard: actions.deleteFlashcard,
      reviewFlashcard: actions.reviewFlashcard,
      addStudyMode: actions.addStudyMode,
      updateStudyMode: actions.updateStudyMode,
      deleteStudyMode: actions.deleteStudyMode,
      resetToDefault: actions.resetToDefault,
      recordActivity: actions.recordActivity,
      getDueCards: actions.getDueCards,
      getGroupProgress: actions.getGroupProgress,
      importState: actions.importState,
      exportState: actions.exportState,
      setVisiblePageCount: actions.setVisiblePageCount,
      setStudyFilter: actions.setStudyFilter,
      setActiveStudyMode: actions.setActiveStudyMode,
      addFlashcardsBulk: actions.addFlashcardsBulk,
    }),
    [
      actions,
      flushPersistence,
      signIn,
      signOut,
    ],
  );

  const value = useMemo<FlashcardStore>(
    () => ({
      ...stateValue,
      ...actionsValue,
    }),
    [actionsValue, stateValue],
  );

  return (
    <StoreStateContext.Provider value={stateValue}>
      <StoreActionsContext.Provider value={actionsValue}>
        <FlashcardStoreContext.Provider value={value}>{children}</FlashcardStoreContext.Provider>
      </StoreActionsContext.Provider>
    </StoreStateContext.Provider>
  );
}

import React, { createContext, useContext, useMemo, useState } from 'react';
import { useSyncedRef } from '@/hooks/useSyncedRef';
import type { User } from 'firebase/auth';
import type { FlashcardGroup, StudyMode, StoreData } from '@/types/models';
import type {
  FlashcardStore,
  FlashcardStoreActions,
  FlashcardStoreState,
  SyncStatus,
} from './FlashcardStoreTypes';
import { StoreActionsContext, StoreStateContext } from './StoreContexts';
import { useStorePersistence } from './useStorePersistence';
import { useStoreBootstrap } from './useStoreBootstrap';
import { useStoreActionsCore } from './useStoreActions';
import { selectActiveGroups, selectArchivedGroups, selectLiveStudyModes } from './selectors/liveSelectors';
import { MigrationDialog } from '@/components/dialogs/MigrationDialog';
import { useArchivePurger } from './useArchivePurger';
import { useAuthActions } from './useAuthActions';

const FlashcardStoreContext = createContext<FlashcardStore | undefined>(undefined);

export function useFlashcardStore(): FlashcardStore {
  const store = useContext(FlashcardStoreContext);
  if (!store) {
    throw new Error('useFlashcardStore must be used within a FlashcardStoreProvider');
  }
  return store;
}

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
  const [lastLoginError, setLastLoginError] = useState<string | null>(null);

  const [migrationPending, setMigrationPending] = useState(false);
  const [pendingGuestSnapshot, setPendingGuestSnapshot] = useState<StoreData | null>(null);

  const groupsRef = useSyncedRef(groups);
  const studyModesRef = useSyncedRef(studyModes);
  const heatmapRef = useSyncedRef(heatmap);
  const userRef = useSyncedRef(user);

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
    setLastStoreError,
  });

  useStoreBootstrap({
    user,
    setUser,
    setIsLoading,
    setLastSyncError,
    setLastPersistenceError,
    setLastStoreError,
    setMigrationPending,
    setPendingGuestSnapshot,
    applySnapshot: persistence.applySnapshot,
    persistLocalSnapshot: persistence.persistLocalSnapshot,
    persistNow: persistence.persistNow,
  });

  const actions = useStoreActionsCore({
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
    flushPersistence: persistence.flushPersistence,
    commitGroups: persistence.commitGroups,
    commitStudyModes: persistence.commitStudyModes,
    commitHeatmap: persistence.commitHeatmap,
    commitGroupsAndHeatmap: persistence.commitGroupsAndHeatmap,
  });

  useArchivePurger(actions.purgeArchives);

  const auth = useAuthActions({
    setLastLoginError,
    flushPersistence: persistence.flushPersistence,
    pendingGuestSnapshot,
    user,
    setMigrationPending,
    setPendingGuestSnapshot,
    persistence,
  });

  const stateValue = useMemo<FlashcardStoreState>(
    () => ({
      groups: selectActiveGroups(groups),
      archivedGroups: selectArchivedGroups(groups),
      studyModes: selectLiveStudyModes(studyModes),
      activityHeatmap: heatmap,
      isLoading,
      user,
      syncStatus,
      lastSyncError,
      lastPersistenceError,
      lastStoreError,
      lastLoginError,
    }),
    [
      groups,
      heatmap,
      isLoading,
      lastPersistenceError,
      lastStoreError,
      lastLoginError,
      lastSyncError,
      studyModes,
      syncStatus,
      user,
    ],
  );

  const actionsValue = useMemo<FlashcardStoreActions>(
    () => ({
      signIn: auth.signIn,
      signOut: auth.signOut,
      flushPersistence: persistence.flushPersistence,
      addGroup: actions.addGroup,
      addGroupWithCards: actions.addGroupWithCards,
      importDeck: actions.importDeck,
      updateGroup: actions.updateGroup,
      deleteGroup: actions.deleteGroup,
      archiveGroup: actions.archiveGroup,
      restoreGroup: actions.restoreGroup,
      purgeArchives: actions.purgeArchives,
      addFlashcard: actions.addFlashcard,
      updateFlashcard: actions.updateFlashcard,
      deleteFlashcard: actions.deleteFlashcard,
      reviewFlashcard: actions.reviewFlashcard,
      addStudyMode: actions.addStudyMode,
      updateStudyMode: actions.updateStudyMode,
      deleteStudyMode: actions.deleteStudyMode,
      resetStudyMode: actions.resetStudyMode,
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
      clearLastLoginError: auth.clearLastLoginError,
    }),
    [actions, persistence.flushPersistence, auth],
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
        <FlashcardStoreContext.Provider value={value}>
          {children}
          <MigrationDialog
            visible={migrationPending}
            onMigrate={auth.migrateGuestToAccount}
            onStartFresh={auth.startFreshOnAccount}
            onDismiss={auth.dismissMigration}
          />
        </FlashcardStoreContext.Provider>
      </StoreActionsContext.Provider>
    </StoreStateContext.Provider>
  );
}

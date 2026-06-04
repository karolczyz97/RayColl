import React, { createContext, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { AppState } from 'react-native';
import type { User } from 'firebase/auth';
import type { FlashcardGroup, StudyMode, StoreData } from '@/types/models';
import { signInWithGoogle, signOutUser } from '@/services/firebase';
import { validateBackupData } from '@/utils/backupValidation';
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
import { useStoreActionsCore } from './useStoreActions';
import { selectActiveGroups, selectArchivedGroups, selectLiveStudyModes } from './selectors/tombstones';
import { createSeedGroups } from './seed/seedGroups';
import { createSeedModes } from './seed/seedModes';
import { normalizeStoreData } from './storeDataNormalization';
import { MigrationDialog } from '@/components/dialogs/MigrationDialog';
import { getErrorMessage } from '@/utils/errors';

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
  const [lastLoginError, setLastLoginError] = useState<string | null>(null);

  const [migrationPending, setMigrationPending] = useState(false);
  const [pendingGuestSnapshot, setPendingGuestSnapshot] = useState<StoreData | null>(null);

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
    setLastStoreError,
  });
  const { flushPersistence } = persistence;

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
    persistCurrentSnapshot: persistence.persistCurrentSnapshot,
    flushPersistence: persistence.flushPersistence,
    commitGroups: persistence.commitGroups,
    commitStudyModes: persistence.commitStudyModes,
    commitHeatmap: persistence.commitHeatmap,
  });

  const purgeArchivesRef = useRef(actions.purgeArchives);
  useEffect(() => { purgeArchivesRef.current = actions.purgeArchives; }, [actions.purgeArchives]);

  useEffect(() => {
    const interval = setInterval(() => {
      purgeArchivesRef.current();
    }, 60 * 60 * 1000);
    return () => clearInterval(interval);
  }, []);

  useEffect(() => {
    const sub = AppState.addEventListener('change', (nextState) => {
      if (nextState === 'active') {
        purgeArchivesRef.current();
      }
    });
    return () => sub.remove();
  }, []);

  const signIn = React.useCallback(async () => {
    setLastLoginError(null);
    try {
      await signInWithGoogle();
    } catch (e: unknown) {
      const message = getErrorMessage(e);
      setLastLoginError(
        message.startsWith('auth.error.') ? message : 'auth.error.login_failed',
      );
      throw e;
    }
  }, []);

  const clearLastLoginError = React.useCallback(() => {
    setLastLoginError(null);
  }, []);

  const signOut = React.useCallback(async () => {
    await flushPersistence();
    await signOutUser();
  }, [flushPersistence]);

  const migrateGuestToAccount = React.useCallback(async () => {
    if (!pendingGuestSnapshot || !user?.uid) return;
    const snapshot = normalizeStoreData(pendingGuestSnapshot);
    persistence.applySnapshot(snapshot);
    await persistence.persistNow({ uid: user.uid, ...snapshot });
    setMigrationPending(false);
    setPendingGuestSnapshot(null);
  }, [pendingGuestSnapshot, user, persistence]);

  const startFreshOnAccount = React.useCallback(async () => {
    if (!user?.uid) return;
    const seedGroups = createSeedGroups();
    const seedModes = createSeedModes();
    const snapshot = normalizeStoreData({
      groups: seedGroups,
      studyModes: seedModes,
      activityHeatmap: {},
    });
    persistence.applySnapshot(snapshot);
    await persistence.persistNow({ uid: user.uid, ...snapshot });
    setMigrationPending(false);
    setPendingGuestSnapshot(null);
  }, [user, persistence]);

  const dismissMigration = React.useCallback(async () => {
    setMigrationPending(false);
    setPendingGuestSnapshot(null);
    await signOutUser();
  }, []);

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
      signIn,
      signOut,
      flushPersistence: flushPersistence,
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
      clearLastLoginError,
    }),
    [
      actions,
      flushPersistence,
      signIn,
      signOut,
      clearLastLoginError,
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
        <FlashcardStoreContext.Provider value={value}>
          {children}
          <MigrationDialog
            visible={migrationPending}
            onMigrate={migrateGuestToAccount}
            onStartFresh={startFreshOnAccount}
            onDismiss={dismissMigration}
          />
        </FlashcardStoreContext.Provider>
      </StoreActionsContext.Provider>
    </StoreStateContext.Provider>
  );
}

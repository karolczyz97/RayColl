import { useCallback } from 'react';
import type { User } from 'firebase/auth';
import type { StoreData } from '@/types/models';
import { signInWithGoogle, signOutUser } from '@/services/firebase';
import { clearCloudSnapshotCache } from './persistence/firebasePersistence';
import { createSeedGroups } from './seed/seedGroups';
import { createSeedModes } from './seed/seedModes';
import { normalizeStoreData } from './storeDataNormalization';
import { getErrorMessage } from '@/utils/errors';
import type { UseStorePersistenceReturn } from './useStorePersistence';

interface UseAuthActionsParams {
  setLastLoginError: (err: string | null) => void;
  flushPersistence: () => Promise<void>;
  pendingGuestSnapshot: StoreData | null;
  user: User | null;
  setMigrationPending: (v: boolean) => void;
  setPendingGuestSnapshot: (v: StoreData | null) => void;
  persistence: UseStorePersistenceReturn;
}

export function useAuthActions({
  setLastLoginError,
  flushPersistence,
  pendingGuestSnapshot,
  user,
  setMigrationPending,
  setPendingGuestSnapshot,
  persistence,
}: UseAuthActionsParams) {
  const signIn = useCallback(async () => {
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
  }, [setLastLoginError]);

  const clearLastLoginError = useCallback(() => {
    setLastLoginError(null);
  }, [setLastLoginError]);

  const signOut = useCallback(async () => {
    const uid = user?.uid ?? undefined;
    // Best-effort final flush; never block sign-out on a persistence failure.
    await flushPersistence().catch(() => {});
    await signOutUser();
    clearCloudSnapshotCache(uid);
  }, [flushPersistence, user]);

  const migrateGuestToAccount = useCallback(async () => {
    if (!pendingGuestSnapshot || !user?.uid) return;
    const snapshot = normalizeStoreData(pendingGuestSnapshot);
    persistence.applySnapshot(snapshot);
    await persistence.persistNow({ uid: user.uid, ...snapshot });
    setMigrationPending(false);
    setPendingGuestSnapshot(null);
  }, [pendingGuestSnapshot, user, persistence, setMigrationPending, setPendingGuestSnapshot]);

  const startFreshOnAccount = useCallback(async () => {
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
  }, [user, persistence, setMigrationPending, setPendingGuestSnapshot]);

  const dismissMigration = useCallback(async () => {
    const uid = user?.uid ?? undefined;
    setMigrationPending(false);
    setPendingGuestSnapshot(null);
    await signOutUser();
    clearCloudSnapshotCache(uid);
  }, [setMigrationPending, setPendingGuestSnapshot, user]);

  return { signIn, signOut, clearLastLoginError, migrateGuestToAccount, startFreshOnAccount, dismissMigration };
}

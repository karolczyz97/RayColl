import React, { useState } from 'react';
import { StyleSheet } from 'react-native';
import { Portal, Snackbar, useTheme } from 'react-native-paper';
import { useI18n } from '@/i18n';
import { useDelayedVisibility } from '@/hooks/useDelayedVisibility';
import { isOfflineError } from '@/utils/errors';
import type { SyncStatus } from '@/store/FlashcardStoreTypes';
import { TOKENS } from '@/theme/tokens';

interface SyncStatusBannerProps {
  syncStatus: SyncStatus;
  lastSyncError: string | null;
  lastPersistenceError: string | null;
  lastStoreError: string | null;
}

export function SyncStatusBanner({
  syncStatus,
  lastSyncError,
  lastPersistenceError,
  lastStoreError,
}: SyncStatusBannerProps) {
  const theme = useTheme();
  const { t } = useI18n();

  // Classify errors: if all cloud errors look like network/offline issues
  // and there is no local-persistence failure, we show an info banner instead
  // of a scary red error. Local-persistence failures are always real errors.
  const syncErrors = [lastStoreError, lastSyncError].filter((e): e is string => !!e);
  const isOffline =
    !lastPersistenceError && syncErrors.length > 0 && syncErrors.every(isOfflineError);

  // Offline banner: once per offline episode (shows once, auto-dismisses after
  // ~6 s, user can also press OK). Resets when connectivity returns and the
  // queued sync succeeds (clearing lastSyncError / lastStoreError).
  // The combined `offlineState` object ensures at most one setState per render
  // so the derived-state pattern stays safe.
  const [offlineState, setOfflineState] = useState({ was: false, dismissed: false });
  if (isOffline && !offlineState.was) {
    setOfflineState({ was: true, dismissed: false }); // episode start
  }
  if (!isOffline && offlineState.was) {
    setOfflineState({ was: false, dismissed: false }); // episode end → ready for next
  }
  const showOffline = isOffline && !offlineState.dismissed;
  const dismissOffline = () => setOfflineState((s) => ({ ...s, dismissed: true }));

  // Real (non-offline) error message: persistence failures always show as errors
  // even when network is also down.
  const realErrorMessage = isOffline
    ? lastPersistenceError
    : lastStoreError || lastSyncError || lastPersistenceError;

  // Progress states only appear when an operation is genuinely slow: the
  // banner waits before showing and, once shown, holds long enough to read.
  // Sub-second background saves/flushes never reach the screen.
  const isBusy = syncStatus === 'saving' || syncStatus === 'syncing';
  const showProgress = useDelayedVisibility(isBusy);
  // Latch the label so it stays correct during the minimum-visible hold
  // after syncStatus has already returned to 'idle'. State is adjusted
  // during render (the React-documented pattern for derived state).
  const [latchedBusy, setLatchedBusy] = useState<'saving' | 'syncing'>('saving');
  if ((syncStatus === 'saving' || syncStatus === 'syncing') && latchedBusy !== syncStatus) {
    setLatchedBusy(syncStatus);
  }

  const showError = !!realErrorMessage;
  const showProgressBanner = !showError && !showOffline && showProgress;

  if (!showError && !showOffline && !showProgressBanner) {
    return null;
  }

  // Offline info banner — only shown when there is no higher-priority real error.
  if (!showError && showOffline) {
    return (
      <Portal>
        <Snackbar
          visible
          onDismiss={dismissOffline}
          duration={6000}
          action={{ label: t('common.ok'), onPress: dismissOffline }}
          accessibilityRole="alert"
          accessibilityLabel={t('sync.status.offline')}
          style={[styles.snackbar, { backgroundColor: theme.colors.inverseSurface }]}
          wrapperStyle={styles.wrapper}
          theme={{ colors: { inverseOnSurface: theme.colors.inverseOnSurface } }}
        >
          {t('sync.status.offline')}
        </Snackbar>
      </Portal>
    );
  }

  // Error or slow-progress banner.
  const statusText = showError
    ? realErrorMessage!
    : latchedBusy === 'syncing'
      ? t('sync.status.syncing')
      : t('sync.status.saving');

  return (
    <Portal>
      <Snackbar
        visible
        onDismiss={() => undefined}
        duration={Snackbar.DURATION_SHORT}
        accessibilityRole="alert"
        accessibilityLabel={statusText}
        style={[
          styles.snackbar,
          {
            backgroundColor: showError
              ? theme.colors.errorContainer
              : theme.colors.inverseSurface,
          },
        ]}
        wrapperStyle={styles.wrapper}
        theme={{
          colors: {
            inverseOnSurface: showError
              ? theme.colors.onErrorContainer
              : theme.colors.inverseOnSurface,
          },
        }}
      >
        {statusText}
      </Snackbar>
    </Portal>
  );
}

const styles = StyleSheet.create({
  wrapper: {
    alignSelf: 'center',
    width: '100%',
    maxWidth: TOKENS.layout.snackbarMaxWidth,
    paddingHorizontal: TOKENS.spacing.lg,
  },
  snackbar: {
    borderRadius: TOKENS.radius.lg,
  },
});

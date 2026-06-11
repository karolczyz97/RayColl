import React, { useState } from 'react';
import { StyleSheet } from 'react-native';
import { Portal, Snackbar, useTheme } from 'react-native-paper';
import { useI18n } from '@/i18n';
import { useDelayedVisibility } from '@/hooks/useDelayedVisibility';
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
  const errorMessage = lastStoreError || lastSyncError || lastPersistenceError;

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

  if (!errorMessage && !showProgress) {
    return null;
  }

  const statusText = errorMessage
    ? errorMessage
    : latchedBusy === 'syncing'
      ? t('sync.status.syncing')
      : t('sync.status.saving');

  const isError = !!errorMessage || syncStatus === 'error';

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
            backgroundColor: isError ? theme.colors.errorContainer : theme.colors.inverseSurface,
          },
        ]}
        wrapperStyle={styles.wrapper}
        theme={{
          colors: {
            inverseOnSurface: isError
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

import React from 'react';
import { StyleSheet } from 'react-native';
import { Portal, Snackbar, useTheme } from 'react-native-paper';
import type { TranslationFn } from '../../i18n';
import type { SyncStatus } from '../../store/FlashcardStoreTypes';
import { TOKENS } from '../../theme/tokens';

interface SyncStatusBannerProps {
  syncStatus: SyncStatus;
  lastSyncError: string | null;
  lastPersistenceError: string | null;
  lastStoreError: string | null;
  t?: TranslationFn;
}

export function SyncStatusBanner({
  syncStatus,
  lastSyncError,
  lastPersistenceError,
  lastStoreError,
  t,
}: SyncStatusBannerProps) {
  const theme = useTheme();
  const errorMessage = lastStoreError || lastSyncError || lastPersistenceError;
  const getLabel = (key: string, fallback: string) => {
    const translated = t?.(key);
    return translated && translated !== key ? translated : fallback;
  };

  if (!errorMessage && syncStatus === 'idle') {
    return null;
  }

  const statusText = errorMessage
    ? errorMessage
    : syncStatus === 'syncing'
      ? getLabel('sync.status.syncing', 'Syncing changes...')
      : syncStatus === 'saving'
        ? getLabel('sync.status.saving', 'Saving changes...')
        : null;

  if (!statusText) {
    return null;
  }

  const isError = !!errorMessage || syncStatus === 'error';

  return (
    <Portal>
      <Snackbar
        visible
        onDismiss={() => undefined}
        duration={Snackbar.DURATION_SHORT}
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
    maxWidth: TOKENS.layout.syncBannerMaxWidth,
    paddingHorizontal: TOKENS.spacing.lg,
  },
  snackbar: {
    borderRadius: TOKENS.radius.lg,
  },
});

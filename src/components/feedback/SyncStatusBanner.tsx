import React from 'react';
import { StyleSheet, View } from 'react-native';
import { Text, useTheme } from 'react-native-paper';
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
    <View
      style={[
        styles.banner,
        {
          backgroundColor: isError ? theme.colors.errorContainer : theme.colors.primaryContainer,
        },
      ]}
    >
      <Text
        selectable
        variant="bodySmall"
        style={{
          color: isError ? theme.colors.onErrorContainer : theme.colors.onPrimaryContainer,
        }}
      >
        {statusText}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  banner: {
    borderRadius: TOKENS.radius.lg,
    paddingHorizontal: TOKENS.spacing.md,
    paddingVertical: TOKENS.spacing.sm,
  },
});

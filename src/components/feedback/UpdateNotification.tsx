import React from 'react';
import { StyleSheet } from 'react-native';
import { Portal, Snackbar, useTheme } from 'react-native-paper';
import { useI18n } from '@/i18n';
import { TOKENS } from '@/theme/tokens';
import { useUpdateNotification } from '@/services/updateNotification';
import { ChangelogDialog } from './ChangelogDialog';

export function UpdateNotification() {
  const { t } = useI18n();
  const theme = useTheme();
  const { showSnackbar, showDialog, dismiss, openChangelog, closeChangelog } =
    useUpdateNotification();

  return (
    <>
      <Portal>
        <Snackbar
          visible={showSnackbar}
          onDismiss={dismiss}
          duration={8000}
          action={{ label: t('update.show_changes'), onPress: openChangelog }}
          wrapperStyle={styles.wrapper}
          style={[styles.snackbar, { backgroundColor: theme.colors.inverseSurface }]}
          theme={{
            colors: {
              inverseOnSurface: theme.colors.inverseOnSurface,
            },
          }}
        >
          {t('update.updated')}
        </Snackbar>
      </Portal>
      <ChangelogDialog visible={showDialog} onDismiss={closeChangelog} />
    </>
  );
}

const styles = StyleSheet.create({
  wrapper: {
    alignSelf: 'center',
    paddingHorizontal: TOKENS.spacing.lg,
  },
  snackbar: {
    borderRadius: TOKENS.radius.lg,
  },
});

import React from 'react';
import { Portal, Snackbar } from 'react-native-paper';
import { useI18n } from '../../i18n';
import { useUpdateNotification } from '../../services/updateNotification';
import { ChangelogDialog } from './ChangelogDialog';

export function UpdateNotification() {
  const { t } = useI18n();
  const { showSnackbar, showDialog, dismiss, openChangelog, closeChangelog } =
    useUpdateNotification();

  return (
    <>
      <Portal>
        <Snackbar
          visible={showSnackbar}
          onDismiss={dismiss}
          duration={8000}
          action={{ label: t('update.show'), onPress: openChangelog }}
        >
          {t('update.whats_new')}
        </Snackbar>
      </Portal>
      <ChangelogDialog visible={showDialog} onDismiss={closeChangelog} />
    </>
  );
}

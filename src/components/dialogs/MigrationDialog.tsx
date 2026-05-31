import React from 'react';
import { Button, Dialog, Portal, Text } from 'react-native-paper';
import { useI18n } from '../../i18n';
import { dialogStyles } from '../../theme/dialogStyles';

interface MigrationDialogProps {
  visible: boolean;
  onMigrate: () => void;
  onStartFresh: () => void;
  onDismiss: () => void;
}

export function MigrationDialog({
  visible,
  onMigrate,
  onStartFresh,
  onDismiss,
}: MigrationDialogProps) {
  const { t } = useI18n();

  return (
    <Portal>
      <Dialog visible={visible} onDismiss={onDismiss} style={dialogStyles.dialog}>
        <Dialog.Title>{t('auth.migration.title')}</Dialog.Title>
        <Dialog.Content>
          <Text selectable>{t('auth.migration.desc')}</Text>
        </Dialog.Content>
        <Dialog.Actions>
          <Button onPress={onDismiss}>{t('btn.cancel')}</Button>
          <Button onPress={onStartFresh}>{t('auth.migration.fresh_btn')}</Button>
          <Button mode="contained" onPress={onMigrate}>
            {t('auth.migration.migrate_btn')}
          </Button>
        </Dialog.Actions>
      </Dialog>
    </Portal>
  );
}

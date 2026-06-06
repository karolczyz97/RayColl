import React, { useState } from 'react';
import { Button, Dialog, Portal, Text } from 'react-native-paper';
import { useI18n } from '@/i18n';
import { dialogStyles } from '@/theme/dialogStyles';

type MigrationAction = 'migrate' | 'fresh' | 'dismiss';

interface MigrationDialogProps {
  visible: boolean;
  onMigrate: () => void | Promise<void>;
  onStartFresh: () => void | Promise<void>;
  onDismiss: () => void | Promise<void>;
}

export function MigrationDialog({
  visible,
  onMigrate,
  onStartFresh,
  onDismiss,
}: MigrationDialogProps) {
  const { t } = useI18n();
  const [pendingAction, setPendingAction] = useState<MigrationAction | null>(null);
  const pending = pendingAction !== null;

  const run =
    (action: MigrationAction, handler: () => void | Promise<void>) => async () => {
      if (pending) return; // block double-clicks while an action is in flight
      setPendingAction(action);
      try {
        await handler();
        // On success the parent flips `visible` to false. On failure the error is
        // surfaced via store sync state and the dialog stays open for a retry.
      } catch {
        // keep the dialog open; nothing to do here
      } finally {
        setPendingAction(null);
      }
    };

  return (
    <Portal>
      <Dialog
        visible={visible}
        onDismiss={run('dismiss', onDismiss)}
        dismissable={!pending}
        style={dialogStyles.dialog}
      >
        <Dialog.Title>{t('auth.migration.title')}</Dialog.Title>
        <Dialog.Content>
          <Text selectable>{t('auth.migration.desc')}</Text>
        </Dialog.Content>
        <Dialog.Actions>
          <Button
            disabled={pending}
            loading={pendingAction === 'dismiss'}
            onPress={run('dismiss', onDismiss)}
          >
            {t('btn.cancel')}
          </Button>
          <Button
            disabled={pending}
            loading={pendingAction === 'fresh'}
            onPress={run('fresh', onStartFresh)}
          >
            {t('auth.migration.fresh_btn')}
          </Button>
          <Button
            mode="contained"
            disabled={pending}
            loading={pendingAction === 'migrate'}
            onPress={run('migrate', onMigrate)}
          >
            {t('auth.migration.migrate_btn')}
          </Button>
        </Dialog.Actions>
      </Dialog>
    </Portal>
  );
}

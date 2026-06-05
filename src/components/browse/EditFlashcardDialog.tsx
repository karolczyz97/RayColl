import React from 'react';
import { Button, Dialog, Portal } from 'react-native-paper';
import type { FlashcardGroup } from '@/types/models';
import { useI18n } from '@/i18n';
import { dialogStyles } from '@/theme/dialogStyles';
import { EditFlashcardForm } from './EditFlashcardForm';

interface EditFlashcardDialogProps {
  visible: boolean;
  group: FlashcardGroup;
  editPages: string[];
  onPagesChange: (pages: string[]) => void;
  onSave: () => void;
  onCancel: () => void;
  saveDisabled?: boolean;
  validationMessage?: string;
}

export function EditFlashcardDialog({
  visible,
  group,
  editPages,
  onPagesChange,
  onSave,
  onCancel,
  saveDisabled,
  validationMessage,
}: EditFlashcardDialogProps) {
  const { t } = useI18n();

  return (
    <Portal>
      <Dialog visible={visible} onDismiss={onCancel} style={dialogStyles.dialog}>
        <Dialog.Title>{t('browse.edit_card')}</Dialog.Title>
        <Dialog.Content>
          <EditFlashcardForm
            group={group}
            editPages={editPages}
            onPagesChange={onPagesChange}
            validationMessage={validationMessage}
          />
        </Dialog.Content>
        <Dialog.Actions>
          <Button onPress={onCancel}>{t('btn.cancel')}</Button>
          <Button mode="contained" onPress={onSave} disabled={saveDisabled}>
            {t('btn.save')}
          </Button>
        </Dialog.Actions>
      </Dialog>
    </Portal>
  );
}

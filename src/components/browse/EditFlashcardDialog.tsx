import React from 'react';
import { Button, Dialog, Portal } from 'react-native-paper';
import type { FlashcardGroup } from '@/types/models';
import type { TranslationFn } from '@/i18n';
import { dialogStyles } from '@/theme/dialogStyles';
import { EditFlashcardForm } from './EditFlashcardForm';

interface Props {
  visible: boolean;
  group: FlashcardGroup;
  editPages: string[];
  setEditPages: React.Dispatch<React.SetStateAction<string[]>>;
  onSave: () => void;
  onCancel: () => void;
  saveDisabled?: boolean;
  validationMessage?: string;
  t: TranslationFn;
}

export function EditFlashcardDialog({
  visible,
  group,
  editPages,
  setEditPages,
  onSave,
  onCancel,
  saveDisabled,
  validationMessage,
  t,
}: Props) {
  return (
    <Portal>
      <Dialog visible={visible} onDismiss={onCancel} style={dialogStyles.dialog}>
        <Dialog.Title>{t('browse.edit_card')}</Dialog.Title>
        <Dialog.Content>
          <EditFlashcardForm
            group={group}
            editPages={editPages}
            setEditPages={setEditPages}
            validationMessage={validationMessage}
            t={t}
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

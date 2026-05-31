import React from 'react';
import type { TranslationFn } from '../../i18n';
import { DangerDialog } from '../dialogs/DangerDialog';

interface Props {
  visible: boolean;
  onDismiss: () => void;
  deckName: string;
  deleteConfirmText: string;
  setDeleteConfirmText: (text: string) => void;
  onDelete: () => void;
  t: TranslationFn;
}

export function DeleteDeckDialog({
  visible,
  onDismiss,
  deckName,
  deleteConfirmText,
  setDeleteConfirmText,
  onDelete,
  t,
}: Props) {
  return (
    <DangerDialog
      visible={visible}
      title={t('settings.delete_btn')}
      message={t('settings.dialog.delete.confirm_text', { name: deckName, token: t('dialog.delete.token') })}
      confirmLabel={t('btn.delete')}
      cancelLabel={t('btn.cancel')}
      requiredText={t('dialog.delete.token')}
      typedText={deleteConfirmText}
      onTypedTextChange={setDeleteConfirmText}
      onConfirm={onDelete}
      onDismiss={onDismiss}
    />
  );
}

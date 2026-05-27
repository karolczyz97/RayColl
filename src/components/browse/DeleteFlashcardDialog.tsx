import React from 'react';
import type { TranslationFn } from '../../i18n';
import { ConfirmDialog } from '../dialogs/ConfirmDialog';

interface Props {
  visible: boolean;
  onDismiss: () => void;
  onConfirm: () => void;
  t: TranslationFn;
}

export function DeleteFlashcardDialog({ visible, onDismiss, onConfirm, t }: Props) {
  return (
    <ConfirmDialog
      visible={visible}
      title={t('browse.delete_card')}
      message={t('dialog.delete.desc')}
      confirmLabel={t('btn.delete')}
      cancelLabel={t('btn.cancel')}
      onConfirm={onConfirm}
      onDismiss={onDismiss}
      destructive
    />
  );
}

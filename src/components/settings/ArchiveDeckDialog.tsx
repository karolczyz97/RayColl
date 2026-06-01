import React from 'react';
import type { TranslationFn } from '../../i18n';
import { ConfirmDialog } from '../dialogs/ConfirmDialog';
import { ARCHIVE_RETENTION_DAYS } from '../../constants/archive';

interface Props {
  visible: boolean;
  onDismiss: () => void;
  onConfirm: () => void;
  t: TranslationFn;
}

export function ArchiveDeckDialog({ visible, onDismiss, onConfirm, t }: Props) {
  return (
    <ConfirmDialog
      visible={visible}
      title={t('dialog.archive.title')}
      message={t('dialog.archive.confirm', { days: ARCHIVE_RETENTION_DAYS })}
      confirmLabel={t('btn.archive')}
      cancelLabel={t('btn.cancel')}
      onConfirm={onConfirm}
      onDismiss={onDismiss}
    />
  );
}

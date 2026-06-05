import React from 'react';
import { useI18n, type TranslationReplacements } from '@/i18n';
import { ConfirmDialog } from './ConfirmDialog';

interface ActionConfirmDialogProps {
  visible: boolean;
  onDismiss: () => void;
  onConfirm: () => void;
  titleKey: string;
  messageKey: string;
  confirmLabelKey: string;
  cancelLabelKey?: string;
  messageReplacements?: TranslationReplacements;
  destructive?: boolean;
  disabled?: boolean;
}

export function ActionConfirmDialog({
  visible,
  onDismiss,
  onConfirm,
  titleKey,
  messageKey,
  confirmLabelKey,
  cancelLabelKey = 'btn.cancel',
  messageReplacements,
  destructive = false,
  disabled = false,
}: ActionConfirmDialogProps) {
  const { t } = useI18n();

  return (
    <ConfirmDialog
      visible={visible}
      title={t(titleKey)}
      message={t(messageKey, messageReplacements)}
      confirmLabel={t(confirmLabelKey)}
      cancelLabel={t(cancelLabelKey)}
      onConfirm={onConfirm}
      onDismiss={onDismiss}
      destructive={destructive}
      disabled={disabled}
    />
  );
}

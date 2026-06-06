import React from 'react';
import { Snackbar } from 'react-native-paper';
import { TOKENS } from '@/theme/tokens';
import { useI18n } from '@/i18n';

interface AppSnackbarProps {
  visible: boolean;
  message: string;
  onDismiss: () => void;
  actionLabel?: string;
  duration?: number;
}

export function AppSnackbar({
  visible,
  message,
  onDismiss,
  actionLabel,
  duration = TOKENS.motion.duration.snackbar,
}: AppSnackbarProps) {
  const { t } = useI18n();
  return (
    <Snackbar
      visible={visible}
      onDismiss={onDismiss}
      duration={duration}
      action={{ label: actionLabel ?? t('common.ok'), onPress: onDismiss }}
    >
      {message}
    </Snackbar>
  );
}

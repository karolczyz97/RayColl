import React from 'react';
import { Snackbar } from 'react-native-paper';

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
  actionLabel = 'OK',
  duration = 6000,
}: AppSnackbarProps) {
  return (
    <Snackbar
      visible={visible}
      onDismiss={onDismiss}
      duration={duration}
      action={{ label: actionLabel, onPress: onDismiss }}
    >
      {message}
    </Snackbar>
  );
}

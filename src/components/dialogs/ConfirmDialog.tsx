import React from 'react';
import { Button, Dialog, Text, useTheme } from 'react-native-paper';
import { dialogStyles } from '../../theme/dialogStyles';

export interface ConfirmDialogProps {
  visible: boolean;
  title: string;
  message: string;
  confirmLabel: string;
  cancelLabel: string;
  onConfirm: () => void;
  onDismiss: () => void;
  destructive?: boolean;
  disabled?: boolean;
}

export function ConfirmDialog({
  visible,
  title,
  message,
  confirmLabel,
  cancelLabel,
  onConfirm,
  onDismiss,
  destructive = false,
  disabled = false,
}: ConfirmDialogProps) {
  const theme = useTheme();

  return (
    <Dialog visible={visible} onDismiss={onDismiss} style={dialogStyles.dialog}>
      <Dialog.Title>{title}</Dialog.Title>
      <Dialog.Content>
        <Text selectable>{message}</Text>
      </Dialog.Content>
      <Dialog.Actions>
        <Button onPress={onDismiss}>{cancelLabel}</Button>
        <Button
          mode="contained"
          onPress={onConfirm}
          buttonColor={destructive ? theme.colors.error : undefined}
          textColor={destructive ? theme.colors.onError : undefined}
          disabled={disabled}
        >
          {confirmLabel}
        </Button>
      </Dialog.Actions>
    </Dialog>
  );
}

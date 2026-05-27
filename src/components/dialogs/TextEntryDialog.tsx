import React from 'react';
import { Button, Dialog, Portal } from 'react-native-paper';
import { AppTextInput } from '../forms/AppTextInput';

interface TextEntryDialogProps {
  visible: boolean;
  title: string;
  value: string;
  onChangeText: (value: string) => void;
  onDismiss: () => void;
  onConfirm: () => void;
  confirmLabel: string;
  cancelLabel: string;
  placeholder?: string;
  multiline?: boolean;
  numberOfLines?: number;
  disabled?: boolean;
}

export function TextEntryDialog({
  visible,
  title,
  value,
  onChangeText,
  onDismiss,
  onConfirm,
  confirmLabel,
  cancelLabel,
  placeholder,
  multiline = false,
  numberOfLines,
  disabled = false,
}: TextEntryDialogProps) {
  return (
    <Portal>
      <Dialog visible={visible} onDismiss={onDismiss}>
        <Dialog.Title>{title}</Dialog.Title>
        <Dialog.Content>
          <AppTextInput
            multiline={multiline}
            numberOfLines={numberOfLines}
            placeholder={placeholder}
            value={value}
            onChangeText={onChangeText}
          />
        </Dialog.Content>
        <Dialog.Actions>
          <Button onPress={onDismiss}>{cancelLabel}</Button>
          <Button mode="contained" onPress={onConfirm} disabled={disabled}>
            {confirmLabel}
          </Button>
        </Dialog.Actions>
      </Dialog>
    </Portal>
  );
}

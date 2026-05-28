import React from 'react';
import { StyleSheet, View } from 'react-native';
import { Button, Dialog, Portal, Text, useTheme } from 'react-native-paper';
import { AppTextInput } from '../forms/AppTextInput';
import { dialogStyles } from '../../theme/dialogStyles';
import { TOKENS } from '../../theme/tokens';

interface DangerDialogProps {
  visible: boolean;
  title: string;
  message: string;
  confirmLabel: string;
  cancelLabel: string;
  onConfirm: () => void;
  onDismiss: () => void;
  requiredText?: string;
  typedText?: string;
  onTypedTextChange?: (value: string) => void;
}

export function DangerDialog({
  visible,
  title,
  message,
  confirmLabel,
  cancelLabel,
  onConfirm,
  onDismiss,
  requiredText,
  typedText,
  onTypedTextChange,
}: DangerDialogProps) {
  const theme = useTheme();
  const isDisabled = !!requiredText && typedText !== requiredText;

  return (
    <Portal>
      <Dialog visible={visible} onDismiss={onDismiss} style={dialogStyles.dialog}>
        <Dialog.Title>{title}</Dialog.Title>
        <Dialog.Content>
          <View style={styles.content}>
            <Text selectable>{message}</Text>
            {requiredText ? (
              <AppTextInput
                value={typedText ?? ''}
                onChangeText={onTypedTextChange}
                placeholder={requiredText}
                accessibilityLabel="Danger confirmation input"
              />
            ) : null}
          </View>
        </Dialog.Content>
        <Dialog.Actions>
          <Button onPress={onDismiss}>{cancelLabel}</Button>
          <Button
            mode="contained"
            buttonColor={theme.colors.error}
            textColor={theme.colors.onError}
            onPress={onConfirm}
            disabled={isDisabled}
          >
            {confirmLabel}
          </Button>
        </Dialog.Actions>
      </Dialog>
    </Portal>
  );
}

const styles = StyleSheet.create({
  content: {
    gap: TOKENS.spacing.md,
  },
});

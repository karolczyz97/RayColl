import React from 'react';
import { Button, Dialog, HelperText, Portal } from 'react-native-paper';
import { dialogStyles } from '@/theme/dialogStyles';
import { AppTextInput } from '@/components/forms/AppTextInput';

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
  required?: boolean;
  requiredMessage?: string;
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
  required = false,
  requiredMessage,
}: TextEntryDialogProps) {
  const [touched, setTouched] = React.useState(false);
  const [submitAttempted, setSubmitAttempted] = React.useState(false);
  const showRequiredError = required && (touched || submitAttempted) && !value.trim();

  const resetValidation = () => {
    setTouched(false);
    setSubmitAttempted(false);
  };

  const handleConfirm = () => {
    setSubmitAttempted(true);
    if (disabled || (required && !value.trim())) {
      return;
    }

    resetValidation();
    onConfirm();
  };

  const handleDismiss = () => {
    resetValidation();
    onDismiss();
  };

  return (
    <Portal>
      <Dialog visible={visible} onDismiss={handleDismiss} style={dialogStyles.dialog}>
        <Dialog.Title>{title}</Dialog.Title>
        <Dialog.Content>
          <AppTextInput
            multiline={multiline}
            numberOfLines={numberOfLines}
            placeholder={placeholder}
            value={value}
            onChangeText={onChangeText}
            onBlur={() => setTouched(true)}
            error={showRequiredError}
          />
          {showRequiredError && requiredMessage ? (
            <HelperText type="error" visible>
              {requiredMessage}
            </HelperText>
          ) : null}
        </Dialog.Content>
        <Dialog.Actions>
          <Button onPress={handleDismiss}>{cancelLabel}</Button>
          <Button mode="contained" onPress={handleConfirm}>
            {confirmLabel}
          </Button>
        </Dialog.Actions>
      </Dialog>
    </Portal>
  );
}

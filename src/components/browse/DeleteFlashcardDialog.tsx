import React from 'react';
import { Dialog, Button, Text, useTheme } from 'react-native-paper';

interface Props {
  visible: boolean;
  onDismiss: () => void;
  onConfirm: () => void;
  t: (key: string, replacements?: any) => string;
}

export function DeleteFlashcardDialog({ visible, onDismiss, onConfirm, t }: Props) {
  const theme = useTheme();

  return (
    <Dialog visible={visible} onDismiss={onDismiss}>
      <Dialog.Title>{t('browse.delete_card')}</Dialog.Title>
      <Dialog.Content>
        <Text>{t('dialog.delete.desc')}</Text>
      </Dialog.Content>
      <Dialog.Actions>
        <Button onPress={onDismiss}>{t('btn.cancel')}</Button>
        <Button
          mode="contained"
          buttonColor={theme.colors.error}
          onPress={onConfirm}
          accessibilityLabel="Confirm deletion button"
        >
          {t('btn.delete')}
        </Button>
      </Dialog.Actions>
    </Dialog>
  );
}

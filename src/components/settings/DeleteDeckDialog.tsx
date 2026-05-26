import React from 'react';
import { StyleSheet } from 'react-native';
import { Dialog, Button, Text, TextInput, useTheme } from 'react-native-paper';

interface Props {
  visible: boolean;
  onDismiss: () => void;
  deckName: string;
  deleteConfirmText: string;
  setDeleteConfirmText: (text: string) => void;
  onDelete: () => void;
  t: (key: string, replacements?: any) => string;
}

export function DeleteDeckDialog({
  visible,
  onDismiss,
  deckName,
  deleteConfirmText,
  setDeleteConfirmText,
  onDelete,
  t,
}: Props) {
  const theme = useTheme();

  return (
    <Dialog visible={visible} onDismiss={onDismiss}>
      <Dialog.Title>{t('settings.delete_btn')}</Dialog.Title>
      <Dialog.Content>
        <Text style={styles.confirmText}>
          {t('settings.dialog.delete.confirm_text', { name: deckName })}
        </Text>
        <TextInput
          mode="outlined"
          value={deleteConfirmText}
          onChangeText={setDeleteConfirmText}
          placeholder="DELETE"
          style={styles.input}
          outlineStyle={{ borderRadius: 12 }}
          accessibilityLabel="Confirm deletion input"
        />
      </Dialog.Content>
      <Dialog.Actions>
        <Button onPress={onDismiss}>{t('btn.cancel')}</Button>
        <Button
          mode="contained"
          buttonColor={theme.colors.error}
          onPress={onDelete}
          disabled={deleteConfirmText !== 'DELETE'}
          accessibilityLabel="Delete deck button"
        >
          {t('btn.delete')}
        </Button>
      </Dialog.Actions>
    </Dialog>
  );
}

const styles = StyleSheet.create({
  confirmText: {
    marginBottom: 12,
  },
  input: {
    height: 44,
  },
});

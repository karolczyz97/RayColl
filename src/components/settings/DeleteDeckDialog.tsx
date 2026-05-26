import React from 'react';
import { StyleSheet } from 'react-native';
import { Dialog, Button, Text, TextInput, useTheme } from 'react-native-paper';
import type { TranslationFn } from '../../i18n';
import { TOKENS } from '../../theme/tokens';
import { dialogStyles } from '../../theme/dialogStyles';

interface Props {
  visible: boolean;
  onDismiss: () => void;
  deckName: string;
  deleteConfirmText: string;
  setDeleteConfirmText: (text: string) => void;
  onDelete: () => void;
  t: TranslationFn;
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
    <Dialog visible={visible} onDismiss={onDismiss} style={dialogStyles.dialog}>
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
          outlineStyle={styles.inputOutline}
          accessibilityLabel="Confirm deletion input"
        />
      </Dialog.Content>
      <Dialog.Actions>
        <Button onPress={onDismiss}>{t('btn.cancel')}</Button>
        <Button
          mode="contained"
          buttonColor={theme.colors.error}
          textColor={theme.colors.onError}
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
    marginBottom: TOKENS.spacing.md,
  },
  input: {
    height: TOKENS.control.height,
  },
  inputOutline: {
    borderRadius: TOKENS.control.borderRadius,
  },
});

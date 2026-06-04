import React from 'react';
import { StyleSheet, View } from 'react-native';
import { Button, Dialog, Portal, Text } from 'react-native-paper';
import { useI18n } from '@/i18n';
import { releaseInfo } from '@/config/releaseInfo';
import { dialogStyles } from '@/theme/dialogStyles';
import { TOKENS } from '@/theme/tokens';

interface ChangelogDialogProps {
  visible: boolean;
  onDismiss: () => void;
}

export function ChangelogDialog({ visible, onDismiss }: ChangelogDialogProps) {
  const { t } = useI18n();

  return (
    <Portal>
      <Dialog visible={visible} onDismiss={onDismiss} style={dialogStyles.dialog}>
        <Dialog.Title>{releaseInfo.commitTitle}</Dialog.Title>
        <Dialog.Content>
          {releaseInfo.notes.length > 0 ? (
            <View style={styles.notesList}>
              {releaseInfo.notes.map((note, index) => (
                <Text key={index} variant="bodyMedium" style={styles.noteItem}>
                  {'• '}
                  {note}
                </Text>
              ))}
            </View>
          ) : (
            <Text variant="bodyMedium">{releaseInfo.commitTitle}</Text>
          )}
        </Dialog.Content>
        <Dialog.Actions>
          <Button mode="contained" onPress={onDismiss}>
            {t('update.ok')}
          </Button>
        </Dialog.Actions>
      </Dialog>
    </Portal>
  );
}

const styles = StyleSheet.create({
  notesList: {
    gap: TOKENS.spacing.sm,
  },
  noteItem: {
    lineHeight: 22,
  },
});

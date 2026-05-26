import React from 'react';
import { StyleSheet } from 'react-native';
import { Dialog, Button, TextInput } from 'react-native-paper';
import { AppSelect } from '../AppSelect';
import type { TranslationFn } from '../../i18n';
import { TOKENS } from '../../theme/tokens';
import { dialogStyles } from '../../theme/dialogStyles';

interface Props {
  visible: boolean;
  onDismiss: () => void;
  newStepType: string;
  setNewStepType: (type: string) => void;
  newPageIdx: number;
  setNewPageIdx: (idx: number) => void;
  newMs: number;
  setNewMs: (ms: number) => void;
  newThreshold: number;
  setNewThreshold: (threshold: number) => void;
  confirmAddStep: () => void;
  t: TranslationFn;
  stepLabels: Record<string, string>;
}

export function AddStepDialog({
  visible,
  onDismiss,
  newStepType,
  setNewStepType,
  newPageIdx,
  setNewPageIdx,
  newMs,
  setNewMs,
  newThreshold,
  setNewThreshold,
  confirmAddStep,
  t,
  stepLabels,
}: Props) {
  const stepOptions = Object.entries(stepLabels).map(([key, label]) => ({ label, value: key }));

  return (
    <Dialog visible={visible} onDismiss={onDismiss} style={dialogStyles.dialog}>
      <Dialog.Title>{t('settings.dialog.add_step.title')}</Dialog.Title>
      <Dialog.Content style={styles.dialogContent}>
        <AppSelect
          label={t('settings.dialog.add_step.type')}
          value={newStepType}
          options={stepOptions}
          onChange={setNewStepType}
          accessibilityLabel="Select step type"
        />

        {newStepType !== 'wait' && (
          <TextInput
            mode="outlined"
            label={t('settings.dialog.add_step.page_idx')}
            keyboardType="numeric"
            value={String(newPageIdx)}
            onChangeText={(value) => setNewPageIdx(Number(value) || 0)}
            style={styles.input}
            outlineStyle={styles.inputOutline}
            accessibilityLabel="Page index input"
          />
        )}

        {(newStepType === 'speak_page' ||
          newStepType === 'dynamic_pause' ||
          newStepType === 'wait') && (
          <TextInput
            mode="outlined"
            label={t('settings.dialog.add_step.time')}
            keyboardType="numeric"
            value={String(newMs)}
            onChangeText={(value) => setNewMs(Number(value) || 0)}
            style={styles.input}
            outlineStyle={styles.inputOutline}
            accessibilityLabel="Duration in milliseconds input"
          />
        )}

        {newStepType === 'listen_and_branch' && (
          <TextInput
            mode="outlined"
            label={t('settings.dialog.add_step.threshold')}
            keyboardType="numeric"
            value={String(newThreshold)}
            onChangeText={(value) => setNewThreshold(Number(value) || 0)}
            style={styles.input}
            outlineStyle={styles.inputOutline}
            accessibilityLabel="Success threshold input"
          />
        )}
      </Dialog.Content>
      <Dialog.Actions>
        <Button onPress={onDismiss}>{t('btn.cancel')}</Button>
        <Button mode="contained" onPress={confirmAddStep} accessibilityLabel="Add step button">
          {t('btn.add')}
        </Button>
      </Dialog.Actions>
    </Dialog>
  );
}

const styles = StyleSheet.create({
  dialogContent: {
    gap: TOKENS.spacing.md,
  },
  input: {
    height: TOKENS.control.height,
  },
  inputOutline: {
    borderRadius: TOKENS.control.borderRadius,
  },
});

import React from 'react';
import { StyleSheet } from 'react-native';
import { Dialog, Button } from 'react-native-paper';
import { AppSelect } from '../AppSelect';
import type { TranslationFn } from '../../i18n';
import { TOKENS } from '../../theme/tokens';
import { dialogStyles } from '../../theme/dialogStyles';
import { AppNumberInput } from '../forms/AppNumberInput';

interface Props {
  visible: boolean;
  onDismiss: () => void;
  newStepType: string;
  setNewStepType: (type: string) => void;
  newPageIdx: number;
  pageCount: number;
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
  pageCount,
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

        {newStepType !== 'wait' &&
          newStepType !== 'reveal_on_tap' &&
          newStepType !== 'rate' && (
            <AppNumberInput
              label={t('settings.dialog.add_step.page_idx')}
              value={newPageIdx}
              onChange={setNewPageIdx}
              min={0}
              max={Math.max(0, pageCount - 1)}
              accessibilityLabel="Page index input"
            />
          )}

        {(newStepType === 'speak_page' ||
          newStepType === 'dynamic_pause' ||
          newStepType === 'wait') && (
          <AppNumberInput
            label={t('settings.dialog.add_step.time')}
            value={newMs}
            onChange={setNewMs}
            min={0}
            accessibilityLabel="Duration in milliseconds input"
          />
        )}

        {newStepType === 'listen_and_branch' && (
          <AppNumberInput
            label={t('settings.dialog.add_step.threshold')}
            value={newThreshold}
            onChange={setNewThreshold}
            min={0}
            max={100}
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
});

import React from 'react';
import { StyleSheet } from 'react-native';
import { Dialog, Button, Portal } from 'react-native-paper';
import { AppSelect } from '@/components/AppSelect';
import { useI18n } from '@/i18n';
import { TOKENS } from '@/theme/tokens';
import { dialogStyles } from '@/theme/dialogStyles';
import { AppNumberInput } from '@/components/forms/AppNumberInput';
import { MAX_PAUSE_MULTIPLIER } from '@/store/storeDataNormalization';

interface AddStepDialogProps {
  visible: boolean;
  onDismiss: () => void;
  newStepType: string;
  setNewStepType: (type: string) => void;
  newPageIdx: number;
  pageCount: number;
  setNewPageIdx: (idx: number) => void;
  newMs: number;
  setNewMs: (ms: number) => void;
  newPauseMultiplier: number;
  setNewPauseMultiplier: (multiplier: number) => void;
  newThreshold: number;
  setNewThreshold: (threshold: number) => void;
  confirmAddStep: () => void;
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
  newPauseMultiplier,
  setNewPauseMultiplier,
  newThreshold,
  setNewThreshold,
  confirmAddStep,
  stepLabels,
}: AddStepDialogProps) {
  const { t } = useI18n();
  const stepOptions = Object.entries(stepLabels).map(([key, label]) => ({ label, value: key }));

  return (
    <Portal>
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
            newStepType !== 'rate' &&
            newStepType !== 'next_card' && (
              <AppNumberInput
                label={t('settings.dialog.add_step.page_idx')}
                value={newPageIdx + 1}
                onChange={(page) => setNewPageIdx(page - 1)}
                min={1}
                max={Math.max(1, pageCount)}
                accessibilityLabel="Page number input"
              />
            )}

          {newStepType === 'speak_page' && (
            <AppNumberInput
              label={t('settings.dialog.add_step.pause_multiplier')}
              value={newPauseMultiplier}
              onChange={setNewPauseMultiplier}
              min={0}
              max={MAX_PAUSE_MULTIPLIER}
              accessibilityLabel="Pause multiplier input"
            />
          )}

          {newStepType === 'wait' && (
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
    </Portal>
  );
}

const styles = StyleSheet.create({
  dialogContent: {
    gap: TOKENS.spacing.md,
  },
});

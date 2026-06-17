import React, { useCallback, useState } from 'react';
import { StyleSheet } from 'react-native';
import { Dialog, Button, Portal } from 'react-native-paper';
import { AppSelect } from '@/components/AppSelect';
import { useI18n } from '@/i18n';
import { TOKENS } from '@/theme/tokens';
import { dialogStyles } from '@/theme/dialogStyles';
import { AppNumberInput } from '@/components/forms/AppNumberInput';
import { MAX_PAUSE_MULTIPLIER } from '@/store/storeDataNormalization';
import type { AtomicStep, StepCondition } from '@/types/models';
import { ATOMIC_STEP_TYPE_ORDER, ATOMIC_STEP_LABEL_KEYS, buildModeStep } from '@/features/settings/buildModeStep';

// Kroki, które operują na konkretnej stronie (pageIndex / nextPageIndex).
const PAGE_INDEX_STEP_TYPES = ['show_page', 'speak_page', 'listen_and_check', 'dynamic_pause'];

interface AddStepDialogProps {
  visible: boolean;
  pageCount: number;
  onDismiss: () => void;
  onConfirm: (step: AtomicStep) => void;
}

export function AddStepDialog({
  visible,
  pageCount,
  onDismiss,
  onConfirm,
}: AddStepDialogProps) {
  const { t } = useI18n();

  const [newStepType, setNewStepType] = useState<string>('show_page');
  const [newPageIdx, setNewPageIdx] = useState(0);
  const [newMs, setNewMs] = useState(500);
  const [newPauseMultiplier, setNewPauseMultiplier] = useState(1);
  const [newThreshold, setNewThreshold] = useState(70);
  const [newRating, setNewRating] = useState(3);
  const [newCondition, setNewCondition] = useState<'always' | StepCondition>('always');

  const confirmAddStep = useCallback(() => {
    const step = buildModeStep({
      newStepType,
      pageCount,
      newPageIdx,
      newMs,
      newPauseMultiplier,
      newThreshold,
      newRating,
      newCondition,
    });
    if (step) {
      onConfirm(step);
    }
  }, [
    newStepType,
    pageCount,
    newPageIdx,
    newMs,
    newPauseMultiplier,
    newThreshold,
    newRating,
    newCondition,
    onConfirm,
  ]);

  const stepOptions = ATOMIC_STEP_TYPE_ORDER.map((type) => ({
    label: t(ATOMIC_STEP_LABEL_KEYS[type]),
    value: type,
  }));

  const conditionOptions = [
    { label: t('step.condition.always'), value: 'always' },
    { label: t('step.condition.correct'), value: 'correct' },
    { label: t('step.condition.wrong'), value: 'wrong' },
  ];

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

          {PAGE_INDEX_STEP_TYPES.includes(newStepType) && (
            <AppNumberInput
              label={t('settings.dialog.add_step.page_idx')}
              value={newPageIdx + 1}
              onChange={(page) => setNewPageIdx(page - 1)}
              min={1}
              max={Math.max(1, pageCount)}
              accessibilityLabel="Page number input"
            />
          )}

          {newStepType === 'dynamic_pause' && (
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

          {newStepType === 'listen_and_check' && (
            <AppNumberInput
              label={t('settings.dialog.add_step.threshold')}
              value={newThreshold}
              onChange={setNewThreshold}
              min={0}
              max={100}
              accessibilityLabel="Success threshold input"
            />
          )}

          {newStepType === 'auto_rate_fixed' && (
            <AppNumberInput
              label={t('settings.dialog.add_step.rating')}
              value={newRating}
              onChange={setNewRating}
              min={1}
              max={4}
              accessibilityLabel="Rating input"
            />
          )}

          <AppSelect
            label={t('settings.dialog.add_step.condition')}
            value={newCondition}
            options={conditionOptions}
            onChange={(value) => {
              if (value === 'always' || value === 'correct' || value === 'wrong') {
                setNewCondition(value);
              }
            }}
            accessibilityLabel="Select step condition"
          />
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

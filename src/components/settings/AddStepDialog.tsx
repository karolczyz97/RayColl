import React, { useCallback, useState } from 'react';
import { StyleSheet } from 'react-native';
import { Dialog, Button, Portal } from 'react-native-paper';
import { AppSelect } from '@/components/AppSelect';
import { useI18n } from '@/i18n';
import { TOKENS } from '@/theme/tokens';
import { dialogStyles } from '@/theme/dialogStyles';
import { AppNumberInput } from '@/components/forms/AppNumberInput';
import type { AtomicStep, StepCondition } from '@/types/models';
import {
  ATOMIC_STEP_REGISTRY,
  buildAtomicStep,
  getStepDefinition,
  getStepPageIndex,
  isAtomicStepType,
  type StepFieldSpec,
} from '@/features/settings/stepRegistry';

interface AddStepDialogProps {
  visible: boolean;
  mode: 'add' | 'edit';
  initialStep: AtomicStep | null;
  pageCount: number;
  onDismiss: () => void;
  onConfirm: (step: AtomicStep) => void;
}

// Wartości pól formularza. Pole strony ma zawsze wspólny klucz 'page' —
// dzięki temu wybrana strona przenosi się przy przełączaniu typu kroku
// (parytet z dotychczasowym zachowaniem). Pola liczbowe leżą pod nazwą parametru.
function initialValues(step: AtomicStep | null): Record<string, number> {
  const values: Record<string, number> = { page: step ? (getStepPageIndex(step) ?? 0) : 0 };
  if (step) {
    for (const [param, spec] of Object.entries(
      getStepDefinition(step.type).fields as Record<string, StepFieldSpec>,
    )) {
      if (spec.kind === 'number') {
        values[param] = (step as unknown as Record<string, number>)[param];
      }
    }
  }
  return values;
}

export function AddStepDialog({
  visible,
  mode,
  initialStep,
  pageCount,
  onDismiss,
  onConfirm,
}: AddStepDialogProps) {
  const { t } = useI18n();

  const [stepType, setStepType] = useState<string>(initialStep?.type ?? 'show_page');
  const [values, setValues] = useState<Record<string, number>>(() => initialValues(initialStep));
  const [condition, setCondition] = useState<'always' | StepCondition>(
    initialStep?.condition ?? 'always',
  );

  const setValue = useCallback((key: string, value: number) => {
    setValues((prev) => ({ ...prev, [key]: value }));
  }, []);

  const fieldEntries = isAtomicStepType(stepType)
    ? Object.entries(getStepDefinition(stepType).fields as Record<string, StepFieldSpec>)
    : [];

  const confirmAddStep = useCallback(() => {
    if (!isAtomicStepType(stepType)) return;
    const collected: Record<string, number> = {};
    for (const [param, spec] of Object.entries(
      getStepDefinition(stepType).fields as Record<string, StepFieldSpec>,
    )) {
      collected[param] =
        spec.kind === 'page' ? (values.page ?? 0) : (values[param] ?? spec.defaultValue);
    }
    const step = buildAtomicStep(stepType, collected, { pageCount }, condition);
    if (step) {
      onConfirm(step);
    }
  }, [stepType, values, pageCount, condition, onConfirm]);

  const stepOptions = ATOMIC_STEP_REGISTRY.map((def) => {
    // Klucz konstruowany dynamicznie — parytet kluczy pilnuje test inwariantów rejestru.
    const sectionKey = `step.category.${def.category}`;
    return {
      label: t(def.labelKey),
      value: def.type,
      section: t(sectionKey),
    };
  });

  const conditionOptions = [
    { label: t('step.condition.always'), value: 'always' },
    { label: t('step.condition.correct'), value: 'correct' },
    { label: t('step.condition.wrong'), value: 'wrong' },
  ];

  return (
    <Portal>
      <Dialog visible={visible} onDismiss={onDismiss} style={dialogStyles.dialog}>
        <Dialog.Title>
          {mode === 'edit' ? t('settings.dialog.edit_step.title') : t('settings.dialog.add_step.title')}
        </Dialog.Title>
        <Dialog.Content style={styles.dialogContent}>
          <AppSelect
            label={t('settings.dialog.add_step.type')}
            value={stepType}
            options={stepOptions}
            onChange={setStepType}
            disabled={mode === 'edit'}
            accessibilityLabel="Select step type"
          />

          {fieldEntries.map(([param, spec]) =>
            spec.kind === 'page' ? (
              <AppNumberInput
                key={param}
                label={t(spec.labelKey)}
                value={(values.page ?? spec.defaultValue) + 1}
                onChange={(page) => setValue('page', page - 1)}
                min={1}
                max={Math.max(1, pageCount)}
                accessibilityLabel={spec.accessibilityLabel}
              />
            ) : (
              <AppNumberInput
                key={param}
                label={t(spec.labelKey)}
                value={values[param] ?? spec.defaultValue}
                onChange={(value) => setValue(param, value)}
                min={spec.min}
                max={spec.max}
                accessibilityLabel={spec.accessibilityLabel}
              />
            ),
          )}

          <AppSelect
            label={t('settings.dialog.add_step.condition')}
            value={condition}
            options={conditionOptions}
            onChange={(value) => {
              if (value === 'always' || value === 'correct' || value === 'wrong') {
                setCondition(value);
              }
            }}
            accessibilityLabel="Select step condition"
          />
        </Dialog.Content>
        <Dialog.Actions>
          <Button onPress={onDismiss}>{t('btn.cancel')}</Button>
          <Button
            mode="contained"
            onPress={confirmAddStep}
            accessibilityLabel={mode === 'edit' ? 'Save step button' : 'Add step button'}
          >
            {mode === 'edit' ? t('btn.save') : t('btn.add')}
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
